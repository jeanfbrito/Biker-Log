package com.motosensorlogger.data

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * High-performance IMU noise filtering system designed for motorcycle data logging.
 * 
 * Implements:
 * - Moving average filter with configurable window size for noise reduction
 * - Statistical outlier detection using 3-sigma rule 
 * - Circular buffer for zero-allocation efficiency
 * - Sub-10ms latency for real-time performance
 * 
 * Thread-safe for concurrent sensor callbacks.
 */
class SensorDataFilter(
    private val windowSize: Int = 5,
    private val outlierSigmaThreshold: Float = 3.0f,
    private val enableOutlierDetection: Boolean = true,
    private val enableMovingAverage: Boolean = true
) {
    
    // Circular buffers for each axis to minimize allocations
    private val accelBufferX = CircularFloatBuffer(windowSize)
    private val accelBufferY = CircularFloatBuffer(windowSize)
    private val accelBufferZ = CircularFloatBuffer(windowSize)
    
    private val gyroBufferX = CircularFloatBuffer(windowSize)
    private val gyroBufferY = CircularFloatBuffer(windowSize)
    private val gyroBufferZ = CircularFloatBuffer(windowSize)
    
    // Statistics tracking for outlier detection
    private var accelMeanX = 0f
    private var accelMeanY = 0f
    private var accelMeanZ = 0f
    private var accelStdX = 0f
    private var accelStdY = 0f
    private var accelStdZ = 0f
    
    private var gyroMeanX = 0f
    private var gyroMeanY = 0f
    private var gyroMeanZ = 0f
    private var gyroStdX = 0f
    private var gyroStdY = 0f
    private var gyroStdZ = 0f
    
    // Statistics update counter for periodic recalculation
    private var statsUpdateCount = 0
    private val statsUpdateInterval = 50 // Update statistics every 50 samples
    
    data class FilteredImuData(
        val accelX: Float,
        val accelY: Float, 
        val accelZ: Float,
        val gyroX: Float,
        val gyroY: Float,
        val gyroZ: Float,
        val wasFiltered: Boolean = false,
        val outlierFlags: OutlierFlags = OutlierFlags()
    )
    
    data class OutlierFlags(
        val accelX: Boolean = false,
        val accelY: Boolean = false,
        val accelZ: Boolean = false,
        val gyroX: Boolean = false,
        val gyroY: Boolean = false,
        val gyroZ: Boolean = false
    ) {
        fun hasOutliers(): Boolean = accelX || accelY || accelZ || gyroX || gyroY || gyroZ
    }
    
    /**
     * Filter IMU sensor data with moving average and outlier detection
     * @param accelX Raw accelerometer X value (m/s²)
     * @param accelY Raw accelerometer Y value (m/s²)
     * @param accelZ Raw accelerometer Z value (m/s²)
     * @param gyroX Raw gyroscope X value (°/s)
     * @param gyroY Raw gyroscope Y value (°/s)
     * @param gyroZ Raw gyroscope Z value (°/s)
     * @return FilteredImuData with processed values and filtering metadata
     */
    @Synchronized
    fun filterImuData(
        accelX: Float, accelY: Float, accelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float
    ): FilteredImuData {
        val startTime = System.nanoTime()
        
        // Step 1: Outlier detection (if enabled)
        val outlierFlags = if (enableOutlierDetection) {
            detectOutliers(accelX, accelY, accelZ, gyroX, gyroY, gyroZ)
        } else {
            OutlierFlags()
        }
        
        // Step 2: Use original or filtered values based on outlier detection
        val processedAccelX = if (outlierFlags.accelX && accelBufferX.size() > 0) {
            accelBufferX.getAverage() // Use previous average if outlier detected
        } else {
            accelX
        }
        
        val processedAccelY = if (outlierFlags.accelY && accelBufferY.size() > 0) {
            accelBufferY.getAverage()
        } else {
            accelY
        }
        
        val processedAccelZ = if (outlierFlags.accelZ && accelBufferZ.size() > 0) {
            accelBufferZ.getAverage()
        } else {
            accelZ
        }
        
        val processedGyroX = if (outlierFlags.gyroX && gyroBufferX.size() > 0) {
            gyroBufferX.getAverage()
        } else {
            gyroX
        }
        
        val processedGyroY = if (outlierFlags.gyroY && gyroBufferY.size() > 0) {
            gyroBufferY.getAverage()
        } else {
            gyroY
        }
        
        val processedGyroZ = if (outlierFlags.gyroZ && gyroBufferZ.size() > 0) {
            gyroBufferZ.getAverage()
        } else {
            gyroZ
        }
        
        // Step 3: Add processed values to circular buffers
        accelBufferX.add(processedAccelX)
        accelBufferY.add(processedAccelY)
        accelBufferZ.add(processedAccelZ)
        
        gyroBufferX.add(processedGyroX)
        gyroBufferY.add(processedGyroY)
        gyroBufferZ.add(processedGyroZ)
        
        // Step 4: Apply moving average filter (if enabled)
        val filteredData = if (enableMovingAverage) {
            FilteredImuData(
                accelX = accelBufferX.getAverage(),
                accelY = accelBufferY.getAverage(),
                accelZ = accelBufferZ.getAverage(),
                gyroX = gyroBufferX.getAverage(),
                gyroY = gyroBufferY.getAverage(),
                gyroZ = gyroBufferZ.getAverage(),
                wasFiltered = true,
                outlierFlags = outlierFlags
            )
        } else {
            FilteredImuData(
                accelX = processedAccelX,
                accelY = processedAccelY,
                accelZ = processedAccelZ,
                gyroX = processedGyroX,
                gyroY = processedGyroY,
                gyroZ = processedGyroZ,
                wasFiltered = outlierFlags.hasOutliers(),
                outlierFlags = outlierFlags
            )
        }
        
        // Step 5: Update statistics periodically
        statsUpdateCount++
        if (statsUpdateCount >= statsUpdateInterval) {
            updateStatistics()
            statsUpdateCount = 0
        }
        
        // Performance monitoring - should be < 10ms
        val elapsedNanos = System.nanoTime() - startTime
        if (elapsedNanos > 10_000_000) { // 10ms in nanoseconds
            // Log performance warning but don't throw - this is production code
            android.util.Log.w("SensorDataFilter", "Filtering took ${elapsedNanos / 1_000_000}ms - exceeds 10ms target")
        }
        
        return filteredData
    }
    
    /**
     * Detect outliers using 3-sigma rule based on running statistics
     */
    private fun detectOutliers(
        accelX: Float, accelY: Float, accelZ: Float,
        gyroX: Float, gyroY: Float, gyroZ: Float
    ): OutlierFlags {
        return OutlierFlags(
            accelX = isOutlier(accelX, accelMeanX, accelStdX),
            accelY = isOutlier(accelY, accelMeanY, accelStdY),
            accelZ = isOutlier(accelZ, accelMeanZ, accelStdZ),
            gyroX = isOutlier(gyroX, gyroMeanX, gyroStdX),
            gyroY = isOutlier(gyroY, gyroMeanY, gyroStdY),
            gyroZ = isOutlier(gyroZ, gyroMeanZ, gyroStdZ)
        )
    }
    
    /**
     * Check if a value is an outlier based on 3-sigma rule
     */
    private fun isOutlier(value: Float, mean: Float, std: Float): Boolean {
        if (std == 0f) return false // No deviation data yet
        val deviation = abs(value - mean)
        return deviation > (outlierSigmaThreshold * std)
    }
    
    /**
     * Update running statistics for outlier detection
     */
    private fun updateStatistics() {
        // Update accelerometer statistics
        if (accelBufferX.size() > 1) {
            accelMeanX = accelBufferX.getAverage()
            accelStdX = accelBufferX.getStandardDeviation()
        }
        if (accelBufferY.size() > 1) {
            accelMeanY = accelBufferY.getAverage()
            accelStdY = accelBufferY.getStandardDeviation()
        }
        if (accelBufferZ.size() > 1) {
            accelMeanZ = accelBufferZ.getAverage()
            accelStdZ = accelBufferZ.getStandardDeviation()
        }
        
        // Update gyroscope statistics
        if (gyroBufferX.size() > 1) {
            gyroMeanX = gyroBufferX.getAverage()
            gyroStdX = gyroBufferX.getStandardDeviation()
        }
        if (gyroBufferY.size() > 1) {
            gyroMeanY = gyroBufferY.getAverage()
            gyroStdY = gyroBufferY.getStandardDeviation()
        }
        if (gyroBufferZ.size() > 1) {
            gyroMeanZ = gyroBufferZ.getAverage()
            gyroStdZ = gyroBufferZ.getStandardDeviation()
        }
    }
    
    /**
     * Reset filter state - useful for testing or when starting new session
     */
    fun reset() {
        accelBufferX.clear()
        accelBufferY.clear()
        accelBufferZ.clear()
        
        gyroBufferX.clear()
        gyroBufferY.clear()
        gyroBufferZ.clear()
        
        // Reset statistics
        accelMeanX = 0f; accelMeanY = 0f; accelMeanZ = 0f
        accelStdX = 0f; accelStdY = 0f; accelStdZ = 0f
        
        gyroMeanX = 0f; gyroMeanY = 0f; gyroMeanZ = 0f
        gyroStdX = 0f; gyroStdY = 0f; gyroStdZ = 0f
        
        statsUpdateCount = 0
    }
    
    /**
     * Get current filter statistics for monitoring/debugging
     */
    fun getFilterStats(): FilterStats {
        return FilterStats(
            bufferSize = windowSize,
            currentSamples = accelBufferX.size(),
            accelMean = Triple(accelMeanX, accelMeanY, accelMeanZ),
            accelStd = Triple(accelStdX, accelStdY, accelStdZ),
            gyroMean = Triple(gyroMeanX, gyroMeanY, gyroMeanZ),
            gyroStd = Triple(gyroStdX, gyroStdY, gyroStdZ)
        )
    }
    
    data class FilterStats(
        val bufferSize: Int,
        val currentSamples: Int,
        val accelMean: Triple<Float, Float, Float>,
        val accelStd: Triple<Float, Float, Float>,
        val gyroMean: Triple<Float, Float, Float>,
        val gyroStd: Triple<Float, Float, Float>
    )
}

