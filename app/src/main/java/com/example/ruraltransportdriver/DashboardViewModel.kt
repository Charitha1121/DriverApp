package com.example.ruraltransportdriver

import android.content.Context
import android.location.Location
import androidx.lifecycle.ViewModel
import com.example.ruraltransportdriver.voice.RideVoiceForegroundService
import com.example.ruraltransportdriver.voice.VehicleStopDetector
import com.example.ruraltransportdriver.voice.VoiceSeatManager
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

    // Hands-free voice state
    private val _isVoiceActive = MutableStateFlow(false)
    val isVoiceActive: StateFlow<Boolean> = _isVoiceActive.asStateFlow()

    private val _showManualSeatPromptFallback = MutableStateFlow(false)
    val showManualSeatPromptFallback: StateFlow<Boolean> = _showManualSeatPromptFallback.asStateFlow()

    private var voiceSeatManager: VoiceSeatManager? = null
    private val vehicleStopDetector = VehicleStopDetector()
    private var appContext: Context? = null

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
        observeProfile(profile.uid)
        observeAllRequests(profile)

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
                val oldRouteId = _currentProfile.value.routeId

                _currentProfile.value = updatedProfile

                if (updatedProfile.isAvailable) {
                    if (!oldAvailability || !RouteData.isSameRoute(oldRouteId, updatedProfile.routeId)) {
                        subscribeToDemand(updatedProfile.routeId, updatedProfile.activeDirection)
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

        _isOperating.value = true
        _statusMessage.value = null
        _errorMessage.value = null

        val selectedDirection = direction ?: driver.activeDirection

        repository.updateDriverAvailability(
            uid = driver.uid,
            isAvailable = isOnline,
            activeDirection = selectedDirection,
            onSuccess = {
                _isOperating.value = false
                _currentProfile.value = _currentProfile.value.copy(
                    isAvailable = isOnline,
                    activeDirection = selectedDirection
                )

                if (isOnline) {
                    _statusMessage.value = "🟢 You are now ONLINE"
                    subscribeToDemand(driver.routeId, selectedDirection)
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

    fun switchDirection(newDirection: RouteDirection) {}

    // ---------------------------------------------------------
    // DIRECTION-AWARE DEMAND SUBSCRIPTION
    // ---------------------------------------------------------

    private fun subscribeToDemand(routeId: String, direction: RouteDirection) {
        unsubscribeDemand()
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
            val dir = subscribedDirection ?: RouteDirection.FORWARD
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

        _demandAheadBreakdown.value = rawDemandMap
        _passengersWaitingAhead.value = rawDemandMap.values.sum()
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
    ) {}

    private fun updateCurrentStopAutomatically(
        stop: String,
        distanceMeters: Double
    ) {}

    // ---------------------------------------------------------
    // DISTANCE CALCULATION
    // ---------------------------------------------------------

    private fun distanceBetweenMeters(
        latitude1: Double,
        longitude1: Double,
        latitude2: Double,
        longitude2: Double
    ): Double {
        val r = 6371000.0
        val dLat = Math.toRadians(latitude2 - latitude1)
        val dLon = Math.toRadians(longitude2 - longitude1)
        val a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                Math.cos(Math.toRadians(latitude1)) * Math.cos(Math.toRadians(latitude2)) *
                Math.sin(dLon / 2) * Math.sin(dLon / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return r * c
    }

    // ---------------------------------------------------------
    // AVAILABLE SEATS
    // ---------------------------------------------------------

    fun updateAvailableSeats(seats: Int) {}

    fun attachVoiceManager(manager: VoiceSeatManager, context: Context) {
        this.voiceSeatManager = manager
        this.appContext = context.applicationContext
    }

    fun dismissManualSeatPromptFallback() {
        _showManualSeatPromptFallback.value = false
    }

    private fun triggerVoiceSeatPrompt() {}

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

                // Arm hands-free stop detection and start foreground service
                vehicleStopDetector.reset()
                _isVoiceActive.value = true
                _showManualSeatPromptFallback.value = false
                appContext?.let { RideVoiceForegroundService.start(it) }

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

                vehicleStopDetector.reset()
                _isVoiceActive.value = false
                _showManualSeatPromptFallback.value = false
                appContext?.let { RideVoiceForegroundService.stop(it) }

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

                vehicleStopDetector.reset()
                _isVoiceActive.value = false
                _showManualSeatPromptFallback.value = false
                appContext?.let { RideVoiceForegroundService.stop(it) }

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

                // Throttle: write every 3 seconds, or if moved > 5 meters, or first tick
                if (now - lastLiveLocationWriteTimestamp >= 3000L || distanceMoved >= 5.0 || lastLiveLocationWriteTimestamp == 0L) {
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
                        activeDirection = _currentProfile.value.activeDirection.name,
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

                // Throttle: write every 2-3 seconds, or if moved >= 5 meters, or first tick
                if (now - lastLiveTrackingWriteTimestamp >= 2500L || liveTrackingDistanceMoved >= 5.0 || lastLiveTrackingWriteTimestamp == 0L) {
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

                // Hands-free stop detection: triggers only during active ride
                val isRideActive = _activeRide.value?.status == RideRequest.STATUS_IN_PROGRESS
                vehicleStopDetector.onLocationUpdate(
                    location = loc,
                    isRideActive = isRideActive,
                    onStopDetected = { _ ->
                        triggerVoiceSeatPrompt()
                    }
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
        appContext?.let { RideVoiceForegroundService.start(it) }
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
            activeDirection = driver.activeDirection.name,
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

        voiceSeatManager?.destroy()
        voiceSeatManager = null
        appContext?.let { RideVoiceForegroundService.stop(it) }
        appContext = null

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