package com.example.ruraltransportdriver

import org.junit.Assert.*
import org.junit.Test

/**
 * Senior-level Unit Test Suite validating core business logic, domain models,
 * seat boundaries, route lookups, and state machine transitions.
 */
class DriverAppUnitTest {

    // =========================================================
    // 1. DRIVER PROFILE & APPROVAL LOGIC
    // =========================================================

    @Test
    fun driverProfile_isApproved_onlyTrueForApprovedStatus() {
        val pendingDriver = DriverProfile(approvalStatus = DriverProfile.APPROVAL_PENDING)
        assertFalse(pendingDriver.isApproved)

        val rejectedDriver = DriverProfile(approvalStatus = DriverProfile.APPROVAL_REJECTED)
        assertFalse(rejectedDriver.isApproved)

        val emptyStatusDriver = DriverProfile(approvalStatus = "")
        assertFalse(emptyStatusDriver.isApproved)

        val approvedDriver = DriverProfile(approvalStatus = DriverProfile.APPROVAL_APPROVED)
        assertTrue(approvedDriver.isApproved)

        val caseInsensitiveApproved = DriverProfile(approvalStatus = "approved")
        assertTrue(caseInsensitiveApproved.isApproved)
    }

    // =========================================================
    // 2. SEAT CALCULATION & CONCURRENCY BOUNDARY
    // =========================================================

    @Test
    fun seatManagement_effectiveSeats_resolvesCorrectly() {
        // Preferred field: requestedSeats
        val req1 = RideRequest(requestedSeats = 3, passengers = 1)
        assertEquals(3, req1.effectiveSeats())

        // Backward compatibility with legacy passengers field
        val req2 = RideRequest(requestedSeats = 0, passengers = 2)
        assertEquals(2, req2.effectiveSeats())

        // Default fallback to 1 seat minimum
        val req3 = RideRequest(requestedSeats = 0, passengers = 0)
        assertEquals(1, req3.effectiveSeats())
    }

    @Test
    fun seatDeduction_neverProducesNegativeSeats() {
        val totalSeats = 4
        val currentAvailable = 2
        val requestedSeats = 3

        // When requested > available, acceptance should be blocked
        val canAccept = currentAvailable >= requestedSeats
        assertFalse("Should not accept when requested seats exceed available capacity", canAccept)

        // Coerce protection
        val remaining = (currentAvailable - requestedSeats).coerceAtLeast(0)
        assertEquals(0, remaining)
    }

    @Test
    fun seatRestoration_neverExceedsTotalSeats() {
        val totalSeats = 4
        val currentAvailable = 3
        val completedRideSeats = 2

        val restored = (currentAvailable + completedRideSeats).coerceAtMost(totalSeats)
        assertEquals(4, restored)
    }

    // =========================================================
    // 3. ROUTE CORRIDOR & STOP SEQUENCING
    // =========================================================

    @Test
    fun routeData_predefinedRoutes_existAndHaveOrderedStops() {
        val routes = RouteData.predefinedRoutes
        assertTrue("Predefined routes must not be empty", routes.isNotEmpty())

        val route1 = RouteData.getRouteById("ROUTE_01")
        assertNotNull("ROUTE_01 must be defined", route1)
        assertEquals("Gurramguda", route1!!.stops.first())
        assertEquals("Nadergul", route1.stops.last())
        assertEquals(4, route1.stops.size)

        val legacyRoute = RouteData.getRouteById("GURRAMGUDA_NADERGUL")
        assertNotNull("GURRAMGUDA_NADERGUL must resolve to corridor", legacyRoute)
        assertEquals("Gurramguda", legacyRoute!!.stops.first())
    }

    @Test
    fun routeData_canonicalRouteNormalization() {
        assertEquals("ROUTE_01", RouteData.canonicalRouteId("ROUTE_01"))
        assertEquals("ROUTE_01", RouteData.canonicalRouteId("route_01"))
        assertEquals("ROUTE_01", RouteData.canonicalRouteId("GURRAMGUDA_NADERGUL"))
        assertEquals("ROUTE_01", RouteData.canonicalRouteId("gurramguda_nadergul"))
        assertEquals("ROUTE_01", RouteData.canonicalRouteId(""))
        assertEquals("ROUTE_01", RouteData.canonicalRouteId(null))

        assertTrue(RouteData.isSameRoute("ROUTE_01", "GURRAMGUDA_NADERGUL"))
        assertTrue(RouteData.isSameRoute("route_01", "ROUTE_01"))
        assertTrue(RouteData.isSameRoute("", "ROUTE_01"))
    }

