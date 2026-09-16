package com.example.ruraltransportdriver

import com.google.firebase.database.IgnoreExtraProperties

/**
 * Ride request data model shared between Passenger and Driver applications.
 * Default values ensure seamless Firebase Realtime Database deserialization.
 */
@IgnoreExtraProperties
data class RideRequest(
    val requestId: String = "",
    val passengerId: String = "",
    val passengerName: String = "Passenger",
    val passengerPhone: String = "",
    val driverId: String = "",
    val pickupStop: String = "",
    val pickupStopName: String = "",
    val destinationStop: String = "",
    val destinationStopName: String = "",
    val route: String = "",
    val routeName: String = "",
    val routeId: String = "",
    val passengers: Int = 1,
    val requestedSeats: Int = 1,
    val status: String = STATUS_PENDING,
    val timestamp: Any? = null,
    val createdAt: Any? = null,
    val updatedAt: Any? = null,
    val acceptedAt: String = "",
    val startedAt: String = "",
    val completedAt: String = "",
    val cancelledReason: String = ""
) {
    companion object {
        const val STATUS_PENDING = "pending"
        const val STATUS_ACCEPTED = "accepted"
        const val STATUS_REJECTED = "rejected"
        const val STATUS_IN_PROGRESS = "in_progress"
        const val STATUS_COMPLETED = "completed"
        const val STATUS_CANCELLED = "cancelled"
    }

    /**
     * Resolves effective pickup stop name across field variations.
     */
    fun effectivePickup(): String = pickupStop.ifBlank { pickupStopName }

    /**
     * Resolves effective destination stop name across field variations.
     */
    fun effectiveDestination(): String = destinationStop.ifBlank { destinationStopName }

    /**
     * Resolves effective route description across field variations.
     */
    fun effectiveRoute(): String = route.ifBlank { routeName }

    /**
     * Resolves effective seat count from either requestedSeats or passengers field.
     */
    fun effectiveSeats(): Int = if (requestedSeats > 0) requestedSeats else if (passengers > 0) passengers else 1
}