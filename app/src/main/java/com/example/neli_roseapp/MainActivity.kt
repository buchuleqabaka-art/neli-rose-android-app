package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth

class MainActivity : AppCompatActivity() {

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var btnHeaderBack: ImageView
    private lateinit var btnHeaderSettings: ImageView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull() == null) {
            startActivity(Intent(this, LoginActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
            finish()
            return
        }
        setContentView(R.layout.activity_main)

        FcmHelper.createChannel(this)
        FcmHelper.registerDevice()

        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle)
        btnHeaderBack = findViewById(R.id.btnHeaderBack)
        btnHeaderSettings = findViewById(R.id.btnHeaderSettings)
        bottomNav = findViewById(R.id.bottomNavigationView)

        btnHeaderBack.setOnClickListener {
            bottomNav.selectedItemId = R.id.navigation_home
        }

        btnHeaderSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    updateHeader("Neli-Rose", "Where Care is Always Near", showBack = false)
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_health -> {
                    updateHeader("Health", showBack = true)
                    loadFragment(HealthFragment())
                    true
                }
                R.id.navigation_schedule -> {
                    updateHeader("Schedule", showBack = true)
                    loadFragment(ScheduleFragment())
                    true
                }
                R.id.navigation_activities -> {
                    updateHeader("Activities", showBack = true)
                    loadFragment(ActivitiesFragment())
                    true
                }
                R.id.navigation_contact -> {
                    updateHeader("Contact", showBack = true)
                    loadFragment(ContactFragment())
                    true
                }
                else -> false
            }
        }

        // Return to Home tab on back press if not already on Home tab
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (bottomNav.selectedItemId != R.id.navigation_home) {
                    bottomNav.selectedItemId = R.id.navigation_home
                } else {
                    finish()
                }
            }
        })

        // Default to Home fragment on initial launch
        if (savedInstanceState == null) {
            bottomNav.selectedItemId = R.id.navigation_home
        }
    }

    private fun updateHeader(title: String, subtitle: String? = null, showBack: Boolean) {
        tvHeaderTitle.text = title
        if (subtitle != null) {
            tvHeaderSubtitle.text = subtitle
            tvHeaderSubtitle.visibility = View.VISIBLE
        } else {
            tvHeaderSubtitle.visibility = View.GONE
        }
        btnHeaderBack.visibility = if (showBack) View.VISIBLE else View.GONE
        btnHeaderSettings.visibility = View.VISIBLE
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
