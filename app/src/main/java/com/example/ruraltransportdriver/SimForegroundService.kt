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
import java.util.Locale

class SimForegroundService : Service() {

    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val repository = FirebaseRepository()
    private val engines = mutableMapOf<String, AutoSimEngine>()

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

        private val _auto3 = MutableStateFlow(
            AutoSimState("demo_auto_3", "Demo Auto 3", "TS 09 DEMO 3", currentLat = 17.3235, currentLng = 78.5630, currentWaypointIndex = 2)
        )
        val auto3: StateFlow<AutoSimState> = _auto3.asStateFlow()

        private val _auto4 = MutableStateFlow(
            AutoSimState("demo_auto_4", "Demo Auto 4", "TS 09 DEMO 4", currentLat = 17.3484, currentLng = 78.5510, currentWaypointIndex = 5)
        )
        val auto4: StateFlow<AutoSimState> = _auto4.asStateFlow()

        private val _auto5 = MutableStateFlow(
            AutoSimState("demo_auto_5", "Demo Auto 5", "TS 09 DEMO 5", currentLat = 17.3502, currentLng = 78.5475, currentWaypointIndex = 1)
        )
        val auto5: StateFlow<AutoSimState> = _auto5.asStateFlow()

        private val _auto6 = MutableStateFlow(
            AutoSimState("demo_auto_6", "Demo Auto 6", "TS 09 DEMO 6", currentLat = 17.3544, currentLng = 78.5076, currentWaypointIndex = 7)
        )
        val auto6: StateFlow<AutoSimState> = _auto6.asStateFlow()

        private val _auto7 = MutableStateFlow(
            AutoSimState("demo_auto_7", "Demo Auto 7", "TS 09 DEMO 7", currentLat = 17.2945, currentLng = 78.5650, currentWaypointIndex = 0)
        )
        val auto7: StateFlow<AutoSimState> = _auto7.asStateFlow()

        private val _auto8 = MutableStateFlow(
            AutoSimState("demo_auto_8", "Demo Auto 8", "TS 09 DEMO 8", currentLat = 17.3615, currentLng = 78.5100, currentWaypointIndex = 0)
        )
        val auto8: StateFlow<AutoSimState> = _auto8.asStateFlow()

        private val _auto9 = MutableStateFlow(
            AutoSimState("demo_auto_9", "Demo Auto 9", "TS 09 DEMO 9", currentLat = 17.3235, currentLng = 78.5630, currentWaypointIndex = 0)
        )
        val auto9: StateFlow<AutoSimState> = _auto9.asStateFlow()

        private val _auto10 = MutableStateFlow(
            AutoSimState("demo_auto_10", "Demo Auto 10", "TS 09 DEMO 10", currentLat = 17.3079, currentLng = 78.5674, currentWaypointIndex = 1)
        )
        val auto10: StateFlow<AutoSimState> = _auto10.asStateFlow()

        private val _auto11 = MutableStateFlow(
            AutoSimState("demo_auto_11", "Demo Auto 11", "TS 09 DEMO 11", currentLat = 17.3484, currentLng = 78.5510, currentWaypointIndex = 0)
        )
        val auto11: StateFlow<AutoSimState> = _auto11.asStateFlow()

        private val _auto12 = MutableStateFlow(
            AutoSimState("demo_auto_12", "Demo Auto 12", "TS 09 DEMO 12", currentLat = 17.3484, currentLng = 78.5510, currentWaypointIndex = 0)
        )
        val auto12: StateFlow<AutoSimState> = _auto12.asStateFlow()

