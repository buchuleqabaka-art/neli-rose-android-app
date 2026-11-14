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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

class SignUpActivity : AppCompatActivity() {

    private var auth: FirebaseAuth? = null

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

            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Toast.makeText(this, "Sign-up is unavailable. Please contact support.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            btnCreate.isEnabled = false
            btnCreate.text = "Creating account..."

            firebaseAuth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        val user = firebaseAuth.currentUser
                        if (user != null) {
                            DataManager.saveUserProfile(this, name, phone, email)
                            // save the name on the auth profile
                            val profile = UserProfileChangeRequest.Builder()
                                .setDisplayName(name)
                                .build()
                            user.updateProfile(profile)

                            // save name + phone in Firestore under the user's id
                            val data = hashMapOf(
                                "fullName" to name,
                                "phone" to phone,
                                "email" to email
                            )
                            runCatching {
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(user.uid)
                                    .set(data)
                                    .addOnFailureListener { e ->
                                        Log.w("SignUp", "Could not save profile", e)
                                    }
                            }
                        }
                        Toast.makeText(this, "Account created successfully! Please log in.", Toast.LENGTH_LONG).show()
                        goToLogin()
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
                                "Sign-up is not configured. Please contact support.",
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
                            if (task.isSuccessful) {
                                val user = firebaseAuth.currentUser
                                if (user != null) {
                                    val name = user.displayName ?: user.email?.substringBefore("@") ?: "User"
                                    val email = user.email ?: ""
                                    DataManager.saveUserProfile(this, name, "", email)

                                    val data = hashMapOf(
                                        "fullName" to name,
                                        "email" to email
                                    )
                                    runCatching {
                                        FirebaseFirestore.getInstance()
                                            .collection("users")
                                            .document(user.uid)
                                            .set(data, SetOptions.merge())
                                    }
                                }
                                Toast.makeText(this, "Google Sign-In successful!", Toast.LENGTH_SHORT).show()
                                goToMain()
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
