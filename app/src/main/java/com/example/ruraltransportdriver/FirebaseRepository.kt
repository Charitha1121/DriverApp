package com.example.ruraltransportdriver

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import android.location.Location
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Centralized Firebase Repository handling Firebase Authentication
 * and Firebase Realtime Database operations.
 */
class FirebaseRepository {

    companion object {
        const val DATABASE_URL = "https://ruraltransport-54174-default-rtdb.asia-southeast1.firebasedatabase.app"
        const val NODE_DRIVERS = "drivers"
        const val NODE_RIDE_REQUESTS = "ride_requests"
        const val NODE_LOCATIONS = "driver_locations"
        const val NODE_AUTO_STATUS = "auto_status"
        const val NODE_PASSENGER_DEMAND = "passenger_demand"
        const val NODE_LIVE_TRACKING = "liveTracking"
    }

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()

    private val database: DatabaseReference =
        FirebaseDatabase.getInstance(DATABASE_URL).reference

    val currentUid: String?
        get() = auth.currentUser?.uid

    val isUserLoggedIn: Boolean
        get() = auth.currentUser != null

    val currentUserEmail: String?
        get() = auth.currentUser?.email

    // =========================================================
    // AUTHENTICATION
    // =========================================================

    fun login(
        email: String,
        pass: String,
        onSuccess: (String) -> Unit,
        onError: (String) -> Unit
    ) {
        if (email.isBlank() || pass.isBlank()) {
            onError("Email and password cannot be empty")
            return
        }

        auth.signInWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: run {
                    onError("Authentication failed: Missing UID")
                    return@addOnSuccessListener
                }
                onSuccess(uid)
            }
            .addOnFailureListener { exception ->
                val errorMsg = when {
                    exception.message?.contains("no user", ignoreCase = true) == true ->
                        "No account found with this email. Please register."
                    exception.message?.contains("password", ignoreCase = true) == true ->
                        "Incorrect password. Please try again."
                    exception.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your internet connection."
                    else -> exception.localizedMessage ?: "Login failed. Please try again."
                }
                onError(errorMsg)
            }
    }

    fun register(
        email: String,
        pass: String,
        profile: DriverProfile,
        onSuccess: (DriverProfile) -> Unit,
        onError: (String) -> Unit
    ) {
        auth.createUserWithEmailAndPassword(email.trim(), pass)
            .addOnSuccessListener { result ->
                val uid = result.user?.uid ?: run {
                    onError("Registration failed: Could not obtain User ID")
                    return@addOnSuccessListener
                }

                val fullProfile = profile.copy(
                    uid = uid,
                    email = email.trim(),
                    approvalStatus = DriverProfile.APPROVAL_PENDING,
                    isAvailable = false,
                    availableSeats = profile.totalSeats,
                    registeredAt = currentTime(),
                    lastUpdated = currentTime()
                )

                saveDriverProfile(
                    profile = fullProfile,
                    onSuccess = { onSuccess(fullProfile) },
                    onError = { err -> onError(err) }
                )
            }
            .addOnFailureListener { exception ->
                val errorMsg = when {
                    exception.message?.contains("already in use", ignoreCase = true) == true ->
                        "This email is already registered. Please login instead."
                    exception.message?.contains("badly formatted", ignoreCase = true) == true ->
                        "Please provide a valid email address."
                    exception.message?.contains("network", ignoreCase = true) == true ->
                        "Network error. Please check your internet connection."
                    else -> exception.localizedMessage ?: "Registration failed. Please try again."
                }
                onError(errorMsg)
            }
    }

    fun logout(onLoggedOut: () -> Unit) {
        auth.signOut()
        onLoggedOut()
    }

    // =========================================================
    // DRIVER PROFILE & APPROVAL
    // =========================================================

    fun saveDriverProfile(
        profile: DriverProfile,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (profile.uid.isBlank()) {
            onError("Driver UID cannot be blank")
            return
        }
        val authenticatedUid = currentUid
        if (profile.uid != authenticatedUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        database.child(NODE_DRIVERS).child(profile.uid)
            .setValue(profile)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to save driver profile")
            }
    }

    fun fetchDriverProfile(
        uid: String,
        onSuccess: (DriverProfile?) -> Unit,
        onError: (String) -> Unit
    ) {
        database.child(NODE_DRIVERS).child(uid)
            .get()
            .addOnSuccessListener { snapshot ->
                val profile = snapshot.getValue(DriverProfile::class.java)
                onSuccess(profile)
            }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to fetch driver profile")
            }
    }

    fun observeDriverProfile(
        uid: String,
        onProfileChanged: (DriverProfile) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                if (snapshot.exists()) {
                    val profile = snapshot.getValue(DriverProfile::class.java)
                    if (profile != null) {
                        onProfileChanged(profile)
                    } else {
                        // Node exists but failed to parse into object
                        onProfileChanged(DriverProfile(uid = uid, approvalStatus = DriverProfile.APPROVAL_PENDING))
                    }
                } else {
                    // Node doesn't exist yet in the database (e.g., deleted or newly created via console)
                    onProfileChanged(DriverProfile(uid = uid, approvalStatus = DriverProfile.APPROVAL_PENDING))
                }
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRepository", "observeDriverProfile onCancelled: ${error.message} (code: ${error.code})")
                onError(error.message)
            }
        }

        database.child(NODE_DRIVERS).child(uid).addValueEventListener(listener)
        return listener
    }

    fun removeDriverProfileListener(uid: String, listener: ValueEventListener) {
        database.child(NODE_DRIVERS).child(uid).removeEventListener(listener)
    }

    // =========================================================
    // AVAILABILITY & SEATS
    // =========================================================

    fun updateDriverAvailability(
        uid: String,
        isAvailable: Boolean,
        activeDirection: RouteDirection? = null,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (uid != currentUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }
        val updates = mutableMapOf<String, Any>(
            "isAvailable" to isAvailable,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_DRIVERS).child(uid)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update availability")
            }
    }

    fun updateDriverDirection(
        uid: String,
        direction: RouteDirection,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        onSuccess()
    }

    fun updateCurrentStop(
        uid: String,
        stop: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSuccess()
    }

    fun updateAvailableSeats(
        uid: String,
        seats: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        onSuccess()
    }

    // Backward compatibility with legacy auto_status node
    fun updateAutoStatus(
        autoId: String,
        status: String,
        currentStop: String,
        availableSeats: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (autoId != currentUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }
        val autoData = mapOf(
            "autoId" to autoId,
            "status" to status,
            "currentStop" to currentStop,
            "availableSeats" to availableSeats,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_AUTO_STATUS).child(autoId)
            .setValue(autoData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update auto status")
            }
    }

    // =========================================================
    // RIDE REQUESTS & CONCURRENCY TRANSACTIONS
    // =========================================================

    fun observeRideRequests(
        onRequestsChanged: (List<RideRequest>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val requests = snapshot.children.mapNotNull { child ->
                    try {
                        val req = child.getValue(RideRequest::class.java)
                        if (req != null) {
                            // Reconcile field aliases between PassengerApp and DriverApp
                            req.copy(
                                pickupStop = req.effectivePickup(),
                                destinationStop = req.effectiveDestination(),
                                route = req.effectiveRoute()
                            )
                        } else null
                    } catch (e: Exception) {
                        android.util.Log.e("FirebaseRepository", "Error deserializing ride request ${child.key}: ${e.message}", e)
                        null
                    }
                }
                onRequestsChanged(requests)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRepository", "observeRideRequests onCancelled: ${error.message} (code: ${error.code})")
                onError(error.message)
            }
        }

        database.child(NODE_RIDE_REQUESTS).addValueEventListener(listener)
        return listener
    }

    fun observeDriverActiveRide(
        driverId: String,
        onActiveRideChanged: (RideRequest?) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val activeRide = snapshot.children.mapNotNull { child ->
                    try {
                        val req = child.getValue(RideRequest::class.java)
                        if (req != null) {
                            req.copy(
                                pickupStop = req.effectivePickup(),
                                destinationStop = req.effectiveDestination(),
                                route = req.effectiveRoute()
                            )
                        } else null
                    } catch (e: Exception) {
                        null
                    }
                }.firstOrNull { req ->
                    req.driverId == driverId && (
                        req.status.equals(RideRequest.STATUS_ACCEPTED, ignoreCase = true) ||
                        req.status.equals(RideRequest.STATUS_IN_PROGRESS, ignoreCase = true)
                    )
                }
                onActiveRideChanged(activeRide)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRepository", "observeDriverActiveRide onCancelled: ${error.message} (code: ${error.code})")
                onError(error.message)
            }
        }

        database.child(NODE_RIDE_REQUESTS).addValueEventListener(listener)
        return listener
    }

    fun removeRideRequestListener(listener: ValueEventListener) {
        database.child(NODE_RIDE_REQUESTS).removeEventListener(listener)
    }

    /**
     * Critical Concurrency Transaction:
     * Atomically validates request is PENDING and unassigned, then commits ACCEPTED with current driver details.
     * Prevents race condition where Driver A and Driver B accept simultaneously.
     */
    fun acceptRideRequestTransaction(
        driver: DriverProfile,
        request: RideRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        if (!driver.isApproved) {
            onError("Only approved drivers can accept ride requests.")
            return
        }
        if (!driver.isAvailable) {
            onError("You must be ONLINE to accept ride requests.")
            return
        }
        val neededSeats = request.effectiveSeats()
        if (driver.availableSeats < neededSeats) {
            onError("Not enough seats available (${driver.availableSeats} available, $neededSeats requested).")
            return
        }

        val authenticatedUid = currentUid
        if (driver.uid != authenticatedUid || authenticatedUid == null) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val requestRef = database.child(NODE_RIDE_REQUESTS).child(request.requestId)

        requestRef.runTransaction(object : com.google.firebase.database.Transaction.Handler {
            override fun doTransaction(mutableData: com.google.firebase.database.MutableData): com.google.firebase.database.Transaction.Result {
                val currentReq = mutableData.getValue(RideRequest::class.java)
                    ?: return com.google.firebase.database.Transaction.abort()

                // Concurrency Guard: must still be PENDING and unassigned
                if (currentReq.status != RideRequest.STATUS_PENDING || currentReq.driverId.isNotBlank()) {
                    return com.google.firebase.database.Transaction.abort()
                }

                mutableData.child("status").value = RideRequest.STATUS_ACCEPTED
                mutableData.child("driverId").value = authenticatedUid
                mutableData.child("acceptedAt").value = currentTime()

                return com.google.firebase.database.Transaction.success(mutableData)
            }

            override fun onComplete(
                error: DatabaseError?,
                committed: Boolean,
                currentData: DataSnapshot?
            ) {
                if (error != null) {
                    onError(error.message)
                    return
                }
                if (!committed) {
                    onError("This ride was already accepted by another driver or cancelled.")
                    return
                }

                onSuccess()
            }
        })
    }

    fun startRide(
        requestId: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "status" to RideRequest.STATUS_IN_PROGRESS,
            "startedAt" to currentTime()
        )
        database.child(NODE_RIDE_REQUESTS).child(requestId)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error.localizedMessage ?: "Failed to start ride") }
    }

    fun completeRide(
        driver: DriverProfile,
        request: RideRequest,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "status" to RideRequest.STATUS_COMPLETED,
            "completedAt" to currentTime()
        )
        database.child(NODE_RIDE_REQUESTS).child(request.requestId)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error.localizedMessage ?: "Failed to complete ride") }
    }

    fun cancelRide(
        driver: DriverProfile,
        request: RideRequest,
        reason: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "status" to RideRequest.STATUS_CANCELLED,
            "cancelledReason" to reason,
            "completedAt" to currentTime()
        )
        database.child(NODE_RIDE_REQUESTS).child(request.requestId)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error -> onError(error.localizedMessage ?: "Failed to cancel ride") }
    }

    fun updateRideRequestStatus(
        requestId: String,
        status: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        database.child(NODE_RIDE_REQUESTS).child(requestId).child("status")
            .setValue(status)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update ride request")
            }
    }

    // =========================================================
    // DRIVER LOCATION
    // =========================================================

    fun updateDriverLocation(
        location: DriverLocation,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (location.driverId.isBlank()) return
        if (location.driverId != currentUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val locationData = mapOf(
            "driverId" to location.driverId,
            "latitude" to location.latitude,
            "longitude" to location.longitude,
            "bearing" to location.bearing,
            "speed" to location.speed,
            "timestamp" to location.timestamp,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_LOCATIONS).child(location.driverId)
            .setValue(locationData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update driver location")
            }
    }

    // =========================================================
    // LIVE POSITION SYNC FOR PASSENGERS
    // Persisted under `drivers/{uid}/liveLocation`
    // =========================================================

    fun updateLiveLocation(
        uid: String,
        location: DriverLiveLocation,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) return
        if (uid != currentUid && !uid.startsWith("demo_auto_")) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val locationData = mapOf(
            "lat" to location.lat,
            "lng" to location.lng,
            "heading" to location.heading,
            "speed" to location.speed,
            "lastUpdated" to currentTime(),
            "isOnline" to location.isOnline,
            "routeId" to location.routeId,
            "activeDirection" to location.activeDirection,
            "currentStop" to location.currentStop
        )

        database.child(NODE_DRIVERS).child(uid).child("liveLocation")
            .setValue(locationData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update live location")
            }
    }

    fun updateSimulatedDriverProfile(
        uid: String,
        profileData: Map<String, Any>,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (!uid.startsWith("demo_auto_")) return
        database.child(NODE_DRIVERS).child(uid)
            .updateChildren(profileData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update simulated driver profile")
            }
    }

    fun setLiveLocationOffline(
        uid: String,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) return
        if (uid != currentUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val updates = mapOf(
            "isOnline" to false,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_DRIVERS).child(uid).child("liveLocation")
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update live location offline status")
            }
    }

    // =========================================================
    // LIVE TRACKING BROADCAST FOR PASSENGERS (RAPIDO/OLA/UBER STYLE)
    // Persisted under `drivers/{uid}/liveTracking`
    // =========================================================

    /**
     * Broadcasts live GPS coordinates, heading, speed, and ride active status
     * under `drivers/{uid}/liveTracking` for passenger live map rendering.
     * Arms Firebase onDisconnect() to automatically reset isRideActive to false
     * if the driver loses connection or app is closed.
     */
    fun updateLiveTracking(
        uid: String,
        location: Location,
        isRideActive: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) return
        if (uid != currentUid && !uid.startsWith("demo_auto_")) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val trackingRef = database.child(NODE_DRIVERS).child(uid).child(NODE_LIVE_TRACKING)

        // Setup onDisconnect cleanup: if client disconnects, isRideActive becomes false automatically
        trackingRef.child("isRideActive").onDisconnect().setValue(false)

        val trackingData = mapOf(
            "lat" to location.latitude,
            "lng" to location.longitude,
            "heading" to location.bearing,
            "speed" to location.speed,
            "isRideActive" to isRideActive,
            "lastUpdated" to System.currentTimeMillis()
        )

        trackingRef.updateChildren(trackingData)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update live tracking")
            }
    }

    /**
     * Directly updates isRideActive status and lastUpdated timestamp under `drivers/{uid}/liveTracking`.
     * Useful for immediate state changes when a ride starts, completes, or location sharing stops.
     */
    fun setLiveTrackingRideActive(
        uid: String,
        isRideActive: Boolean,
        onSuccess: () -> Unit = {},
        onError: (String) -> Unit = {}
    ) {
        if (uid.isBlank()) return
        if (uid != currentUid) {
            onError("Permission denied: Authentication ownership mismatch")
            return
        }

        val trackingRef = database.child(NODE_DRIVERS).child(uid).child(NODE_LIVE_TRACKING)
        if (isRideActive) {
            trackingRef.child("isRideActive").onDisconnect().setValue(false)
        }

        val updates = mapOf(
            "isRideActive" to isRideActive,
            "lastUpdated" to System.currentTimeMillis()
        )

        trackingRef.updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update live tracking active status")
            }
    }

    /**
     * Explicitly registers onDisconnect cleanup to set isRideActive = false.
     * Enforces continuous resiliency across reconnections.
     */
    fun setupLiveTrackingOnDisconnect(uid: String) {
        if (uid.isBlank()) return
        if (uid != currentUid) return

        val trackingRef = database.child(NODE_DRIVERS).child(uid).child(NODE_LIVE_TRACKING).child("isRideActive")
        trackingRef.onDisconnect().setValue(false)

        // Resiliency loop: listen to client connection lifecycle state changes to re-arm onDisconnect listeners
        database.child(".info/connected").addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val connected = snapshot.getValue(Boolean::class.java) ?: false
                if (connected) {
                    trackingRef.onDisconnect().setValue(false)
                }
            }
            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRepository", ".info/connected onCancelled: ${error.message} (code: ${error.code})")
            }
        })
    }

    // =========================================================
    // DIRECTION-AWARE PASSENGER DEMAND
    // =========================================================

    /**
     * Observes demand for a specific route and direction:
     * `passenger_demand/{routeId}/{direction.name}`
     * Returns a map of stopName -> waitingCount
     */
    fun observeDirectionDemand(
        routeId: String,
        direction: RouteDirection,
        onDemandChanged: (Map<String, Int>) -> Unit,
        onError: (String) -> Unit
    ): ValueEventListener {
        val listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                android.util.Log.d(
                    "FIREBASE_AUDIT",
                    "Raw passenger demand snapshot: ${snapshot.value}"
                )
                val demandMap = mutableMapOf<String, Int>()
                for (child in snapshot.children) {
                    val stopName = child.key ?: continue

                    // Priority 1: Count active waiting passengers from children count (supports live onDisconnect cleanup)
                    val activeChildrenCount = if (child.hasChild("waitingPassengers")) {
                        child.child("waitingPassengers").childrenCount.toInt()
                    } else 0

                    // Priority 2: Safely extract waitingCount if written as Number or String
                    val waitingCountVal = when (val v = child.child("waitingCount").value) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> null
                    }

                    // Priority 3: Fallback to primitive value at the stop node
                    val primitiveVal = when (val v = child.value) {
                        is Number -> v.toInt()
                        is String -> v.toIntOrNull() ?: 0
                        else -> null
                    }

                    val count = when {
                        activeChildrenCount > 0 -> activeChildrenCount
                        waitingCountVal != null -> waitingCountVal
                        primitiveVal != null -> primitiveVal
                        else -> 0
                    }

                    demandMap[stopName] = count
                    android.util.Log.d(
                        "FIREBASE_AUDIT",
                        "Parsed demand: stop='$stopName' -> count=$count (activeChildren=$activeChildrenCount, waitingCount=$waitingCountVal)"
                    )
                }
                onDemandChanged(demandMap)
            }

            override fun onCancelled(error: DatabaseError) {
                android.util.Log.e("FirebaseRepository", "observeDirectionDemand onCancelled: ${error.message} (code: ${error.code})")
                onError(error.message)
            }
        }

        database.child(NODE_PASSENGER_DEMAND)
            .child(routeId)
            .child(direction.name)
            .addValueEventListener(listener)
        return listener
    }

    fun removeDemandListener(
        routeId: String,
        direction: RouteDirection,
        listener: ValueEventListener
    ) {
        database.child(NODE_PASSENGER_DEMAND)
            .child(routeId)
            .child(direction.name)
            .removeEventListener(listener)
    }

    // =========================================================
    // UTILITIES
    // =========================================================

    fun currentTime(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
}