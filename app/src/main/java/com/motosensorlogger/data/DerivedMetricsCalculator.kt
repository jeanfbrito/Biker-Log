package com.motosensorlogger.data

import com.motosensorlogger.calibration.CalibrationData
import kotlinx.coroutines.yield
import kotlin.math.*

/**
 * High-performance derived metrics calculator
 * Calculates lean angle, g-force, acceleration, velocity, and orientation
 * Optimized for processing large datasets efficiently
 */
class DerivedMetricsCalculator {

    companion object {
        private const val GRAVITY_CONSTANT = 9.81f // m/s²
        private const val DEGREES_TO_RADIANS = PI / 180.0
        private const val RADIANS_TO_DEGREES = 180.0 / PI
        
        // Filter constants for noise reduction
        private const val GYRO_NOISE_THRESHOLD = 0.1f // rad/s
        private const val ACCEL_NOISE_THRESHOLD = 0.2f // m/s²
        private const val GPS_MIN_ACCURACY = 20.0f // meters
    }

    /**
     * Calculate all derived metrics from raw sensor data
     */
    suspend fun calculate(
        sensorData: Map<SensorType, List<SensorEvent>>,
        calibrationData: CalibrationData?,
        progressCallback: ((Float) -> Unit)? = null
    ): DerivedMetrics {
        
        val imuEvents = (sensorData[SensorType.IMU] as? List<ImuEvent>) ?: emptyList()
        val gpsEvents = (sensorData[SensorType.GPS] as? List<GpsEvent>) ?: emptyList()
        val magEvents = (sensorData[SensorType.MAG] as? List<MagEvent>) ?: emptyList()

        val totalSteps = 5
        var currentStep = 0

        // Step 1: Calculate lean angles
        progressCallback?.invoke(currentStep++ / totalSteps.toFloat())
        val leanAngleSamples = calculateLeanAngles(imuEvents, calibrationData)
        yield()

        // Step 2: Calculate g-forces
        progressCallback?.invoke(currentStep++ / totalSteps.toFloat())
        val gForceSamples = calculateGForces(imuEvents, calibrationData)
        yield()

        // Step 3: Calculate accelerations (world coordinates)
        progressCallback?.invoke(currentStep++ / totalSteps.toFloat())
        val accelerationSamples = calculateAccelerations(imuEvents, calibrationData)
        yield()

        // Step 4: Calculate velocities (GPS + IMU fusion)
        progressCallback?.invoke(currentStep++ / totalSteps.toFloat())
        val velocitySamples = calculateVelocities(gpsEvents, imuEvents)
        yield()

        // Step 5: Calculate orientations
        progressCallback?.invoke(currentStep++ / totalSteps.toFloat())
        val orientationSamples = calculateOrientations(imuEvents, magEvents, calibrationData)

        return DerivedMetrics(
            leanAngle = leanAngleSamples,
            gForce = gForceSamples,
            acceleration = accelerationSamples,
            velocity = velocitySamples,
            orientation = orientationSamples
        )
    }

    /**
     * Calculate lean angles from IMU data
     * Uses accelerometer data to determine vehicle lean angle
     */
    private suspend fun calculateLeanAngles(
        imuEvents: List<ImuEvent>,
        calibrationData: CalibrationData?
    ): List<LeanAngleSample> {
        if (imuEvents.isEmpty()) return emptyList()

        val samples = mutableListOf<LeanAngleSample>()
        
        // Apply complementary filter for stable angle estimation
        var filteredRoll = 0.0
        var filteredPitch = 0.0
        var lastTimestamp = imuEvents.first().timestamp
        
        val alpha = 0.98 // Complementary filter coefficient
        
        for ((index, event) in imuEvents.withIndex()) {
            if (index % 1000 == 0) yield() // Yield periodically for coroutine cooperation

            val dt = (event.timestamp - lastTimestamp) / 1000.0 // Convert to seconds
            lastTimestamp = event.timestamp

            // Calculate accelerometer-based angles (gravity vector)
            val accelMagnitude = sqrt(event.accelX * event.accelX + 
                                    event.accelY * event.accelY + 
                                    event.accelZ * event.accelZ)

            val accelRoll = if (accelMagnitude > 0) {
                atan2(event.accelY.toDouble(), event.accelZ.toDouble()) * RADIANS_TO_DEGREES
            } else 0.0

            val accelPitch = if (accelMagnitude > 0) {
                atan2(-event.accelX.toDouble(), 
                      sqrt(event.accelY * event.accelY + event.accelZ * event.accelZ).toDouble()) * RADIANS_TO_DEGREES
            } else 0.0

            if (dt > 0 && dt < 0.1) { // Valid time step (< 100ms)
                // Integrate gyroscope data
                val gyroRoll = filteredRoll + event.gyroX * dt * RADIANS_TO_DEGREES
                val gyroPitch = filteredPitch + event.gyroY * dt * RADIANS_TO_DEGREES

                // Complementary filter: combine gyro (high frequency) with accel (low frequency)
                filteredRoll = alpha * gyroRoll + (1 - alpha) * accelRoll
                filteredPitch = alpha * gyroPitch + (1 - alpha) * accelPitch
            } else {
                // Use accelerometer angles for large time gaps
                filteredRoll = accelRoll
                filteredPitch = accelPitch
            }

            // Apply calibration offset if available
            val calibratedRoll = if (calibrationData != null) {
                (filteredRoll - calibrationData.referenceRoll).toFloat()
            } else {
                filteredRoll.toFloat()
            }

            val calibratedPitch = if (calibrationData != null) {
                (filteredPitch - calibrationData.referencePitch).toFloat()
            } else {
                filteredPitch.toFloat()
            }

            // Calculate confidence based on accelerometer stability
            val confidence = calculateAngleConfidence(accelMagnitude, GRAVITY_CONSTANT)

            samples.add(LeanAngleSample(
                timestamp = event.timestamp,
                rollAngle = calibratedRoll,
                pitchAngle = calibratedPitch,
                confidence = confidence
            ))
        }

        return samples
    }

