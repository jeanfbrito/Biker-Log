package com.motosensorlogger.calibration

import android.hardware.SensorManager
import kotlin.math.*

/**
 * Professional calibration manager using quaternion-based rotation and sensor fusion
 * Provides robust phone orientation calibration for any mounting position
 */
class CalibrationManager {
    // Calibration state
    enum class CalibrationState {
        IDLE,
        PREPARING, // Waiting for stable conditions
        CALIBRATING, // Collecting samples
        PROCESSING, // Computing calibration
        COMPLETED, // Calibration successful
        FAILED, // Calibration failed
    }

    // Calibration quality metrics
    data class CalibrationQuality(
        val overallScore: Float, // 0-100 quality score
        val stabilityScore: Float, // How stable was the device
        val magneticScore: Float, // Magnetic field quality
        val gravityScore: Float, // Gravity vector consistency
        val sampleCount: Int, // Number of samples collected
        val isAcceptable: Boolean, // Whether calibration is usable
    )

    // Quaternion representation for rotation
    data class Quaternion(
        val w: Float,
        val x: Float,
        val y: Float,
        val z: Float,
    ) {
        fun normalize(): Quaternion {
            val norm = sqrt(w * w + x * x + y * y + z * z)
            return if (norm > 0) {
                Quaternion(w / norm, x / norm, y / norm, z / norm)
            } else {
                Quaternion(1f, 0f, 0f, 0f)
            }
        }

        fun conjugate(): Quaternion {
            return Quaternion(w, -x, -y, -z)
        }

        fun toRotationMatrix(): FloatArray {
            val matrix = FloatArray(9)

            val xx = x * x
            val xy = x * y
            val xz = x * z
            val xw = x * w
            val yy = y * y
            val yz = y * z
            val yw = y * w
            val zz = z * z
            val zw = z * w

            matrix[0] = 1 - 2 * (yy + zz)
            matrix[1] = 2 * (xy - zw)
            matrix[2] = 2 * (xz + yw)

            matrix[3] = 2 * (xy + zw)
            matrix[4] = 1 - 2 * (xx + zz)
            matrix[5] = 2 * (yz - xw)

            matrix[6] = 2 * (xz - yw)
            matrix[7] = 2 * (yz + xw)
            matrix[8] = 1 - 2 * (xx + yy)

            return matrix
        }

        companion object {
            fun fromEuler(
                pitch: Float,
                roll: Float,
                yaw: Float,
            ): Quaternion {
                // Convert Euler angles to quaternion
                val cp = cos(pitch * 0.5f)
                val sp = sin(pitch * 0.5f)
                val cr = cos(roll * 0.5f)
                val sr = sin(roll * 0.5f)
                val cy = cos(yaw * 0.5f)
                val sy = sin(yaw * 0.5f)

                return Quaternion(
                    w = cp * cr * cy + sp * sr * sy,
                    x = sp * cr * cy - cp * sr * sy,
                    y = cp * sr * cy + sp * cr * sy,
                    z = cp * cr * sy - sp * sr * cy,
                ).normalize()
            }

            fun fromRotationMatrix(matrix: FloatArray): Quaternion {
                val w = sqrt(max(0f, 1 + matrix[0] + matrix[4] + matrix[8])) / 2
                val x = sqrt(max(0f, 1 + matrix[0] - matrix[4] - matrix[8])) / 2
                val y = sqrt(max(0f, 1 - matrix[0] + matrix[4] - matrix[8])) / 2
                val z = sqrt(max(0f, 1 - matrix[0] - matrix[4] + matrix[8])) / 2

                // Determine signs
                val signX = if ((matrix[7] - matrix[5]) < 0) -1f else 1f
                val signY = if ((matrix[2] - matrix[6]) < 0) -1f else 1f
                val signZ = if ((matrix[3] - matrix[1]) < 0) -1f else 1f

                return Quaternion(w, x * signX, y * signY, z * signZ).normalize()
            }
        }
    }

