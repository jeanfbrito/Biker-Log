package com.motosensorlogger.data

import kotlinx.coroutines.yield
import kotlin.math.*

/**
 * Intelligent ride segment detector
 * Identifies start, stop, pause, and riding segments from sensor data
 */
class RideSegmentDetector {

    companion object {
        // Thresholds for segment detection
        private const val MIN_SPEED_THRESHOLD = 1.0f // m/s (3.6 km/h)
        private const val MAX_STATIONARY_ACCEL = 1.5f // m/s² above gravity
        private const val MIN_SEGMENT_DURATION = 10_000L // 10 seconds minimum
        private const val PAUSE_DETECTION_WINDOW = 30_000L // 30 seconds
        private const val GPS_STALE_THRESHOLD = 60_000L // 60 seconds
    }

    /**
     * Detect ride segments from sensor data and derived metrics
     */
    suspend fun detectSegments(
        sensorData: Map<SensorType, List<SensorEvent>>,
        derivedMetrics: DerivedMetrics,
        progressCallback: ((Float) -> Unit)? = null
    ): List<RideSegment> {

        val gpsEvents = (sensorData[SensorType.GPS] as? List<GpsEvent>) ?: emptyList()
        val imuEvents = (sensorData[SensorType.IMU] as? List<ImuEvent>) ?: emptyList()
        
        if (gpsEvents.isEmpty() && imuEvents.isEmpty()) {
            return emptyList()
        }

        progressCallback?.invoke(0.1f)

        // Create time-aligned activity indicators
        val activityWindows = createActivityWindows(gpsEvents, imuEvents)
        progressCallback?.invoke(0.4f)
        yield()

        // Detect segment boundaries
        val boundaries = detectSegmentBoundaries(activityWindows)
        progressCallback?.invoke(0.7f)
        yield()

        // Create segments with statistics
        val segments = createSegmentsWithStats(boundaries, sensorData, derivedMetrics)
        progressCallback?.invoke(1.0f)

        return segments.filter { it.duration >= MIN_SEGMENT_DURATION }
    }

    /**
     * Create time-windowed activity indicators
     */
    private suspend fun createActivityWindows(
        gpsEvents: List<GpsEvent>,
        imuEvents: List<ImuEvent>
    ): List<ActivityWindow> {
        
        val allEvents = (gpsEvents + imuEvents).sortedBy { it.timestamp }
        if (allEvents.isEmpty()) return emptyList()

        val windows = mutableListOf<ActivityWindow>()
        val windowSize = 5_000L // 5-second windows
        
        var windowStart = allEvents.first().timestamp
        val endTime = allEvents.last().timestamp

        while (windowStart < endTime) {
            val windowEnd = windowStart + windowSize
            
            // Collect events in this window
            val windowGpsEvents = gpsEvents.filter { 
                it.timestamp >= windowStart && it.timestamp < windowEnd 
            }
            val windowImuEvents = imuEvents.filter { 
                it.timestamp >= windowStart && it.timestamp < windowEnd 
            }

            // Calculate activity indicators
            val activityLevel = calculateActivityLevel(windowGpsEvents, windowImuEvents)
            val isMoving = isMovingInWindow(windowGpsEvents, windowImuEvents)
            val confidence = calculateActivityConfidence(windowGpsEvents, windowImuEvents)

            windows.add(ActivityWindow(
                startTime = windowStart,
                endTime = windowEnd,
                activityLevel = activityLevel,
                isMoving = isMoving,
                confidence = confidence,
                gpsCount = windowGpsEvents.size,
                imuCount = windowImuEvents.size
            ))

            windowStart = windowEnd
            
            if (windows.size % 100 == 0) yield()
        }

        return windows
    }

    /**
     * Detect segment boundaries from activity windows
     */
    private fun detectSegmentBoundaries(windows: List<ActivityWindow>): List<SegmentBoundary> {
        if (windows.isEmpty()) return emptyList()

        val boundaries = mutableListOf<SegmentBoundary>()
        var currentSegmentType: RideSegment.SegmentType? = null
        var segmentStartTime = windows.first().startTime

        for (i in windows.indices) {
            val window = windows[i]
            val segmentType = determineSegmentType(window, windows, i)

            if (segmentType != currentSegmentType) {
                // Segment type changed - create boundary
                if (currentSegmentType != null) {
                    boundaries.add(SegmentBoundary(
                        timestamp = window.startTime,
                        fromType = currentSegmentType,
                        toType = segmentType,
                        confidence = window.confidence
                    ))
                }
                
                currentSegmentType = segmentType
                segmentStartTime = window.startTime
            }
        }

        // Add final boundary
        if (currentSegmentType != null) {
            boundaries.add(SegmentBoundary(
                timestamp = windows.last().endTime,
                fromType = currentSegmentType,
                toType = RideSegment.SegmentType.STOP,
                confidence = 1.0f
            ))
        }

        return boundaries
    }

