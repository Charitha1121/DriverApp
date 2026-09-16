package com.example.ruraltransportdriver

import com.google.firebase.database.IgnoreExtraProperties
import com.google.firebase.database.PropertyName

/**
 * Driver Profile model representing driver identity, vehicle details,
 * route assignment, approval status, and live operating state in Firebase.
 */
@IgnoreExtraProperties
data class DriverProfile(
    val uid: String = "",
    val name: String = "",
    val email: String = "",
    val phone: String = "",
    val vehicleNumber: String = "",
    val vehicleType: String = "Shared Auto",
    val totalSeats: Int = 4,
    val availableSeats: Int = 4,

    // Current project route
    val routeId: String = RouteData.ROUTE_ID,
    val routeName: String =
        "Gurramguda → Jay Suryapatnam → Sphoorthy College → Nadergul",

    val activeDirection: RouteDirection = RouteDirection.FORWARD,

    val currentStop: String = "",

    @get:PropertyName("isAvailable")
    @set:PropertyName("isAvailable")
    var isAvailable: Boolean = false,

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
        get() = approvalStatus.equals(
            APPROVAL_APPROVED,
            ignoreCase = true
        )
}