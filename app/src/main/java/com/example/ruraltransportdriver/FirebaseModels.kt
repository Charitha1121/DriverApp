package com.example.ruraltransportdriver

import com.google.firebase.database.IgnoreExtraProperties

@IgnoreExtraProperties
data class DbStop(
    val id: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lng: Double = 0.0,
    val createdByUid: String = "system",
    val createdAt: Long = System.currentTimeMillis()
)

@IgnoreExtraProperties
data class DbRoute(
    val id: String = "",
    val name: String = "",
    val sourceStopId: String = "",
    val destinationStopId: String = "",
    val stopOrder: List<String> = emptyList(),
    val createdByUid: String = "system",
    val createdAt: Long = System.currentTimeMillis(),
    val status: String = "active"
)
