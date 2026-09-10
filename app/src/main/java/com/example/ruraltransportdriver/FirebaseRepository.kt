package com.example.ruraltransportdriver

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
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
                val profile = snapshot.getValue(DriverProfile::class.java)
                if (profile != null) {
                    onProfileChanged(profile)
                }
            }

            override fun onCancelled(error: DatabaseError) {
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
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
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

    fun updateCurrentStop(
        uid: String,
        stop: String,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "currentStop" to stop,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_DRIVERS).child(uid)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update current stop")
            }
    }

    fun updateAvailableSeats(
        uid: String,
        seats: Int,
        onSuccess: () -> Unit,
        onError: (String) -> Unit
    ) {
        val updates = mapOf(
            "availableSeats" to seats,
            "lastUpdated" to currentTime()
        )

        database.child(NODE_DRIVERS).child(uid)
            .updateChildren(updates)
            .addOnSuccessListener { onSuccess() }
            .addOnFailureListener { error ->
                onError(error.localizedMessage ?: "Failed to update available seats")
            }
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
                    child.getValue(RideRequest::class.java)
                }
                onRequestsChanged(requests)
            }

            override fun onCancelled(error: DatabaseError) {
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
                    child.getValue(RideRequest::class.java)
                }.firstOrNull { req ->
                    req.driverId == driverId && (req.status == RideRequest.STATUS_ACCEPTED || req.status == RideRequest.STATUS_IN_PROGRESS)
                }
                onActiveRideChanged(activeRide)
            }

            override fun onCancelled(error: DatabaseError) {
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
                mutableData.child("driverId").value = driver.uid
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

                // Decrement seats safely
                val remainingSeats = (driver.availableSeats - neededSeats).coerceAtLeast(0)
                updateAvailableSeats(
                    uid = driver.uid,
                    seats = remainingSeats,
                    onSuccess = { onSuccess() },
                    onError = { onSuccess() }
                )
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
            .addOnSuccessListener {
                // Restore seats atomically
                val restoredSeats = (driver.availableSeats + request.effectiveSeats()).coerceAtMost(driver.totalSeats)
                updateAvailableSeats(
                    uid = driver.uid,
                    seats = restoredSeats,
                    onSuccess = { onSuccess() },
                    onError = { onSuccess() }
                )
            }
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
            .addOnSuccessListener {
                val restoredSeats = (driver.availableSeats + request.effectiveSeats()).coerceAtMost(driver.totalSeats)
                updateAvailableSeats(
                    uid = driver.uid,
                    seats = restoredSeats,
                    onSuccess = { onSuccess() },
                    onError = { onSuccess() }
                )
            }
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
    // UTILITIES
    // =========================================================

    fun currentTime(): String {
        val formatter = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        return formatter.format(Date())
    }
}