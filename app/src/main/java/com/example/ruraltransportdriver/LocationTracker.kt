package com.example.ruraltransportdriver

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.os.Looper
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*

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
            onError(
                "Location permission not granted. Please allow location access."
            )
            return
        }

        if (isTrackingActive) return

        try {

            /*
             * --------------------------------------------------
             * 1. GET CURRENT LOCATION IMMEDIATELY
             * --------------------------------------------------
             *
             * This does not wait for the 12-second interval.
             */
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                null
            ).addOnSuccessListener { location ->

                if (location != null) {
                    onLocationUpdated(location)
                }

            }.addOnFailureListener { exception ->

                onError(
                    exception.localizedMessage
                        ?: "Unable to get current GPS location."
                )
            }

            /*
             * --------------------------------------------------
             * 2. CONTINUOUS LOCATION UPDATES
             * --------------------------------------------------
             */

            val locationRequest = LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                12000L
            ).apply {

                setMinUpdateIntervalMillis(8000L)

                setMinUpdateDistanceMeters(20f)

                setWaitForAccurateLocation(false)

            }.build()

            locationCallback = object : LocationCallback() {

                override fun onLocationResult(
                    result: LocationResult
                ) {

                    val location =
                        result.lastLocation ?: return

                    onLocationUpdated(location)
                }

                override fun onLocationAvailability(
                    availability: LocationAvailability
                ) {
                    // Signal strength changes are handled via location updates themselves.
                    // We don't trigger fatal errors for temporary signal loss.
                }
            }

            fusedClient.requestLocationUpdates(
                locationRequest,
                locationCallback!!,
                Looper.getMainLooper()
            )

            isTrackingActive = true

        } catch (e: SecurityException) {

            onError(
                "Security Exception: Location permission revoked."
            )

        } catch (e: Exception) {

            onError(
                e.localizedMessage
                    ?: "Failed to start location updates."
            )
        }
    }

    fun stopTracking() {

        locationCallback?.let {
            fusedClient.removeLocationUpdates(it)
        }

        locationCallback = null
        isTrackingActive = false
    }

    fun isTracking(): Boolean =
        isTrackingActive
}