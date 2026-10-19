package com.example.neli_roseapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class HomeFragment : Fragment() {

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

        val tvGreeting = view.findViewById<TextView>(R.id.tvGreeting)
        val tvDate = view.findViewById<TextView>(R.id.tvDate)
        val tvReminderIn = view.findViewById<TextView>(R.id.tvReminderIn)
        val tvVitalsCheckedBadge = view.findViewById<TextView>(R.id.tvVitalsCheckedBadge)
        val tvStatMedsCount = view.findViewById<TextView>(R.id.tvStatMedsCount)
        val tvStatActivitiesCount = view.findViewById<TextView>(R.id.tvStatActivitiesCount)
        val btnMedsMinus = view.findViewById<TextView>(R.id.btnMedsMinus)
        val btnMedsPlus = view.findViewById<TextView>(R.id.btnMedsPlus)
        val btnActivitiesMinus = view.findViewById<TextView>(R.id.btnActivitiesMinus)
        val btnActivitiesPlus = view.findViewById<TextView>(R.id.btnActivitiesPlus)
        val btnCallCaregiver = view.findViewById<Button>(R.id.btnCallCaregiver)
        val btnShareLocation = view.findViewById<Button>(R.id.btnShareLocation)

        val ctx = requireContext()
        val userName = DataManager.getUserName(ctx)

        var medsCount = DataManager.getMedsCount(ctx)
        var activitiesCount = DataManager.getActivitiesCount(ctx)

        tvStatMedsCount.text = medsCount.toString()
        tvStatActivitiesCount.text = activitiesCount.toString()

        btnMedsMinus.setOnClickListener {
            if (medsCount > 0) {
                medsCount--
                tvStatMedsCount.text = medsCount.toString()
                DataManager.saveStatCounts(ctx, medsCount, activitiesCount)
            }
        }

        btnMedsPlus.setOnClickListener {
            medsCount++
            tvStatMedsCount.text = medsCount.toString()
            DataManager.saveStatCounts(ctx, medsCount, activitiesCount)
        }

        btnActivitiesMinus.setOnClickListener {
            if (activitiesCount > 0) {
                activitiesCount--
                tvStatActivitiesCount.text = activitiesCount.toString()
                DataManager.saveStatCounts(ctx, medsCount, activitiesCount)
            }
        }

        btnActivitiesPlus.setOnClickListener {
            activitiesCount++
            tvStatActivitiesCount.text = activitiesCount.toString()
            DataManager.saveStatCounts(ctx, medsCount, activitiesCount)
        }

        tvVitalsCheckedBadge.setOnClickListener {
            val bottomNav = activity?.findViewById<BottomNavigationView>(R.id.bottomNavigationView)
            if (bottomNav != null) {
                bottomNav.selectedItemId = R.id.navigation_health
            } else {
                Toast.makeText(ctx, "Edit your vitals on the Health screen", Toast.LENGTH_SHORT).show()
            }
        }

        val now = Calendar.getInstance()
        val hour = now.get(Calendar.HOUR_OF_DAY)
        val greetingPrefix = when {
            hour < 12 -> "Good morning"
            hour < 18 -> "Good afternoon"
            else -> "Good evening"
        }
        val greeting = "$greetingPrefix, $userName"
        val date = SimpleDateFormat("EEEE, d MMMM yyyy", Locale.getDefault()).format(Date())
        val minsToMeds = 14 * 60 - (hour * 60 + now.get(Calendar.MINUTE))
        val reminderIn = if (minsToMeds > 0) "${(minsToMeds + 59) / 60}h" else "Due"

        tvGreeting.text = greeting
        tvDate.text = date
        tvReminderIn.text = reminderIn

        btnCallCaregiver.setOnClickListener {
            requireContext().dial(CareData.nurse.phone)
        }

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

    @SuppressLint("MissingPermission")
    private fun shareLocation() {
        val ctx = requireContext()
        val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val loc = lm.getProviders(true)
            .mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
            .maxByOrNull { it.time }
        if (loc == null) {
            Toast.makeText(ctx, "Could not find your location. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
            return
        }
        val link = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "I need help. My location: $link")
        }
        startActivity(Intent.createChooser(send, "Share my location"))
    }
}