    /**
     * Create segments with detailed statistics
     */
    private suspend fun createSegmentsWithStats(
        boundaries: List<SegmentBoundary>,
        sensorData: Map<SensorType, List<SensorEvent>>,
        derivedMetrics: DerivedMetrics
    ): List<RideSegment> {
        
        if (boundaries.size < 2) return emptyList()

        val segments = mutableListOf<RideSegment>()
        
        for (i in 0 until boundaries.size - 1) {
            val startBoundary = boundaries[i]
            val endBoundary = boundaries[i + 1]
            
            val segmentStats = calculateSegmentStatistics(
                startTime = startBoundary.timestamp,
                endTime = endBoundary.timestamp,
                sensorData = sensorData,
                derivedMetrics = derivedMetrics
            )

            segments.add(RideSegment(
                startTime = startBoundary.timestamp,
                endTime = endBoundary.timestamp,
                duration = endBoundary.timestamp - startBoundary.timestamp,
                type = startBoundary.toType,
                statistics = segmentStats
            ))

            if (i % 10 == 0) yield()
        }

        return segments
    }

    /**
     * Calculate activity level for a time window
     */
    private fun calculateActivityLevel(
        gpsEvents: List<GpsEvent>,
        imuEvents: List<ImuEvent>
    ): Float {
        var activityScore = 0f

        // GPS-based activity
        if (gpsEvents.isNotEmpty()) {
            val avgSpeed = gpsEvents.map { it.speed }.average().toFloat()
            val maxSpeed = gpsEvents.maxOfOrNull { it.speed } ?: 0f
            activityScore += (avgSpeed + maxSpeed) / 2f
        }

        // IMU-based activity  
        if (imuEvents.isNotEmpty()) {
            val accelVariance = calculateAccelVariance(imuEvents)
            val gyroVariance = calculateGyroVariance(imuEvents)
            activityScore += (accelVariance + gyroVariance) * 10f
        }

        return minOf(100f, activityScore)
    }

    /**
     * Determine if vehicle is moving in this window
     */
    private fun isMovingInWindow(
        gpsEvents: List<GpsEvent>,
        imuEvents: List<ImuEvent>
    ): Boolean {
        // Primary: GPS speed check
        val hasGpsMovement = gpsEvents.any { 
            it.speed > MIN_SPEED_THRESHOLD && it.accuracy < 20f 
        }

        // Secondary: IMU acceleration check
        val hasImuMovement = imuEvents.any { event ->
            val totalAccel = sqrt(event.accelX * event.accelX + 
                                event.accelY * event.accelY + 
                                event.accelZ * event.accelZ)
            abs(totalAccel - 9.81f) > MAX_STATIONARY_ACCEL
        }

        return hasGpsMovement || hasImuMovement
    }

    /**
     * Calculate confidence in activity detection
     */
    private fun calculateActivityConfidence(
        gpsEvents: List<GpsEvent>,
        imuEvents: List<ImuEvent>
    ): Float {
        var confidence = 0f
        var factors = 0

        // GPS availability and accuracy
        if (gpsEvents.isNotEmpty()) {
            val avgAccuracy = gpsEvents.map { it.accuracy }.average().toFloat()
            confidence += maxOf(0f, 1f - avgAccuracy / 50f) // Better confidence with higher accuracy
            factors++
        }

        // IMU data availability
        if (imuEvents.isNotEmpty()) {
            confidence += if (imuEvents.size >= 10) 1f else imuEvents.size / 10f
            factors++
        }

        return if (factors > 0) confidence / factors else 0f
    }

    /**
     * Determine segment type for current window with context
     */
    private fun determineSegmentType(
        currentWindow: ActivityWindow,
        allWindows: List<ActivityWindow>,
        currentIndex: Int
    ): RideSegment.SegmentType {
        
        // Look at surrounding windows for context
        val contextStart = maxOf(0, currentIndex - 2)
        val contextEnd = minOf(allWindows.size - 1, currentIndex + 2)
        val contextWindows = allWindows.subList(contextStart, contextEnd + 1)
        
        val movingCount = contextWindows.count { it.isMoving }
        val totalCount = contextWindows.size
        val movingRatio = movingCount.toFloat() / totalCount

        return when {
            movingRatio > 0.6f && currentWindow.activityLevel > 10f -> RideSegment.SegmentType.ACTIVE_RIDING
            movingRatio < 0.2f && currentWindow.activityLevel < 5f -> RideSegment.SegmentType.STOP
            movingRatio < 0.4f -> RideSegment.SegmentType.PAUSE
            else -> RideSegment.SegmentType.STOP
        }
    }

