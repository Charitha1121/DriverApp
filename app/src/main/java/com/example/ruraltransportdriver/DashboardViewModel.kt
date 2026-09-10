package com.example.ruraltransportdriver

import androidx.lifecycle.ViewModel
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DashboardViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _currentProfile = MutableStateFlow(DriverProfile())
    val currentProfile: StateFlow<DriverProfile> = _currentProfile.asStateFlow()

    private val _activeRide = MutableStateFlow<RideRequest?>(null)
    val activeRide: StateFlow<RideRequest?> = _activeRide.asStateFlow()

    private val _pendingRequests = MutableStateFlow<List<RideRequest>>(emptyList())
    val pendingRequests: StateFlow<List<RideRequest>> = _pendingRequests.asStateFlow()

    private val _statusMessage = MutableStateFlow<String?>(null)
    val statusMessage: StateFlow<String?> = _statusMessage.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _isOperating = MutableStateFlow(false)
    val isOperating: StateFlow<Boolean> = _isOperating.asStateFlow()

    private val _isSharingLocation = MutableStateFlow(false)
    val isSharingLocation: StateFlow<Boolean> = _isSharingLocation.asStateFlow()

    private val _lastKnownLocation = MutableStateFlow<DriverLocation?>(null)
    val lastKnownLocation: StateFlow<DriverLocation?> = _lastKnownLocation.asStateFlow()

    private val dismissedRequestIds = mutableSetOf<String>()

    private var profileListener: ValueEventListener? = null
    private var requestsListener: ValueEventListener? = null

    fun initialize(profile: DriverProfile) {
        _currentProfile.value = profile
        observeProfile(profile.uid)
        observeAllRequests(profile)
    }

    private fun observeProfile(uid: String) {
        profileListener?.let { repository.removeDriverProfileListener(uid, it) }

        profileListener = repository.observeDriverProfile(
            uid = uid,
            onProfileChanged = { updatedProfile ->
                _currentProfile.value = updatedProfile
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    private fun observeAllRequests(profile: DriverProfile) {
        requestsListener?.let { repository.removeRideRequestListener(it) }

        requestsListener = repository.observeRideRequests(
            onRequestsChanged = { allRequests ->
                // Identify active ride for this driver
                val active = allRequests.firstOrNull { req ->
                    req.driverId == profile.uid &&
                            (req.status == RideRequest.STATUS_ACCEPTED || req.status == RideRequest.STATUS_IN_PROGRESS)
                }
                _activeRide.value = active

                // Identify pending requests for route
                val pending = allRequests.filter { req ->
                    req.status == RideRequest.STATUS_PENDING &&
                            req.driverId.isBlank() &&
                            !dismissedRequestIds.contains(req.requestId) &&
                            (req.routeId.isBlank() || req.routeId.equals(profile.routeId, ignoreCase = true) ||
                                    req.route.contains("IBP", ignoreCase = true) || req.route.contains("Issdan", ignoreCase = true))
                }
                _pendingRequests.value = pending
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    fun toggleAvailability(isOnline: Boolean) {
        val driver = _currentProfile.value
        if (!driver.isApproved) {
            _errorMessage.value = "Cannot go online: Driver account is not approved."
            return
        }

        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateDriverAvailability(
            uid = driver.uid,
            isAvailable = isOnline,
            onSuccess = {
                _isOperating.value = false
                _statusMessage.value = if (isOnline) "🟢 You are now ONLINE" else "🔴 You are now OFFLINE"
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    fun updateCurrentStop(stop: String) {
        val driver = _currentProfile.value
        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateCurrentStop(
            uid = driver.uid,
            stop = stop,
            onSuccess = {
                _statusMessage.value = "Stop updated to $stop"
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    fun updateAvailableSeats(seats: Int) {
        val driver = _currentProfile.value
        if (seats < 0 || seats > driver.totalSeats) {
            _errorMessage.value = "Seat count must be between 0 and ${driver.totalSeats}."
            return
        }

        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateAvailableSeats(
            uid = driver.uid,
            seats = seats,
            onSuccess = {
                _statusMessage.value = "Seats updated to $seats"
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    fun acceptRequest(request: RideRequest) {
        val driver = _currentProfile.value
        if (!driver.isAvailable) {
            _errorMessage.value = "Please go ONLINE to accept ride requests."
            return
        }
        if (_activeRide.value != null) {
            _errorMessage.value = "You already have an active ride in progress."
            return
        }
        val neededSeats = request.effectiveSeats()
        if (driver.availableSeats < neededSeats) {
            _errorMessage.value = "Insufficient seats ($neededSeats needed, ${driver.availableSeats} available)."
            return
        }

        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.acceptRideRequestTransaction(
            driver = driver,
            request = request,
            onSuccess = {
                _isOperating.value = false
                _statusMessage.value = "Ride accepted! Head to ${request.pickupStop}."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    fun rejectRequest(requestId: String) {
        dismissedRequestIds.add(requestId)
        _pendingRequests.value = _pendingRequests.value.filter { it.requestId != requestId }
        _statusMessage.value = "Ride request dismissed."
    }

    fun startRide() {
        val ride = _activeRide.value ?: return
        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.startRide(
            requestId = ride.requestId,
            onSuccess = {
                _isOperating.value = false
                _statusMessage.value = "Ride started. En route to ${ride.destinationStop.ifBlank { "destination" }}."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    fun completeRide() {
        val ride = _activeRide.value ?: return
        val driver = _currentProfile.value
        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.completeRide(
            driver = driver,
            request = ride,
            onSuccess = {
                _isOperating.value = false
                _statusMessage.value = "Ride completed successfully. Seats restored."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    fun cancelRide(reason: String = "Driver cancellation") {
        val ride = _activeRide.value ?: return
        val driver = _currentProfile.value
        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.cancelRide(
            driver = driver,
            request = ride,
            reason = reason,
            onSuccess = {
                _isOperating.value = false
                _statusMessage.value = "Ride cancelled. Seats restored."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    fun startLocationSharing(tracker: LocationTracker) {
        val driver = _currentProfile.value
        if (driver.uid.isBlank()) return

        tracker.startTracking(
            onLocationUpdated = { loc ->
                val driverLoc = DriverLocation(
                    driverId = driver.uid,
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    bearing = loc.bearing,
                    speed = loc.speed,
                    timestamp = loc.time,
                    lastUpdated = repository.currentTime()
                )
                _lastKnownLocation.value = driverLoc
                repository.updateDriverLocation(driverLoc)
            },
            onError = { err ->
                _errorMessage.value = err
                _isSharingLocation.value = false
            }
        )
        _isSharingLocation.value = true
        _statusMessage.value = "GPS Location sharing started."
    }

    fun stopLocationSharing(tracker: LocationTracker) {
        tracker.stopTracking()
        _isSharingLocation.value = false
        _statusMessage.value = "GPS Location sharing stopped."
    }

    fun clearFeedback() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    override fun onCleared() {
        super.onCleared()
        val uid = _currentProfile.value.uid
        if (uid.isNotBlank()) {
            profileListener?.let { repository.removeDriverProfileListener(uid, it) }
        }
        requestsListener?.let { repository.removeRideRequestListener(it) }
    }
}
