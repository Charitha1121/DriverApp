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

    /**
     * Resolves routeId to canonical routeId ("ROUTE_01") recognized by the Passenger App.
     */
    fun canonicalRouteId(routeId: String?): String {
        val r = routeId?.trim() ?: return ROUTE_ID
        return if (r.isBlank() ||
            r.equals(ROUTE_ID, ignoreCase = true) ||
            r.equals(LEGACY_ROUTE_ID, ignoreCase = true) ||
            r.contains("GURRAMGUDA", ignoreCase = true) ||
            r.contains("NADERGUL", ignoreCase = true)
        ) {
            ROUTE_ID
        } else {
            r
        }
    }

    /**
     * Checks whether two route identifiers refer to the same corridor.
     */
    fun isSameRoute(r1: String?, r2: String?): Boolean {
        if (r1.isNullOrBlank() || r2.isNullOrBlank()) return true
        return canonicalRouteId(r1).equals(canonicalRouteId(r2), ignoreCase = true)
    }

    /**
     * Main route used for automatic GPS stop detection.
     */
    val stops = listOf(

        RouteStop(
            name = "Gurramguda",
            latitude = 17.29421,
            longitude = 78.56753
        ),

        RouteStop(
            name = "Jay Suryapatnam",
            latitude = 17.2788,
            longitude = 78.5573
        ),

        RouteStop(
            name = "Sphoorthy College",
            latitude = 17.28218,
            longitude = 78.55251
        ),

        RouteStop(
            name = "Nadergul",
            latitude = 17.27464,
            longitude = 78.53995
        )
    )

    /**
     * Compatibility list for RegistrationScreen.
     */
    val predefinedRoutes = listOf(
        Route(
            id = ROUTE_ID,
            name = "Gurramguda Corridor",
            stops = stops.map { it.name }
        ),
        Route(
            id = LEGACY_ROUTE_ID,
            name = "Gurramguda Corridor (Legacy)",
            stops = stops.map { it.name }
        )
    )

    /**
     * Looks up a route by its ID (canonical or legacy).
     */
    fun getRouteById(id: String): Route? {
        val canonical = canonicalRouteId(id)
        return predefinedRoutes.firstOrNull { it.id.equals(canonical, ignoreCase = true) }
    }

    /**
     * Returns the list of stop names for a given route ID in base order.
     */
    fun getStopsForRoute(routeId: String): List<String> {
        val canonical = canonicalRouteId(routeId)
        return if (canonical == ROUTE_ID) {
            stops.map { it.name }
        } else {
            emptyList()
        }
    }

    /**
     * Returns the ordered list of stop names based on the direction of travel.
     * FORWARD: Gurramguda -> Jay Suryapatnam -> Sphoorthy College -> Nadergul
     * REVERSE: Nadergul -> Sphoorthy College -> Jay Suryapatnam -> Gurramguda
     */
    fun getStopsInDirection(routeId: String, direction: RouteDirection): List<String> {
        val baseStops = getStopsForRoute(routeId)
        return when (direction) {
            RouteDirection.FORWARD -> baseStops
            RouteDirection.REVERSE -> baseStops.reversed()
        }
    }

    /**
     * Returns all stops that are at or ahead of the driver's current stop in their travel direction.
     * Only passengers waiting at these stops can be picked up by this driver.
     */
    fun getStopsAhead(routeId: String, currentStop: String, direction: RouteDirection): List<String> {
        val orderedStops = getStopsInDirection(routeId, direction)
        if (orderedStops.isEmpty()) return emptyList()

        val currentIndex = orderedStops.indexOfFirst { it.equals(currentStop, ignoreCase = true) }
        return if (currentIndex >= 0) {
            orderedStops.subList(currentIndex, orderedStops.size)
        } else {
            // Default to all stops in that direction if current stop not matched
            orderedStops
        }
    }

    /**
     * Human-readable label for the route direction (e.g. "Gurramguda → Nadergul")
     */
    fun getDirectionTitle(routeId: String, direction: RouteDirection): String {
        val ordered = getStopsInDirection(routeId, direction)
        return if (ordered.size >= 2) {
            "${ordered.first()} → ${ordered.last()}"
        } else {
            direction.name
        }
    }

    /**
     * Start and end terminals for this route.
     */
    fun getTerminalStops(routeId: String): Pair<String, String> {
        val base = getStopsForRoute(routeId)
        return if (base.isNotEmpty()) {
            base.first() to base.last()
        } else {
            "Start" to "End"
        }
    }

    /**
     * Automatic GPS stop detection radius.
     */
    const val AUTO_DETECTION_RADIUS_METERS = 150f
}