    /**
     * Calculate detailed statistics for a segment
     */
    private fun calculateSegmentStatistics(
        startTime: Long,
        endTime: Long,
        sensorData: Map<SensorType, List<SensorEvent>>,
        derivedMetrics: DerivedMetrics
    ): SegmentStatistics {
        
        // Filter data for this segment
        val segmentGps = (sensorData[SensorType.GPS] as? List<GpsEvent>)
            ?.filter { it.timestamp in startTime..endTime } ?: emptyList()
        
        val segmentLeanAngles = derivedMetrics.leanAngle
            .filter { it.timestamp in startTime..endTime }
        
        val segmentGForces = derivedMetrics.gForce
            .filter { it.timestamp in startTime..endTime }

        // Calculate distance
        val distance = calculateDistance(segmentGps)
        
        // Calculate speed statistics
        val speeds = segmentGps.map { it.speed }
        val maxSpeed = speeds.maxOrNull() ?: 0f
        val avgSpeed = if (speeds.isNotEmpty()) speeds.average().toFloat() else 0f

        // Calculate lean angle statistics
        val leanAngles = segmentLeanAngles.map { abs(it.rollAngle) }
        val maxLeanAngle = leanAngles.maxOrNull() ?: 0f

        // Calculate g-force statistics
        val maxGForce = segmentGForces.maxOfOrNull { it.total } ?: 0f

        // Calculate elevation change
        val elevationChange = calculateElevationChange(segmentGps)

        // Count samples per sensor
        val sampleCount = mutableMapOf<SensorType, Int>()
        sensorData.forEach { (sensorType, events) ->
            sampleCount[sensorType] = events.count { 
                it.timestamp in startTime..endTime 
            }
        }

        return SegmentStatistics(
            distance = distance,
            avgSpeed = avgSpeed.toDouble(),
            maxSpeed = maxSpeed.toDouble()
        )
    }

    /**
     * Calculate distance from GPS coordinates
     */
    private fun calculateDistance(gpsEvents: List<GpsEvent>): Double {
        if (gpsEvents.size < 2) return 0.0

        var totalDistance = 0.0
        
        for (i in 1 until gpsEvents.size) {
            val prev = gpsEvents[i - 1]
            val curr = gpsEvents[i]
            
            // Only use accurate GPS points
            if (prev.accuracy < 20f && curr.accuracy < 20f) {
                totalDistance += haversineDistance(
                    prev.latitude, prev.longitude,
                    curr.latitude, curr.longitude
                )
            }
        }

        return totalDistance
    }

    /**
     * Calculate elevation change from GPS altitude data
     */
    private fun calculateElevationChange(gpsEvents: List<GpsEvent>): Float {
        if (gpsEvents.size < 2) return 0f

        val altitudes = gpsEvents
            .filter { it.accuracy < 20f } // Use accurate readings only
            .map { it.altitude.toFloat() }

        return if (altitudes.isNotEmpty()) {
            altitudes.maxOrNull()!! - altitudes.minOrNull()!!
        } else 0f
    }

    /**
     * Calculate accelerometer variance for activity detection
     */
    private fun calculateAccelVariance(imuEvents: List<ImuEvent>): Float {
        if (imuEvents.isEmpty()) return 0f

        val magnitudes = imuEvents.map { event ->
            sqrt(event.accelX * event.accelX + 
                 event.accelY * event.accelY + 
                 event.accelZ * event.accelZ)
        }

        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        
        return sqrt(variance)
    }

    /**
     * Calculate gyroscope variance for activity detection
     */
    private fun calculateGyroVariance(imuEvents: List<ImuEvent>): Float {
        if (imuEvents.isEmpty()) return 0f

        val magnitudes = imuEvents.map { event ->
            sqrt(event.gyroX * event.gyroX + 
                 event.gyroY * event.gyroY + 
                 event.gyroZ * event.gyroZ)
        }

        val mean = magnitudes.average().toFloat()
        val variance = magnitudes.map { (it - mean) * (it - mean) }.average().toFloat()
        
        return sqrt(variance)
    }

    /**
     * Calculate distance between two GPS coordinates using Haversine formula
     */
    private fun haversineDistance(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val R = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        
        val a = sin(dLat / 2) * sin(dLat / 2) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2) * sin(dLon / 2)
                
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        
        return R * c
    }

    /**
     * Activity window for segment detection
     */
    private data class ActivityWindow(
        val startTime: Long,
        val endTime: Long,
        val activityLevel: Float,
        val isMoving: Boolean,
        val confidence: Float,
        val gpsCount: Int,
        val imuCount: Int
    )

    /**
     * Segment boundary marker
     */
    private data class SegmentBoundary(
        val timestamp: Long,
        val fromType: RideSegment.SegmentType,
        val toType: RideSegment.SegmentType,
        val confidence: Float
    )
}