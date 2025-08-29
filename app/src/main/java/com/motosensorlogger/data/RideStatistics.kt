package com.motosensorlogger.data

import kotlin.math.*

/**
 * Core ride statistics calculated from sensor data
 */
data class RideStatistics(
    val duration: Long, // milliseconds
    val distance: Double, // meters
    val averageSpeed: Double, // m/s
    val maxSpeed: Double, // m/s
    val maxLeanAngle: Double, // degrees
    val maxAcceleration: Double, // m/s²
    val maxDeceleration: Double, // m/s²
    val maxLateralG: Double, // g-force
    val elevationGain: Double, // meters
    val elevationLoss: Double, // meters
    val startTime: Long,
    val endTime: Long,
    val startLocation: GpsLocation?,
    val endLocation: GpsLocation?,
    // Data counts for info dialog
    val totalDataPoints: Int = 0,
    val imuSamples: Int = 0,
    val gpsSamples: Int = 0,
    val magnetometerSamples: Int = 0,
    val barometerSamples: Int = 0
) {
    fun getAverageSpeedKmh() = averageSpeed * 3.6
    fun getMaxSpeedKmh() = maxSpeed * 3.6
    fun getDistanceKm() = distance / 1000.0
    val rideDuration: Long
        get() = duration
    
    fun getDurationFormatted(): String {
        val hours = duration / 3600000
        val minutes = (duration % 3600000) / 60000
        val seconds = (duration % 60000) / 1000
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${seconds}s"
            minutes > 0 -> "${minutes}m ${seconds}s"
            else -> "${seconds}s"
        }
    }
}

/**
 * Location data point
 */
data class GpsLocation(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracy: Float
)

/**
 * Derived metrics from sensor analysis
 */
data class DerivedMetrics(
    val leanAngleStats: AngleStatistics,
    val accelerationStats: AccelerationStatistics,
    val elevationProfile: ElevationProfile,
    val speedProfile: SpeedProfile,
    val cornersCount: Int,
    val hardBrakingCount: Int,
    val hardAccelerationCount: Int,
    val smoothnessScore: Double, // 0-100, higher is smoother
    // Time series data for detailed analysis
    val leanAngle: List<LeanAngleSample>,
    val gForce: List<GForceSample>,
    val acceleration: List<AccelerationSample>,
    val velocity: List<VelocitySample>,
    val orientation: List<OrientationSample>
)

data class AngleStatistics(
    val maxLeft: Double,
    val maxRight: Double,
    val averageCorneringAngle: Double,
    val timeAtAngle: Map<Double, Long> // angle ranges to time spent
)

data class AccelerationStatistics(
    val maxForward: Double,
    val maxBraking: Double,
    val maxLateral: Double,
    val averageAcceleration: Double,
    val jerkEvents: List<JerkEvent>
)

data class JerkEvent(
    val timestamp: Long,
    val magnitude: Double,
    val type: JerkType
)

enum class JerkType {
    HARD_BRAKING, HARD_ACCELERATION, AGGRESSIVE_TURN
}

data class ElevationProfile(
    val minAltitude: Double,
    val maxAltitude: Double,
    val totalGain: Double,
    val totalLoss: Double,
    val averageGradient: Double,
    val steepestClimb: Double,
    val steepestDescent: Double
)

data class SpeedProfile(
    val averageSpeed: Double,
    val maxSpeed: Double,
    val timeAtSpeed: Map<Double, Long>, // speed ranges to time spent
    val accelerationPhases: List<AccelerationPhase>
)

data class AccelerationPhase(
    val startTime: Long,
    val endTime: Long,
    val startSpeed: Double,
    val endSpeed: Double,
    val peakAcceleration: Double
)

/**
 * Detected special events during the ride
 */
data class DetectedEvent(
    val timestamp: Long,
    val type: EventType,
    val magnitude: Double,
    val duration: Long = 0,
    val description: String
) {
    enum class EventType {
        WHEELIE, STOPPIE, JUMP, AGGRESSIVE_CORNERING, HARD_BRAKING, 
        RAPID_ACCELERATION, HIGH_LEAN_ANGLE, SPEED_WOBBLE
    }
}

/**
 * Complete processing result containing all analysis data
 */
data class ProcessingResult(
    val statistics: RideStatistics,
    val derivedMetrics: DerivedMetrics,
    val detectedEvents: List<DetectedEvent>,
    val processingTimeMs: Long,
    val dataQuality: DataQuality
)

data class DataQuality(
    val gpsAccuracy: GpsQualityLevel,
    val imuDataQuality: Double, // 0-1
    val dataCompleteness: Double, // 0-1, percentage of expected data points
    val calibrationStatus: CalibrationStatus
)

enum class GpsQualityLevel {
    POOR, MODERATE, GOOD, EXCELLENT
}

enum class CalibrationStatus {
    NOT_CALIBRATED, CALIBRATED, PARTIAL_CALIBRATION
}

/**
 * Error information from data processing
 */
data class ProcessingError(
    val timestamp: Long,
    val errorType: ErrorType,
    val message: String,
    val severity: Severity
) {
    enum class ErrorType {
        CORRUPTED_DATA, MISSING_CALIBRATION, PROCESSING_TIMEOUT, UNKNOWN_FORMAT
    }
    
    enum class Severity {
        WARNING, ERROR, CRITICAL
    }
}

/**
 * File information metadata
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
 * Export data classes for JSON serialization
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

/**
 * Ride segment data classes
 */
data class RideSegment(
    val startTime: Long,
    val endTime: Long,
    val duration: Long,
    val type: SegmentType,
    val statistics: SegmentStatistics
) {
    enum class SegmentType {
        ACTIVE_RIDING, PAUSE, STOP
    }
}

data class SegmentStatistics(
    val distance: Double,
    val avgSpeed: Double,
    val maxSpeed: Double
)


/**
 * Sample data classes for derived metrics
 */
data class LeanAngleSample(
    val timestamp: Long,
    val rollAngle: Float,
    val pitchAngle: Float,
    val confidence: Float
)

data class GForceSample(
    val timestamp: Long,
    val lateral: Float,
    val longitudinal: Float,
    val vertical: Float,
    val total: Float
)

data class AccelerationSample(
    val timestamp: Long,
    val forward: Float,
    val lateral: Float,
    val vertical: Float
)

data class VelocitySample(
    val timestamp: Long,
    val speed: Float,
    val acceleration: Float
)

data class OrientationSample(
    val timestamp: Long,
    val roll: Float,
    val pitch: Float,
    val yaw: Float
)