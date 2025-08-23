package com.motosensorlogger.calibration

import android.content.Context
import android.hardware.SensorManager
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.math.*

/**
 * Simplified calibration service that collects reference orientation
 * No real-time transformation - just captures mounting position
 */
class CalibrationService(context: Context) {
    
    private val settingsManager = SettingsManager.getInstance(context)
    
    enum class State {
        IDLE,
        COLLECTING,
        PROCESSING,
        COMPLETED,
        FAILED
    }
    
    data class Progress(
        val state: State,
        val percent: Float,
        val message: String,
        val remainingTimeMs: Long = 0
    )
    
    // State management
    private val _state = MutableStateFlow(State.IDLE)
    val state: StateFlow<State> = _state.asStateFlow()
    
    private val _progress = MutableStateFlow(Progress(State.IDLE, 0f, "Ready"))
    val progress: StateFlow<Progress> = _progress.asStateFlow()
    
    
    // Sample collection
    private val accelerometerSamples = mutableListOf<FloatArray>()
    private val gyroscopeSamples = mutableListOf<FloatArray>()
    private val magnetometerSamples = mutableListOf<FloatArray>()
    private var calibrationStartTime = 0L
    
    private var calibrationJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)
    
    /**
     * Start calibration process
     */
    fun startCalibration() {
        if (_state.value != State.IDLE) {
            return
        }
        
        resetCalibration()
        _state.value = State.COLLECTING
        calibrationStartTime = System.currentTimeMillis()
        
        // Get calibration duration from settings
        val calibrationDurationMs = settingsManager.calibrationSettings.value.durationMs
        
        // Start timeout timer
        calibrationJob = scope.launch {
            var elapsed = 0L
            while (elapsed < calibrationDurationMs && _state.value == State.COLLECTING) {
                elapsed = System.currentTimeMillis() - calibrationStartTime
                val percent = (elapsed.toFloat() / calibrationDurationMs * 100).coerceIn(0f, 100f)
                val remaining = calibrationDurationMs - elapsed
                
                _progress.emit(Progress(
                    State.COLLECTING,
                    percent,
                    "Hold still... ${(remaining / 1000f).toInt() + 1}s",
                    remaining
                ))
                
                delay(100) // Update every 100ms
            }
            
            // Process calibration after duration
            if (_state.value == State.COLLECTING) {
                android.util.Log.d("CalibrationService", "Timer finished, processing calibration")
                processCalibration()
            }
        }
    }
    
    /**
     * Add sensor sample during calibration
     */
    fun addSensorSample(
        accelerometer: FloatArray,
        gyroscope: FloatArray,
        magnetometer: FloatArray
    ) {
        if (_state.value != State.COLLECTING) {
            return
        }
        
        accelerometerSamples.add(accelerometer.clone())
        gyroscopeSamples.add(gyroscope.clone())
        magnetometerSamples.add(magnetometer.clone())
        
        // Log sample count for debugging
        if (accelerometerSamples.size % 10 == 0) {
            android.util.Log.d("CalibrationService", "Collected ${accelerometerSamples.size} samples")
        }
    }
    
    /**
     * Process collected samples and compute calibration
     */
    private suspend fun processCalibration() {
        android.util.Log.d("CalibrationService", "Processing calibration with ${accelerometerSamples.size} samples")
        
        _state.value = State.PROCESSING
        _progress.emit(Progress(State.PROCESSING, 95f, "Processing...", 0))
        
        val minSamples = settingsManager.calibrationSettings.value.minSamples
        if (accelerometerSamples.size < minSamples) {
            android.util.Log.e("CalibrationService", "Not enough samples: ${accelerometerSamples.size} < $minSamples")
            failCalibration("Not enough samples collected (${accelerometerSamples.size}/$minSamples)")
            return
        }
        
        // Calculate averages
        val avgAccel = FloatArray(3)
        val avgGyro = FloatArray(3)
        val avgMag = FloatArray(3)
        
        for (i in 0..2) {
            avgAccel[i] = accelerometerSamples.map { it[i] }.average().toFloat()
            avgGyro[i] = gyroscopeSamples.map { it[i] }.average().toFloat()
            avgMag[i] = magnetometerSamples.map { it[i] }.average().toFloat()
        }
        
        // Calculate standard deviations to check stability
        val accelStd = calculateStandardDeviation(accelerometerSamples)
        val gyroStd = calculateStandardDeviation(gyroscopeSamples)
        
        // Check if device was stable
        val stabilityThreshold = settingsManager.calibrationSettings.value.stabilityThreshold
        val wasStable = accelStd.all { it < stabilityThreshold } && 
                       gyroStd.all { it < 0.5f }  // Relaxed for handheld/motorcycle mount
        
        android.util.Log.d("CalibrationService", "Stability check: accelStd=${accelStd.contentToString()}, threshold=$stabilityThreshold, wasStable=$wasStable")
        
        if (!wasStable) {
            android.util.Log.e("CalibrationService", "Device was moving: accelStd=${accelStd.contentToString()}, gyroStd=${gyroStd.contentToString()}")
            failCalibration("Device was moving during calibration")
            return
        }
        
        // Calculate rotation matrix and quaternion
        val rotationMatrix = FloatArray(9)
        val inclinationMatrix = FloatArray(9)
        
        if (!SensorManager.getRotationMatrix(rotationMatrix, inclinationMatrix, avgAccel, avgMag)) {
            failCalibration("Could not determine device orientation")
            return
        }
        
        // Get Euler angles
        val orientation = FloatArray(3)
        SensorManager.getOrientation(rotationMatrix, orientation)
        
        val pitch = Math.toDegrees(orientation[1].toDouble()).toFloat()
        val roll = Math.toDegrees(orientation[2].toDouble()).toFloat()
        val azimuth = Math.toDegrees(orientation[0].toDouble()).toFloat()
        
        // Convert rotation matrix to quaternion
        val quaternion = rotationMatrixToQuaternion(rotationMatrix)
        
        // Calculate quality metrics
        val quality = CalibrationQuality(
            overallScore = calculateQualityScore(accelStd, gyroStd, avgAccel, avgMag),
            stabilityScore = 100f * (1f - accelStd.average().toFloat() / stabilityThreshold),
            magneticFieldQuality = calculateMagneticFieldQuality(avgMag),
            gravityConsistency = calculateGravityConsistency(avgAccel),
            isAcceptable = wasStable
        )
        
        // Create calibration data
        val calibrationData = CalibrationData(
            referenceGravity = avgAccel,
            referenceMagnetic = avgMag,
            referenceRotationMatrix = rotationMatrix,
            referenceQuaternion = quaternion,
            referencePitch = pitch,
            referenceRoll = roll,
            referenceAzimuth = azimuth,
            gyroscopeBias = avgGyro,  // Bias when stationary
            timestamp = calibrationStartTime,
            duration = System.currentTimeMillis() - calibrationStartTime,
            sampleCount = accelerometerSamples.size,
            quality = quality
        )
        
        // Store and complete
        currentCalibration = calibrationData
        _state.value = State.COMPLETED
        _progress.emit(Progress(
            State.COMPLETED, 
            100f, 
            "Calibration complete! Quality: ${quality.getQualityLevel()}", 
            0
        ))
    }
    
    private fun calculateStandardDeviation(samples: List<FloatArray>): FloatArray {
        val std = FloatArray(3)
        val mean = FloatArray(3)
        
        // Calculate means
        for (i in 0..2) {
            mean[i] = samples.map { it[i] }.average().toFloat()
        }
        
        // Calculate standard deviations
        for (i in 0..2) {
            val variance = samples.map { (it[i] - mean[i]).pow(2) }.average().toFloat()
            std[i] = sqrt(variance)
        }
        
        return std
    }
    
    private fun rotationMatrixToQuaternion(matrix: FloatArray): FloatArray {
        val w = sqrt(max(0f, 1 + matrix[0] + matrix[4] + matrix[8])) / 2
        val x = sqrt(max(0f, 1 + matrix[0] - matrix[4] - matrix[8])) / 2
        val y = sqrt(max(0f, 1 - matrix[0] + matrix[4] - matrix[8])) / 2
        val z = sqrt(max(0f, 1 - matrix[0] - matrix[4] + matrix[8])) / 2
        
        // Determine signs
        val signX = if ((matrix[7] - matrix[5]) < 0) -1f else 1f
        val signY = if ((matrix[2] - matrix[6]) < 0) -1f else 1f
        val signZ = if ((matrix[3] - matrix[1]) < 0) -1f else 1f
        
        return floatArrayOf(w, x * signX, y * signY, z * signZ)
    }
    
    private fun calculateQualityScore(
        accelStd: FloatArray,
        gyroStd: FloatArray,
        avgAccel: FloatArray,
        avgMag: FloatArray
    ): Float {
        // Check gravity magnitude (should be ~9.81)
        val gravityMag = sqrt(avgAccel.map { it * it }.sum())
        val gravityScore = 100f * (1f - min(abs(gravityMag - 9.81f) / 2f, 1f))
        
        // Check stability
        val stabilityThreshold = settingsManager.calibrationSettings.value.stabilityThreshold
        val stabilityScore = 100f * (1f - accelStd.average().toFloat() / stabilityThreshold)
        
        // Check magnetic field (typical range 25-65 μT)
        val magMag = sqrt(avgMag.map { it * it }.sum())
        val magScore = when {
            magMag in 25f..65f -> 100f
            magMag in 20f..70f -> 75f
            else -> 50f
        }
        
        return (gravityScore + stabilityScore + magScore) / 3f
    }
    
    private fun calculateMagneticFieldQuality(mag: FloatArray): Float {
        val magnitude = sqrt(mag.map { it * it }.sum())
        return when {
            magnitude in 25f..65f -> 100f
            magnitude in 20f..70f -> 75f
            else -> 50f
        }
    }
    
    private fun calculateGravityConsistency(accel: FloatArray): Float {
        val magnitude = sqrt(accel.map { it * it }.sum())
        return 100f * (1f - min(abs(magnitude - 9.81f) / 2f, 1f))
    }
    
    private suspend fun failCalibration(reason: String) {
        _state.value = State.FAILED
        _progress.emit(Progress(State.FAILED, 0f, reason, 0))
        resetCalibration()
    }
    
    fun cancelCalibration() {
        calibrationJob?.cancel()
        resetCalibration()
        _state.value = State.IDLE
        scope.launch {
            _progress.emit(Progress(State.IDLE, 0f, "Calibration cancelled", 0))
        }
    }
    
    private fun resetCalibration() {
        accelerometerSamples.clear()
        gyroscopeSamples.clear()
        magnetometerSamples.clear()
        calibrationStartTime = 0L
    }
    
    var currentCalibration: CalibrationData? = null
        private set
    
    fun clearCalibration() {
        currentCalibration = null
        _state.value = State.IDLE
        scope.launch {
            _progress.emit(Progress(State.IDLE, 0f, "Ready", 0))
        }
    }
    
    fun dispose() {
        calibrationJob?.cancel()
        scope.cancel()
    }
}