    // Calibration data with quaternion rotation
    data class CalibrationData(
        val quaternion: Quaternion, // Device orientation as quaternion
        val gravityVector: FloatArray, // Gravity in device frame
        val magneticVector: FloatArray, // Magnetic field in device frame
        val gyroscopeBias: FloatArray, // Gyroscope bias (drift)
        val accelerometerBias: FloatArray, // Accelerometer bias
        val quality: CalibrationQuality, // Quality metrics
        val timestamp: Long,
        val deviceModel: String = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}",
    ) {
        fun toRotationMatrix(): FloatArray = quaternion.toRotationMatrix()

        fun transformVector(input: FloatArray): FloatArray {
            val matrix = toRotationMatrix()
            return floatArrayOf(
                matrix[0] * input[0] + matrix[1] * input[1] + matrix[2] * input[2],
                matrix[3] * input[0] + matrix[4] * input[1] + matrix[5] * input[2],
                matrix[6] * input[0] + matrix[7] * input[1] + matrix[8] * input[2],
            )
        }

        fun inverseTransformVector(input: FloatArray): FloatArray {
            val matrix = toRotationMatrix()
            // Transpose for inverse rotation
            return floatArrayOf(
                matrix[0] * input[0] + matrix[3] * input[1] + matrix[6] * input[2],
                matrix[1] * input[0] + matrix[4] * input[1] + matrix[7] * input[2],
                matrix[2] * input[0] + matrix[5] * input[1] + matrix[8] * input[2],
            )
        }
    }

    // Madgwick filter for sensor fusion
    class MadgwickFilter(private var beta: Float = 0.1f) {
        private var q = Quaternion(1f, 0f, 0f, 0f)

        fun update(
            ax: Float,
            ay: Float,
            az: Float,
            gx: Float,
            gy: Float,
            gz: Float,
            mx: Float,
            my: Float,
            mz: Float,
            dt: Float,
        ): Quaternion {
            var qw = q.w
            var qx = q.x
            var qy = q.y
            var qz = q.z

            // Normalize accelerometer
            val normA = sqrt(ax * ax + ay * ay + az * az)
            if (normA == 0f) return q
            val axn = ax / normA
            val ayn = ay / normA
            val azn = az / normA

            // Normalize magnetometer
            val normM = sqrt(mx * mx + my * my + mz * mz)
            if (normM == 0f) return q
            val mxn = mx / normM
            val myn = my / normM
            val mzn = mz / normM

            // Auxiliary variables
            val hx = mxn * (1 - 2 * (qy * qy + qz * qz)) + myn * 2 * (qx * qy - qw * qz) + mzn * 2 * (qx * qz + qw * qy)
            val hy = mxn * 2 * (qx * qy + qw * qz) + myn * (1 - 2 * (qx * qx + qz * qz)) + mzn * 2 * (qy * qz - qw * qx)
            val bx = sqrt(hx * hx + hy * hy)
            val bz = mxn * 2 * (qx * qz - qw * qy) + myn * 2 * (qy * qz + qw * qx) + mzn * (1 - 2 * (qx * qx + qy * qy))

            // Gradient descent algorithm corrective step
            val fx = 2 * (qx * qz - qw * qy) - axn
            val fy = 2 * (qw * qx + qy * qz) - ayn
            val fz = 2 * (0.5f - qx * qx - qy * qy) - azn
            val fm1 = 2 * bx * (0.5f - qy * qy - qz * qz) + 2 * bz * (qx * qz - qw * qy) - mxn
            val fm2 = 2 * bx * (qx * qy - qw * qz) + 2 * bz * (qw * qx + qy * qz) - myn
            val fm3 = 2 * bx * (qw * qy + qx * qz) + 2 * bz * (0.5f - qx * qx - qy * qy) - mzn

            // Compute the Jacobian
            val j11 = -2 * qy
            val j12 = 2 * qz
            val j13 = -2 * qw
            val j14 = 2 * qx
            val j21 = 2 * qx
            val j22 = 2 * qw
            val j23 = 2 * qz
            val j24 = 2 * qy
            val j31 = 0f
            val j32 = -4 * qx
            val j33 = -4 * qy
            val j34 = 0f

            // Compute gradient
            var sw = j11 * fx + j21 * fy + j31 * fz
            var sx = j12 * fx + j22 * fy + j32 * fz
            var sy = j13 * fx + j23 * fy + j33 * fz
            var sz = j14 * fx + j24 * fy + j34 * fz

            // Add magnetic field gradient
            val j41 = -2 * bz * qy
            val j42 = 2 * bz * qz
            val j43 = -4 * bx * qy - 2 * bz * qw
            val j44 = -4 * bx * qz + 2 * bz * qx
            val j51 = -2 * bx * qz + 2 * bz * qx
            val j52 = 2 * bx * qy + 2 * bz * qw
            val j53 = 2 * bx * qx + 2 * bz * qz
            val j54 = -2 * bx * qw + 2 * bz * qy
            val j61 = 2 * bx * qy
            val j62 = 2 * bx * qz - 4 * bz * qx
            val j63 = 2 * bx * qw - 4 * bz * qy
            val j64 = 2 * bx * qx

            sw += j41 * fm1 + j51 * fm2 + j61 * fm3
            sx += j42 * fm1 + j52 * fm2 + j62 * fm3
            sy += j43 * fm1 + j53 * fm2 + j63 * fm3
            sz += j44 * fm1 + j54 * fm2 + j64 * fm3

            // Normalize gradient
            val norm = sqrt(sw * sw + sx * sx + sy * sy + sz * sz)
            if (norm > 0) {
                sw /= norm
                sx /= norm
                sy /= norm
                sz /= norm
            }

            // Apply feedback step
            qw -= beta * sw
            qx -= beta * sx
            qy -= beta * sy
            qz -= beta * sz

            // Integrate rate of change of quaternion
            val gxr = gx * 0.5f * dt
            val gyr = gy * 0.5f * dt
            val gzr = gz * 0.5f * dt

            qw += (-qx * gxr - qy * gyr - qz * gzr)
            qx += (qw * gxr + qy * gzr - qz * gyr)
            qy += (qw * gyr - qx * gzr + qz * gxr)
            qz += (qw * gzr + qx * gyr - qy * gxr)

            // Normalize quaternion
            q = Quaternion(qw, qx, qy, qz).normalize()
            return q
        }

        fun reset() {
            q = Quaternion(1f, 0f, 0f, 0f)
        }

        fun getQuaternion(): Quaternion = q
    }

    // Calibration sample collector
    private class SampleCollector {
        private val accelSamples = mutableListOf<FloatArray>()
        private val gyroSamples = mutableListOf<FloatArray>()
        private val magSamples = mutableListOf<FloatArray>()
        private var startTime = 0L

        fun reset() {
            accelSamples.clear()
            gyroSamples.clear()
            magSamples.clear()
            startTime = System.currentTimeMillis()
        }

        fun addSample(
            accel: FloatArray,
            gyro: FloatArray,
            mag: FloatArray,
        ) {
            accelSamples.add(accel.clone())
            gyroSamples.add(gyro.clone())
            magSamples.add(mag.clone())
        }

        fun getSampleCount(): Int = accelSamples.size

        fun getDuration(): Long = System.currentTimeMillis() - startTime

        fun computeStatistics(): CalibrationStatistics {
            if (accelSamples.isEmpty()) {
                return CalibrationStatistics()
            }

            // Compute means
            val accelMean = FloatArray(3)
            val gyroMean = FloatArray(3)
            val magMean = FloatArray(3)

            for (i in 0..2) {
                accelMean[i] = accelSamples.map { it[i] }.average().toFloat()
                gyroMean[i] = gyroSamples.map { it[i] }.average().toFloat()
                magMean[i] = magSamples.map { it[i] }.average().toFloat()
            }

            // Compute standard deviations
            val accelStd = FloatArray(3)
            val gyroStd = FloatArray(3)
            val magStd = FloatArray(3)

            for (i in 0..2) {
                accelStd[i] = sqrt(accelSamples.map { (it[i] - accelMean[i]).pow(2) }.average().toFloat())
                gyroStd[i] = sqrt(gyroSamples.map { (it[i] - gyroMean[i]).pow(2) }.average().toFloat())
                magStd[i] = sqrt(magSamples.map { (it[i] - magMean[i]).pow(2) }.average().toFloat())
            }

            return CalibrationStatistics(
                accelMean = accelMean,
                accelStd = accelStd,
                gyroMean = gyroMean,
                gyroStd = gyroStd,
                magMean = magMean,
                magStd = magStd,
                sampleCount = accelSamples.size,
            )
        }
    }

    private data class CalibrationStatistics(
        val accelMean: FloatArray = FloatArray(3),
        val accelStd: FloatArray = FloatArray(3),
        val gyroMean: FloatArray = FloatArray(3),
        val gyroStd: FloatArray = FloatArray(3),
        val magMean: FloatArray = FloatArray(3),
        val magStd: FloatArray = FloatArray(3),
        val sampleCount: Int = 0,
    )

    // Main calibration manager state
    private var state = CalibrationState.IDLE
    private val sampleCollector = SampleCollector()
    private val madgwickFilter = MadgwickFilter()
    private var calibrationData: CalibrationData? = null
    private var calibrationListener: CalibrationListener? = null

    // Calibration parameters
    private val MIN_SAMPLES = 100
    private val MAX_SAMPLES = 500
    private val CALIBRATION_DURATION_MS = 3000L
    private val MAX_ACCEL_STD = 0.5f // Maximum acceptable accelerometer standard deviation
    private val MAX_GYRO_STD = 0.1f // Maximum acceptable gyroscope standard deviation
    private val MAX_MAG_STD = 10f // Maximum acceptable magnetometer standard deviation

    interface CalibrationListener {
        fun onStateChanged(state: CalibrationState)

        fun onProgress(
            progress: Float,
            message: String,
        )

        fun onCalibrationComplete(data: CalibrationData)

        fun onCalibrationFailed(reason: String)
    }

    fun setListener(listener: CalibrationListener) {
        this.calibrationListener = listener
    }

    fun startCalibration() {
        if (state != CalibrationState.IDLE && state != CalibrationState.FAILED) {
            return
        }

        state = CalibrationState.PREPARING
        calibrationListener?.onStateChanged(state)
        calibrationListener?.onProgress(0f, "Preparing calibration...")

        sampleCollector.reset()
        madgwickFilter.reset()
    }

    fun processSensorData(
        accel: FloatArray,
        gyro: FloatArray,
        mag: FloatArray,
        timestamp: Long,
    ) {
        when (state) {
            CalibrationState.PREPARING -> {
                // Check if device is stable
                state = CalibrationState.CALIBRATING
                calibrationListener?.onStateChanged(state)
                calibrationListener?.onProgress(0.1f, "Keep device still...")
            }

            CalibrationState.CALIBRATING -> {
                sampleCollector.addSample(accel, gyro, mag)

                val progress =
                    minOf(
                        sampleCollector.getSampleCount().toFloat() / MIN_SAMPLES,
                        sampleCollector.getDuration().toFloat() / CALIBRATION_DURATION_MS,
                    )

                calibrationListener?.onProgress(
                    progress * 0.8f + 0.1f,
                    "Collecting data... ${(progress * 100).toInt()}%",
                )

                // Check completion conditions
                if (sampleCollector.getSampleCount() >= MIN_SAMPLES &&
                    sampleCollector.getDuration() >= CALIBRATION_DURATION_MS
                ) {
                    processCalibration()
                } else if (sampleCollector.getSampleCount() >= MAX_SAMPLES) {
                    processCalibration()
                }
            }

            else -> {
                // Not calibrating
            }
        }
    }

    private fun processCalibration() {
        state = CalibrationState.PROCESSING
        calibrationListener?.onStateChanged(state)
        calibrationListener?.onProgress(0.9f, "Processing calibration...")

        val stats = sampleCollector.computeStatistics()

        // Validate calibration quality
        val quality = validateCalibration(stats)

        if (!quality.isAcceptable) {
            state = CalibrationState.FAILED
            calibrationListener?.onStateChanged(state)
            calibrationListener?.onCalibrationFailed("Device was not stable enough. Please try again.")
            return
        }

        // Compute calibration quaternion
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)

        if (SensorManager.getRotationMatrix(
                rotationMatrix,
                inclinationMatrix,
                stats.accelMean,
                stats.magMean,
            )
        ) {
            val quaternion = Quaternion.fromRotationMatrix(rotationMatrix)

            calibrationData =
                CalibrationData(
                    quaternion = quaternion,
                    gravityVector = stats.accelMean,
                    magneticVector = stats.magMean,
                    gyroscopeBias = stats.gyroMean, // Gyro bias when stationary
                    accelerometerBias = FloatArray(3), // Could compute if needed
                    quality = quality,
                    timestamp = System.currentTimeMillis(),
                )

            state = CalibrationState.COMPLETED
            calibrationListener?.onStateChanged(state)
            calibrationListener?.onProgress(1.0f, "Calibration complete!")
            calibrationListener?.onCalibrationComplete(calibrationData!!)
        } else {
            state = CalibrationState.FAILED
            calibrationListener?.onStateChanged(state)
            calibrationListener?.onCalibrationFailed("Could not compute device orientation.")
        }
    }

    private fun validateCalibration(stats: CalibrationStatistics): CalibrationQuality {
        // Calculate stability scores
        val accelStability = 100f * (1f - minOf(stats.accelStd.average().toFloat() / MAX_ACCEL_STD, 1f))
        val gyroStability = 100f * (1f - minOf(stats.gyroStd.average().toFloat() / MAX_GYRO_STD, 1f))
        val magStability = 100f * (1f - minOf(stats.magStd.average().toFloat() / MAX_MAG_STD, 1f))

        // Check gravity magnitude (should be close to 9.81)
        val gravityMag = sqrt(stats.accelMean.map { it * it }.sum())
        val gravityScore = 100f * (1f - minOf(abs(gravityMag - 9.81f) / 2f, 1f))

        // Check magnetic field magnitude (varies by location, but typically 25-65 μT)
        val magMag = sqrt(stats.magMean.map { it * it }.sum())
        val magScore = if (magMag in 20f..70f) 100f else 50f

        // Overall score
        val overallScore = (accelStability + gyroStability + gravityScore + magScore) / 4f

        return CalibrationQuality(
            overallScore = overallScore,
            stabilityScore = (accelStability + gyroStability) / 2f,
            magneticScore = magScore,
            gravityScore = gravityScore,
            sampleCount = stats.sampleCount,
            isAcceptable = overallScore >= 70f && gravityScore >= 80f,
        )
    }

    fun getCalibrationData(): CalibrationData? = calibrationData

    fun resetCalibration() {
        state = CalibrationState.IDLE
        calibrationData = null
        sampleCollector.reset()
        madgwickFilter.reset()
    }

    fun applyCalibration(
        input: FloatArray,
        sensorType: Int,
    ): FloatArray {
        val data = calibrationData ?: return input

        return when (sensorType) {
            android.hardware.Sensor.TYPE_ACCELEROMETER -> {
                // Transform accelerometer data to world frame
                data.transformVector(input)
            }
            android.hardware.Sensor.TYPE_MAGNETIC_FIELD -> {
                // Transform magnetometer data to world frame
                data.transformVector(input)
            }
            android.hardware.Sensor.TYPE_GYROSCOPE -> {
                // Apply gyroscope bias correction
                floatArrayOf(
                    input[0] - data.gyroscopeBias[0],
                    input[1] - data.gyroscopeBias[1],
                    input[2] - data.gyroscopeBias[2],
                )
            }
            else -> input
        }
    }
}
