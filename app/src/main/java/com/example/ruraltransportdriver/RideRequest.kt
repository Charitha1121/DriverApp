package com.example.ruraltransportdriver

/**
 * Ride request data model shared between Passenger and Driver applications.
 * Default values ensure seamless Firebase Realtime Database deserialization.
 */
data class RideRequest(
    val requestId: String = "",
    val passengerId: String = "",
    val passengerName: String = "Passenger",
    val passengerPhone: String = "",
    val driverId: String = "",
    val pickupStop: String = "",
    val destinationStop: String = "",
    val route: String = "",
    val routeId: String = "",
    val passengers: Int = 1,
    val requestedSeats: Int = 1,
    val status: String = STATUS_PENDING,
    val timestamp: String = "",
    val createdAt: String = "",
    val acceptedAt: String = "",
    val startedAt: String = "",
    val completedAt: String = ""
) {
    companion object {
        const val STATUS_PENDING = "PENDING"
        const val STATUS_ACCEPTED = "ACCEPTED"
        const val STATUS_REJECTED = "REJECTED"
        const val STATUS_IN_PROGRESS = "IN_PROGRESS"
        const val STATUS_COMPLETED = "COMPLETED"
        const val STATUS_CANCELLED = "CANCELLED"
    }

    /**
     * Resolves effective seat count from either requestedSeats or passengers field.
     */
    fun effectiveSeats(): Int = if (requestedSeats > 0) requestedSeats else if (passengers > 0) passengers else 1
}