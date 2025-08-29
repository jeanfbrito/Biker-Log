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
    val endLocation: GpsLocation?
) {
    fun getAverageSpeedKmh() = averageSpeed * 3.6
    fun getMaxSpeedKmh() = maxSpeed * 3.6
    fun getDistanceKm() = distance / 1000.0
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
    val smoothnessScore: Double // 0-100, higher is smoother
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