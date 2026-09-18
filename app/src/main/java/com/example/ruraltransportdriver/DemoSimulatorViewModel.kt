package com.example.ruraltransportdriver

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

data class DemoLatLng(val lat: Double, val lng: Double)

data class AutoSimState(
    val uid: String,
    val name: String,
    val vehicleNumber: String,
    val isRunning: Boolean = false,
    val currentWaypointIndex: Int = 0,
    val fraction: Double = 0.0,
    val speedMs: Float = 8f,
    val availableSeats: Int = 3,
    val totalSeats: Int = 4,
    val currentLat: Double = 17.29421,
    val currentLng: Double = 78.56753,
    val currentHeading: Float = 0f,
    val currentStop: String = "Gurramguda",
    val isAvailableInFirebase: Boolean = false
)

class DemoSimulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    companion object {
        const val ROUTE_ID = "ROUTE_01"
        val waypointsList = listOf(
            DemoLatLng(17.29421, 78.56753), // Gurramguda Stop
            DemoLatLng(17.29150, 78.56620),
            DemoLatLng(17.28820, 78.56450),
            DemoLatLng(17.28510, 78.56210),
            DemoLatLng(17.28180, 78.55950),
            DemoLatLng(17.27880, 78.55730), // Jay Suryapatnam Stop
            DemoLatLng(17.27980, 78.55520),
            DemoLatLng(17.28110, 78.55360),
            DemoLatLng(17.28218, 78.55251), // Sphoorthy College Stop
            DemoLatLng(17.27850, 78.54600),
            DemoLatLng(17.27464, 78.53995)  // Nadergul Stop
        )
    }

    val waypoints = waypointsList

    val auto1: StateFlow<AutoSimState> = SimForegroundService.auto1
    val auto2: StateFlow<AutoSimState> = SimForegroundService.auto2

    init {
        SimForegroundService.start(context)
    }

    fun toggleStartPause(uid: String) {
        SimForegroundService.toggleStartPause(uid)
    }

    fun updateSpeed(uid: String, speedMs: Float) {
        SimForegroundService.updateSpeed(uid, speedMs)
    }

    fun jumpToWaypoint(uid: String, index: Int) {
        SimForegroundService.jumpToWaypoint(uid, index, waypointsList)
    }

    fun resetDemo() {
        SimForegroundService.resetSim()
    }

    fun calculateDistanceToDestination(state: AutoSimState): Double {
        val dest = waypointsList.last()
        return calculateDistanceMeters(state.currentLat, state.currentLng, dest.lat, dest.lng)
    }

    fun calculateEtaMinutes(state: AutoSimState): Int {
        val dist = calculateDistanceToDestination(state)
        val speed = if (state.speedMs > 0) state.speedMs else 8f
        val timeSec = dist / speed
        return (timeSec / 60.0).toInt().coerceAtLeast(1)
    }

    private fun calculateDistanceMeters(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }
}
