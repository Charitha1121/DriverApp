package com.example.ruraltransportdriver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

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

class DemoSimulatorViewModel : ViewModel() {
    private val repository = FirebaseRepository()

    val waypoints = listOf(
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

    private val _auto1 = MutableStateFlow(
        AutoSimState("demo_auto_1", "Demo Auto 1", "TS 09 DEMO 1", currentLat = 17.29421, currentLng = 78.56753)
    )
    val auto1: StateFlow<AutoSimState> = _auto1.asStateFlow()

    private val _auto2 = MutableStateFlow(
        AutoSimState("demo_auto_2", "Demo Auto 2", "TS 09 DEMO 2", currentLat = 17.27880, currentLng = 78.55730, currentWaypointIndex = 5)
    )
    val auto2: StateFlow<AutoSimState> = _auto2.asStateFlow()

    private var simJob: Job? = null

    init {
        startSimulationLoop()
    }

    private fun startSimulationLoop() {
        simJob = viewModelScope.launch {
            while (true) {
                delay(1000)
                tickAuto(_auto1)
                tickAuto(_auto2)
            }
        }
    }

    private fun tickAuto(autoFlow: MutableStateFlow<AutoSimState>) {
        val state = autoFlow.value
        if (!state.isRunning) return

        var index = state.currentWaypointIndex
        var frac = state.fraction

        if (index >= waypoints.size - 1) {
            index = 0
            frac = 0.0
        }

        val p1 = waypoints[index]
        val p2 = waypoints[index + 1]

        val segDist = calculateDistanceMeters(p1.lat, p1.lng, p2.lat, p2.lng)
        val distMoved = state.speedMs * 1.0
        val fracMoved = if (segDist > 0) distMoved / segDist else 1.0

        frac += fracMoved
        while (frac >= 1.0 && index < waypoints.size - 1) {
            frac -= 1.0
            index++
        }

        if (index >= waypoints.size - 1) {
            index = 0
            frac = 0.0
        }

        val currentP1 = waypoints[index]
        val currentP2 = waypoints[index + 1]
        val nextLat = currentP1.lat + (currentP2.lat - currentP1.lat) * frac
        val nextLng = currentP1.lng + (currentP2.lng - currentP1.lng) * frac
        val heading = calculateHeading(currentP1.lat, currentP1.lng, currentP2.lat, currentP2.lng)
        val nearestStop = findNearestStop(nextLat, nextLng)

        autoFlow.update {
            it.copy(
                currentWaypointIndex = index,
                fraction = frac,
                currentLat = nextLat,
                currentLng = nextLng,
                currentHeading = heading,
                currentStop = nearestStop,
                isAvailableInFirebase = true
            )
        }

        pushToFirebase(autoFlow.value)
    }

    fun toggleStartPause(uid: String) {
        val flow = if (uid == "demo_auto_1") _auto1 else _auto2
        flow.update { it.copy(isRunning = !it.isRunning) }
        
        if (!flow.value.isRunning) {
            pushToFirebase(flow.value.copy(speedMs = 0f))
        } else {
            pushToFirebase(flow.value)
        }
    }

    fun updateSpeed(uid: String, speedMs: Float) {
        val flow = if (uid == "demo_auto_1") _auto1 else _auto2
        flow.update { it.copy(speedMs = speedMs) }
        if (flow.value.isRunning) {
            pushToFirebase(flow.value)
        }
    }

    fun jumpToWaypoint(uid: String, index: Int) {
        val flow = if (uid == "demo_auto_1") _auto1 else _auto2
        val safeIndex = index.coerceIn(0, waypoints.size - 1)
        val p = waypoints[safeIndex]
        val nextIndex = if (safeIndex < waypoints.size - 1) safeIndex else safeIndex - 1
        val heading = calculateHeading(waypoints[nextIndex].lat, waypoints[nextIndex].lng, waypoints[nextIndex + 1].lat, waypoints[nextIndex + 1].lng)
        val nearestStop = findNearestStop(p.lat, p.lng)

        flow.update {
            it.copy(
                currentWaypointIndex = safeIndex,
                fraction = 0.0,
                currentLat = p.lat,
                currentLng = p.lng,
                currentHeading = heading,
                currentStop = nearestStop
            )
        }
        pushToFirebase(flow.value)
    }

    fun resetDemo() {
        _auto1.update {
            AutoSimState("demo_auto_1", "Demo Auto 1", "TS 09 DEMO 1", currentLat = 17.29421, currentLng = 78.56753)
        }
        _auto2.update {
            AutoSimState("demo_auto_2", "Demo Auto 2", "TS 09 DEMO 2", currentLat = 17.27880, currentLng = 78.55730, currentWaypointIndex = 5)
        }
        clearFromFirebase("demo_auto_1")
        clearFromFirebase("demo_auto_2")
    }

    private fun pushToFirebase(state: AutoSimState) {
        val profileData = mapOf(
            "uid" to state.uid,
            "name" to state.name,
            "driverName" to state.name,
            "vehicleNumber" to state.vehicleNumber,
            "vehicleType" to "Shared Auto",
            "isAvailable" to true,
            "availableSeats" to state.availableSeats,
            "totalSeats" to state.totalSeats,
            "routeId" to RouteData.ROUTE_ID,
            "activeDirection" to "FORWARD",
            "currentStop" to state.currentStop,
            "lastUpdated" to currentTime()
        )

        repository.updateSimulatedDriverProfile(state.uid, profileData)

        val liveLoc = DriverLiveLocation(
            lat = state.currentLat,
            lng = state.currentLng,
            heading = state.currentHeading,
            speed = if (state.isRunning) state.speedMs else 0f,
            isOnline = true,
            routeId = RouteData.ROUTE_ID,
            activeDirection = "FORWARD",
            currentStop = state.currentStop
        )
        repository.updateLiveLocation(state.uid, liveLoc)

        // Also update liveTracking node which the map uses for smooth movement
        val mockLocation = android.location.Location("demo").apply {
            latitude = state.currentLat
            longitude = state.currentLng
            bearing = state.currentHeading
            speed = if (state.isRunning) state.speedMs else 0f
            time = System.currentTimeMillis()
        }
        repository.updateLiveTracking(
            uid = state.uid,
            location = mockLocation,
            isRideActive = true
        )
    }

    private fun clearFromFirebase(uid: String) {
        val profileData = mapOf(
            "isAvailable" to false,
            "lastUpdated" to currentTime()
        )
        repository.updateSimulatedDriverProfile(uid, profileData)
        repository.updateLiveLocation(uid, DriverLiveLocation(isOnline = false))
    }

    fun calculateDistanceToDestination(state: AutoSimState): Double {
        val dest = waypoints.last()
        return calculateDistanceMeters(state.currentLat, state.currentLng, dest.lat, dest.lng)
    }

    fun calculateEtaMinutes(state: AutoSimState): Int {
        val dist = calculateDistanceToDestination(state)
        val speed = if (state.speedMs > 0) state.speedMs else 8f
        val timeSec = dist / speed
        return (timeSec / 60.0).toInt().coerceAtLeast(1)
    }

    private fun findNearestStop(lat: Double, lng: Double): String {
        var minDest = Double.MAX_VALUE
        var closest = "Gurramguda"
        for (stop in RouteData.stops) {
            val d = calculateDistanceMeters(lat, lng, stop.latitude, stop.longitude)
            if (d < minDest) {
                minDest = d
                closest = stop.name
            }
        }
        return closest
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

    private fun calculateHeading(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Float {
        val dLon = Math.toRadians(lon2 - lon1)
        val lat1Rad = Math.toRadians(lat1)
        val lat2Rad = Math.toRadians(lat2)
        val y = Math.sin(dLon) * Math.cos(lat2Rad)
        val x = Math.cos(lat1Rad) * Math.sin(lat2Rad) -
                Math.sin(lat1Rad) * Math.cos(lat2Rad) * Math.cos(dLon)
        val brng = Math.atan2(y, x)
        return ((Math.toDegrees(brng) + 360) % 360).toFloat()
    }

    private fun currentTime(): String {
        return SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).format(Date())
    }

    override fun onCleared() {
        super.onCleared()
        simJob?.cancel()
    }
}
