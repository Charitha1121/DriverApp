package com.example.ruraltransportdriver

import android.location.Location
import com.example.ruraltransportdriver.voice.SpeechNumberParser
import com.example.ruraltransportdriver.voice.VehicleStopDetector
import org.junit.Assert.*
import org.junit.Test

class VoiceSeatUpdateUnitTest {

    // =========================================================
    // 1. SPEECH NUMBER PARSER TESTS
    // =========================================================

    @Test
    fun parseDirectDigits_extractsAndClamps() {
        assertEquals(0, SpeechNumberParser.parseSingle("0", maxSeats = 4))
        assertEquals(1, SpeechNumberParser.parseSingle("1", maxSeats = 4))
        assertEquals(2, SpeechNumberParser.parseSingle("2", maxSeats = 4))
        assertEquals(3, SpeechNumberParser.parseSingle("3", maxSeats = 4))
        assertEquals(4, SpeechNumberParser.parseSingle("4", maxSeats = 4))
        // Clamping above maxSeats
        assertEquals(4, SpeechNumberParser.parseSingle("5", maxSeats = 4))
        assertEquals(4, SpeechNumberParser.parseSingle("10", maxSeats = 4))
    }

    @Test
    fun parseWordNumbers_extractsCorrectly() {
        assertEquals(0, SpeechNumberParser.parseSingle("zero", maxSeats = 6))
        assertEquals(1, SpeechNumberParser.parseSingle("one", maxSeats = 6))
        assertEquals(2, SpeechNumberParser.parseSingle("two", maxSeats = 6))
        assertEquals(3, SpeechNumberParser.parseSingle("three", maxSeats = 6))
        assertEquals(4, SpeechNumberParser.parseSingle("four", maxSeats = 6))
        assertEquals(5, SpeechNumberParser.parseSingle("five", maxSeats = 6))
        assertEquals(6, SpeechNumberParser.parseSingle("six", maxSeats = 6))
    }

    @Test
    fun parseColloquialAndPhrases() {
        assertEquals(0, SpeechNumberParser.parseSingle("no seats", maxSeats = 4))
        assertEquals(0, SpeechNumberParser.parseSingle("full", maxSeats = 4))
        assertEquals(0, SpeechNumberParser.parseSingle("none available", maxSeats = 4))
        assertEquals(0, SpeechNumberParser.parseSingle("empty", maxSeats = 4))

        assertEquals(2, SpeechNumberParser.parseSingle("two seats", maxSeats = 4))
        assertEquals(3, SpeechNumberParser.parseSingle("we have 3 seats available", maxSeats = 4))
        assertEquals(1, SpeechNumberParser.parseSingle("just a single seat", maxSeats = 4))
        assertEquals(2, SpeechNumberParser.parseSingle("a couple of seats", maxSeats = 4))
    }

    @Test
    fun parseCandidateList_picksFirstValidCandidate() {
        val candidates = listOf("I am stopped", "two seats", "2")
        val result = SpeechNumberParser.parse(candidates, maxSeats = 4)
        assertEquals(2, result)
    }

    @Test
    fun parseInvalidSpeech_returnsNull() {
        assertNull(SpeechNumberParser.parseSingle("hello there", maxSeats = 4))
        assertNull(SpeechNumberParser.parseSingle("", maxSeats = 4))
        assertNull(SpeechNumberParser.parseSingle(null, maxSeats = 4))
        assertNull(SpeechNumberParser.parse(emptyList(), maxSeats = 4))
    }

    // =========================================================
    // 2. VEHICLE STOP DETECTOR TESTS
    // =========================================================

    private fun createMockLocation(speedMps: Float, timeMs: Long): Location {
        // Simple mock-like location without Android platform dependencies
        val loc = Location("gps")
        loc.speed = speedMps
        loc.time = timeMs
        return loc
    }

