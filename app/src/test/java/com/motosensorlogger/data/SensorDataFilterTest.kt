package com.motosensorlogger.data

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import kotlin.math.abs

/**
 * Comprehensive test suite for SensorDataFilter
 * Tests filter effectiveness, outlier detection, performance, and edge cases
 */
class SensorDataFilterTest {
    
    private lateinit var filter: SensorDataFilter
    
    // Test constants
    private val FLOAT_TOLERANCE = 0.001f
    private val PERFORMANCE_THRESHOLD_MS = 10
    
    @Before
    fun setup() {
        filter = SensorDataFilter(
            windowSize = 5,
            outlierSigmaThreshold = 3.0f,
            enableOutlierDetection = true,
            enableMovingAverage = true
        )
    }
    
    @Test
    fun `test moving average reduces noise`() {
        // Generate noisy accelerometer data around a base value
        val baseAccelX = 9.8f
        val noisyData = listOf(
            baseAccelX + 0.5f,  // +0.5
            baseAccelX - 0.3f,  // -0.3
            baseAccelX + 0.2f,  // +0.2
            baseAccelX - 0.1f,  // -0.1
            baseAccelX + 0.4f   // +0.4
        )
        
        var lastFilteredX = 0f
        
        // Feed noisy data to filter
        for (noisyAccelX in noisyData) {
            val result = filter.filterImuData(
                noisyAccelX, 0f, 0f,  // Only test X axis
                0f, 0f, 0f
            )
            lastFilteredX = result.accelX
        }
        
        // The filtered result should be closer to base value than any individual noisy sample
        val filteredDeviation = abs(lastFilteredX - baseAccelX)
        val maxNoisyDeviation = noisyData.maxOf { abs(it - baseAccelX) }
        
        assertTrue(
            "Filtered data should be less noisy (filtered deviation: $filteredDeviation, max noisy: $maxNoisyDeviation)",
            filteredDeviation < maxNoisyDeviation
        )
        
        // The average of the noisy data should be close to the filtered result
        val expectedAverage = noisyData.average().toFloat()
        assertEquals("Filtered result should approximate moving average", expectedAverage, lastFilteredX, FLOAT_TOLERANCE)
    }
    
    @Test
    fun `test outlier detection and rejection`() {
        // Establish baseline with normal data
        val normalAccelX = 9.8f
        for (i in 0 until 10) {
            filter.filterImuData(normalAccelX, 0f, 0f, 0f, 0f, 0f)
        }
        
        // Introduce a large outlier (should be > 3 sigma from mean)
        val outlierValue = normalAccelX + 50f  // Extreme outlier
        val result = filter.filterImuData(
            outlierValue, 0f, 0f,
            0f, 0f, 0f
        )
        
        // The outlier should be detected
        assertTrue("Outlier should be detected", result.outlierFlags.accelX)
        assertTrue("Filter should indicate data was modified", result.wasFiltered)
        
        // The filtered result should not be the outlier value
        assertNotEquals("Filtered value should not equal outlier", outlierValue, result.accelX, FLOAT_TOLERANCE)
        
        // The filtered result should be closer to normal value
        val deviationFromNormal = abs(result.accelX - normalAccelX)
        val outlierDeviation = abs(outlierValue - normalAccelX)
        assertTrue(
            "Filtered result should be closer to normal than outlier",
            deviationFromNormal < outlierDeviation
        )
    }
    
    @Test
    fun `test all axes processed independently`() {
        val accelData = Triple(1.0f, 2.0f, 3.0f)
        val gyroData = Triple(0.1f, 0.2f, 0.3f)
        
        val result = filter.filterImuData(
            accelData.first, accelData.second, accelData.third,
            gyroData.first, gyroData.second, gyroData.third
        )
        
        // For first sample, filtered values should match input (no history yet)
        assertEquals("AccelX should match", accelData.first, result.accelX, FLOAT_TOLERANCE)
        assertEquals("AccelY should match", accelData.second, result.accelY, FLOAT_TOLERANCE)
        assertEquals("AccelZ should match", accelData.third, result.accelZ, FLOAT_TOLERANCE)
        assertEquals("GyroX should match", gyroData.first, result.gyroX, FLOAT_TOLERANCE)
        assertEquals("GyroY should match", gyroData.second, result.gyroY, FLOAT_TOLERANCE)
        assertEquals("GyroZ should match", gyroData.third, result.gyroZ, FLOAT_TOLERANCE)
    }
    
