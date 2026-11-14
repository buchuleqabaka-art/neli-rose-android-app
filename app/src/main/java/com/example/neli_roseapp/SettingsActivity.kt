package com.example.neli_roseapp

import android.app.NotificationManager
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationManagerCompat
import com.google.firebase.auth.FirebaseAuth

class SettingsActivity : AppCompatActivity() {
    private lateinit var auth: FirebaseAuth
    private lateinit var notificationStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val firebaseAuth = runCatching { FirebaseAuth.getInstance() }.getOrNull()
        if (firebaseAuth?.currentUser == null) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        auth = firebaseAuth
        setContentView(R.layout.activity_settings)
        val user = auth.currentUser ?: return
        val name = findViewById<EditText>(R.id.etSettingsName)
        val phone = findViewById<EditText>(R.id.etSettingsPhone)
        name.setText(DataManager.getUserName(this))
        phone.setText(DataManager.getSettingsPhone(this))
        findViewById<TextView>(R.id.tvSettingsEmail).text = user.email ?: getString(R.string.settings_no_email)
        notificationStatus = findViewById(R.id.tvNotificationStatus)
        findViewById<TextView>(R.id.tvVersion).text = getString(R.string.settings_version, packageManager.getPackageInfo(packageName, 0).versionName ?: "")

        findViewById<android.view.View>(R.id.btnSettingsBack).setOnClickListener { finish() }
        findViewById<Button>(R.id.btnSaveProfile).setOnClickListener {
            val newName = name.text.toString().trim()
            val newPhone = phone.text.toString().trim()
            if (newName.isBlank()) {
                name.error = getString(R.string.settings_name_required)
                return@setOnClickListener
            }
            if (newPhone.isNotEmpty() && (newPhone.count { it.isDigit() } !in 7..15 || newPhone.any { !it.isDigit() && it !in "+ ()-" })) {
                phone.error = getString(R.string.settings_phone_invalid)
                return@setOnClickListener
            }
            val saved = DataManager.saveSettingsProfile(this, newName, newPhone)
            Toast.makeText(this, if (saved) R.string.settings_saved else R.string.settings_save_failed, Toast.LENGTH_LONG).show()
        }
        findViewById<Button>(R.id.btnDisplaySettings).setOnClickListener {
            openSettings(Intent(Settings.ACTION_DISPLAY_SETTINGS))
        }
        findViewById<Button>(R.id.btnNotificationSettings).setOnClickListener {
            openSettings(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
        findViewById<Button>(R.id.btnChangePassword).setOnClickListener { sendPasswordReset() }
        findViewById<Button>(R.id.btnViewData).setOnClickListener { showLocalVitals() }
        findViewById<Button>(R.id.btnSettingsLogout).setOnClickListener {
            AlertDialog.Builder(this).setTitle(R.string.settings_logout)
                .setMessage(R.string.settings_logout_confirm)
                .setNegativeButton(android.R.string.cancel, null)
                .setPositiveButton(R.string.settings_logout) { _, _ ->
                    auth.signOut()
                    startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }.show()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!::notificationStatus.isInitialized) return
        val channel = getSystemService(NotificationManager::class.java).getNotificationChannel(FcmHelper.CHANNEL_ID)
        val enabled = NotificationManagerCompat.from(this).areNotificationsEnabled() &&
            (channel == null || channel.importance != NotificationManager.IMPORTANCE_NONE)
        notificationStatus.setText(if (enabled) R.string.settings_notifications_on else R.string.settings_notifications_off)
    }

    private fun openSettings(intent: Intent) {
        runCatching { startActivity(intent) }.onFailure {
            Toast.makeText(this, R.string.settings_device_settings_unavailable, Toast.LENGTH_LONG).show()
        }
    }

    private fun sendPasswordReset() {
        val user = auth.currentUser ?: return
        val email = user.email
        if (email.isNullOrBlank() || user.providerData.none { it.providerId == "password" }) {
            Toast.makeText(this, R.string.settings_password_google, Toast.LENGTH_LONG).show()
            return
        }
        AlertDialog.Builder(this).setTitle(R.string.settings_change_password)
            .setMessage(getString(R.string.settings_reset_confirm, email))
            .setNegativeButton(android.R.string.cancel, null)
            .setPositiveButton(R.string.settings_send) { _, _ ->
                auth.sendPasswordResetEmail(email).addOnCompleteListener { task ->
                    Toast.makeText(this, if (task.isSuccessful) R.string.settings_reset_sent else R.string.settings_reset_failed, Toast.LENGTH_LONG).show()
                }
            }.show()
    }

    private fun showLocalVitals() {
        val vitals = DataManager.getVitals(this)
        fun value(key: String) = vitals[key]?.takeIf { it.isNotBlank() } ?: getString(R.string.settings_not_recorded)
        val message = getString(R.string.settings_vitals_summary, value("bp"), value("hr"), value("temp"), value("weight"))
        AlertDialog.Builder(this).setTitle(R.string.settings_view_data)
            .setMessage(message).setPositiveButton(android.R.string.ok, null).show()
    }
}
