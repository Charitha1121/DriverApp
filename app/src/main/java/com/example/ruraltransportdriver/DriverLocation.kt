package com.example.ruraltransportdriver

/**
 * Real-time Driver Location model persisted to Firebase Realtime Database
 * under `driver_locations/{driverId}`.
 */
data class DriverLocation(
    val driverId: String = "",
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val bearing: Float = 0f,
    val speed: Float = 0f,
    val timestamp: Long = 0L,
    val lastUpdated: String = ""
)