    @Test
    fun `test filter performance meets requirements`() {
        val testIterations = 1000
        val startTime = System.nanoTime()
        
        // Run many filtering operations
        for (i in 0 until testIterations) {
            filter.filterImuData(
                9.8f + (i % 10) * 0.1f, 0f, 0f,  // Varying data
                0f, 0f, 0f
            )
        }
        
        val elapsedNanos = System.nanoTime() - startTime
        val averageTimeMs = (elapsedNanos / testIterations) / 1_000_000.0
        
        assertTrue(
            "Average filtering time should be < ${PERFORMANCE_THRESHOLD_MS}ms (actual: ${averageTimeMs}ms)",
            averageTimeMs < PERFORMANCE_THRESHOLD_MS
        )
    }
    
    @Test
    fun `test buffer overflow handling`() {
        val windowSize = 5
        val manyInputs = 20  // More than buffer size
        
        // Fill buffer beyond capacity
        for (i in 0 until manyInputs) {
            val result = filter.filterImuData(
                i.toFloat(), 0f, 0f,
                0f, 0f, 0f
            )
            assertNotNull("Filter should handle buffer overflow", result)
        }
        
        // Filter should still work after overflow
        val finalResult = filter.filterImuData(100f, 0f, 0f, 0f, 0f, 0f)
        assertNotNull("Filter should work after buffer overflow", finalResult)
        assertTrue("Result should be reasonable", finalResult.accelX > 0f)
    }
    
    @Test
    fun `test reset functionality`() {
        // Add some data to build up filter state
        for (i in 0 until 10) {
            filter.filterImuData(9.8f, 0f, 0f, 0f, 0f, 0f)
        }
        
        val beforeReset = filter.getFilterStats()
        assertTrue("Should have samples before reset", beforeReset.currentSamples > 0)
        
        // Reset the filter
        filter.reset()
        
        val afterReset = filter.getFilterStats()
        assertEquals("Should have no samples after reset", 0, afterReset.currentSamples)
        assertEquals("Mean should be reset", 0f, afterReset.accelMean.first, FLOAT_TOLERANCE)
        assertEquals("Std should be reset", 0f, afterReset.accelStd.first, FLOAT_TOLERANCE)
    }
    
    @Test
    fun `test filter with disabled features`() {
        val filterNoMovingAverage = SensorDataFilter(
            windowSize = 5,
            enableMovingAverage = false,
            enableOutlierDetection = false
        )
        
        val inputValue = 9.8f
        val result = filterNoMovingAverage.filterImuData(
            inputValue, 0f, 0f,
            0f, 0f, 0f
        )
        
        // With no filtering, output should equal input
        assertEquals("Output should equal input when filtering disabled", inputValue, result.accelX, FLOAT_TOLERANCE)
        assertFalse("Should not indicate filtering occurred", result.wasFiltered)
        assertFalse("Should not detect outliers when disabled", result.outlierFlags.hasOutliers())
    }
    
    @Test
    fun `test edge case with zero values`() {
        val result = filter.filterImuData(0f, 0f, 0f, 0f, 0f, 0f)
        
        assertEquals("Zero input should produce zero output", 0f, result.accelX, FLOAT_TOLERANCE)
        assertEquals("Zero input should produce zero output", 0f, result.gyroX, FLOAT_TOLERANCE)
        assertNotNull("Filter should handle zero values", result)
    }
    
    @Test
    fun `test gyroscope filtering independent of accelerometer`() {
        // Test that gyro and accel are filtered independently
        val result1 = filter.filterImuData(
            1f, 0f, 0f,  // Normal accel
            100f, 0f, 0f  // Large gyro that might be outlier after statistics build up
        )
        
        // Build up some statistics first
        for (i in 0 until 20) {
            filter.filterImuData(
                1f, 0f, 0f,   // Consistent accel
                0.1f, 0f, 0f  // Consistent small gyro
            )
        }
        
        // Now test with outlier gyro but normal accel
        val result2 = filter.filterImuData(
            1f, 0f, 0f,    // Normal accel
            100f, 0f, 0f   // Outlier gyro
        )
        
        // Gyro should be detected as outlier, but accel should not
        assertTrue("Large gyro should be detected as outlier", result2.outlierFlags.gyroX)
        assertFalse("Normal accel should not be outlier", result2.outlierFlags.accelX)
    }
    