    /**
     * Calculate g-forces from accelerometer data
     */
    private suspend fun calculateGForces(
        imuEvents: List<ImuEvent>,
        calibrationData: CalibrationData?
    ): List<GForceSample> {
        if (imuEvents.isEmpty()) return emptyList()

        val samples = mutableListOf<GForceSample>()

        for ((index, event) in imuEvents.withIndex()) {
            if (index % 1000 == 0) yield()

            // Convert to g-forces (1g = 9.81 m/s²)
            val gx = event.accelX / GRAVITY_CONSTANT
            val gy = event.accelY / GRAVITY_CONSTANT
            val gz = event.accelZ / GRAVITY_CONSTANT

            // If calibrated, transform to world coordinates
            val (worldGx, worldGy, worldGz) = if (calibrationData != null) {
                transformToWorldCoordinates(gx, gy, gz, calibrationData.referenceRotationMatrix)
            } else {
                Triple(gx, gy, gz)
            }

            // Remove gravity component (assuming Z is up in world coordinates)
            val longitudinal = worldGx
            val lateral = worldGy  
            val vertical = worldGz - 1.0f // Remove 1g gravity

            val totalMagnitude = sqrt(longitudinal * longitudinal + 
                                    lateral * lateral + 
                                    vertical * vertical)

            samples.add(GForceSample(
                timestamp = event.timestamp,
                longitudinal = longitudinal,
                lateral = lateral,
                vertical = vertical,
                total = totalMagnitude
            ))
        }

        return samples
    }

    /**
     * Calculate accelerations in world coordinates
     */
    private suspend fun calculateAccelerations(
        imuEvents: List<ImuEvent>,
        calibrationData: CalibrationData?
    ): List<AccelerationSample> {
        if (imuEvents.isEmpty()) return emptyList()

        val samples = mutableListOf<AccelerationSample>()

        for ((index, event) in imuEvents.withIndex()) {
            if (index % 1000 == 0) yield()

            // Transform to world coordinates if calibration available
            val (worldX, worldY, worldZ) = if (calibrationData != null) {
                transformToWorldCoordinates(
                    event.accelX, 
                    event.accelY, 
                    event.accelZ, 
                    calibrationData.referenceRotationMatrix
                )
            } else {
                Triple(event.accelX, event.accelY, event.accelZ)
            }

            val magnitude = sqrt(worldX * worldX + worldY * worldY + worldZ * worldZ)

            samples.add(AccelerationSample(
                timestamp = event.timestamp,
                x = worldX,
                y = worldY,
                z = worldZ,
                magnitude = magnitude
            ))
        }

        return samples
    }

    /**
     * Calculate velocities using GPS and IMU sensor fusion
     */
    private suspend fun calculateVelocities(
        gpsEvents: List<GpsEvent>,
        imuEvents: List<ImuEvent>
    ): List<VelocitySample> {
        val samples = mutableListOf<VelocitySample>()

        // Primary source: GPS velocity data
        for ((index, gpsEvent) in gpsEvents.withIndex()) {
            if (index % 100 == 0) yield()

            // Only use high-accuracy GPS data
            if (gpsEvent.accuracy <= GPS_MIN_ACCURACY) {
                samples.add(VelocitySample(
                    timestamp = gpsEvent.timestamp,
                    speed = gpsEvent.speed,
                    bearing = gpsEvent.bearing,
                    acceleration = 0f, // Will be calculated from speed changes
                    source = VelocitySample.VelocitySource.GPS_ONLY
                ))
            }
        }

        // Calculate acceleration from speed changes
        for (i in 1 until samples.size) {
            val current = samples[i]
            val previous = samples[i - 1]
            val dt = (current.timestamp - previous.timestamp) / 1000.0f

            if (dt > 0 && dt < 10.0f) { // Valid time interval
                val acceleration = (current.speed - previous.speed) / dt
                samples[i] = current.copy(acceleration = acceleration)
            }
        }

        return samples.sortedBy { it.timestamp }
    }

