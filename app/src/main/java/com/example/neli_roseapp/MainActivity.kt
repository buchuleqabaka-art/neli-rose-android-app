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
    private lateinit var btnHeaderLogout: ImageView
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle)
        btnHeaderBack = findViewById(R.id.btnHeaderBack)
        btnHeaderLogout = findViewById(R.id.btnHeaderLogout)
        bottomNav = findViewById(R.id.bottomNavigationView)

        btnHeaderBack.setOnClickListener {
            bottomNav.selectedItemId = R.id.navigation_home
        }

        btnHeaderLogout.setOnClickListener {
            runCatching { FirebaseAuth.getInstance().signOut() }
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_home -> {
                    updateHeader("Neli-Rose", "Where Care is Always Near", showBack = false, showLogout = true)
                    loadFragment(HomeFragment())
                    true
                }
                R.id.navigation_health -> {
                    updateHeader("Health", showBack = true, showLogout = false)
                    loadFragment(HealthFragment())
                    true
                }
                R.id.navigation_schedule -> {
                    updateHeader("Schedule", showBack = true, showLogout = false)
                    loadFragment(ScheduleFragment())
                    true
                }
                R.id.navigation_activities -> {
                    updateHeader("Activities", showBack = true, showLogout = false)
                    loadFragment(ActivitiesFragment())
                    true
                }
                R.id.navigation_contact -> {
                    updateHeader("Contact", showBack = true, showLogout = false)
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

    private fun updateHeader(title: String, subtitle: String? = null, showBack: Boolean, showLogout: Boolean) {
        tvHeaderTitle.text = title
        if (subtitle != null) {
            tvHeaderSubtitle.text = subtitle
            tvHeaderSubtitle.visibility = View.VISIBLE
        } else {
            tvHeaderSubtitle.visibility = View.GONE
        }
        btnHeaderBack.visibility = if (showBack) View.VISIBLE else View.GONE
        btnHeaderLogout.visibility = if (showLogout) View.VISIBLE else View.GONE
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
