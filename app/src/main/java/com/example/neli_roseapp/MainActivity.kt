package com.example.neli_roseapp

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CancellationException

class MainActivity : AppCompatActivity() {

    private lateinit var tvHeaderTitle: TextView
    private lateinit var tvHeaderSubtitle: TextView
    private lateinit var btnHeaderBack: View
    private lateinit var btnHeaderProfile: View
    private lateinit var btnHeaderLogout: View
    private lateinit var bottomNav: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Everything behind this point belongs to a signed-in person.
        // Without one there is nobody to keep records for, so go back to the login screen.
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (user == null) {
            goToLogin()
            return
        }

        setContentView(R.layout.activity_main)

        FcmHelper.createChannel(this)
        FcmHelper.registerDevice()

        tvHeaderTitle = findViewById(R.id.tvHeaderTitle)
        tvHeaderSubtitle = findViewById(R.id.tvHeaderSubtitle)
        btnHeaderBack = findViewById(R.id.btnHeaderBack)
        btnHeaderProfile = findViewById(R.id.btnHeaderProfile)
        btnHeaderLogout = findViewById(R.id.btnHeaderLogout)
        bottomNav = findViewById(R.id.bottomNavigationView)

        btnHeaderBack.setOnClickListener {
            bottomNav.selectedItemId = R.id.navigation_home
        }

        btnHeaderProfile.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        btnHeaderLogout.setOnClickListener {
            runCatching { FirebaseAuth.getInstance().signOut() }
            goToLogin()
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
            launchUi {
                // Before any screen reads data: bring over records that an earlier version of the
                // app kept outside the database, and make sure this account has a local profile.
                try {
                    DataManager.prepareForUser(this@MainActivity)
                    DataManager.ensureUserProfile(
                        this@MainActivity,
                        user.displayName ?: user.email?.substringBefore("@") ?: "User",
                        user.email ?: ""
                    )
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e("MainActivity", "Could not prepare this account's records", e)
                }
                bottomNav.selectedItemId = R.id.navigation_home
            }
        }
    }

    private fun goToLogin() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun updateHeader(title: String, subtitle: String? = null, showBack: Boolean, showLogout: Boolean) {
        tvHeaderTitle.text = title
        if (subtitle != null) {
            tvHeaderSubtitle.text = subtitle
            tvHeaderSubtitle.visibility = View.VISIBLE
        } else {
            tvHeaderSubtitle.visibility = View.GONE
        }
        // Home shows Profile and Log out; the inner screens show Back instead
        btnHeaderBack.visibility = if (showBack) View.VISIBLE else View.GONE
        btnHeaderProfile.visibility = if (showBack) View.GONE else View.VISIBLE
        // kept in place (invisible) so the title stays centred
        btnHeaderLogout.visibility = if (showLogout) View.VISIBLE else View.INVISIBLE
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, fragment)
            .commit()
    }
}
