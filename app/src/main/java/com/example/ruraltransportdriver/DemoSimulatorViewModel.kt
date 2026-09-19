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
    val currentStop: String = "GURRAMGUDA",
    val isAvailableInFirebase: Boolean = false
)

class DemoSimulatorViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    companion object {
        const val ROUTE_ID = "ROUTE_01"
        
        // GURRAMGUDA -> SPHOORTHY (Indices 0-8 of original list)
        val waypointsGurramgudaSphoorthy = listOf(
            DemoLatLng(17.29421, 78.56753), // Gurramguda Stop
            DemoLatLng(17.29150, 78.56620),
            DemoLatLng(17.28820, 78.56450),
            DemoLatLng(17.28510, 78.56210),
            DemoLatLng(17.28180, 78.55950),
            DemoLatLng(17.27880, 78.55730), // Jay Suryapatnam Stop
            DemoLatLng(17.27980, 78.55520),
            DemoLatLng(17.28110, 78.55360),
            DemoLatLng(17.28218, 78.55251)  // Sphoorthy College Stop
        )

        // NADERGUL -> SPHOORTHY
        val waypointsNadergulSphoorthy = listOf(
            DemoLatLng(17.27464, 78.53995), // Nadergul Stop
            DemoLatLng(17.27850, 78.54600),
            DemoLatLng(17.28218, 78.55251)  // Sphoorthy College Stop
        )

        // BN REDDY -> SAGAR RING ROAD
        val waypointsBnReddySagar = listOf(
            DemoLatLng(17.3235, 78.5630), // B.N. Reddy Nagar Bus Stop
            DemoLatLng(17.3350, 78.5510), // Vanasthalipuram
            DemoLatLng(17.3440, 78.5512), // Bairamalguda Cross Road
            DemoLatLng(17.3484, 78.5510)  // Sagar Ring Road (LB Nagar)
        )

        // SAIDABAD -> SANTOSHNAGAR
        val waypointsSaidabadSantoshnagar = listOf(
            DemoLatLng(17.3615, 78.5100), // Saidabad Colony
            DemoLatLng(17.3544, 78.5076)  // Santoshnagar Cross Roads
        )

        // BN REDDY -> GURRAMGUDA
        val waypointsBnReddyGurramguda = listOf(
            DemoLatLng(17.3235, 78.5630), // B.N. Reddy Nagar Bus Stop
            DemoLatLng(17.3079, 78.5674), // Gurramguda Cross Road
            DemoLatLng(17.2940, 78.5660), // Gurramguda Village
            DemoLatLng(17.29421, 78.56753) // Gurramguda Stop
        )

        // SAGAR RING ROAD -> BAIRAMALGUDA
        val waypointsSagarBairamalguda = listOf(
            DemoLatLng(17.3484, 78.5510), // Sagar Ring Road (LB Nagar)
            DemoLatLng(17.3440, 78.5512)  // Bairamalguda Cross Road
        )
    }

    val auto1 = SimForegroundService.auto1
    val auto2 = SimForegroundService.auto2
    val auto3 = SimForegroundService.auto3
    val auto4 = SimForegroundService.auto4
    val auto5 = SimForegroundService.auto5
    val auto6 = SimForegroundService.auto6
    val auto7 = SimForegroundService.auto7
    val auto8 = SimForegroundService.auto8
    val auto9 = SimForegroundService.auto9
    val auto10 = SimForegroundService.auto10
    val auto11 = SimForegroundService.auto11
    val auto12 = SimForegroundService.auto12

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
        val wps = getWaypointsForAuto(uid)
        SimForegroundService.jumpToWaypoint(uid, index, wps)
    }

    fun resetDemo() {
        SimForegroundService.resetSim()
    }

    fun getWaypointsForAuto(uid: String): List<DemoLatLng> {
        return when (uid) {
            "demo_auto_1", "demo_auto_2" -> waypointsGurramgudaSphoorthy
            "demo_auto_3", "demo_auto_4" -> waypointsNadergulSphoorthy
            "demo_auto_5", "demo_auto_6" -> waypointsBnReddySagar
            "demo_auto_7", "demo_auto_8" -> waypointsSaidabadSantoshnagar
            "demo_auto_9", "demo_auto_10" -> waypointsBnReddyGurramguda
            "demo_auto_11", "demo_auto_12" -> waypointsSagarBairamalguda
            else -> waypointsGurramgudaSphoorthy
        }
    }

    fun calculateDistanceToDestination(state: AutoSimState): Double {
        val wps = getWaypointsForAuto(state.uid)
        val dest = wps.lastOrNull() ?: return 0.0
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
