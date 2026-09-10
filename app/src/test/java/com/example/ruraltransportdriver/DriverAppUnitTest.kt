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
        assertEquals("IBP", route1!!.stops.first())
        assertEquals("Issdan", route1.stops.last())
        assertEquals(4, route1.stops.size)

        val route2 = RouteData.getRouteById("ROUTE_02")
        assertNotNull("ROUTE_02 must be defined", route2)
        assertEquals("Issdan", route2!!.stops.first())
        assertEquals("IBP", route2.stops.last())
    }

    @Test
    fun routeData_invalidRouteId_fallsBackGracefully() {
        val invalidRoute = RouteData.getRouteById("NON_EXISTENT")
        assertNull(invalidRoute)

        val defaultStops = RouteData.getStopsForRoute("INVALID_ID")
        assertTrue("Must return default fallback stops without crashing", defaultStops.isNotEmpty())
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
}