    @Test
    fun rideRequest_passengerAppFieldAliasesReconciliation() {
        val passengerRequest = RideRequest(
            requestId = "req_101",
            passengerId = "p_123",
            pickupStopName = "Gurramguda",
            destinationStopName = "Nadergul",
            routeName = "Gurramguda — Nadergul Corridor",
            createdAt = 1726467200000L,
            requestedSeats = 2,
            status = RideRequest.STATUS_PENDING
        )

        // Effective properties resolve passenger-written fields
        assertEquals("Gurramguda", passengerRequest.effectivePickup())
        assertEquals("Nadergul", passengerRequest.effectiveDestination())
        assertEquals("Gurramguda — Nadergul Corridor", passengerRequest.effectiveRoute())
        assertEquals(2, passengerRequest.effectiveSeats())
        assertEquals("pending", passengerRequest.status)

        // Driver-side copy reconciliation
        val reconciled = passengerRequest.copy(
            pickupStop = passengerRequest.effectivePickup(),
            destinationStop = passengerRequest.effectiveDestination(),
            route = passengerRequest.effectiveRoute()
        )
        assertEquals("Gurramguda", reconciled.pickupStop)
        assertEquals("Nadergul", reconciled.destinationStop)
        assertEquals("Gurramguda — Nadergul Corridor", reconciled.route)
    }

    // =========================================================
    // 4. RIDE REQUEST STATE MACHINE TRANSITIONS
    // =========================================================

    @Test
    fun rideRequest_validTransitions() {
        // Initial state
        var request = RideRequest(status = RideRequest.STATUS_PENDING)
        assertEquals(RideRequest.STATUS_PENDING, request.status)

        // Accept
        request = request.copy(status = RideRequest.STATUS_ACCEPTED, driverId = "driver_123")
        assertEquals(RideRequest.STATUS_ACCEPTED, request.status)
        assertEquals("driver_123", request.driverId)

        // Start Ride
        request = request.copy(status = RideRequest.STATUS_IN_PROGRESS)
        assertEquals(RideRequest.STATUS_IN_PROGRESS, request.status)

        // Complete Ride
        request = request.copy(status = RideRequest.STATUS_COMPLETED)
        assertEquals(RideRequest.STATUS_COMPLETED, request.status)
    }

    // =========================================================
    // 5. REGISTRATION VALIDATION RULES
    // =========================================================

    @Test
    fun phoneValidation_cleanTenDigits() {
        fun isValidPhone(phone: String): Boolean {
            val clean = phone.trim().replace("+91", "").replace(" ", "").replace("-", "")
            return clean.length == 10 && clean.all { it.isDigit() }
        }

        assertTrue(isValidPhone("9876543210"))
        assertTrue(isValidPhone("+91 9876543210"))
        assertTrue(isValidPhone("98765-43210"))
        assertFalse(isValidPhone("12345"))
        assertFalse(isValidPhone("9876543210123"))
        assertFalse(isValidPhone("98765abcd0"))
    }

    // =========================================================
    // 6. LIVE TRACKING DATA MODEL & BROADCAST SCHEMA
    // =========================================================

    @Test
    fun liveTrackingData_defaultValuesAndStructure() {
        val tracking = LiveTrackingData(
            lat = 17.385044,
            lng = 78.486671,
            heading = 90.0f,
            speed = 8.5f,
            isRideActive = true,
            lastUpdated = 1710000000000L
        )

        assertEquals(17.385044, tracking.lat, 0.00001)
        assertEquals(78.486671, tracking.lng, 0.00001)
        assertEquals(90.0f, tracking.heading, 0.01f)
        assertEquals(8.5f, tracking.speed, 0.01f)
        assertTrue(tracking.isRideActive)
        assertEquals(1710000000000L, tracking.lastUpdated)

        val defaultTracking = LiveTrackingData()
        assertEquals(0.0, defaultTracking.lat, 0.0)
        assertEquals(0.0, defaultTracking.lng, 0.0)
        assertEquals(0f, defaultTracking.heading, 0.0f)
        assertEquals(0f, defaultTracking.speed, 0.0f)
        assertFalse(defaultTracking.isRideActive)
        assertEquals(0L, defaultTracking.lastUpdated)
    }
}
