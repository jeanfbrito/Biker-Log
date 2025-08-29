package com.motosensorlogger.data

import kotlin.math.*

/**
 * Comprehensive ride statistics generator
 * Calculates detailed ride metrics and detects special events
 */
class RideStatisticsGenerator {

    companion object {
        // Event detection thresholds
        private const val HARD_ACCEL_THRESHOLD = 4.0f // m/s²
        private const val HARD_BRAKE_THRESHOLD = -6.0f // m/s²
        private const val SHARP_TURN_THRESHOLD = 45.0f // degrees lean angle
        private const val HIGH_G_THRESHOLD = 1.5f // g-force
        private const val WHEELIE_PITCH_THRESHOLD = 30.0f // degrees
        private const val MIN_EVENT_DURATION = 500L // 0.5 seconds minimum
    }

    /**
     * Generate comprehensive ride statistics
     */
    fun generate(
        sensorData: Map<SensorType, List<SensorEvent>>,
        derivedMetrics: DerivedMetrics,
        segments: List<RideSegment>
    ): RideStatistics {

        val gpsEvents = (sensorData[SensorType.GPS] as? List<GpsEvent>) ?: emptyList()
        
        // Calculate basic statistics
        val totalDistance = calculateTotalDistance(segments)
        val totalDuration = calculateTotalDuration(segments)
        val ridingDuration = calculateRidingDuration(segments)
        
        // Speed statistics
        val speeds = gpsEvents.filter { it.accuracy < 20f }.map { it.speed }
        val maxSpeed = speeds.maxOrNull() ?: 0f
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average().toFloat() else 0f

        // Lean angle statistics  
        val leanAngles = derivedMetrics.leanAngle.map { abs(it.rollAngle) }
        val maxLeanAngle = leanAngles.maxOrNull() ?: 0f

        // G-force statistics
        val maxGForce = derivedMetrics.gForce.maxOfOrNull { it.total } ?: 0f

        // Elevation statistics
        val (elevationGain, elevationLoss) = calculateElevationStats(gpsEvents)

        // Detect special events
        val specialEvents = detectSpecialEvents(sensorData, derivedMetrics)

        val gpsStart = gpsEvents.firstOrNull()
        val gpsEnd = gpsEvents.lastOrNull()
        
        return RideStatistics(
            duration = totalDuration,
            distance = totalDistance,
            averageSpeed = avgSpeed.toDouble(),
            maxSpeed = maxSpeed.toDouble(),
            maxLeanAngle = maxLeanAngle.toDouble(),
            maxAcceleration = derivedMetrics.accelerationStats.maxForward,
            maxDeceleration = derivedMetrics.accelerationStats.maxBraking,
            maxLateralG = maxGForce.toDouble(),
            elevationGain = elevationGain.toDouble(),
            elevationLoss = elevationLoss.toDouble(),
            startTime = gpsStart?.timestamp ?: 0L,
            endTime = gpsEnd?.timestamp ?: 0L,
            startLocation = gpsStart?.let { GpsLocation(it.latitude, it.longitude, it.altitude, it.accuracy) },
            endLocation = gpsEnd?.let { GpsLocation(it.latitude, it.longitude, it.altitude, it.accuracy) }
        )
    }

    /**
     * Calculate total distance across all segments
     */
    private fun calculateTotalDistance(segments: List<RideSegment>): Double {
        return segments
            .filter { it.type == RideSegment.SegmentType.ACTIVE_RIDING }
            .sumOf { it.statistics.distance }
    }

    /**
     * Calculate total duration including pauses
     */
    private fun calculateTotalDuration(segments: List<RideSegment>): Long {
        if (segments.isEmpty()) return 0L
        
        val startTime = segments.minOfOrNull { it.startTime } ?: 0L
        val endTime = segments.maxOfOrNull { it.endTime } ?: 0L
        
        return endTime - startTime
    }

    /**
     * Calculate actual riding duration excluding pauses
     */
    private fun calculateRidingDuration(segments: List<RideSegment>): Long {
        return segments
            .filter { it.type == RideSegment.SegmentType.ACTIVE_RIDING }
            .sumOf { it.duration }
    }