/**
 * High-performance circular buffer for float values with zero allocation operations.
 * Optimized for real-time sensor data processing.
 */
private class CircularFloatBuffer(private val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0
    private var size = 0
    
    fun add(value: Float) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (size < capacity) size++
    }
    
    fun size(): Int = size
    
    fun getAverage(): Float {
        if (size == 0) return 0f
        
        var sum = 0f
        for (i in 0 until size) {
            sum += buffer[i]
        }
        return sum / size
    }
    
    fun getStandardDeviation(): Float {
        if (size <= 1) return 0f
        
        val mean = getAverage()
        var sumSquaredDiff = 0f
        
        for (i in 0 until size) {
            val diff = buffer[i] - mean
            sumSquaredDiff += diff * diff
        }
        
        return sqrt(sumSquaredDiff / (size - 1))
    }
    
    fun clear() {
        writeIndex = 0
        size = 0
        // No need to clear the array - we track size
    }
    
    /**
     * Get the last n values - useful for debugging
     */
    fun getLastValues(n: Int = size): FloatArray {
        val result = FloatArray(minOf(n, size))
        val actualSize = minOf(n, size)
        
        for (i in 0 until actualSize) {
            val index = if (size < capacity) {
                // Buffer not full yet - read from beginning
                i
            } else {
                // Buffer is full - read backwards from writeIndex
                (writeIndex - actualSize + i + capacity) % capacity
            }
            result[i] = buffer[index]
        }
        
        return result
    }
}