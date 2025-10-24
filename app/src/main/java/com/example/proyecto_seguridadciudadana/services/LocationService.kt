package com.example.proyecto_seguridadciudadana.services

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Geocoder
import androidx.core.app.ActivityCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.firebase.firestore.GeoPoint
import kotlinx.coroutines.tasks.await
import java.util.Locale

class LocationService(private val context: Context) {

    private val fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    data class LocationResult(
        val geoPoint: GeoPoint,
        val address: String
    )

    suspend fun getCurrentLocation(): LocationResult? {
        // Verificar permisos
        if (ActivityCompat.checkSelfPermission(
                context,
                Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return null
        }

        return try {
            // Obtener ubicación actual con alta precisión
            val location = fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                CancellationTokenSource().token
            ).await()

            location?.let {
                val geoPoint = GeoPoint(it.latitude, it.longitude)
                val address = getAddressFromLocation(it.latitude, it.longitude)
                LocationResult(geoPoint, address)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun getAddressFromLocation(latitude: Double, longitude: Double): String {
        return try {
            val geocoder = Geocoder(context, Locale.getDefault())
            val addresses = geocoder.getFromLocation(latitude, longitude, 1)

            if (!addresses.isNullOrEmpty()) {
                val address = addresses[0]
                val street = address.thoroughfare ?: ""
                val number = address.subThoroughfare ?: ""
                val city = address.locality ?: ""

                "$street $number, $city".trim().ifEmpty { "Ubicación desconocida" }
            } else {
                "Ubicación desconocida"
            }
        } catch (e: Exception) {
            "Ubicación: $latitude, $longitude"
        }
    }
}