    /**
     * Calculate elevation gain and loss
     */
    private fun calculateElevationStats(gpsEvents: List<GpsEvent>): Pair<Float, Float> {
        val accurateAltitudes = gpsEvents
            .filter { it.accuracy < 15f } // Use high accuracy GPS only
            .map { it.altitude.toFloat() }
            .let { altitudes ->
                if (altitudes.size < 3) return Pair(0f, 0f)
                // Apply smoothing filter to reduce GPS noise
                smoothAltitudes(altitudes)
            }

        var elevationGain = 0f
        var elevationLoss = 0f

        for (i in 1 until accurateAltitudes.size) {
            val elevationChange = accurateAltitudes[i] - accurateAltitudes[i - 1]
            if (elevationChange > 1f) { // Minimum 1m change to avoid noise
                elevationGain += elevationChange
            } else if (elevationChange < -1f) {
                elevationLoss += abs(elevationChange)
            }
        }

        return Pair(elevationGain, elevationLoss)
    }

    /**
     * Smooth altitude data to reduce GPS noise
     */
    private fun smoothAltitudes(altitudes: List<Float>): List<Float> {
        if (altitudes.size < 3) return altitudes

        val smoothed = mutableListOf<Float>()
        
        // Use simple moving average with window of 3
        smoothed.add(altitudes[0])
        
        for (i in 1 until altitudes.size - 1) {
            val avg = (altitudes[i - 1] + altitudes[i] + altitudes[i + 1]) / 3f
            smoothed.add(avg)
        }
        
        smoothed.add(altitudes.last())
        
        return smoothed
    }

    /**
     * Detect special events during the ride
     */
    fun detectSpecialEvents(
        sensorData: Map<SensorType, List<SensorEvent>>,
        derivedMetrics: DerivedMetrics
    ): List<DetectedEvent> {
        
        val events = mutableListOf<DetectedEvent>()
        
        // Detect hard acceleration/braking events
        events.addAll(detectAccelerationEvents(derivedMetrics.velocity))
        
        // Detect sharp turns
        events.addAll(detectSharpTurns(derivedMetrics.leanAngle))
        
        // Detect high g-force events
        events.addAll(detectHighGForceEvents(derivedMetrics.gForce))
        
        // Detect potential wheelies
        events.addAll(detectWheelies(derivedMetrics.leanAngle))
        
        // Sort events by timestamp
        return events.sortedBy { it.timestamp }
    }

    /**
     * Detect hard acceleration and braking events
     */
    private fun detectAccelerationEvents(velocitySamples: List<VelocitySample>): List<DetectedEvent> {
        if (velocitySamples.size < 2) return emptyList()
        
        val events = mutableListOf<DetectedEvent>()
        var eventStart: VelocitySample? = null
        var maxAccel = 0f
        
        for (i in 1 until velocitySamples.size) {
            val sample = velocitySamples[i]
            val accel = sample.acceleration
            
            // Check for hard acceleration
            if (accel > HARD_ACCEL_THRESHOLD) {
                if (eventStart == null) {
                    eventStart = sample
                    maxAccel = accel
                } else {
                    maxAccel = maxOf(maxAccel, accel)
                }
            }
            // Check for hard braking  
            else if (accel < HARD_BRAKE_THRESHOLD) {
                if (eventStart == null) {
                    eventStart = sample
                    maxAccel = abs(accel)
                } else {
                    maxAccel = maxOf(maxAccel, abs(accel))
                }
            }
            // End of event
            else if (eventStart != null) {
                val duration = sample.timestamp - eventStart.timestamp
                if (duration >= MIN_EVENT_DURATION) {
                    val eventType = if (eventStart.acceleration > 0) {
                        DetectedEvent.EventType.RAPID_ACCELERATION
                    } else {
                        DetectedEvent.EventType.HARD_BRAKING
                    }
                    
                    events.add(DetectedEvent(
                        timestamp = eventStart.timestamp,
                        type = eventType,
                        magnitude = maxAccel.toDouble(),
                        duration = duration,
                        description = "${eventType.name.lowercase().replace('_', ' ')} (${String.format("%.1f", maxAccel)} m/s²)"
                    ))
                }
                eventStart = null
                maxAccel = 0f
            }
        }
        
        return events
    }

