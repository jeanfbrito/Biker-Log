package com.motosensorlogger.calibration

import android.os.Build

/**
 * Simplified calibration data that stores reference orientation
 * Raw sensor data is collected without transformation
 * Calibration is applied during post-processing/analysis
 */
data class CalibrationData(
    // Reference orientation when phone was mounted
    val referenceGravity: FloatArray, // Gravity vector at mount time
    val referenceMagnetic: FloatArray, // Magnetic field at mount time
    val referenceRotationMatrix: FloatArray, // 9-element rotation matrix
    val referenceQuaternion: FloatArray, // Quaternion [w, x, y, z]
    // Reference angles for human readability
    val referencePitch: Float, // Pitch angle in degrees
    val referenceRoll: Float, // Roll angle in degrees
    val referenceAzimuth: Float, // Azimuth/yaw in degrees
    // Sensor biases (if device is stationary during calibration)
    val gyroscopeBias: FloatArray, // Gyroscope offset when still
    // Calibration metadata
    val timestamp: Long, // When calibration was performed
    val duration: Long, // How long calibration took (ms)
    val sampleCount: Int, // Number of samples collected
    val quality: CalibrationQuality, // Quality metrics
    // Device info
    val deviceModel: String = "${Build.MANUFACTURER} ${Build.MODEL}",
    val androidVersion: Int = Build.VERSION.SDK_INT,
) {
    /**
     * Convert to CSV header format
     * Stores minimal reference data needed for post-processing
     */
    fun toCsvHeader(): String {
        return """
            # Calibration: {
            #   "format_version": "2.0",
            #   "timestamp": $timestamp,
            #   "device": "$deviceModel",
            #   "android_sdk": $androidVersion,
            #   "reference": {
            #     "gravity": [${referenceGravity.joinToString(", ") { "%.6f".format(it) }}],
            #     "magnetic": [${referenceMagnetic.joinToString(", ") { "%.6f".format(it) }}],
            #     "quaternion": [${referenceQuaternion.joinToString(", ") { "%.6f".format(it) }}],
            #     "euler_angles": {
            #       "pitch": ${"%.2f".format(referencePitch)},
            #       "roll": ${"%.2f".format(referenceRoll)},
            #       "azimuth": ${"%.2f".format(referenceAzimuth)}
            #     },
            #     "gyro_bias": [${gyroscopeBias.joinToString(", ") { "%.6f".format(it) }}]
            #   },
            #   "quality": {
            #     "score": ${"%.1f".format(quality.overallScore)},
            #     "samples": $sampleCount,
            #     "duration_ms": $duration
            #   },
            #   "note": "Raw sensor data follows. Apply calibration during post-processing."
            # }
            """.trimIndent()
    }

    companion object {
        /**
         * Parse calibration data from CSV header
         * Used when loading existing log files
         */
        fun fromCsvHeader(header: String): CalibrationData? {
            // Simple parser for loading calibration from file
            // Implementation would parse the JSON-like structure
            // For now, return null as placeholder
            return null
        }
    }
}

/**
 * Calibration quality assessment
 */
data class CalibrationQuality(
    val overallScore: Float, // 0-100 overall quality
    val stabilityScore: Float, // Device stability during calibration
    val magneticFieldQuality: Float, // Magnetic field consistency
    val gravityConsistency: Float, // Gravity vector stability
    val isAcceptable: Boolean, // Whether calibration meets minimum standards
) {
    fun getQualityLevel(): QualityLevel {
        return when {
            overallScore >= 90 -> QualityLevel.EXCELLENT
            overallScore >= 75 -> QualityLevel.GOOD
            overallScore >= 60 -> QualityLevel.ACCEPTABLE
            else -> QualityLevel.POOR
        }
    }

    enum class QualityLevel {
        EXCELLENT,
        GOOD,
        ACCEPTABLE,
        POOR,
    }
}
