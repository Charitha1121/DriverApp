package com.example.ruraltransportdriver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

/**
 * Battery-conscious Location Manager utilizing Google Play Services FusedLocationProviderClient.
 * Employs distance displacement and throttled update intervals to optimize network and battery usage.
 */
class LocationTracker(private val context: Context) {

    private val fusedClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    private var locationCallback: LocationCallback? = null
    private var isTrackingActive: Boolean = false

    fun isPermissionGranted(): Boolean {
        val fineLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        val coarseLocation = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        return fineLocation || coarseLocation
    }

    fun startTracking(
        onLocationUpdated: (Location) -> Unit,
        onError: (String) -> Unit
    ) {
        if (!isPermissionGranted()) {
            onError("Location permission not granted. Please allow location access.")
            return
        }

        if (isTrackingActive) return

        try {
            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                12000L // 12 seconds interval for rural transport
            ).apply {
                setMinUpdateIntervalMillis(8000L)
                setMinUpdateDistanceMeters(20f) // Only emit if driver moves 20 meters
                setWaitForAccurateLocation(false)
            }.build()

            locationCallback = object : LocationCallback() {
                override fun onLocationResult(result: LocationResult) {
                    val location = result.lastLocation ?: return
                    onLocationUpdated(location)
                }

                override fun onLocationAvailability(availability: LocationAvailability) {
                    if (!availability.isLocationAvailable) {
                        onError("GPS Signal Weak or Location Services Disabled.")
                    }
                }
            }

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )
            isTrackingActive = true
        } catch (e: SecurityException) {
            onError("Security Exception: Location permission revoked.")
        } catch (e: Exception) {
            onError(e.localizedMessage ?: "Failed to start location updates.")
        }
    }

    fun stopTracking() {
        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
        }
        locationCallback = null
        isTrackingActive = false
    }

    fun isTracking(): Boolean = isTrackingActive
}