    @Test
    fun stopDetector_sustainedStopTriggersCallback_onlyWhenRideActive() {
        var currentTime = 1000L
        val detector = VehicleStopDetector(
            stopThresholdSpeedMps = 1.0f,
            stopDurationMillis = 60_000L,
            movingThresholdSpeedMps = 2.0f,
            movingDurationMillis = 5_000L,
            timeProvider = { currentTime }
        )

        var stopTriggerCount = 0

        // 1. Vehicle is moving at 10 m/s
        detector.onLocationUpdate(createMockLocation(10f, currentTime), isRideActive = true) {
            stopTriggerCount++
        }
        assertEquals(VehicleStopDetector.State.IDLE, detector.currentState)
        assertEquals(0, stopTriggerCount)

        // 2. Vehicle stops (speed = 0.2 m/s) -> enters STOP_CANDIDATE
        currentTime += 1000L
        detector.onLocationUpdate(createMockLocation(0.2f, currentTime), isRideActive = true) {
            stopTriggerCount++
        }
        assertEquals(VehicleStopDetector.State.STOP_CANDIDATE, detector.currentState)
        assertEquals(0, stopTriggerCount)

        // 3. 30 seconds pass at stop (< 60s) -> should NOT trigger yet
        currentTime += 30_000L
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = true) {
            stopTriggerCount++
        }
        assertEquals(VehicleStopDetector.State.STOP_CANDIDATE, detector.currentState)
        assertEquals(0, stopTriggerCount)

        // 4. 60 seconds total elapsed -> triggers once!
        currentTime += 30_000L
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = true) {
            stopTriggerCount++
        }
        assertEquals(VehicleStopDetector.State.STOP_TRIGGERED, detector.currentState)
        assertEquals(1, stopTriggerCount)

        // 5. Debounce test: still stopped after another 20 seconds -> MUST NOT fire again!
        currentTime += 20_000L
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = true) {
            stopTriggerCount++
        }
        assertEquals(VehicleStopDetector.State.STOP_TRIGGERED, detector.currentState)
        assertEquals(1, stopTriggerCount) // Still 1!
    }

    @Test
    fun stopDetector_transientStopResetsCandidate() {
        var currentTime = 1000L
        val detector = VehicleStopDetector(
            stopThresholdSpeedMps = 1.0f,
            stopDurationMillis = 60_000L,
            timeProvider = { currentTime }
        )

        var triggerCount = 0

        // Stopped for 20s
        detector.onLocationUpdate(createMockLocation(0.1f, currentTime), isRideActive = true) { triggerCount++ }
        currentTime += 20_000L
        detector.onLocationUpdate(createMockLocation(0.1f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(VehicleStopDetector.State.STOP_CANDIDATE, detector.currentState)

        // Moves away before 60s (e.g. traffic light changes)
        currentTime += 1000L
        detector.onLocationUpdate(createMockLocation(5.0f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(VehicleStopDetector.State.IDLE, detector.currentState)
        assertEquals(0, triggerCount)
    }

    @Test
    fun stopDetector_reArmsAfterSustainedMovement() {
        var currentTime = 1000L
        val detector = VehicleStopDetector(
            stopThresholdSpeedMps = 1.0f,
            stopDurationMillis = 60_000L,
            movingThresholdSpeedMps = 2.0f,
            movingDurationMillis = 5_000L,
            timeProvider = { currentTime }
        )

        var triggerCount = 0

        // First stop
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = true) { triggerCount++ }
        currentTime += 60_000L
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(1, triggerCount)
        assertEquals(VehicleStopDetector.State.STOP_TRIGGERED, detector.currentState)

        // Starts moving: 3 m/s for 2 seconds (not yet 5s)
        currentTime += 2_000L
        detector.onLocationUpdate(createMockLocation(3.0f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(VehicleStopDetector.State.MOVING_CANDIDATE, detector.currentState)

        // Sustained movement passes 5 seconds -> re-armed to IDLE
        currentTime += 4_000L
        detector.onLocationUpdate(createMockLocation(4.0f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(VehicleStopDetector.State.IDLE, detector.currentState)

        // Next stop arrives!
        currentTime += 10_000L
        detector.onLocationUpdate(createMockLocation(0.1f, currentTime), isRideActive = true) { triggerCount++ }
        currentTime += 60_000L
        detector.onLocationUpdate(createMockLocation(0.1f, currentTime), isRideActive = true) { triggerCount++ }
        assertEquals(2, triggerCount) // Triggered at the second stop!
    }

    @Test
    fun stopDetector_doesNotTriggerIfRideNotActive() {
        var currentTime = 1000L
        val detector = VehicleStopDetector(
            stopThresholdSpeedMps = 1.0f,
            stopDurationMillis = 60_000L,
            timeProvider = { currentTime }
        )

        var triggerCount = 0

        // Stopped for 65s while at the depot (isRideActive = false)
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = false) { triggerCount++ }
        currentTime += 65_000L
        detector.onLocationUpdate(createMockLocation(0.0f, currentTime), isRideActive = false) { triggerCount++ }

        // Must NOT fire callback
        assertEquals(0, triggerCount)
    }
}
