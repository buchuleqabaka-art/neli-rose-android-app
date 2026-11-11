package com.example.neli_roseapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions

/** The person's own details and the app's settings. Opened from the Profile button on Home. */
class ProfileActivity : AppCompatActivity() {

    private lateinit var tvNotificationStatus: TextView
    private lateinit var btnEnableNotifications: Button

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        showNotificationStatus()
        if (!granted) {
            Toast.makeText(
                this,
                "Notifications stay off. You can turn them on later in the phone's settings.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) {
            logOut()
            return
        }

        setContentView(R.layout.activity_profile)

        val etName = findViewById<EditText>(R.id.etProfileName)
        val etPhone = findViewById<EditText>(R.id.etProfilePhone)
        val tvEmail = findViewById<TextView>(R.id.tvProfileEmail)
        val btnSave = findViewById<Button>(R.id.btnProfileSave)
        val tvCaregiver = findViewById<TextView>(R.id.tvProfileCaregiver)
        val cardCareTeam = findViewById<View>(R.id.cardCareTeam)
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        btnEnableNotifications = findViewById(R.id.btnEnableNotifications)

        findViewById<View>(R.id.btnProfileBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnProfileLogout).setOnClickListener { logOut() }
        findViewById<Button>(R.id.btnCareRequests).setOnClickListener {
            startActivity(Intent(this, CareRequestsActivity::class.java))
        }

        val email = user.email ?: ""
        tvEmail.text = if (email.isBlank()) "No email on this account" else email

        launchUi {
            val profile = DataManager.getUserProfile(this@ProfileActivity)
            etName.setText(profile?.fullName ?: DataManager.getUserName(this@ProfileActivity))
            etPhone.setText(profile?.phone ?: "")

            val caregiver = DataManager.getCaregiver(this@ProfileActivity)
            tvCaregiver.text = if (caregiver == null) {
                "Not chosen yet. Choose one of your contacts on the Contact screen."
            } else {
                "${caregiver.name} (${caregiver.phone}). You can change this on the Contact screen."
            }
        }

        btnSave.setOnClickListener {
            val name = etName.text.toString().trim()
            val phone = etPhone.text.toString().trim()
            if (name.isEmpty()) {
                etName.error = "Enter your full name"
                return@setOnClickListener
            }
            if (phone.isNotEmpty() && !CareRules.isPlausiblePhone(phone)) {
                etPhone.error = "Enter a phone number with at least 7 digits, or leave it empty"
                return@setOnClickListener
            }
            btnSave.isEnabled = false
            launchUi {
                try {
                    DataManager.saveUserProfile(this@ProfileActivity, name, phone, email)
                } finally {
                    btnSave.isEnabled = true
                }
                saveOnline(user.uid, name, phone, email)
            }
        }

        btnEnableNotifications.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 33) {
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        showNotificationStatus()

        // The care team's list is only offered to accounts marked as caregiver in Firestore.
        // The Firestore rules enforce this as well, so hiding the button is not the protection.
        runCatching {
            FirebaseFirestore.getInstance()
                .collection(CareRequests.USERS_COLLECTION)
                .document(user.uid)
                .get()
                .addOnSuccessListener { document ->
                    if (document.getString(CareRequests.FIELD_ROLE) == CareRequests.ROLE_CAREGIVER) {
                        cardCareTeam.visibility = View.VISIBLE
                    }
                }
        }

        val version = runCatching { packageManager.getPackageInfo(packageName, 0).versionName }.getOrNull()
        findViewById<TextView>(R.id.tvAppVersion).text = if (version == null) "" else "Neli-Rose app version $version"
    }

    /** The details are already saved on the phone. This also stores them online and reports the result. */
    private fun saveOnline(uid: String, name: String, phone: String, email: String) {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) {
            Toast.makeText(this, "Saved on this phone. It could not be saved online.", Toast.LENGTH_LONG).show()
            return
        }
        val data = hashMapOf("fullName" to name, "phone" to phone, "email" to email)
        firestore.collection(CareRequests.USERS_COLLECTION).document(uid)
            .set(data, SetOptions.merge())
            .addOnSuccessListener {
                Toast.makeText(this, "Your details are saved", Toast.LENGTH_SHORT).show()
            }
            .addOnFailureListener { error ->
                Toast.makeText(
                    this,
                    "Saved on this phone. It could not be saved online: ${error.localizedMessage ?: "please try again"}",
                    Toast.LENGTH_LONG
                ).show()
            }
    }

    private fun showNotificationStatus() {
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled()
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        when {
            enabled && !needsPermission -> {
                tvNotificationStatus.text = "Notifications are on. Care alerts will be shown."
                btnEnableNotifications.visibility = View.GONE
            }
            needsPermission -> {
                tvNotificationStatus.text = "Notifications are off, so care alerts will not be shown."
                btnEnableNotifications.visibility = View.VISIBLE
            }
            else -> {
                tvNotificationStatus.text =
                    "Notifications are switched off for this app in the phone's settings, so care alerts will not be shown."
                btnEnableNotifications.visibility = View.GONE
            }
        }
    }

    private fun logOut() {
        runCatching { FirebaseAuth.getInstance().signOut() }
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
