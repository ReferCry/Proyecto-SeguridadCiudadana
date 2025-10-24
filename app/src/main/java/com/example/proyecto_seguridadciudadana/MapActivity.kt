package com.example.proyecto_seguridadciudadana

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.proyecto_seguridadciudadana.models.Alert
import com.example.proyecto_seguridadciudadana.repository.FirebaseRepository
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.GoogleMap
import com.google.android.gms.maps.OnMapReadyCallback
import com.google.android.gms.maps.SupportMapFragment
import com.google.android.gms.maps.model.BitmapDescriptor
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.LatLng
import com.google.android.gms.maps.model.MarkerOptions
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.Timestamp
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

class MapActivity : AppCompatActivity(), OnMapReadyCallback {

    private lateinit var googleMap: GoogleMap
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private val repository = FirebaseRepository()
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val LOCATION_PERMISSION_REQUEST = 2001
        private const val DEFAULT_ZOOM = 15f
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_map)

        // Inicializar vistas
        progressBar = findViewById(R.id.progressBarMap)

        // Inicializar cliente de ubicación
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Configurar el fragmento del mapa
        val mapFragment = supportFragmentManager
            .findFragmentById(R.id.mapFragment) as SupportMapFragment
        mapFragment.getMapAsync(this)

        // Botón de volver
        findViewById<View>(R.id.btnBackFromMap)?.setOnClickListener {
            finish()
        }
    }

    override fun onMapReady(map: GoogleMap) {
        googleMap = map

        // Configurar el mapa
        setupMap()

        // Centrar en ubicación del usuario
        centerOnUserLocation()

        // Cargar alertas activas
        loadActiveAlerts()
    }

    private fun setupMap() {
        // Configurar UI del mapa
        googleMap.uiSettings.apply {
            isZoomControlsEnabled = true
            isCompassEnabled = true
            isMyLocationButtonEnabled = true
        }

        // Verificar y habilitar ubicación
        if (checkLocationPermission()) {
            try {
                googleMap.isMyLocationEnabled = true
            } catch (e: SecurityException) {
                Log.e("MapActivity", "Error al habilitar ubicación: ${e.message}")
            }
        } else {
            requestLocationPermission()
        }
    }

    private fun centerOnUserLocation() {
        if (!checkLocationPermission()) {
            // Ubicación por defecto: Lima, Perú
            val lima = LatLng(-12.0464, -77.0428)
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lima, DEFAULT_ZOOM))
            return
        }

        lifecycleScope.launch {
            try {
                val location = fusedLocationClient.getCurrentLocation(
                    Priority.PRIORITY_HIGH_ACCURACY,
                    CancellationTokenSource().token
                ).await()

                location?.let {
                    val userLocation = LatLng(it.latitude, it.longitude)
                    googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(userLocation, DEFAULT_ZOOM)
                    )
                }
            } catch (e: Exception) {
                Log.e("MapActivity", "Error al obtener ubicación: ${e.message}")
                // Ubicación por defecto si falla
                val lima = LatLng(-12.0464, -77.0428)
                googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(lima, DEFAULT_ZOOM))
            }
        }
    }

    private fun loadActiveAlerts() {
        showLoading(true)

        lifecycleScope.launch {
            try {
                // Obtener alertas activas de la comunidad "general"
                val alerts = repository.getActiveAlerts("general")

                // Filtrar alertas que no hayan expirado
                val now = Timestamp.now()
                val activeAlerts = alerts.filter { alert ->
                    alert.expiresAt.seconds > now.seconds && alert.status == "active"
                }

                Log.d("MapActivity", "Alertas activas encontradas: ${activeAlerts.size}")

                // Limpiar marcadores existentes
                googleMap.clear()

                // Agregar marcadores
                activeAlerts.forEach { alert ->
                    addAlertMarker(alert)
                }

                showLoading(false)

                if (activeAlerts.isEmpty()) {
                    Toast.makeText(
                        this@MapActivity,
                        "No hay alertas activas en este momento",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(
                        this@MapActivity,
                        "${activeAlerts.size} alertas activas",
                        Toast.LENGTH_SHORT
                    ).show()
                }

            } catch (e: Exception) {
                showLoading(false)
                Log.e("MapActivity", "Error al cargar alertas: ${e.message}")
                Toast.makeText(
                    this@MapActivity,
                    "Error al cargar alertas",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    private fun addAlertMarker(alert: Alert) {
        alert.location?.let { geoPoint ->
            val position = LatLng(geoPoint.latitude, geoPoint.longitude)

            // Seleccionar color según tipo de alerta
            val markerColor = when (alert.type) {
                "HOME_ACCIDENT" -> BitmapDescriptorFactory.HUE_BLUE
                "STREET_INCIDENT" -> BitmapDescriptorFactory.HUE_ORANGE
                "CRIMINAL_ACTIVITY" -> BitmapDescriptorFactory.HUE_RED
                else -> BitmapDescriptorFactory.HUE_RED
            }

            // Emoji según tipo
            val emoji = when (alert.type) {
                "HOME_ACCIDENT" -> "🏠"
                "STREET_INCIDENT" -> "🚗"
                "CRIMINAL_ACTIVITY" -> "⚠️"
                else -> "🚨"
            }

            // Calcular tiempo transcurrido
            val now = Timestamp.now()
            val minutesAgo = ((now.seconds - alert.createdAt.seconds) / 60).toInt()
            val timeText = when {
                minutesAgo < 1 -> "Justo ahora"
                minutesAgo == 1 -> "Hace 1 minuto"
                minutesAgo < 60 -> "Hace $minutesAgo minutos"
                else -> "Hace ${minutesAgo / 60} hora(s)"
            }

            // Crear marcador
            googleMap.addMarker(
                MarkerOptions()
                    .position(position)
                    .title("$emoji ${alert.subtype}")
                    .snippet("${alert.userName} - $timeText\n${alert.address}")
                    .icon(BitmapDescriptorFactory.defaultMarker(markerColor))
            )
        }
    }

    private fun showLoading(show: Boolean) {
        progressBar.visibility = if (show) View.VISIBLE else View.GONE
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
            LOCATION_PERMISSION_REQUEST
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == LOCATION_PERMISSION_REQUEST) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                setupMap()
                centerOnUserLocation()
            } else {
                Toast.makeText(
                    this,
                    "Permiso de ubicación denegado",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
}