    /**
     * Calculate device orientations using IMU and magnetometer
     */
    private suspend fun calculateOrientations(
        imuEvents: List<ImuEvent>,
        magEvents: List<MagEvent>,
        calibrationData: CalibrationData?
    ): List<OrientationSample> {
        if (imuEvents.isEmpty()) return emptyList()

        val samples = mutableListOf<OrientationSample>()
        
        // Initialize orientation
        var quaternion = floatArrayOf(1.0f, 0.0f, 0.0f, 0.0f) // [w, x, y, z]
        var lastTimestamp = imuEvents.first().timestamp

        for ((index, event) in imuEvents.withIndex()) {
            if (index % 1000 == 0) yield()

            val dt = (event.timestamp - lastTimestamp) / 1000.0f
            lastTimestamp = event.timestamp

            if (dt > 0 && dt < 0.1f) {
                // Integrate gyroscope data to update quaternion
                quaternion = integrateGyroscope(quaternion, event.gyroX, event.gyroY, event.gyroZ, dt)
            }

            // Convert quaternion to Euler angles
            val (roll, pitch, yaw) = quaternionToEuler(quaternion)

            samples.add(OrientationSample(
                timestamp = event.timestamp,
                roll = roll,
                pitch = pitch,
                yaw = yaw,
                quaternion = quaternion.copyOf()
            ))
        }

        return samples
    }

    /**
     * Transform coordinates from device to world reference frame
     */
    private fun transformToWorldCoordinates(
        x: Float, 
        y: Float, 
        z: Float, 
        rotationMatrix: FloatArray
    ): Triple<Float, Float, Float> {
        if (rotationMatrix.size != 9) return Triple(x, y, z)

        // Apply rotation matrix transformation
        val worldX = rotationMatrix[0] * x + rotationMatrix[1] * y + rotationMatrix[2] * z
        val worldY = rotationMatrix[3] * x + rotationMatrix[4] * y + rotationMatrix[5] * z
        val worldZ = rotationMatrix[6] * x + rotationMatrix[7] * y + rotationMatrix[8] * z

        return Triple(worldX, worldY, worldZ)
    }

    /**
     * Calculate confidence for angle measurements based on accelerometer stability
     */
    private fun calculateAngleConfidence(accelMagnitude: Float, expectedGravity: Float): Float {
        val deviation = abs(accelMagnitude - expectedGravity) / expectedGravity
        return maxOf(0f, minOf(1f, 1f - deviation * 2f))
    }

    /**
     * Integrate gyroscope data to update quaternion orientation
     */
    private fun integrateGyroscope(
        quaternion: FloatArray,
        gyroX: Float,
        gyroY: Float,
        gyroZ: Float,
        dt: Float
    ): FloatArray {
        // Apply noise threshold
        val gx = if (abs(gyroX) > GYRO_NOISE_THRESHOLD) gyroX else 0f
        val gy = if (abs(gyroY) > GYRO_NOISE_THRESHOLD) gyroY else 0f
        val gz = if (abs(gyroZ) > GYRO_NOISE_THRESHOLD) gyroZ else 0f

        // Quaternion integration using angular velocity
        val halfDt = dt * 0.5f
        val qw = quaternion[0]
        val qx = quaternion[1]
        val qy = quaternion[2] 
        val qz = quaternion[3]

        // Update quaternion
        val newQw = qw - halfDt * (gx * qx + gy * qy + gz * qz)
        val newQx = qx + halfDt * (gx * qw + gz * qy - gy * qz)
        val newQy = qy + halfDt * (gy * qw - gz * qx + gx * qz)
        val newQz = qz + halfDt * (gz * qw + gy * qx - gx * qy)

        // Normalize quaternion
        val norm = sqrt(newQw * newQw + newQx * newQx + newQy * newQy + newQz * newQz)
        return if (norm > 0) {
            floatArrayOf(newQw / norm, newQx / norm, newQy / norm, newQz / norm)
        } else {
            quaternion
        }
    }

    /**
     * Convert quaternion to Euler angles (roll, pitch, yaw)
     */
    private fun quaternionToEuler(quaternion: FloatArray): Triple<Float, Float, Float> {
        val w = quaternion[0]
        val x = quaternion[1]
        val y = quaternion[2]
        val z = quaternion[3]

        // Roll (x-axis rotation)
        val sinRoll = 2 * (w * x + y * z)
        val cosRoll = 1 - 2 * (x * x + y * y)
        val roll = atan2(sinRoll, cosRoll).toFloat() * RADIANS_TO_DEGREES.toFloat()

        // Pitch (y-axis rotation)
        val sinPitch = 2 * (w * y - z * x)
        val pitch = if (abs(sinPitch) >= 1) {
            (PI / 2).toFloat() * if (sinPitch > 0) 1 else -1
        } else {
            asin(sinPitch).toFloat()
        } * RADIANS_TO_DEGREES.toFloat()

        // Yaw (z-axis rotation)
        val sinYaw = 2 * (w * z + x * y)
        val cosYaw = 1 - 2 * (y * y + z * z)
        val yaw = atan2(sinYaw, cosYaw).toFloat() * RADIANS_TO_DEGREES.toFloat()

        return Triple(roll, pitch, yaw)
    }
}