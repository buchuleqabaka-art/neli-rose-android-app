package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
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
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.launch

class LoginActivity : AppCompatActivity() {

    private var auth: FirebaseAuth? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // safely initialize Firebase Auth (handles missing google-services.json gracefully)
        auth = runCatching { FirebaseAuth.getInstance() }.getOrNull()

        // already logged in -> skip straight to home
        if (auth?.currentUser != null) {
            val user = auth?.currentUser
            if (user != null) {
                val name = user.displayName ?: user.email?.substringBefore("@") ?: "User"
                DataManager.saveUserProfile(this, name, "", user.email ?: "")
            }
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

            val firebaseAuth = auth
            if (firebaseAuth == null) {
                Toast.makeText(this, "Sign-in is unavailable. Please contact support.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // stop double taps while Firebase works
            btnLogin.isEnabled = false
            btnLogin.text = "Logging in..."

            firebaseAuth.signInWithEmailAndPassword(email, password)
                .addOnCompleteListener { task ->
                    if (task.isSuccessful) {
                        Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                        val user = firebaseAuth.currentUser
                        if (user != null) {
                            val initialName = user.displayName ?: user.email?.substringBefore("@") ?: "User"
                            DataManager.saveUserProfile(this@LoginActivity, initialName, "", user.email ?: "")

                            // Fetch profile from Firestore before opening main dashboard
                            runCatching {
                                FirebaseFirestore.getInstance()
                                    .collection("users")
                                    .document(user.uid)
                                    .get()
                                    .addOnCompleteListener { docTask ->
                                        val doc = docTask.result
                                        if (docTask.isSuccessful && doc != null && doc.exists()) {
                                            val fullName = doc.getString("fullName") ?: initialName
                                            val phone = doc.getString("phone") ?: ""
                                            val mail = doc.getString("email") ?: user.email ?: ""
                                            DataManager.saveUserProfile(this@LoginActivity, fullName, phone, mail)
                                        }
                                        goToMain()
                                    }
                            }.getOrElse {
                                goToMain()
                            }
                        } else {
                            goToMain()
                        }
                    } else {
                        btnLogin.isEnabled = true
                        btnLogin.text = "Log In"

                        val exc = task.exception
                        val excMessage = exc?.localizedMessage ?: ""

                        val message = when {
                            excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) -> "Sign-in is not configured. Please contact support."
                            exc is FirebaseAuthInvalidUserException || exc is FirebaseAuthInvalidCredentialsException -> "Incorrect email or password"
                            else -> "Could not sign in. Check your connection and try again."
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
                Toast.makeText(this, "Password reset is unavailable. Please contact support.", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Firebase emails a reset link
            firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                val excMessage = task.exception?.localizedMessage ?: ""
                val message = when {
                    task.isSuccessful -> "Password reset email sent to $email"
                    excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                        "Password reset is not configured. Please contact support."
                    else -> task.exception?.localizedMessage ?: "Could not send reset email"
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
        // clear back stack so Back doesn't return to login
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