    /**
     * Detect sharp turning events
     */
    private fun detectSharpTurns(leanAngleSamples: List<LeanAngleSample>): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        var eventStart: LeanAngleSample? = null
        var maxLeanAngle = 0f
        
        for (sample in leanAngleSamples) {
            val leanAngle = abs(sample.rollAngle)
            
            if (leanAngle > SHARP_TURN_THRESHOLD && sample.confidence > 0.7f) {
                if (eventStart == null) {
                    eventStart = sample
                    maxLeanAngle = leanAngle
                } else {
                    maxLeanAngle = maxOf(maxLeanAngle, leanAngle)
                }
            } else if (eventStart != null) {
                val duration = sample.timestamp - eventStart.timestamp
                if (duration >= MIN_EVENT_DURATION) {
                    events.add(DetectedEvent(
                        timestamp = eventStart.timestamp,
                        type = DetectedEvent.EventType.AGGRESSIVE_CORNERING,
                        magnitude = maxLeanAngle.toDouble(),
                        duration = duration,
                        description = "Sharp turn (${String.format("%.1f", maxLeanAngle)}° lean)"
                    ))
                }
                eventStart = null
                maxLeanAngle = 0f
            }
        }
        
        return events
    }

    /**
     * Detect high g-force events
     */
    private fun detectHighGForceEvents(gForceSamples: List<GForceSample>): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        var eventStart: GForceSample? = null
        var maxGForce = 0f
        
        for (sample in gForceSamples) {
            val totalGForce = sample.total
            
            if (totalGForce > HIGH_G_THRESHOLD) {
                if (eventStart == null) {
                    eventStart = sample
                    maxGForce = totalGForce
                } else {
                    maxGForce = maxOf(maxGForce, totalGForce)
                }
            } else if (eventStart != null) {
                val duration = sample.timestamp - eventStart.timestamp
                if (duration >= MIN_EVENT_DURATION) {
                    events.add(DetectedEvent(
                        timestamp = eventStart.timestamp,
                        type = DetectedEvent.EventType.AGGRESSIVE_CORNERING,
                        magnitude = maxGForce.toDouble(),
                        duration = duration,
                        description = "High g-force event (${String.format("%.1f", maxGForce)}g)"
                    ))
                }
                eventStart = null
                maxGForce = 0f
            }
        }
        
        return events
    }

    /**
     * Detect potential wheelie events
     */
    private fun detectWheelies(leanAngleSamples: List<LeanAngleSample>): List<DetectedEvent> {
        val events = mutableListOf<DetectedEvent>()
        var eventStart: LeanAngleSample? = null
        var maxPitchAngle = 0f
        
        for (sample in leanAngleSamples) {
            val pitchAngle = sample.pitchAngle
            
            // Positive pitch angle indicates front wheel up (wheelie)
            if (pitchAngle > WHEELIE_PITCH_THRESHOLD && sample.confidence > 0.6f) {
                if (eventStart == null) {
                    eventStart = sample
                    maxPitchAngle = pitchAngle
                } else {
                    maxPitchAngle = maxOf(maxPitchAngle, pitchAngle)
                }
            } else if (eventStart != null) {
                val duration = sample.timestamp - eventStart.timestamp
                if (duration >= MIN_EVENT_DURATION * 2) { // Wheelies need longer duration
                    events.add(DetectedEvent(
                        timestamp = eventStart.timestamp,
                        type = DetectedEvent.EventType.WHEELIE,
                        magnitude = maxPitchAngle.toDouble(),
                        duration = duration,
                        description = "Possible wheelie (${String.format("%.1f", maxPitchAngle)}° pitch)"
                    ))
                }
                eventStart = null
                maxPitchAngle = 0f
            }
        }
        
        return events
    }
}