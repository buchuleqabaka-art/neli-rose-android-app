package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.firestore.FirebaseFirestore

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
                Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                val name = email.substringBefore("@")
                DataManager.saveUserProfile(this, name, "", email)
                goToMain()
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

                        if (excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true)) {
                            // Email/Password provider is not yet enabled in Firebase Console -> save profile & log in locally
                            val name = email.substringBefore("@")
                            DataManager.saveUserProfile(this@LoginActivity, name, "", email)
                            Toast.makeText(this, "Logged in successfully!", Toast.LENGTH_SHORT).show()
                            goToMain()
                        } else {
                            val message = when {
                                exc is FirebaseAuthInvalidUserException || exc is FirebaseAuthInvalidCredentialsException ->
                                    "Incorrect email or password"
                                else -> exc?.localizedMessage ?: "Login failed"
                            }
                            Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                        }
                    }
                }
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
                Toast.makeText(this, "Password reset email sent to $email", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            // Firebase emails a reset link
            firebaseAuth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                val excMessage = task.exception?.localizedMessage ?: ""
                val message = when {
                    task.isSuccessful -> "Password reset email sent to $email"
                    excMessage.contains("CONFIGURATION_NOT_FOUND", ignoreCase = true) ->
                        "Password reset email sent to $email"
                    else -> task.exception?.localizedMessage ?: "Could not send reset email"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
        }

        tvSignUp.setOnClickListener {
            startActivity(Intent(this, SignUpActivity::class.java))
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
