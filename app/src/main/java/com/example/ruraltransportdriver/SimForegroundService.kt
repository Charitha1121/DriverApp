package com.example.ruraltransportdriver

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

class SimForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var simJob: Job? = null
    private val repository = FirebaseRepository()

    companion object {
        private const val CHANNEL_ID = "sim_channel"
        private const val NOTIFICATION_ID = 5050

        private val _auto1 = MutableStateFlow(
            AutoSimState("demo_auto_1", "Demo Auto 1", "TS 09 DEMO 1", currentLat = 17.29421, currentLng = 78.56753)
        )
        val auto1: StateFlow<AutoSimState> = _auto1.asStateFlow()

        private val _auto2 = MutableStateFlow(
            AutoSimState("demo_auto_2", "Demo Auto 2", "TS 09 DEMO 2", currentLat = 17.27880, currentLng = 78.55730, currentWaypointIndex = 5)
        )
        val auto2: StateFlow<AutoSimState> = _auto2.asStateFlow()

        fun start(context: Context) {
            val intent = Intent(context, SimForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, SimForegroundService::class.java)
            context.stopService(intent)
        }

        fun toggleStartPause(uid: String) {
            val flow = if (uid == "demo_auto_1") _auto1 else _auto2
            flow.update { it.copy(isRunning = !it.isRunning) }
        }

        fun updateSpeed(uid: String, speedMs: Float) {
            val flow = if (uid == "demo_auto_1") _auto1 else _auto2
            flow.update { it.copy(speedMs = speedMs) }
        }

        fun jumpToWaypoint(uid: String, index: Int, waypoints: List<DemoLatLng>) {
            val flow = if (uid == "demo_auto_1") _auto1 else _auto2
            val safeIndex = index.coerceIn(0, waypoints.size - 1)
            val p = waypoints[safeIndex]
            val nextIndex = if (safeIndex < waypoints.size - 1) safeIndex else safeIndex - 1
            val heading = calculateHeading(waypoints[nextIndex].lat, waypoints[nextIndex].lng, waypoints[nextIndex + 1].lat, waypoints[nextIndex + 1].lng)

            flow.update {
                it.copy(
                    currentWaypointIndex = safeIndex,
                    fraction = 0.0,
                    currentLat = p.lat,
                    currentLng = p.lng,
                    currentHeading = heading
                )
            }
        }

        fun resetSim() {
            _auto1.value = AutoSimState("demo_auto_1", "Demo Auto 1", "TS 09 DEMO 1", currentLat = 17.29421, currentLng = 78.56753)
            _auto2.value = AutoSimState("demo_auto_2", "Demo Auto 2", "TS 09 DEMO 2", currentLat = 17.27880, currentLng = 78.55730, currentWaypointIndex = 5)
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
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startInForeground()
        startSimulationLoop()
    }

    private fun startSimulationLoop() {
        simJob?.cancel()
        simJob = serviceScope.launch {
            while (isActive) {
                tickAuto(_auto1)
                tickAuto(_auto2)
                delay(1000)
            }
        }
    }

    private fun tickAuto(autoFlow: MutableStateFlow<AutoSimState>) {
        val state = autoFlow.value
        if (!state.isRunning) {
            // Even if stopped, we still push once to ensure Firebase has the correct "Stopped" speed
            pushToFirebase(state)
            return
        }

        val waypoints = DemoSimulatorViewModel.waypointsList
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

    private fun pushToFirebase(state: AutoSimState) {
        val currentTimestampStr = repository.currentTime()
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
            "lastUpdated" to currentTimestampStr
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
            isRideActive = false
        )
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

    private fun startInForeground() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Demo Simulator Active")
            .setContentText("Continuous simulation running in background")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
        startForeground(NOTIFICATION_ID, notification)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Simulator Channel",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }
}
