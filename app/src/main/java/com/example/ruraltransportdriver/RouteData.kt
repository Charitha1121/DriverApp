package com.example.ruraltransportdriver

enum class RouteDirection {
    FORWARD,
    REVERSE;

    companion object {
        fun fromString(value: String?): RouteDirection {
            return when (value?.uppercase(java.util.Locale.ROOT)) {
                "REVERSE" -> REVERSE
                else -> FORWARD
            }
        }
    }
}

data class RouteStop(
    val name: String,
    val latitude: Double,
    val longitude: Double
)

data class Route(
    val id: String,
    val name: String,
    val stops: List<String>
)

object RouteData {

    const val ROUTE_ID = "ROUTE_01"
    const val LEGACY_ROUTE_ID = "GURRAMGUDA_NADERGUL"

    // New route IDs
    const val ROUTE_IBP_GURRAMGUDA = "ROUTE_IBP_GURRAMGUDA"
    const val ROUTE_GURRAMGUDA_RINGROAD = "ROUTE_GURRAMGUDA_RINGROAD"
    const val ROUTE_RINGROAD_SANTOSHNAGAR = "ROUTE_RINGROAD_SANTOSHNAGAR"
    const val ROUTE_BALAPUR_SPHOORTHY = "ROUTE_BALAPUR_SPHOORTHY"

    /**
     * Resolves routeId to canonical routeId ("ROUTE_01") recognized by the Passenger App.
     * UNCHANGED — only ROUTE_01/legacy Gurramguda-Nadergul strings resolve to ROUTE_01.
     * New route IDs pass through unchanged, exactly as before for any unrecognized ID.
     */
    fun canonicalRouteId(routeId: String?): String {
        val r = routeId?.trim() ?: return ROUTE_ID
        return if (r.isBlank() ||
            r.equals(ROUTE_ID, ignoreCase = true) ||
            r.equals(LEGACY_ROUTE_ID, ignoreCase = true)
        ) {
            ROUTE_ID
        } else {
            r
        }
    }

    fun isSameRoute(r1: String?, r2: String?): Boolean {
        if (r1.isNullOrBlank() || r2.isNullOrBlank()) return true
        return canonicalRouteId(r1).equals(canonicalRouteId(r2), ignoreCase = true)
    }

    /**
     * Main route used for automatic GPS stop detection.
     * UNCHANGED — untouched from the original file.
     * NOTE: automatic GPS stop detection in DashboardViewModel currently always
     * checks against THIS list only, regardless of which route the driver selected.
     * That's a pre-existing limitation, not something this change fixes — flagging
     * it so it doesn't surprise you later if a driver on one of the new routes
     * doesn't get auto-stop-detected correctly.
     */
    val stops = listOf(
        RouteStop(name = "Gurramguda", latitude = 17.29421, longitude = 78.56753),
        RouteStop(name = "Jay Suryapatnam", latitude = 17.2788, longitude = 78.5573),
        RouteStop(name = "Sphoorthy College", latitude = 17.28218, longitude = 78.55251),
        RouteStop(name = "Nadergul", latitude = 17.27464, longitude = 78.53995)
    )

    // ---------------------------------------------------------
    // NEW ROUTE STOP DEFINITIONS
    // ---------------------------------------------------------

    private val ibpGurramgudaStops = listOf(
        RouteStop(name = "IBP Petrol Pump, Nagarjuna Sagar Road", latitude = 17.2945, longitude = 78.5650),
        RouteStop(name = "Gurramguda Village", latitude = 17.2940, longitude = 78.5660)
    )

    private val gurramgudaRingRoadStops = listOf(
        RouteStop(name = "Gurramguda Village", latitude = 17.2940, longitude = 78.5660),
        RouteStop(name = "Gurramguda Cross Road", latitude = 17.3079, longitude = 78.5674),
        RouteStop(name = "B.N. Reddy Nagar Bus Stop", latitude = 17.3235, longitude = 78.5630),
        RouteStop(name = "Vanasthalipuram", latitude = 17.3350, longitude = 78.5510),
        RouteStop(name = "Bairamalguda Cross Road", latitude = 17.3440, longitude = 78.5512),
        RouteStop(name = "Sagar Ring Road (LB Nagar)", latitude = 17.3484, longitude = 78.5510)
    )

    private val ringRoadSantoshnagarStops = listOf(
        RouteStop(name = "Sagar Ring Road (LB Nagar)", latitude = 17.3484, longitude = 78.5510),
        RouteStop(name = "L.B. Nagar Metro Station", latitude = 17.3502, longitude = 78.5475),
        RouteStop(name = "Kothapet Fruit Market", latitude = 17.3565, longitude = 78.5450),
        RouteStop(name = "Chaitanyapuri Metro Station", latitude = 17.3620, longitude = 78.5430),
        RouteStop(name = "Dilsukhnagar Bus Station", latitude = 17.3687, longitude = 78.5247),
        RouteStop(name = "Moosarambagh X Road", latitude = 17.3712, longitude = 78.5135),
        RouteStop(name = "Saidabad Colony", latitude = 17.3615, longitude = 78.5100),
        RouteStop(name = "Santoshnagar Cross Roads", latitude = 17.3544, longitude = 78.5076)
    )