        private var instance: SimForegroundService? = null

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
            instance?.engines?.get(uid)?.toggleRunning()
        }

        fun updateSpeed(uid: String, speedMs: Float) {
            instance?.engines?.get(uid)?.updateSpeed(speedMs)
        }

        fun jumpToWaypoint(uid: String, index: Int, waypoints: List<DemoLatLng>) {
            instance?.engines?.get(uid)?.jumpToWaypoint(index, waypoints)
        }

        fun resetSim() {
            instance?.engines?.values?.forEach { it.reset() }
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
        instance = this
        createNotificationChannel()
        startInForeground()
        initializeEngines()
    }

    private fun initializeEngines() {
        engines["demo_auto_1"] = AutoSimEngine(_auto1, "ROUTE_01", DemoSimulatorViewModel.waypointsGurramgudaSphoorthy)
        engines["demo_auto_2"] = AutoSimEngine(_auto2, "ROUTE_01", DemoSimulatorViewModel.waypointsGurramgudaSphoorthy)
        engines["demo_auto_3"] = AutoSimEngine(_auto3, "ROUTE_BALAPUR_SPHOORTHY", DemoSimulatorViewModel.waypointsNadergulSphoorthy)
        engines["demo_auto_4"] = AutoSimEngine(_auto4, "ROUTE_BALAPUR_SPHOORTHY", DemoSimulatorViewModel.waypointsNadergulSphoorthy)
        engines["demo_auto_5"] = AutoSimEngine(_auto5, "ROUTE_GURRAMGUDA_RINGROAD", DemoSimulatorViewModel.waypointsBnReddySagar)
        engines["demo_auto_6"] = AutoSimEngine(_auto6, "ROUTE_GURRAMGUDA_RINGROAD", DemoSimulatorViewModel.waypointsBnReddySagar)
        engines["demo_auto_7"] = AutoSimEngine(_auto7, "ROUTE_RINGROAD_SANTOSHNAGAR", DemoSimulatorViewModel.waypointsSaidabadSantoshnagar)
        engines["demo_auto_8"] = AutoSimEngine(_auto8, "ROUTE_RINGROAD_SANTOSHNAGAR", DemoSimulatorViewModel.waypointsSaidabadSantoshnagar)
        engines["demo_auto_9"] = AutoSimEngine(_auto9, "ROUTE_GURRAMGUDA_RINGROAD", DemoSimulatorViewModel.waypointsBnReddyGurramguda, "REVERSE")
        engines["demo_auto_10"] = AutoSimEngine(_auto10, "ROUTE_GURRAMGUDA_RINGROAD", DemoSimulatorViewModel.waypointsBnReddyGurramguda, "REVERSE")
        engines["demo_auto_11"] = AutoSimEngine(_auto11, "ROUTE_RINGROAD_SANTOSHNAGAR", DemoSimulatorViewModel.waypointsSagarBairamalguda, "REVERSE")
        engines["demo_auto_12"] = AutoSimEngine(_auto12, "ROUTE_RINGROAD_SANTOSHNAGAR", DemoSimulatorViewModel.waypointsSagarBairamalguda, "REVERSE")
        
        engines.values.forEach { it.start() }
    }

    inner class AutoSimEngine(
        private val autoFlow: MutableStateFlow<AutoSimState>,
        private val routeId: String,
        private val waypoints: List<DemoLatLng>,
        private val direction: String = "FORWARD"
    ) {
        private var simJob: Job? = null

        fun start() {
            simJob?.cancel()
            simJob = serviceScope.launch {
                while (isActive) {
                    tick()
                    delay(1000)
                }
            }
        }

        fun toggleRunning() {
            autoFlow.update { it.copy(isRunning = !it.isRunning) }
            if (!autoFlow.value.isRunning) {
                pushToFirebase(autoFlow.value.copy(speedMs = 0f))
            }
        }

        fun updateSpeed(speedMs: Float) {
            autoFlow.update { it.copy(speedMs = speedMs) }
        }

        fun jumpToWaypoint(index: Int, waypoints: List<DemoLatLng>) {
            val safeIndex = index.coerceIn(0, waypoints.size - 1)
            val p = waypoints[safeIndex]
            val nextIndex = if (safeIndex < waypoints.size - 1) safeIndex else safeIndex - 1
            val heading = calculateHeading(waypoints[nextIndex].lat, waypoints[nextIndex].lng, waypoints[nextIndex + 1].lat, waypoints[nextIndex + 1].lng)

            autoFlow.update {
                it.copy(
                    currentWaypointIndex = safeIndex,
                    fraction = 0.0,
                    currentLat = p.lat,
                    currentLng = p.lng,
                    currentHeading = heading
                )
            }
            pushToFirebase(autoFlow.value)
        }

        fun reset() {
            val initial = when (autoFlow.value.uid) {
                "demo_auto_1" -> AutoSimState("demo_auto_1", "Demo Auto 1", "TS 09 DEMO 1", currentLat = 17.29421, currentLng = 78.56753)
                "demo_auto_2" -> AutoSimState("demo_auto_2", "Demo Auto 2", "TS 09 DEMO 2", currentLat = 17.29150, currentLng = 78.56620, currentWaypointIndex = 1)
                "demo_auto_3" -> AutoSimState("demo_auto_3", "Demo Auto 3", "TS 09 DEMO 3", currentLat = 17.27464, currentLng = 78.53995)
                "demo_auto_4" -> AutoSimState("demo_auto_4", "Demo Auto 4", "TS 09 DEMO 4", currentLat = 17.27850, currentLng = 78.54600, currentWaypointIndex = 1)
                "demo_auto_5" -> AutoSimState("demo_auto_5", "Demo Auto 5", "TS 09 DEMO 5", currentLat = 17.3235, currentLng = 78.5630)
                "demo_auto_6" -> AutoSimState("demo_auto_6", "Demo Auto 6", "TS 09 DEMO 6", currentLat = 17.3350, currentLng = 78.5510, currentWaypointIndex = 1)
                "demo_auto_7" -> AutoSimState("demo_auto_7", "Demo Auto 7", "TS 09 DEMO 7", currentLat = 17.3615, currentLng = 78.5100)
                "demo_auto_8" -> AutoSimState("demo_auto_8", "Demo Auto 8", "TS 09 DEMO 8", currentLat = 17.3615, currentLng = 78.5100)
                "demo_auto_9" -> AutoSimState("demo_auto_9", "Demo Auto 9", "TS 09 DEMO 9", currentLat = 17.3235, currentLng = 78.5630)
                "demo_auto_10" -> AutoSimState("demo_auto_10", "Demo Auto 10", "TS 09 DEMO 10", currentLat = 17.3079, currentLng = 78.5674, currentWaypointIndex = 1)
                "demo_auto_11" -> AutoSimState("demo_auto_11", "Demo Auto 11", "TS 09 DEMO 11", currentLat = 17.3484, currentLng = 78.5510)
                "demo_auto_12" -> AutoSimState("demo_auto_12", "Demo Auto 12", "TS 09 DEMO 12", currentLat = 17.3484, currentLng = 78.5510)
                else -> AutoSimState(autoFlow.value.uid, autoFlow.value.name, autoFlow.value.vehicleNumber)
            }
            autoFlow.value = initial
            clearFromFirebase(initial.uid)
        }

        private fun tick() {
            val state = autoFlow.value
            if (!state.isRunning) {
                pushToFirebase(state)
                return
            }

            if (waypoints.size < 2) {
                autoFlow.update { it.copy(isRunning = false, speedMs = 0f) }
                pushToFirebase(autoFlow.value)
                return
            }

            var index = state.currentWaypointIndex
            var frac = state.fraction

            // Natural termination: if at last waypoint, stop the auto
            if (index >= waypoints.size - 1) {
                autoFlow.update { it.copy(isRunning = false, speedMs = 0f) }
                pushToFirebase(autoFlow.value)
                return
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

            // Natural termination during movement
            if (index >= waypoints.size - 1) {
                autoFlow.update { 
                    it.copy(
                        currentWaypointIndex = waypoints.size - 1, 
                        fraction = 0.0,
                        isRunning = false, 
                        speedMs = 0f,
                        currentLat = waypoints.last().lat,
                        currentLng = waypoints.last().lng
                    ) 
                }
                pushToFirebase(autoFlow.value)
                return
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
                "routeId" to routeId,
                "activeDirection" to direction,
                "currentStop" to state.currentStop.uppercase(Locale.ROOT),
                "lastUpdated" to currentTimestampStr
            )

            repository.updateSimulatedDriverProfile(state.uid, profileData)

            val liveLoc = DriverLiveLocation(
                lat = state.currentLat,
                lng = state.currentLng,
                heading = state.currentHeading,
                speed = if (state.isRunning) state.speedMs else 0f,
                lastUpdated = currentTimestampStr,
                isOnline = true,
                routeId = routeId,
                activeDirection = direction,
                currentStop = state.currentStop.uppercase(Locale.ROOT)
            )
            repository.updateLiveLocation(state.uid, liveLoc)

            // CRITICAL: Update legacy/driver_locations node which the map uses for its main query
            val driverLoc = DriverLocation(
                driverId = state.uid,
                latitude = state.currentLat,
                longitude = state.currentLng,
                bearing = state.currentHeading,
                speed = if (state.isRunning) state.speedMs else 0f,
                timestamp = System.currentTimeMillis(),
                lastUpdated = currentTimestampStr
            )
            repository.updateDriverLocation(driverLoc)

            val mockLocation = android.location.Location("demo").apply {
                latitude = state.currentLat
                longitude = state.currentLng
                bearing = state.currentHeading
                speed = if (state.isRunning) state.speedMs else 0f
                time = System.currentTimeMillis()
            }
            
            // Set isRideActive to false so it appears as an available cruising auto
            repository.updateLiveTracking(
                uid = state.uid,
                location = mockLocation,
                isRideActive = false
            )
        }

        private fun clearFromFirebase(uid: String) {
            val currentTimestampStr = repository.currentTime()
            val profileData = mapOf(
                "isAvailable" to false,
                "lastUpdated" to currentTimestampStr
            )
            repository.updateSimulatedDriverProfile(uid, profileData)
            repository.updateLiveLocation(uid, DriverLiveLocation(isOnline = false))
        }
    }

    private fun findNearestStop(lat: Double, lng: Double): String {
        var minDest = Double.MAX_VALUE
        var closest = "GURRAMGUDA"
        
        // Use both static and dynamic stops to ensure coverage
        val allStops = mutableListOf<RouteStop>()
        allStops.addAll(RouteData.stops)
        RouteData.getAllCachedStops().forEach { 
            allStops.add(RouteStop(it.name, it.lat, it.lng))
        }

        for (stop in allStops) {
            val d = calculateDistanceMeters(lat, lng, stop.latitude, stop.longitude)
            if (d < minDest) {
                minDest = d
                closest = stop.name
            }
        }
        return closest.uppercase(Locale.ROOT)
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
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID, 
                notification, 
                android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
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
        instance = null
        serviceScope.cancel()
    }
}
