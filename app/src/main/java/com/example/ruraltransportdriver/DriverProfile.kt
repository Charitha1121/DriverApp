package com.example.ruraltransportdriver

/**
 * Driver Profile model representing driver identity, vehicle details,
 * route assignment, approval status, and live operating state in Firebase.
 */
data class DriverProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    val vehicleType: String = "Shared Auto",
    val totalSeats: Int = 4,
    val availableSeats: Int = 4,
    val routeId: String = "ROUTE_01",
    val routeName: String = "IBP → Gurramguda → Champapet → Issdan",
    val currentStop: String = "IBP",
    val isAvailable: Boolean = false,
    val approvalStatus: String = APPROVAL_PENDING,
    val registeredAt: String = "",
    val lastUpdated: String = ""
) {
    companion object {
        const val APPROVAL_PENDING = "PENDING"
        const val APPROVAL_APPROVED = "APPROVED"
        const val APPROVAL_REJECTED = "REJECTED"
    }

    val isApproved: Boolean
        get() = approvalStatus.equals(APPROVAL_APPROVED, ignoreCase = true)
}
