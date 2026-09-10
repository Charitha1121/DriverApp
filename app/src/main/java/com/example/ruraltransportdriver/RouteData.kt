package com.example.ruraltransportdriver

/**
 * Route entity representing predefined rural transport corridors.
 */
data class RouteInfo(
    val id: String,
    val name: String,
    val stops: List<String>
)

object RouteData {
    val predefinedRoutes = listOf(
        RouteInfo(
            id = "ROUTE_01",
            name = "IBP → Gurramguda → Champapet → Issdan",
            stops = listOf("IBP", "Gurramguda", "Champapet", "Issdan")
        ),
        RouteInfo(
            id = "ROUTE_02",
            name = "Issdan → Champapet → Gurramguda → IBP",
            stops = listOf("Issdan", "Champapet", "Gurramguda", "IBP")
        )
    )

    fun getRouteById(id: String): RouteInfo? {
        return predefinedRoutes.firstOrNull { it.id == id }
    }

    fun getStopsForRoute(routeId: String): List<String> {
        return getRouteById(routeId)?.stops ?: predefinedRoutes.first().stops
    }
}
