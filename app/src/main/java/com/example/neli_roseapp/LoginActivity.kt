package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.credentials.Credential
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.GetCredentialRequest
import androidx.lifecycle.lifecycleScope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "LoginActivity"
        private const val MESSAGE_SIGN_IN_UNAVAILABLE =
            "Signing in is not available right now. Please try again later."
        private const val MESSAGE_EMAIL_SIGN_IN_OFF =
            "Email sign-in is not switched on for this app yet. Please tell the Neli-Rose team."
    }

    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // safely initialize Firebase Auth (handles missing google-services.json gracefully)
        auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()

        // already logged in -> skip straight to home
        if (auth?.currentUser != null) {
            goToMain()
            return
        }

        setContentView(R.layout.activity_login)

        val tilEmail = findViewById<TextInputLayout>(R.id.tilEmail)
        val tilPassword = findViewById<TextInputLayout>(R.id.tilPassword)
        val etEmail = findViewById<TextInputEditText>(R.id.etEmail)
        val etPassword = findViewById<TextInputEditText>(R.id.etPassword)
        val btnLogin = findViewById<MaterialButton>(R.id.btnLogin)
        val btnGoogleSignIn = findViewById<MaterialButton>(R.id.btnGoogleSignIn)
        val tvForgot = findViewById<TextView>(R.id.tvForgot)
        val tvSignUp = findViewById<TextView>(R.id.tvSignUp)

        btnLogin.setOnClickListener {
            val email = etEmail.text.toString().trim()
            val password = etPassword.text.toString()

            // clear old errors
            tilEmail.error = null
            tilPassword.error = null

            // validate inputs
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Enter a valid email address"
                return@setOnClickListener
            }
            if (password.isEmpty()) {
                tilPassword.error = "Enter your password"
                return@setOnClickListener
            }

            // Without Firebase nobody can be checked, so nobody is let in
            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Toast.makeText(this, MESSAGE_SIGN_IN_UNAVAILABLE, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // stop double taps while Firebase works
            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    val user = if (task.isSuccessful) firebaseAuth.currentUser else null
                    if (user != null) {
                        // The home screen opens only here, after Firebase accepted the email and password
                        Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                        openHomeFor(user)
                    } else {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log In"

                        val exc = task.exception
                        val excMessage = exc?.localizedMessage ?: ""
                        val message = when {
                            excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                                MESSAGE_EMAIL_SIGN_IN_OFF
                            exc is FirebaseAuthInvalidUserException || exc is FirebaseAuthInvalidCredentialsException ->
                                "Incorrect email or password"
                            else -> exc?.localizedMessage ?: "Login failed"
                        }
                        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                    }
                }
        }

        btnGoogleSignIn.setOnClickListener {
            signInWithGoogle()
        }

        tvForgot.setOnClickListener {
            val email = etEmail.text.toString().trim()
            if (!Patterns.EMAIL_ADDRESS.matcher(email).matches()) {
                tilEmail.error = "Type your email above first"
                return@setOnClickListener
            }
            tilEmail.error = null

            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Toast.makeText(this, MESSAGE_SIGN_IN_UNAVAILABLE, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Firebase emails a reset link. Success is only reported when Firebase confirms the request.
            firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                val excMessage = task.exception?.localizedMessage ?: ""
                val message = when {
                    task.isSuccessful -> "Password reset email sent to $email"
                    excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) -> MESSAGE_EMAIL_SIGN_IN_OFF
                    else -> "Could not send the reset email: " + (task.exception?.localizedMessage ?: "please try again")
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
        }
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
                val result = credentialManager.getCredential(this@LoginActivity, request)
                handleGoogleCredential(result.credential)
            } catch (e: GetCredentialException) {
                Toast.makeText(this@LoginActivity, "Google sign-in failed: ${e.message}", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(this@LoginActivity, "Google sign-in error: ${e.message}", Toast.LENGTH_SHORT).show()
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
     * Called only after Firebase has signed the person in.
     * Loads the profile saved online, keeps a copy on the phone, then opens the home screen.
     */
    private fun openHomeFor(user: FirebaseUser) {
        val fallbackName = user.displayName ?: user.email?.substringBefore("@") ?: "User"
        val email = user.email ?: ""
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) {
            finishLogin(fallbackName, null, email)
            return
        }
        val userDocument = firestore.collection("users").document(user.uid)
        userDocument.get().addOnCompleteListener { docTask ->
            val doc = if (docTask.isSuccessful) docTask.result else null
            if (doc != null && doc.exists()) {
                finishLogin(doc.getString("fullName") ?: fallbackName, doc.getString("phone"), doc.getString("email") ?: email)
            } else {
                // nothing stored online yet (first Google sign-in, or an earlier save did not get through)
                if (docTask.isSuccessful) {
                    userDocument.set(hashMapOf("fullName" to fallbackName, "email" to email), SetOptions.merge())
                }
                finishLogin(fallbackName, null, email)
            }
        }
    }

    private fun finishLogin(name: String, phone: String?, email: String) {
        lifecycleScope.launch {
            try {
                if (phone != null) {
                    DataManager.saveUserProfile(this@LoginActivity, name, phone, email)
                } else {
                    DataManager.ensureUserProfile(this@LoginActivity, name, email)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Could not keep the profile on this phone", e)
            }
            goToMain()
        }
    }

    private fun goToMain() {
        // clear back stack so Back doesn't return to login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