    private val balapurSphoorthyStops = listOf(
        RouteStop(name = "Balapur X Road", latitude = 17.3020, longitude = 78.5150),
        RouteStop(name = "Udyog Nagar", latitude = 17.2965, longitude = 78.5210),
        RouteStop(name = "Badangpet Cheruvu Bus Stop", latitude = 17.2885, longitude = 78.5320),
        RouteStop(name = "MVSR Engineering College, Nadergul", latitude = 17.2831, longitude = 78.5492),
        RouteStop(name = "Kammaguda Bus Stop", latitude = 17.2840, longitude = 78.5570),
        RouteStop(name = "Nadergul Village", latitude = 17.2985, longitude = 78.5670),
        RouteStop(name = "Sphoorthy Engineering College", latitude = 17.2960, longitude = 78.5675)
    )

    /**
     * Maps every route ID (existing + new) to its ordered RouteStop list.
     * ROUTE_01 continues to resolve to the original `stops` list, unchanged.
     */
    private val allRouteStops: Map<String, List<RouteStop>> = mapOf(
        ROUTE_ID to stops,
        ROUTE_IBP_GURRAMGUDA to ibpGurramgudaStops,
        ROUTE_GURRAMGUDA_RINGROAD to gurramgudaRingRoadStops,
        ROUTE_RINGROAD_SANTOSHNAGAR to ringRoadSantoshnagarStops,
        ROUTE_BALAPUR_SPHOORTHY to balapurSphoorthyStops
    )

    /**
     * Compatibility list for RegistrationScreen.
     * Original ROUTE_01 and LEGACY_ROUTE_ID entries UNCHANGED.
     * Four new routes appended.
     */
    val predefinedRoutes = listOf(
        Route(id = ROUTE_ID, name = "Gurramguda Corridor", stops = stops.map { it.name }),
        Route(id = LEGACY_ROUTE_ID, name = "Gurramguda Corridor (Legacy)", stops = stops.map { it.name }),
        Route(id = ROUTE_IBP_GURRAMGUDA, name = "IBP – Gurramguda Local", stops = ibpGurramgudaStops.map { it.name }),
        Route(id = ROUTE_GURRAMGUDA_RINGROAD, name = "Gurramguda – Sagar Ring Road", stops = gurramgudaRingRoadStops.map { it.name }),
        Route(id = ROUTE_RINGROAD_SANTOSHNAGAR, name = "Sagar Ring Road – Santoshnagar", stops = ringRoadSantoshnagarStops.map { it.name }),
        Route(id = ROUTE_BALAPUR_SPHOORTHY, name = "Balapur – Sphoorthy College", stops = balapurSphoorthyStops.map { it.name })
    )

    private val firebaseRepository = FirebaseRepository()
    private val cachedStops = mutableMapOf<String, DbStop>()
    private val cachedRoutes = mutableMapOf<String, DbRoute>()

    init {
        firebaseRepository.seedInitialRouteData()
        firebaseRepository.observeAllStops { stopsList ->
            synchronized(cachedStops) {
                cachedStops.clear()
                for (s in stopsList) cachedStops[s.id] = s
            }
        }
        firebaseRepository.observeAllRoutes { routesList ->
            synchronized(cachedRoutes) {
                cachedRoutes.clear()
                for (r in routesList) cachedRoutes[r.id] = r
            }
        }
    }

    fun getAllCachedStops(): List<DbStop> {
        return synchronized(cachedStops) { cachedStops.values.toList() }
    }

    fun getRouteById(id: String): Route? {
        val canonical = canonicalRouteId(id)
        val staticMatch = predefinedRoutes.firstOrNull { it.id.equals(canonical, ignoreCase = true) }
        if (staticMatch != null) return staticMatch

        val dbRoute = synchronized(cachedRoutes) { cachedRoutes[canonical] } ?: return null
        val stopNames = dbRoute.stopOrder.map { stopId ->
            synchronized(cachedStops) { cachedStops[stopId]?.name ?: "Stop" }
        }
        return Route(dbRoute.id, dbRoute.name, stopNames)
    }

    fun getStopsForRoute(routeId: String): List<String> {
        val canonical = canonicalRouteId(routeId)
        val staticStops = allRouteStops[canonical]
        if (staticStops != null) return staticStops.map { it.name }

        val dbRoute = synchronized(cachedRoutes) { cachedRoutes[canonical] } ?: return emptyList()
        return dbRoute.stopOrder.map { stopId ->
            synchronized(cachedStops) { cachedStops[stopId]?.name ?: "Stop" }
        }
    }

    fun getStopsInDirection(routeId: String, direction: RouteDirection): List<String> {
        val baseStops = getStopsForRoute(routeId)
        return when (direction) {
            RouteDirection.FORWARD -> baseStops
            RouteDirection.REVERSE -> baseStops.reversed()
        }
    }

    fun getStopsAhead(routeId: String, currentStop: String, direction: RouteDirection): List<String> {
        val orderedStops = getStopsInDirection(routeId, direction)
        if (orderedStops.isEmpty()) return emptyList()

        val currentIndex = orderedStops.indexOfFirst { it.equals(currentStop, ignoreCase = true) }
        return if (currentIndex >= 0) {
            orderedStops.subList(currentIndex, orderedStops.size)
        } else {
            orderedStops
        }
    }

    fun getDirectionTitle(routeId: String, direction: RouteDirection): String {
        val ordered = getStopsInDirection(routeId, direction)
        return if (ordered.size >= 2) {
            "${ordered.first()} → ${ordered.last()}"
        } else {
            direction.name
        }
    }

    fun getTerminalStops(routeId: String): Pair<String, String> {
        val base = getStopsForRoute(routeId)
        return if (base.isNotEmpty()) {
            base.first() to base.last()
        } else {
            "Start" to "End"
        }
    }

    const val AUTO_DETECTION_RADIUS_METERS = 150f
}