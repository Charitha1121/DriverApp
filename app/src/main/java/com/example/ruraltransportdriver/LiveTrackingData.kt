package com.example.ruraltransportdriver

/**
 * Real-time driver live tracking model persisted to Firebase Realtime Database
 * under `drivers/{uid}/liveTracking`.
 * Consumed by passenger app for live vehicle tracking and rendering moving vehicle icon.
 */
data class LiveTrackingData(
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val heading: Float = 0f,
    val speed: Float = 0f,
    val isRideActive: Boolean = false,
    val lastUpdated: Long = 0L
)
