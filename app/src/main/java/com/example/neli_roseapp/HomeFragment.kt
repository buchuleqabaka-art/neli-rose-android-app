package com.example.neli_roseapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

    private lateinit var tvGreeting: TextView
    private lateinit var tvVitalsStatus: TextView
    private lateinit var tvStatMedsCount: TextView
    private lateinit var tvStatMedsLabel: TextView
    private lateinit var tvStatActivitiesCount: TextView
    private lateinit var tvCaregiverName: TextView
    private lateinit var tvReminderTitle: TextView
    private lateinit var tvReminderDetail: TextView
    private lateinit var tvReminderIn: TextView
    private lateinit var btnShareLocation: Button

    private var greetingPrefix = "Good morning"

    // lets a location search be stopped when the screen goes away
    private var locationSearch: CancellationTokenSource? = null

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) shareLocation()
        else Toast.makeText(requireContext(), "Location permission is needed to share your location", Toast.LENGTH_LONG).show()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_home, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        tvGreeting = view.findViewById(R.id.tvGreeting)
        val btnCallCaregiver = view.findViewById<Button>(R.id.btnCallCaregiver)
        tvVitalsStatus = view.findViewById(R.id.tvVitalsStatus)
        tvStatMedsCount = view.findViewById(R.id.tvStatMedsCount)
        tvStatMedsLabel = view.findViewById(R.id.tvStatMedsLabel)
        tvStatActivitiesCount = view.findViewById(R.id.tvStatActivitiesCount)
        tvCaregiverName = view.findViewById(R.id.tvCaregiverName)
        tvReminderTitle = view.findViewById(R.id.tvReminderTitle)
        tvReminderDetail = view.findViewById(R.id.tvReminderDetail)
        tvReminderIn = view.findViewById(R.id.tvReminderIn)
        btnShareLocation = view.findViewById(R.id.btnShareLocation)

        val ctx = requireContext()

        tvVitalsStatus.setOnClickListener { openTab(R.id.navigation_health) }

        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        greetingPrefix = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        tvGreeting.text = greetingPrefix
        tvDate.text = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())

        btnCallCaregiver.setOnClickListener { launchUi { callCaregiver() } }

        btnShareLocation.setOnClickListener {
            val fine = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION)
            val coarse = ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_COARSE_LOCATION)
            if (fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED) {
                shareLocation()
            } else {
                permissionLauncher.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
            }
        }

    }

    /** Runs when the screen appears and again when the person comes back to it, for example from Profile. */
    override fun onResume() {
        super.onResume()
        launchUi {
            tvGreeting.text = "$greetingPrefix, ${DataManager.getUserName(requireContext())}"
            showDashboard()
        }
    }

    override fun onDestroyView() {
        locationSearch?.cancel()
        locationSearch = null
        super.onDestroyView()
    }

    private fun openTab(itemId: Int) {
        val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
        if (bottomNav != null) bottomNav.selectedItemId = itemId
    }

    /** Fills the counters, the vitals status and the next reminder from the saved records. */
    private suspend fun showDashboard() {
        val ctx = requireContext()
        val dashboard = DataManager.getDashboard(ctx)

        if (dashboard.medsTotal == 0) {
            tvStatMedsCount.text = "0"
            tvStatMedsLabel.text = "No medicines added yet"
        } else {
            tvStatMedsCount.text = "${dashboard.medsTaken} of ${dashboard.medsTotal}"
            tvStatMedsLabel.text = "Medicines taken today"
        }
        tvStatActivitiesCount.text = "${dashboard.activitiesJoined} of ${dashboard.activitiesTotal}"

        val vitals = dashboard.vitalsToday
        if (vitals != null) {
            tvVitalsStatus.text = "Vitals recorded today at ${formatTime(vitals.recordedAt)} (tap to add another reading)"
            tvVitalsStatus.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_light_green))
            tvVitalsStatus.setTextColor(ContextCompat.getColor(ctx, R.color.neli_green))
        } else {
            tvVitalsStatus.text = "No vitals recorded today (tap to add them)"
            tvVitalsStatus.setBackgroundColor(ContextCompat.getColor(ctx, R.color.neli_grey))
            tvVitalsStatus.setTextColor(ContextCompat.getColor(ctx, android.R.color.black))
        }

        tvReminderTitle.text = dashboard.reminder.title
        tvReminderDetail.text = dashboard.reminder.detail
        tvReminderIn.text = dashboard.reminder.badge

        val caregiver = DataManager.getCaregiver(ctx)
        tvCaregiverName.text = if (caregiver == null) {
            "No caregiver chosen yet. Choose one of your contacts on the Contact screen."
        } else {
            "The button below calls ${caregiver.name} (${caregiver.phone})."
        }
    }

    /** Calls the contact the person chose as caregiver, or explains how to choose one. */
    private suspend fun callCaregiver() {
        val ctx = requireContext()
        val caregiver = DataManager.getCaregiver(ctx)
        if (caregiver == null) {
            AlertDialog.Builder(ctx)
                .setTitle("No caregiver chosen yet")
                .setMessage("Choose which of your contacts is your caregiver. This button will then call that person.")
                .setPositiveButton("Open Contacts") { _, _ -> openTab(R.id.navigation_contact) }
                .setNegativeButton("Cancel", null)
                .show()
        } else if (!ctx.dial(caregiver.phone)) {
            Toast.makeText(ctx, "No phone app found on this device", Toast.LENGTH_LONG).show()
        }
    }

    /**
     * Asks the phone for a new position and shares it.
     * A saved earlier position is never used, because it may be from somewhere else.
     */
    @SuppressLint("MissingPermission")
    private fun shareLocation() {
        val ctx = requireContext()
        locationSearch?.cancel()
        val search = CancellationTokenSource()
        locationSearch = search

        btnShareLocation.isEnabled = false
        btnShareLocation.text = "Finding your location..."

        // maxUpdateAge 0: do not accept a position the phone already had; wait up to 20 seconds for a new one
        val request = CurrentLocationRequest.Builder()
            .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
            .setMaxUpdateAgeMillis(0)
            .setDurationMillis(20_000)
            .build()

        LocationServices.getFusedLocationProviderClient(ctx)
            .getCurrentLocation(request, search.token)
            .addOnSuccessListener { location ->
                if (view == null) return@addOnSuccessListener
                resetShareButton()
                if (location == null) {
                    Toast.makeText(ctx, "Could not find your location. Turn on Location and try again.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                val link = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, "I need help. My location: $link")
                }
                startActivity(Intent.createChooser(send, "Share my location"))
            }
            .addOnFailureListener {
                if (view == null) return@addOnFailureListener
                resetShareButton()
                Toast.makeText(ctx, "Could not find your location. Turn on Location and try again.", Toast.LENGTH_LONG).show()
            }
    }

    private fun resetShareButton() {
        btnShareLocation.isEnabled = true
        btnShareLocation.text = "Share My Location"
    }
}