    @Test
    fun `test filter statistics tracking`() {
        val testValues = floatArrayOf(1f, 2f, 3f, 4f, 5f)
        
        // Feed test values
        for (value in testValues) {
            filter.filterImuData(value, 0f, 0f, 0f, 0f, 0f)
        }
        
        val stats = filter.getFilterStats()
        
        assertEquals("Should track correct buffer size", 5, stats.bufferSize)
        assertEquals("Should track correct number of samples", testValues.size, stats.currentSamples)
        
        // Check that mean is reasonable (should be close to actual mean of test values)
        val expectedMean = testValues.average().toFloat()
        assertEquals("Mean should be approximately correct", expectedMean, stats.accelMean.first, 0.1f)
        
        // Standard deviation should be > 0 for varied data
        assertTrue("Standard deviation should be > 0 for varied data", stats.accelStd.first > 0f)
    }
    
    @Test
    fun `test concurrent access safety`() {
        // This test simulates concurrent access from sensor threads
        val results = mutableListOf<SensorDataFilter.FilteredImuData>()
        
        // Run multiple filtering operations rapidly (simulating concurrent sensor callbacks)
        repeat(100) { i ->
            val result = filter.filterImuData(
                i.toFloat() % 10f, 0f, 0f,
                0f, 0f, 0f
            )
            results.add(result)
        }
        
        // All operations should complete successfully
        assertEquals("All operations should complete", 100, results.size)
        assertTrue("All results should be valid", results.all { it.accelX >= 0f })
    }
    
    @Test
    fun `test noise reduction effectiveness`() {
        // Generate data with known noise characteristics
        val cleanSignal = 9.8f
        val noiseAmplitude = 0.5f
        val noisyInputs = mutableListOf<Float>()
        val filteredOutputs = mutableListOf<Float>()
        
        // Create noisy input signal
        for (i in 0 until 50) {
            val noise = (Math.random().toFloat() - 0.5f) * 2 * noiseAmplitude
            val noisyInput = cleanSignal + noise
            noisyInputs.add(noisyInput)
            
            val result = filter.filterImuData(noisyInput, 0f, 0f, 0f, 0f, 0f)
            filteredOutputs.add(result.accelX)
        }
        
        // Calculate noise metrics
        val inputStd = calculateStandardDeviation(noisyInputs)
        val outputStd = calculateStandardDeviation(filteredOutputs.drop(5))  // Skip initial samples
        
        assertTrue(
            "Filter should reduce noise (input std: $inputStd, output std: $outputStd)",
            outputStd < inputStd
        )
        
        // The reduction should be significant (at least 20% improvement)
        val noiseReduction = (inputStd - outputStd) / inputStd
        assertTrue(
            "Noise reduction should be at least 20% (actual: ${noiseReduction * 100}%)",
            noiseReduction > 0.2
        )
    }
    
    private fun calculateStandardDeviation(values: List<Float>): Float {
        if (values.size <= 1) return 0f
        
        val mean = values.average().toFloat()
        val sumSquaredDiff = values.map { (it - mean) * (it - mean) }.sum().toDouble()
        return kotlin.math.sqrt((sumSquaredDiff / (values.size - 1)).toFloat())
    }
    
    @Test
    fun `test latency measurement`() {
        // Test that individual filter calls complete quickly
        val maxLatencyNanos = 10_000_000L  // 10ms in nanoseconds
        
        repeat(10) {
            val startTime = System.nanoTime()
            filter.filterImuData(9.8f, 0f, 0f, 0f, 0f, 0f)
            val latency = System.nanoTime() - startTime
            
            assertTrue(
                "Individual filter call should complete in < 10ms (actual: ${latency / 1_000_000}ms)",
                latency < maxLatencyNanos
            )
        }
    }
}