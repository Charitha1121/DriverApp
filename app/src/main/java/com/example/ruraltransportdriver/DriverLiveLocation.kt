package com.example.ruraltransportdriver

/**
 * Real-time driver location model persisted to Firebase Realtime Database
 * under `drivers/{uid}/liveLocation`.
 * Consumed by passenger app for live vehicle tracking and arrival forecasting.
 */
data class DriverLiveLocation(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val heading: Float = 0f,
    val speed: Float = 0f,
    val lastUpdated: String = "",
    val isOnline: Boolean = false,
    val routeId: String = RouteData.ROUTE_ID,
    val activeDirection: String = RouteDirection.FORWARD.name,
    val currentStop: String = ""
)
