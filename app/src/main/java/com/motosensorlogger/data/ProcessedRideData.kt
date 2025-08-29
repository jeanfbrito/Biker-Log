package com.motosensorlogger.data

import com.motosensorlogger.calibration.CalibrationData
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Processed ride data structures for the data processing pipeline
 * Optimized for performance and memory efficiency
 */

/**
 * Comprehensive ride data containing all processed information
 */
data class ProcessedRideData(
    val metadata: RideMetadata,
    val calibrationData: CalibrationData?,
    val segments: List<RideSegment>,
    val statistics: RideStatistics,
    val derivedMetrics: DerivedMetrics,
    val rawSensorData: Map<SensorType, List<SensorEvent>>,
) {
    fun toJson(): String {
        // JSON export will be implemented as part of the pipeline
        return ""  // Placeholder
    }
}

/**
 * Ride metadata extracted from log files
 */
data class RideMetadata(
    val fileName: String,
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val deviceInfo: String,
    val schemaVersion: String,
    val dataQuality: DataQuality,
) {
    val durationMinutes: Double get() = duration / 60_000.0
    val durationHours: Double get() = duration / 3_600_000.0
}

/**
 * Data quality assessment for ride files
 */
data class DataQuality(
    val overallScore: Float, // 0-100
    val completeness: Float, // Percentage of expected data present
    val consistency: Float, // Data consistency checks
    val sensorCoverage: Map<SensorType, Float>, // Coverage per sensor type
    val issues: List<DataIssue>,
) {
    enum class DataIssue {
        MISSING_GPS,
        MISSING_IMU,
        CALIBRATION_MISSING,
        LARGE_TIME_GAPS,
        CORRUPTED_SAMPLES,
        INSUFFICIENT_DATA
    }
}

/**
 * Individual ride segment (continuous data collection period)
 */
data class RideSegment(
    val id: Int,
    val startTime: Long,
    val endTime: Long,
    val type: SegmentType,
    val statistics: SegmentStatistics,
) {
    enum class SegmentType {
        ACTIVE_RIDING,
        STATIONARY,
        PAUSE,
        UNKNOWN
    }
    
    val duration: Long get() = endTime - startTime
}

/**
 * Statistics for individual segments
 */
data class SegmentStatistics(
    val distance: Double, // meters
    val maxSpeed: Float, // m/s
    val avgSpeed: Float, // m/s
    val maxLeanAngle: Float, // degrees
    val maxGForce: Float, // g
    val elevationChange: Float, // meters
    val sampleCount: Map<SensorType, Int>,
)

/**
 * Overall ride statistics
 */
data class RideStatistics(
    val totalDistance: Double, // meters
    val totalDuration: Long, // milliseconds
    val ridingDuration: Long, // excluding pauses
    val maxSpeed: Float, // m/s
    val avgSpeed: Float, // m/s
    val maxLeanAngle: Float, // degrees
    val maxGForce: Float, // g
    val totalElevationGain: Float, // meters
    val totalElevationLoss: Float, // meters
    val segmentCount: Int,
    val specialEvents: List<DetectedEvent>,
) {
    val avgSpeedKmh: Float get() = avgSpeed * 3.6f
    val maxSpeedKmh: Float get() = maxSpeed * 3.6f
    val totalDistanceKm: Double get() = totalDistance / 1000.0
}

/**
 * Detected special events during the ride
 */
data class DetectedEvent(
    val timestamp: Long,
    val type: EventType,
    val confidence: Float, // 0-1
    val duration: Long,
    val maxValue: Float,
    val description: String,
) {
    enum class EventType {
        HARD_ACCELERATION,
        HARD_BRAKING,
        SHARP_TURN,
        WHEELIE_DETECTED,
        JUMP_DETECTED,
        AGGRESSIVE_MANEUVER,
        ENGINE_BRAKING
    }
}

/**
 * Derived metrics calculated from raw sensor data
 */
data class DerivedMetrics(
    val leanAngle: List<LeanAngleSample>,
    val gForce: List<GForceSample>,
    val acceleration: List<AccelerationSample>,
    val velocity: List<VelocitySample>,
    val orientation: List<OrientationSample>,
) {
    /**
     * Get time-series data for a specific metric
     */
    inline fun <reified T> getTimeSeries(): List<T> {
        return when (T::class) {
            LeanAngleSample::class -> leanAngle as List<T>
            GForceSample::class -> gForce as List<T>
            AccelerationSample::class -> acceleration as List<T>
            VelocitySample::class -> velocity as List<T>
            OrientationSample::class -> orientation as List<T>
            else -> emptyList()
        }
    }
}

/**
 * Lean angle sample with timestamp
 */
data class LeanAngleSample(
    val timestamp: Long,
    val rollAngle: Float, // degrees, positive = right lean
    val pitchAngle: Float, // degrees, positive = front down
    val confidence: Float, // 0-1, based on data quality
)

/**
 * G-force sample
 */
data class GForceSample(
    val timestamp: Long,
    val longitudinal: Float, // g, forward/backward
    val lateral: Float, // g, left/right
    val vertical: Float, // g, up/down
    val total: Float, // g, total magnitude
)

/**
 * Acceleration sample (world coordinates if calibrated)
 */
data class AccelerationSample(
    val timestamp: Long,
    val x: Float, // m/s², world coordinates
    val y: Float, // m/s²
    val z: Float, // m/s²
    val magnitude: Float, // m/s², total magnitude
)

/**
 * Velocity sample derived from GPS and IMU fusion
 */
data class VelocitySample(
    val timestamp: Long,
    val speed: Float, // m/s
    val bearing: Float, // degrees
    val acceleration: Float, // m/s²
    val source: VelocitySource,
) {
    enum class VelocitySource {
        GPS_ONLY,
        IMU_INTEGRATION,
        FUSED
    }
}

/**
 * Orientation sample (device orientation in world coordinates)
 */
data class OrientationSample(
    val timestamp: Long,
    val roll: Float, // degrees
    val pitch: Float, // degrees  
    val yaw: Float, // degrees
    val quaternion: FloatArray, // [w, x, y, z]
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as OrientationSample
        return timestamp == other.timestamp &&
               roll == other.roll &&
               pitch == other.pitch &&
               yaw == other.yaw &&
               quaternion.contentEquals(other.quaternion)
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

/**
 * Processing progress callback interface
 */
interface ProcessingProgressCallback {
    suspend fun onProgress(stage: String, progress: Float)
    suspend fun onStageComplete(stage: String)
    suspend fun onError(stage: String, error: String)
}

/**
 * Processing stages enumeration
 */
enum class ProcessingStage(val displayName: String) {
    PARSING("Parsing CSV file"),
    VALIDATION("Validating data"),
    CALIBRATION("Processing calibration"),
    METRICS("Calculating derived metrics"),
    SEGMENTATION("Detecting ride segments"),
    STATISTICS("Generating statistics"),
    EXPORT("Exporting results")
}