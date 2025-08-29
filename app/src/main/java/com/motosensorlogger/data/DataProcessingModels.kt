package com.motosensorlogger.data

// Using basic JSON serialization without external libraries

/**
 * Data models for the data processing pipeline
 * Contains all derived metrics, segments, and statistics
 */

// ========================= DERIVED METRICS =========================

/**
 * Container for all derived metrics from sensor data processing
 */
data class DerivedMetrics(
    val leanAngle: List<LeanAngleSample>,
    val gForce: List<GForceSample>,
    val acceleration: List<AccelerationSample>,
    val velocity: List<VelocitySample>,
    val orientation: List<OrientationSample>
)

/**
 * Lean angle sample with confidence
 */
data class LeanAngleSample(
    val timestamp: Long,
    val rollAngle: Float,
    val pitchAngle: Float,
    val confidence: Float
)

/**
 * G-force sample with directional components
 */
data class GForceSample(
    val timestamp: Long,
    val longitudinal: Float, // Forward/backward
    val lateral: Float,      // Left/right
    val vertical: Float,     // Up/down
    val total: Float         // Total magnitude
)

/**
 * Acceleration sample in world coordinates
 */
data class AccelerationSample(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val z: Float,
    val magnitude: Float
)

/**
 * Velocity sample from GPS and IMU fusion
 */
data class VelocitySample(
    val timestamp: Long,
    val speed: Float,
    val bearing: Float,
    val acceleration: Float,
    val source: VelocitySource
) {
    enum class VelocitySource {
        GPS_ONLY,
        IMU_ONLY,
        GPS_IMU_FUSION
    }
}

/**
 * Orientation sample with quaternion and Euler angles
 */
data class OrientationSample(
    val timestamp: Long,
    val roll: Float,
    val pitch: Float,
    val yaw: Float,
    val quaternion: FloatArray
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as OrientationSample

        if (timestamp != other.timestamp) return false
        if (roll != other.roll) return false
        if (pitch != other.pitch) return false
        if (yaw != other.yaw) return false
        if (!quaternion.contentEquals(other.quaternion)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestamp.hashCode()
        result = 31 * result + roll.hashCode()
        result = 31 * result + pitch.hashCode()
        result = 31 * result + yaw.hashCode()
        result = 31 * result + quaternion.contentHashCode()
        return result
    }
}

// ========================= RIDE SEGMENTS =========================

/**
 * Represents a segment of the ride with timing and statistics
 */
data class RideSegment(
    val id: Int,
    val startTime: Long,
    val endTime: Long,
    val type: SegmentType,
    val statistics: SegmentStatistics
) {
    val duration: Long get() = endTime - startTime

    enum class SegmentType {
        ACTIVE_RIDING,
        STATIONARY,
        PAUSE,
        UNKNOWN
    }
}

/**
 * Detailed statistics for a ride segment
 */
data class SegmentStatistics(
    val distance: Double,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val maxLeanAngle: Float,
    val maxGForce: Float,
    val elevationChange: Float,
    val sampleCount: Map<SensorType, Int>
)

// ========================= RIDE STATISTICS =========================

/**
 * Overall ride statistics and summary
 */
data class RideStatistics(
    val totalDistance: Double,
    val totalDuration: Long,
    val ridingDuration: Long,
    val maxSpeed: Float,
    val avgSpeed: Float,
    val maxLeanAngle: Float,
    val maxGForce: Float,
    val totalElevationGain: Float,
    val totalElevationLoss: Float,
    val segmentCount: Int,
    val specialEvents: List<DetectedEvent>
)

/**
 * Special event detected during the ride
 */
data class DetectedEvent(
    val timestamp: Long,
    val type: EventType,
    val confidence: Float,
    val duration: Long,
    val maxValue: Float,
    val description: String
) {
    enum class EventType {
        HARD_ACCELERATION,
        HARD_BRAKING,
        SHARP_TURN,
        AGGRESSIVE_MANEUVER,
        WHEELIE_DETECTED,
        HIGH_SPEED,
        TECHNICAL_SECTION
    }
}

// ========================= PROCESSING RESULTS =========================

/**
 * Complete results from data processing pipeline
 */
data class ProcessingResult(
    val fileInfo: FileInfo,
    val processingTime: Long,
    val sampleCounts: Map<SensorType, Int>,
    val derivedMetrics: DerivedMetrics,
    val segments: List<RideSegment>,
    val statistics: RideStatistics,
    val errors: List<ProcessingError>
) {
    fun toJson(): String {
        // Simple JSON serialization for basic export needs
        return buildString {
            append("{\n")
            append("  \"file_info\": {\n")
            append("    \"fileName\": \"${fileInfo.fileName}\",\n")
            append("    \"fileSizeBytes\": ${fileInfo.fileSizeBytes},\n")
            append("    \"isCalibrated\": ${fileInfo.isCalibrated}\n")
            append("  },\n")
            append("  \"processing_time\": $processingTime,\n")
            append("  \"sample_counts\": {\n")
            sampleCounts.entries.joinToString(",\n") { (type, count) ->
                "    \"${type.code}\": $count"
            }.let { append(it) }
            append("\n  },\n")
            append("  \"statistics\": {\n")
            append("    \"totalDistance\": ${statistics.totalDistance},\n")
            append("    \"maxSpeed\": ${statistics.maxSpeed},\n")
            append("    \"maxLeanAngle\": ${statistics.maxLeanAngle},\n")
            append("    \"specialEventsCount\": ${statistics.specialEvents.size}\n")
            append("  }\n")
            append("}")
        }
    }
}

/**
 * File information for processed data
 */
data class FileInfo(
    val fileName: String,
    val fileSizeBytes: Long,
    val recordingStartTime: Long,
    val recordingEndTime: Long,
    val isCalibrated: Boolean,
    val calibrationQuality: String?
)

/**
 * Processing error information
 */
data class ProcessingError(
    val timestamp: Long,
    val errorType: ErrorType,
    val message: String,
    val severity: Severity
) {
    enum class ErrorType {
        CORRUPTED_DATA,
        MISSING_CALIBRATION,
        INVALID_GPS,
        SENSOR_DROPOUT,
        PROCESSING_TIMEOUT,
        UNKNOWN_FORMAT
    }
    
    enum class Severity {
        WARNING,
        ERROR,
        CRITICAL
    }
}

// ========================= EXPORT FORMATS =========================

/**
 * Simplified export format for external analysis
 */
data class ExportData(
    val rideInfo: RideInfo,
    val summaryStats: SummaryStats,
    val timeSeries: TimeSeriesData,
    val events: List<EventSummary>
)

data class RideInfo(
    val fileName: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val distance: Double,
    val isCalibrated: Boolean
)

data class SummaryStats(
    val maxSpeed: Float,
    val avgSpeed: Float,
    val maxLeanAngle: Float,
    val maxGForce: Float,
    val elevationGain: Float,
    val elevationLoss: Float
)

data class TimeSeriesData(
    val gps: List<GpsPoint>,
    val leanAngles: List<AnglePoint>,
    val speeds: List<SpeedPoint>
)

data class GpsPoint(
    val timestamp: Long,
    val lat: Double,
    val lon: Double,
    val alt: Double,
    val speed: Float
)

data class AnglePoint(
    val timestamp: Long,
    val roll: Float,
    val pitch: Float
)

data class SpeedPoint(
    val timestamp: Long,
    val speed: Float,
    val acceleration: Float
)

data class EventSummary(
    val timestamp: Long,
    val type: String,
    val description: String,
    val confidence: Float
)