package com.example.ruraltransportdriver.voice

import android.location.Location

/**
 * Detects sustained vehicle stops using incoming GPS speed and distance updates.
 *
 * Requirements:
 * 1. Low speed threshold: < 1.0 m/s continuously for 60-120 seconds.
 * 2. Debounce: Fires ONCE per stop.
 * 3. Re-arming: Requires the vehicle to achieve a moving speed (>= 2.0 m/s) for a sustained
 *    duration (>= 5 seconds) before another stop trigger can occur at the next stop.
 */
class VehicleStopDetector(
    private val stopThresholdSpeedMps: Float = 1.0f,
    private val stopDurationMillis: Long = 60_000L,
    private val movingThresholdSpeedMps: Float = 2.0f,
    private val movingDurationMillis: Long = 5_000L,
    private val timeProvider: () -> Long = { System.currentTimeMillis() }
) {

    enum class State {
        IDLE,               // Vehicle moving or waiting for stop
        STOP_CANDIDATE,     // Speed < threshold, accumulating stop time
        STOP_TRIGGERED,     // Stop event fired; debounced until vehicle moves again
        MOVING_CANDIDATE    // Speed >= moving threshold, verifying sustained resumption of motion
    }

    var currentState: State = State.IDLE
        private set

    private var stopCandidateStartTime: Long = 0L
    private var movingCandidateStartTime: Long = 0L
    private var previousLocation: Location? = null

    /**
     * Processes a new GPS location update.
     *
     * @param location The latest GPS location
     * @param isRideActive Only trigger callback when ride is actively in progress
     * @param onStopDetected Invoked exactly once per sustained stop when isRideActive is true
     */
    fun onLocationUpdate(
        location: Location,
        isRideActive: Boolean,
        onStopDetected: (Location) -> Unit
    ) {
        val now = timeProvider()
        val speed = resolveSpeed(location)

        when (currentState) {
            State.IDLE -> {
                if (speed < stopThresholdSpeedMps) {
                    currentState = State.STOP_CANDIDATE
                    stopCandidateStartTime = now
                }
            }

            State.STOP_CANDIDATE -> {
                if (speed < stopThresholdSpeedMps) {
                    val elapsedStop = now - stopCandidateStartTime
                    if (elapsedStop >= stopDurationMillis) {
                        currentState = State.STOP_TRIGGERED
                        if (isRideActive) {
                            onStopDetected(location)
                        }
                    }
                } else {
                    // Vehicle sped back up before reaching stop duration (e.g. brief red light)
                    currentState = State.IDLE
                }
            }

            State.STOP_TRIGGERED -> {
                if (speed >= movingThresholdSpeedMps) {
                    currentState = State.MOVING_CANDIDATE
                    movingCandidateStartTime = now
                }
            }

            State.MOVING_CANDIDATE -> {
                if (speed >= movingThresholdSpeedMps) {
                    val elapsedMoving = now - movingCandidateStartTime
                    if (elapsedMoving >= movingDurationMillis) {
                        // Sustained movement verified, re-arm for next stop
                        currentState = State.IDLE
                    }
                } else {
                    // Crept slightly but stopped again; return to debounced triggered state
                    currentState = State.STOP_TRIGGERED
                }
            }
        }

        previousLocation = location
    }

    /**
     * Resolves the speed in m/s using GPS speed if available, or distance / time fallback.
     */
    private fun resolveSpeed(current: Location): Float {
        if (current.hasSpeed() && current.speed >= 0f) {
            return current.speed
        }

        val prev = previousLocation ?: return 0f
        val timeDiffSec = (current.time - prev.time) / 1000.0
        if (timeDiffSec <= 0.0) return 0f

        val distance = prev.distanceTo(current)
        return (distance / timeDiffSec).toFloat()
    }

    /**
     * Resets detector state (e.g. when starting a new ride or clearing session).
     */
    fun reset() {
        currentState = State.IDLE
        stopCandidateStartTime = 0L
        movingCandidateStartTime = 0L
        previousLocation = null
    }
}
