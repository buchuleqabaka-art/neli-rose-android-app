package com.example.neli_roseapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.net.Uri
import android.os.Bundle
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.LatLngBounds
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.material.button.MaterialButton

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    companion object {
        // TODO: replace with the real Neli-Rose coordinates (right-click the building in Google Maps)
        private val HOME = LatLng(-26.2041, 28.0473)
        private const val HOME_NAME = "Neli-Rose Retirement Home"
    }

    private var map: GoogleMap? = null
    private lateinit var fusedClient: FusedLocationProviderClient
    private var lastLocation: Location? = null

    // asks for location, then turns on the blue dot
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { result ->
        if (result.values.any { it }) {
            enableMyLocation()
        } else {
            Toast.makeText(this, "Location permission is needed to show where you are", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        fusedClient = LocationServices.getFusedLocationProviderClient(this)

        findViewById<ImageView>(R.id.btnMapBack).setOnClickListener { finish() }
        findViewById<MaterialButton>(R.id.btnDirections).setOnClickListener { openDirections() }
        findViewById<MaterialButton>(R.id.btnMapShare).setOnClickListener { shareLocation() }

        // load the map, onMapReady fires when it is ready
        val mapFragment = supportFragmentManager.findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)
    }

    override fun onMapReady(googleMap: GoogleMap) {
        map = googleMap
        googleMap.uiSettings.isZoomControlsEnabled = true

        // marker + camera on Neli-Rose
        googleMap.addMarker(MarkerOptions().position(HOME).title(HOME_NAME))
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(HOME, 15f))

        if (hasLocationPermission()) {
            enableMyLocation()
        } else {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }
    }

    private fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
        val coarse = ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
        return fine == PackageManager.PERMISSION_GRANTED || coarse == PackageManager.PERMISSION_GRANTED
    }

    @SuppressLint("MissingPermission")
    private fun enableMyLocation() {
        val googleMap = map ?: return
        googleMap.isMyLocationEnabled = true

        // get a fresh fix, then zoom so both the user and Neli-Rose are visible
        fusedClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
            .addOnSuccessListener { loc ->
                if (loc == null) {
                    Toast.makeText(this, "Could not find your location. Turn on GPS and try again.", Toast.LENGTH_LONG).show()
                    return@addOnSuccessListener
                }
                lastLocation = loc
                val me = LatLng(loc.latitude, loc.longitude)
                val bounds = LatLngBounds.Builder().include(me).include(HOME).build()
                runCatching {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngBounds(bounds, 150))
                }
            }
    }

    // opens Google Maps (or the browser) with turn-by-turn directions to Neli-Rose
    private fun openDirections() {
        val uri = Uri.parse(
            "https://www.google.com/maps/dir/?api=1&destination=${HOME.latitude},${HOME.longitude}"
        )
        startActivity(Intent(Intent.ACTION_VIEW, uri))
    }

    private fun shareLocation() {
        if (!hasLocationPermission()) {
            permissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
            return
        }
        val loc = lastLocation
        if (loc == null) {
            Toast.makeText(this, "Still finding your location, try again in a moment", Toast.LENGTH_SHORT).show()
            return
        }
        val link = "https://maps.google.com/?q=${loc.latitude},${loc.longitude}"
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, "My location: $link")
        }
        startActivity(Intent.createChooser(send, "Share my location"))
    }
}
