package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "SignUp"
        // how long to wait for the profile to be stored online before moving on
        private const val PROFILE_SAVE_WAIT_MS = 10_000L
    }

    private var auth: FirebaseAuth? = null

    // set once the sign-up has moved on to the login screen, so it cannot happen twice
    private var signUpFinished = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_signup)

        // safely initialize Firebase Auth (handles missing google-services.json gracefully)
        auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()

        val tilName = findViewById<TextInputLayout>(R.id.tilName)
        val tilPhone = findViewById<TextInputLayout>(R.id.tilPhone)
        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val tilConfirm = findViewById<TextInputLayout>(R.id.tilConfirm)
        val etName = findViewById<TextInputEditText>(R.id.etName)
        val etPhone = findViewById<TextInputEditText>(R.id.etPhone)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val etConfirm = findViewById<TextInputEditText>(R.id.etConfirm)
        val btnCreate = findViewById<MaterialButton>(R.id.btnCreate)
        val btnGoogleSignUp = findViewById<MaterialButton>(R.id.btnGoogleSignUp)
        val tvLogin = findViewById<TextView>(R.id.tvLogin)
        val imgBack = findViewById<ImageView>(R.id.imgBack)

        btnCreate.setOnClickListener {
            val name = etName.text.toString().trim()
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()
            val confirm = etConfirm.text.toString()

            // the +27 is fixed in the layout, so keep digits only and drop a leading 0
            var digits = etPhone.text.toString().filter { it.isDigit() }
            if (digits.startsWith("27") && digits.length == 11) digits = digits.drop(2)
            digits = digits.removePrefix("0")
            val phone = "+27$digits"

            // clear old errors
            listOf(tilName, tilPhone, tilEmail, tilPassword, tilConfirm).forEach { it.error = null }

            // validate every field, one error at a time
            if (name.isEmpty()) {
                tilName.error = "Enter your full name"
                return@setOnClickListener
            }
            if (digits.length != 9) {
                tilPhone.error = "Enter 9 digits, e.g. 71 234 5678"
                return@setOnClickListener
            }
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Enter a valid email address"
                return@setOnClickListener
            }

            // Strong Password Validation
            val (isStrong, passwordError) = validateStrongPassword(password)
            if (!isStrong) {
                tilPassword.error = passwordError
                return@setOnClickListener
            }

            if (password != confirm) {
                tilConfirm.error = "Passwords do not match"
                return@setOnClickListener
            }

            // Without Firebase no account can be created, so no success is shown
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Toast.makeText(
                    this,
                    "Accounts cannot be created right now. Please try again later.",
                    Toast.LENGTH_LONG
                ).show()
                return@setOnClickListener
            }

            btnCreate.isEnabled = false
            btnCreate.text = "Creating account..."

            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    val user = if (task.isSuccessful) firebaseAuth.currentUser else null
                    if (user != null) {
                        // The account now exists. Only now is the profile saved.
                        val profile = UserProfileChangeRequest.Builder()
                            .setDisplayName(name)
                            .build()
                        user.updateProfile(profile)
                        saveProfileThenFinish(user, name, phone, email)
                    } else {
                        btnCreate.isEnabled = true
                        btnCreate.text = "Create Account"
                        val exc = task.exception
                        val excMessage = exc?.localizedMessage ?: ""

                        if (exc is FirebaseAuthUserCollisionException) {
                            tilEmail.error = "An account with this email already exists"
                        } else if (excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                            Toast.makeText(
                                this,
                                "Email sign-up is not switched on for this app yet. Please tell the Neli-Rose team.",
                                Toast.LENGTH_LONG
                            ).show()
                        } else {
                            Toast.makeText(
                                this,
                                exc?.localizedMessage ?: "Sign up failed",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                }
        }

        btnGoogleSignUp.setOnClickListener {
            signInWithGoogle()
        }

        // back to login (arrow and link)
        tvLogin.setOnClickListener { finish() }
        imgBack.setOnClickListener { finish() }
    }

    private fun signInWithGoogle() {
        val googleIdOption = GetGoogleIdOption.Builder()
            .setFilterByAuthorizedAccounts(false)
            .setServerClientId("487739624548-ggpdu5b15v6cbhie1rffv0lmib3gus42.apps.googleusercontent.com")
            .setAutoSelectEnabled(true)
            .build()

        val request = GetCredentialRequest.Builder()
            .addCredentialOption(googleIdOption)
            .build()

        val credentialManager = CredentialManager.create(this)

        lifecycleScope.launch {
            try {
                val result = credentialManager.getCredential(this@SignUpActivity, request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Toast.makeText(this@SignUpActivity, "Google sign-up failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@SignUpActivity, "Google sign-up error: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleGoogleCredential(credential: Credential) {
        if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
            try {
                val googleIdTokenCredential = GoogleIdTokenCredential.createFrom(credential.data)
                val googleIdToken = googleIdTokenCredential.idToken
                val firebaseCredential = GoogleAuthProvider.getCredential(googleIdToken, null)

                val firebaseAuth = auth
                if (firebaseAuth != null) {
                    firebaseAuth.signInWithCredential(firebaseCredential)
                        .addOnCompleteListener { task ->
                            val user = if (task.isSuccessful) firebaseAuth.currentUser else null
                            if (user != null) {
                                Toast.makeText(this, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                                openHomeFor(user)
                            } else {
                                Toast.makeText(this, "Authentication failed: ${task.exception?.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        }
                } else {
                    Toast.makeText(this, "Firebase not initialized", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Toast.makeText(this, "Failed to parse Google credentials: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "Unexpected credential type", Toast.LENGTH_SHORT).show()
        }
    }

    /**
     * Runs after Firebase has created the account: keeps the profile on the phone, stores it online
     * and waits for that to be confirmed (or to fail) before going to the login screen.
     */
    private fun saveProfileThenFinish(user: FirebaseUser, name: String, phone: String, email: String) {
        lifecycleScope.launch {
            try {
                DataManager.saveUserProfile(this@SignUpActivity, name, phone, email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not keep the profile on this phone", e)
            }

            val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            if (firestore == null) {
                finishSignUp(savedOnline = false)
                return@launch
            }

            // save name + phone in Firestore under the user's id
            val data = hashMapOf(
                "fullName" to name,
                "phone" to phone,
                "email" to email
            )
            firestore.collection("users")
                .document(user.uid)
                .set(data)
                .addOnCompleteListener { writeTask ->
                    if (!writeTask.isSuccessful) Log.w(TAG, "Could not save profile online", writeTask.exception)
                    finishSignUp(savedOnline = writeTask.isSuccessful)
                }

            // with no connection the write never reports back, so stop waiting after a while
            window.decorView.postDelayed({ finishSignUp(savedOnline = false) }, PROFILE_SAVE_WAIT_MS)
        }
    }

    private fun finishSignUp(savedOnline: Boolean) {
        if (signUpFinished || isFinishing) return
        signUpFinished = true
        val message = if (savedOnline) {
            "Account created successfully! Please log in."
        } else {
            "Account created. Your details are kept on this phone but are not stored online yet. Please log in."
        }
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        goToLogin()
    }

    /** Google sign-up: the account is signed in, so store what Google gave us and open the home screen. */
    private fun openHomeFor(user: FirebaseUser) {
        val name = user.displayName ?: user.email?.substringBefore("@") ?: "User"
        val email = user.email ?: ""
        runCatching {
            FirebaseFirestore.getInstance()
                .collection("users")
                .document(user.uid)
                .set(hashMapOf("fullName" to name, "email" to email), SetOptions.merge())
        }
        lifecycleScope.launch {
            try {
                DataManager.ensureUserProfile(this@SignUpActivity, name, email)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not keep the profile on this phone", e)
            }
            goToMain()
        }
    }

    private fun goToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun validateStrongPassword(password: String): Pair<Boolean, String?> {
        if (password.length < 8) {
            return Pair(false, "Password must be at least 8 characters long")
        }
        if (!password.any { it.isUpperCase() }) {
            return Pair(false, "Password must contain at least 1 uppercase letter (A-Z)")
        }
        if (!password.any { it.isLowerCase() }) {
            return Pair(false, "Password must contain at least 1 lowercase letter (a-z)")
        }
        if (!password.any { it.isDigit() }) {
            return Pair(false, "Password must contain at least 1 number (0-9)")
        }
        if (!password.any { !it.isLetterOrDigit() }) {
            return Pair(false, "Password must contain at least 1 special character (e.g. @, #, $, %)")
        }
        return Pair(true, null)
    }

    private fun goToLogin() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
