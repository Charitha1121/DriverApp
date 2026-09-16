package com.example.ruraltransportdriver

import android.location.Location
import androidx.lifecycle.ViewModel
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.*

class DashboardViewModel(
    private val repository: FirebaseRepository = FirebaseRepository()
) : ViewModel() {

    private val _currentProfile = MutableStateFlow(DriverProfile())
    val currentProfile: StateFlow<DriverProfile> = _currentProfile.asStateFlow()

    private val _activeDirection = MutableStateFlow(RouteDirection.FORWARD)
    val activeDirection: StateFlow<RouteDirection> = _activeDirection.asStateFlow()

    private val _passengersWaitingAhead = MutableStateFlow(0)
    val passengersWaitingAhead: StateFlow<Int> = _passengersWaitingAhead.asStateFlow()

    private val _demandAheadBreakdown = MutableStateFlow<Map<String, Int>>(emptyMap())
    val demandAheadBreakdown: StateFlow<Map<String, Int>> = _demandAheadBreakdown.asStateFlow()

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

    // Demand subscription listener state
    private var demandListener: ValueEventListener? = null
    private var subscribedRouteId: String? = null
    private var subscribedDirection: RouteDirection? = null
    private var rawDemandMap: Map<String, Int> = emptyMap()

    // Throttled liveLocation write timestamps & coordinates
    private var lastLiveLocationWriteTimestamp: Long = 0L
    private var lastLiveLocationLat: Double = 0.0
    private var lastLiveLocationLng: Double = 0.0

    // Throttled liveTracking write timestamps, coordinates & raw location (Rapido/Ola style)
    private var lastLiveTrackingWriteTimestamp: Long = 0L
    private var lastLiveTrackingLat: Double = 0.0
    private var lastLiveTrackingLng: Double = 0.0
    private var lastRawLocation: Location? = null

    // Prevents repeated Firebase updates for the same automatically detected stop.
    private var lastAutomaticallyDetectedStop: String? = null

    // ---------------------------------------------------------
    // INITIALIZATION
    // ---------------------------------------------------------

    fun initialize(profile: DriverProfile) {
        _currentProfile.value = profile
        _activeDirection.value = profile.activeDirection
        observeProfile(profile.uid)
        observeAllRequests(profile)

        // Sync the detector with the driver's existing Firebase stop.
        lastAutomaticallyDetectedStop =
            profile.currentStop.takeIf { it.isNotBlank() }

        if (profile.isAvailable) {
            subscribeToDemand(profile.routeId, profile.activeDirection)
        }

        // Arm Firebase onDisconnect to reset isRideActive = false if driver loses connection
        if (profile.uid.isNotBlank()) {
            repository.setupLiveTrackingOnDisconnect(profile.uid)
        }
    }

    private fun observeProfile(uid: String) {
        profileListener?.let {
            repository.removeDriverProfileListener(uid, it)
        }

        profileListener = repository.observeDriverProfile(
            uid = uid,
            onProfileChanged = { updatedProfile ->
                val oldAvailability = _currentProfile.value.isAvailable
                val oldDirection = _currentProfile.value.activeDirection
                val oldRouteId = _currentProfile.value.routeId
                val oldStop = _currentProfile.value.currentStop

                _currentProfile.value = updatedProfile
                _activeDirection.value = updatedProfile.activeDirection

                if (updatedProfile.isAvailable) {
                    if (!oldAvailability || oldDirection != updatedProfile.activeDirection || !RouteData.isSameRoute(oldRouteId, updatedProfile.routeId)) {
                        subscribeToDemand(updatedProfile.routeId, updatedProfile.activeDirection)
                    } else if (oldStop != updatedProfile.currentStop) {
                        recalculateDemandAhead()
                    }
                } else if (oldAvailability) {
                    unsubscribeDemand()
                }
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    private fun observeAllRequests(profile: DriverProfile) {
        requestsListener?.let {
            repository.removeRideRequestListener(it)
        }

        requestsListener = repository.observeRideRequests(
            onRequestsChanged = { allRequests ->

                val active = allRequests.firstOrNull { req ->
                    req.driverId == profile.uid && (
                        req.status.equals(RideRequest.STATUS_ACCEPTED, ignoreCase = true) ||
                        req.status.equals(RideRequest.STATUS_IN_PROGRESS, ignoreCase = true)
                    )
                }

                _activeRide.value = active

                val pending = allRequests.filter { req ->
                    req.status.equals(RideRequest.STATUS_PENDING, ignoreCase = true) &&
                            req.driverId.isBlank() &&
                            !dismissedRequestIds.contains(req.requestId) &&
                            (
                                    req.routeId.isBlank() ||
                                            RouteData.isSameRoute(req.routeId, profile.routeId) ||
                                            req.route.contains("Gurramguda", ignoreCase = true) ||
                                            req.route.contains("Nadergul", ignoreCase = true) ||
                                            req.routeName.contains("Gurramguda", ignoreCase = true) ||
                                            req.routeName.contains("Nadergul", ignoreCase = true)
                            )
                }

                _pendingRequests.value = pending
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // DRIVER AVAILABILITY & DIRECTION
    // ---------------------------------------------------------

    fun toggleAvailability(isOnline: Boolean, direction: RouteDirection? = null) {
        val driver = _currentProfile.value

        if (!driver.isApproved) {
            _errorMessage.value =
                "Cannot go online: Driver account is not approved."
            return
        }

        val chosenDirection = direction ?: _activeDirection.value

        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateDriverAvailability(
            uid = driver.uid,
            isAvailable = isOnline,
            activeDirection = chosenDirection,
            onSuccess = {
                _isOperating.value = false
                _activeDirection.value = chosenDirection
                _currentProfile.value = _currentProfile.value.copy(
                    isAvailable = isOnline,
                    activeDirection = chosenDirection
                )

                if (isOnline) {
                    _statusMessage.value = "🟢 You are now ONLINE (${RouteData.getDirectionTitle(driver.routeId, chosenDirection)})"
                    subscribeToDemand(driver.routeId, chosenDirection)
                    publishLiveLocationNow()
                } else {
                    _statusMessage.value = "🔴 You are now OFFLINE"
                    unsubscribeDemand()
                    repository.setLiveLocationOffline(driver.uid)
                    repository.setLiveTrackingRideActive(driver.uid, false)
                }
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    /**
     * Reverses or switches direction mid-route.
     * Re-subscribes demand listeners cleanly and updates live position.
     */
    fun switchDirection(newDirection: RouteDirection) {
        val driver = _currentProfile.value
        _activeDirection.value = newDirection
        _currentProfile.value = _currentProfile.value.copy(activeDirection = newDirection)

        if (driver.uid.isNotBlank()) {
            repository.updateDriverDirection(driver.uid, newDirection)
        }

        if (driver.isAvailable) {
            subscribeToDemand(driver.routeId, newDirection)
            publishLiveLocationNow()
            _statusMessage.value = "Switched direction to ${RouteData.getDirectionTitle(driver.routeId, newDirection)}"
        }
    }

    // ---------------------------------------------------------
    // DIRECTION-AWARE DEMAND SUBSCRIPTION
    // ---------------------------------------------------------

    private fun subscribeToDemand(routeId: String, direction: RouteDirection) {
        // Clear previous listener if any
        unsubscribeDemand()

        // Normalize routeId to canonical routeId matching passenger app ("ROUTE_01")
        val canonicalRoute = RouteData.canonicalRouteId(routeId)

        subscribedRouteId = canonicalRoute
        subscribedDirection = direction
        demandListener = repository.observeDirectionDemand(
            routeId = canonicalRoute,
            direction = direction,
            onDemandChanged = { demandMap ->
                rawDemandMap = demandMap
                recalculateDemandAhead()
            },
            onError = { error ->
                _errorMessage.value = "Demand sync error: $error"
            }
        )
    }

    private fun unsubscribeDemand() {
        demandListener?.let { listener ->
            val rId = subscribedRouteId ?: RouteData.canonicalRouteId(_currentProfile.value.routeId)
            val dir = subscribedDirection ?: _activeDirection.value
            repository.removeDemandListener(rId, dir, listener)
        }
        demandListener = null
        subscribedRouteId = null
        subscribedDirection = null
        rawDemandMap = emptyMap()
        _passengersWaitingAhead.value = 0
        _demandAheadBreakdown.value = emptyMap()
    }

    private fun recalculateDemandAhead() {
        val profile = _currentProfile.value
        if (!profile.isAvailable) {
            _passengersWaitingAhead.value = 0
            _demandAheadBreakdown.value = emptyMap()
            return
        }

        val direction = _activeDirection.value
        val stopsAhead = RouteData.getStopsAhead(
            routeId = profile.routeId,
            currentStop = profile.currentStop,
            direction = direction
        ).map { it.trim().lowercase() }.toSet()

        // Match demand using normalized stop names
        val filtered = rawDemandMap.filter { (stopName, _) ->
            stopName.trim().lowercase() in stopsAhead
        }
        
        _demandAheadBreakdown.value = filtered
        _passengersWaitingAhead.value = filtered.values.sum()
    }

    // ---------------------------------------------------------
    // MANUAL STOP UPDATE
    // ---------------------------------------------------------

    fun updateCurrentStop(stop: String) {
        val driver = _currentProfile.value

        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateCurrentStop(
            uid = driver.uid,
            stop = stop,
            onSuccess = {
                // Manual selection becomes the current known stop.
                lastAutomaticallyDetectedStop = stop
                _currentProfile.value = _currentProfile.value.copy(currentStop = stop)

                // Recalculate stops ahead & demand
                recalculateDemandAhead()

                // Sync live position node
                publishLiveLocationNow()

                _statusMessage.value =
                    "Stop updated to $stop"
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // AUTOMATIC GPS STOP DETECTION
    // ---------------------------------------------------------

    private fun detectCurrentStop(
        latitude: Double,
        longitude: Double,
        accuracy: Float
    ) {

        // Ignore obviously invalid GPS coordinates.
        if (!latitude.isFinite() || !longitude.isFinite()) {
            return
        }

        if (latitude == 0.0 && longitude == 0.0) {
            return
        }

        // Very inaccurate GPS should not automatically change the stop.
        // This protects against false detections.
        if (accuracy > 100f) {
            return
        }

        val nearestStop = RouteData.stops
            .map { stop ->
                val distance = distanceBetweenMeters(
                    latitude,
                    longitude,
                    stop.latitude,
                    stop.longitude
                )

                stop to distance
            }
            .minByOrNull { it.second }

        nearestStop ?: return

        val stop = nearestStop.first
        val distance = nearestStop.second

        val detectionRadius =
            RouteData.AUTO_DETECTION_RADIUS_METERS.toDouble()

        // Driver is not close enough to any stop.
        if (distance > detectionRadius) {
            return
        }

        // Same stop already detected.
        if (lastAutomaticallyDetectedStop.equals(
                stop.name,
                ignoreCase = true
            )
        ) {
            return
        }

        updateCurrentStopAutomatically(stop.name, distance)
    }

    private fun updateCurrentStopAutomatically(
        stop: String,
        distanceMeters: Double
    ) {

        val driver = _currentProfile.value

        // Mark immediately to prevent multiple GPS callbacks
        // from sending duplicate Firebase updates.
        lastAutomaticallyDetectedStop = stop

        repository.updateCurrentStop(
            uid = driver.uid,
            stop = stop,
            onSuccess = {
                _currentProfile.value = _currentProfile.value.copy(currentStop = stop)

                // Recalculate stops ahead & demand
                recalculateDemandAhead()

                // Sync live position node
                publishLiveLocationNow()

                _statusMessage.value =
                    "📍 Automatically detected: $stop"
            },
            onError = { error ->

                // Allow another attempt if Firebase failed.
                lastAutomaticallyDetectedStop = null

                _errorMessage.value =
                    "Failed to update stop automatically: $error"
            }
        )
    }

    // ---------------------------------------------------------
    // DISTANCE CALCULATION
    // ---------------------------------------------------------

    private fun distanceBetweenMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {

        val earthRadius = 6_371_000.0

        val lat1 = Math.toRadians(latitude1)
        val lat2 = Math.toRadians(latitude2)

        val deltaLat =
            Math.toRadians(latitude2 - latitude1)

        val deltaLon =
            Math.toRadians(longitude2 - longitude1)

        val a =
            sin(deltaLat / 2).pow(2) +
                    cos(lat1) *
                    cos(lat2) *
                    sin(deltaLon / 2).pow(2)

        val c =
            2 * atan2(
                sqrt(a),
                sqrt(1 - a)
            )

        return earthRadius * c
    }

    // ---------------------------------------------------------
    // AVAILABLE SEATS
    // ---------------------------------------------------------

    fun updateAvailableSeats(seats: Int) {
        val driver = _currentProfile.value

        if (seats < 0 || seats > driver.totalSeats) {
            _errorMessage.value =
                "Seat count must be between 0 and ${driver.totalSeats}."
            return
        }

        _statusMessage.value = null
        _errorMessage.value = null

        repository.updateAvailableSeats(
            uid = driver.uid,
            seats = seats,
            onSuccess = {
                _statusMessage.value =
                    "Seats updated to $seats"
            },
            onError = { error ->
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // ACCEPT RIDE
    // ---------------------------------------------------------

    fun acceptRequest(request: RideRequest) {
        val driver = _currentProfile.value

        if (!driver.isAvailable) {
            _errorMessage.value =
                "Please go ONLINE to accept ride requests."
            return
        }

        if (_activeRide.value != null) {
            _errorMessage.value =
                "You already have an active ride in progress."
            return
        }

        val neededSeats = request.effectiveSeats()

        if (driver.availableSeats < neededSeats) {
            _errorMessage.value =
                "Insufficient seats ($neededSeats needed, " +
                        "${driver.availableSeats} available)."
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

                _statusMessage.value =
                    "Ride accepted! Head to ${request.pickupStop}."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // REJECT RIDE
    // ---------------------------------------------------------

    fun rejectRequest(requestId: String) {
        dismissedRequestIds.add(requestId)

        _pendingRequests.value =
            _pendingRequests.value.filter {
                it.requestId != requestId
            }

        _statusMessage.value =
            "Ride request dismissed."
    }

    // ---------------------------------------------------------
    // START RIDE
    // ---------------------------------------------------------

    fun startRide() {
        val ride = _activeRide.value ?: return
        val driver = _currentProfile.value

        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        repository.startRide(
            requestId = ride.requestId,
            onSuccess = {
                _isOperating.value = false

                // Broadcast isRideActive = true to liveTracking immediately
                lastRawLocation?.let { loc ->
                    repository.updateLiveTracking(driver.uid, loc, isRideActive = true)
                } ?: repository.setLiveTrackingRideActive(driver.uid, true)

                _statusMessage.value =
                    "Ride started. En route to " +
                            ride.destinationStop.ifBlank {
                                "destination"
                            } + "."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // COMPLETE RIDE
    // ---------------------------------------------------------

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

                // Broadcast isRideActive = false to liveTracking immediately
                lastRawLocation?.let { loc ->
                    repository.updateLiveTracking(driver.uid, loc, isRideActive = false)
                } ?: repository.setLiveTrackingRideActive(driver.uid, false)

                _statusMessage.value =
                    "Ride completed successfully. Seats restored."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // CANCEL RIDE
    // ---------------------------------------------------------

    fun cancelRide(
        reason: String = "Driver cancellation"
    ) {

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

                // Broadcast isRideActive = false to liveTracking immediately
                lastRawLocation?.let { loc ->
                    repository.updateLiveTracking(driver.uid, loc, isRideActive = false)
                } ?: repository.setLiveTrackingRideActive(driver.uid, false)

                _statusMessage.value =
                    "Ride cancelled. Seats restored."
            },
            onError = { error ->
                _isOperating.value = false
                _errorMessage.value = error
            }
        )
    }

    // ---------------------------------------------------------
    // START GPS LOCATION SHARING
    // ---------------------------------------------------------

    fun startLocationSharing(
        tracker: LocationTracker
    ) {

        val driver = _currentProfile.value

        if (driver.uid.isBlank()) {
            _errorMessage.value =
                "Driver profile is not initialized."
            return
        }

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

                // Update UI state.
                _lastKnownLocation.value = driverLoc

                // Save legacy location.
                repository.updateDriverLocation(driverLoc)

                // Throttled publish to drivers/{uid}/liveLocation for passenger visibility
                val now = System.currentTimeMillis()
                val distanceMoved = distanceBetweenMeters(
                    lastLiveLocationLat,
                    lastLiveLocationLng,
                    loc.latitude,
                    loc.longitude
                )

                // Throttle: write every 8 seconds, or if moved > 15 meters, or first tick
                if (now - lastLiveLocationWriteTimestamp >= 8000L || distanceMoved >= 15.0 || lastLiveLocationWriteTimestamp == 0L) {
                    lastLiveLocationWriteTimestamp = now
                    lastLiveLocationLat = loc.latitude
                    lastLiveLocationLng = loc.longitude

                    val liveLoc = DriverLiveLocation(
                        lat = loc.latitude,
                        lng = loc.longitude,
                        heading = loc.bearing,
                        speed = loc.speed,
                        lastUpdated = repository.currentTime(),
                        isOnline = _currentProfile.value.isAvailable,
                        routeId = _currentProfile.value.routeId,
                        activeDirection = _activeDirection.value.name,
                        currentStop = _currentProfile.value.currentStop
                    )
                    repository.updateLiveLocation(driver.uid, liveLoc)
                }

                // Additive live tracking for passenger app (Rapido/Ola/Uber style)
                // Persisted under `drivers/{uid}/liveTracking`
                lastRawLocation = loc
                val liveTrackingDistanceMoved = distanceBetweenMeters(
                    lastLiveTrackingLat,
                    lastLiveTrackingLng,
                    loc.latitude,
                    loc.longitude
                )

                // Throttle: write every 3-5 seconds (4000ms), or if moved >= 10 meters, or first tick
                if (now - lastLiveTrackingWriteTimestamp >= 4000L || liveTrackingDistanceMoved >= 10.0 || lastLiveTrackingWriteTimestamp == 0L) {
                    lastLiveTrackingWriteTimestamp = now
                    lastLiveTrackingLat = loc.latitude
                    lastLiveTrackingLng = loc.longitude

                    val isRideActive = _activeRide.value?.status == RideRequest.STATUS_IN_PROGRESS
                    repository.updateLiveTracking(driver.uid, loc, isRideActive)
                }

                // Automatically determine current route stop.
                detectCurrentStop(
                    latitude = loc.latitude,
                    longitude = loc.longitude,
                    accuracy = loc.accuracy
                )
            },

            onError = { err ->
                // Only treat fatal errors as session killers.
                // Weak GPS signal shouldn't stop the location sharing UI state.
                if (err.contains("permission", ignoreCase = true) || 
                    err.contains("Security", ignoreCase = true) ||
                    err.contains("Unable to get current", ignoreCase = true)) {
                    _isSharingLocation.value = false
                    _errorMessage.value = err
                } else {
                    // Just show a transient warning for weak signals
                    _statusMessage.value = "⚠️ $err"
                }
            }
        )

        _isSharingLocation.value = true
        _statusMessage.value =
            "GPS Location sharing started."
    }

    // ---------------------------------------------------------
    // STOP GPS LOCATION SHARING
    // ---------------------------------------------------------

    fun stopLocationSharing(
        tracker: LocationTracker
    ) {

        tracker.stopTracking()

        _isSharingLocation.value = false

        // Mark ride tracking inactive on sharing stop
        repository.setLiveTrackingRideActive(_currentProfile.value.uid, false)

        _statusMessage.value =
            "GPS Location sharing stopped."
    }

    // ---------------------------------------------------------
    // PUBLISH LIVE LOCATION IMMEDIATELY
    // ---------------------------------------------------------

    private fun publishLiveLocationNow() {
        val driver = _currentProfile.value
        if (driver.uid.isBlank()) return

        val lastLoc = _lastKnownLocation.value
        
        // If GPS is unavailable, fall back to the coordinates of the current stop 
        // to ensure the driver remains visible to passengers at their last known location.
        val currentStopObj = RouteData.stops.firstOrNull { 
            it.name.equals(driver.currentStop, ignoreCase = true) 
        }
        
        val lat = lastLoc?.latitude ?: currentStopObj?.latitude ?: 0.0
        val lng = lastLoc?.longitude ?: currentStopObj?.longitude ?: 0.0

        val liveLoc = DriverLiveLocation(
            lat = lat,
            lng = lng,
            heading = lastLoc?.bearing ?: 0f,
            speed = lastLoc?.speed ?: 0f,
            lastUpdated = repository.currentTime(),
            isOnline = driver.isAvailable,
            routeId = driver.routeId,
            activeDirection = _activeDirection.value.name,
            currentStop = driver.currentStop
        )
        repository.updateLiveLocation(driver.uid, liveLoc)
    }

    // ---------------------------------------------------------
    // CLEAR FEEDBACK
    // ---------------------------------------------------------

    fun clearFeedback() {
        _statusMessage.value = null
        _errorMessage.value = null
    }

    // ---------------------------------------------------------
    // CLEANUP
    // ---------------------------------------------------------

    override fun onCleared() {
        super.onCleared()

        val uid = _currentProfile.value.uid

        if (uid.isNotBlank()) {
            profileListener?.let {
                repository.removeDriverProfileListener(
                    uid,
                    it
                )
            }
        }

        requestsListener?.let {
            repository.removeRideRequestListener(it)
        }

        unsubscribeDemand()
    }
}