package com.motosensorlogger.services

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import androidx.test.core.app.ApplicationProvider
import com.motosensorlogger.data.SensorDataFilter
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Integration tests for sensor filtering within the SensorLoggerService context
 * Tests the interaction between the filter and service components
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SensorFilterIntegrationTest {
    
    private lateinit var context: Context
    private lateinit var filter: SensorDataFilter
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        filter = SensorDataFilter(
            windowSize = 5,
            outlierSigmaThreshold = 3.0f,
            enableOutlierDetection = true,
            enableMovingAverage = true
        )
    }
    
    @Test
    fun `test filter initialization with service parameters`() {
        // Test that filter initializes with production parameters
        val productionFilter = SensorDataFilter(
            windowSize = 5,  // Production window size
            outlierSigmaThreshold = 3.0f,  // 3-sigma rule
            enableOutlierDetection = true,
            enableMovingAverage = true
        )
        
        assertNotNull("Production filter should initialize", productionFilter)
        
        val stats = productionFilter.getFilterStats()
        assertEquals("Buffer size should match window", 5, stats.bufferSize)
        assertEquals("Should start with no samples", 0, stats.currentSamples)
    }
    
    @Test
    fun `test typical motorcycle sensor data patterns`() {
        // Simulate typical motorcycle sensor patterns
        val motorcycleAccelPatterns = listOf(
            // Straight line riding (gravity + small vibrations)
            Triple(0.2f, 0.1f, 9.8f),
            Triple(-0.1f, 0.3f, 9.7f),
            Triple(0.3f, -0.2f, 9.9f),
            
            // Cornering (lateral acceleration)
            Triple(3.5f, 0.2f, 9.2f),
            Triple(3.8f, 0.1f, 9.1f),
            Triple(3.2f, 0.3f, 9.3f),
            
            // Braking (forward acceleration)
            Triple(-1.2f, 0.1f, 8.9f),
            Triple(-2.1f, 0.2f, 8.7f),
            Triple(-1.8f, 0.0f, 8.8f),
        )
        
        val gyroPatterns = listOf(
            // Straight (minimal rotation)
            Triple(0.05f, 0.02f, 0.01f),
            Triple(-0.03f, 0.04f, -0.02f),
            Triple(0.01f, -0.01f, 0.03f),
            
            // Cornering (yaw rotation)
            Triple(0.1f, 0.05f, 0.8f),
            Triple(0.2f, 0.03f, 1.2f),
            Triple(0.0f, 0.04f, 0.9f),
            
            // Rough road (pitch/roll oscillations)
            Triple(0.3f, 0.4f, 0.1f),
            Triple(-0.2f, -0.3f, 0.05f),
            Triple(0.1f, 0.2f, 0.02f),
        )
        
        val results = mutableListOf<SensorDataFilter.FilteredImuData>()
        
        // Process all patterns
        for (i in motorcycleAccelPatterns.indices) {
            val accel = motorcycleAccelPatterns[i]
            val gyro = gyroPatterns[i]
            
            val result = filter.filterImuData(
                accel.first, accel.second, accel.third,
                gyro.first, gyro.second, gyro.third
            )
            results.add(result)
        }
        
        // Verify all processing completed successfully
        assertEquals("All patterns should be processed", motorcycleAccelPatterns.size, results.size)
        
        // Results should be reasonable (no NaN, infinite values)
        assertTrue("All results should be finite", results.all { 
            it.accelX.isFinite() && it.accelY.isFinite() && it.accelZ.isFinite() &&
            it.gyroX.isFinite() && it.gyroY.isFinite() && it.gyroZ.isFinite()
        })
        
        // Cornering data should show higher lateral acceleration
        val corneringResults = results.subList(3, 6)
        val avgCorneringLatAccel = corneringResults.map { kotlin.math.abs(it.accelX) }.average()
        assertTrue("Cornering should show significant lateral acceleration", avgCorneringLatAccel > 1.0)
    }
    
    @Test
    fun `test sensor noise patterns common in motorcycles`() {
        // Motorcycle-specific noise patterns
        val baseAccelZ = 9.8f
        val engineVibrationNoise = 0.1f  // Typical engine vibration
        val roadNoise = 0.3f  // Road surface irregularities
        
        val noisyReadings = mutableListOf<Triple<Float, Float, Float>>()
        
        // Generate realistic noisy readings
        for (i in 0 until 20) {
            val engineNoise = (Math.random().toFloat() - 0.5f) * 2 * engineVibrationNoise
            val roadBump = if (i % 7 == 0) roadNoise else 0f  // Occasional road bumps
            
            noisyReadings.add(Triple(
                engineNoise * 0.5f,  // X-axis engine vibration
                engineNoise * 0.3f,  // Y-axis engine vibration  
                baseAccelZ + engineNoise + roadBump  // Z-axis with gravity + noise
            ))
        }
        
        var lastFiltered: SensorDataFilter.FilteredImuData? = null
        
        // Process noisy readings
        for (reading in noisyReadings) {
            lastFiltered = filter.filterImuData(
                reading.first, reading.second, reading.third,
                0f, 0f, 0f
            )
        }
        
        assertNotNull("Should process all readings", lastFiltered)
        
        // Z-axis should be close to gravity after filtering
        val zAxisError = kotlin.math.abs(lastFiltered!!.accelZ - baseAccelZ)
        assertTrue(
            "Filtered Z-axis should be close to gravity (error: $zAxisError)",
            zAxisError < engineVibrationNoise
        )
    }
    
    @Test
    fun `test high frequency sensor data processing`() {
        // Simulate high-frequency sensor data (50Hz)
        val sampleCount = 500  // 10 seconds at 50Hz
        val startTime = System.nanoTime()
        
        var outlierCount = 0
        var filteredCount = 0
        
        for (i in 0 until sampleCount) {
            // Simulate varying data with occasional outliers
            val baseValue = 9.8f + 2f * kotlin.math.sin(i * 0.1f)  // Slow variation
            val noise = (Math.random().toFloat() - 0.5f) * 0.2f
            val outlier = if (Math.random() < 0.01) 50f else 0f  // 1% outlier rate
            
            val result = filter.filterImuData(
                baseValue + noise + outlier, 0f, 0f,
                0f, 0f, 0f
            )
            
            if (result.outlierFlags.hasOutliers()) outlierCount++
            if (result.wasFiltered) filteredCount++
        }
        
        val elapsedMs = (System.nanoTime() - startTime) / 1_000_000
        val averageLatencyMs = elapsedMs.toFloat() / sampleCount
        
        // Performance requirements
        assertTrue(
            "Average processing time should be < 1ms per sample (actual: ${averageLatencyMs}ms)",
            averageLatencyMs < 1.0f
        )
        
        // Should detect some outliers
        assertTrue("Should detect some outliers in noisy data", outlierCount > 0)
        
        // Most data should be filtered (due to moving average)
        assertTrue("Most samples should be filtered", filteredCount > sampleCount * 0.8)
    }
    
    @Test
    fun `test filter state consistency across service lifecycle`() {
        // Simulate service start -> logging -> stop -> restart cycle
        
        // Phase 1: Initial logging
        for (i in 0 until 10) {
            filter.filterImuData(9.8f, 0f, 0f, 0f, 0f, 0f)
        }
        
        val phase1Stats = filter.getFilterStats()
        assertTrue("Should have accumulated samples", phase1Stats.currentSamples > 0)
        
        // Phase 2: Service restart (filter reset)
        filter.reset()
        
        val resetStats = filter.getFilterStats()
        assertEquals("Should have no samples after reset", 0, resetStats.currentSamples)
        
        // Phase 3: Resume logging
        for (i in 0 until 5) {
            filter.filterImuData(10.2f, 0f, 0f, 0f, 0f, 0f)
        }
        
        val phase3Stats = filter.getFilterStats()
        assertEquals("Should have new samples", 5, phase3Stats.currentSamples)
        
        // Mean should reflect new data, not old data
        assertTrue("Mean should reflect new data range", 
            phase3Stats.accelMean.first > 10.0f && phase3Stats.accelMean.first < 10.5f)
    }
    
    @Test
    fun `test edge case handling in service context`() {
        // Test various edge cases that could occur in service
        
        // Case 1: Filter with extremely small values (sensor precision limits)
        var result = filter.filterImuData(0.001f, 0.0001f, 0.00001f, 0f, 0f, 0f)
        assertTrue("Should handle very small values", result.accelX >= 0f)
        
        // Case 2: Filter with large values (extreme maneuvers)
        result = filter.filterImuData(50f, -30f, 20f, 10f, -15f, 25f)
        assertTrue("Should handle large values", result.accelX.isFinite())
        
        // Case 3: Alternating extreme values (rapid changes)
        for (i in 0 until 10) {
            val sign = if (i % 2 == 0) 1f else -1f
            result = filter.filterImuData(sign * 20f, 0f, 0f, 0f, 0f, 0f)
        }
        assertTrue("Should handle rapid alternating values", result.accelX.isFinite())
        
        // Case 4: Sudden sensor disconnection/reconnection (zero values)
        for (i in 0 until 5) {
            result = filter.filterImuData(0f, 0f, 0f, 0f, 0f, 0f)
        }
        assertEquals("Should handle zero values correctly", 0f, result.accelX, 0.001f)
    }
    
    @Test
    fun `test production configuration effectiveness`() {
        // Test the actual production configuration values
        val productionFilter = SensorDataFilter(
            windowSize = 5,  // As configured in service
            outlierSigmaThreshold = 3.0f,
            enableOutlierDetection = true,
            enableMovingAverage = true
        )
        
        // Simulate real motorcycle data collection scenario
        val testDurationSamples = 250  // 5 seconds at 50Hz
        var totalOutliers = 0
        val results = mutableListOf<Float>()
        
        for (i in 0 until testDurationSamples) {
            // Realistic motorcycle acceleration pattern
            val timeS = i / 50.0  // Time in seconds
            val baseAccel = 9.8f + 2f * kotlin.math.sin((timeS * 0.5).toFloat())  // Slow body roll
            val vibration = 0.1f * kotlin.math.sin((timeS * 20).toFloat())  // Engine vibration
            val noise = (Math.random().toFloat() - 0.5f) * 0.05f  // Sensor noise
            val outlier = if (Math.random() < 0.005) 20f else 0f  // 0.5% outlier rate
            
            val input = baseAccel + vibration + noise + outlier
            val result = productionFilter.filterImuData(input, 0f, 0f, 0f, 0f, 0f)
            
            if (result.outlierFlags.accelX) totalOutliers++
            results.add(result.accelX)
        }
        
        // Production filter should:
        // 1. Detect outliers effectively
        assertTrue("Should detect outliers", totalOutliers > 0)
        
        // 2. Produce smooth output
        val outputVariance = calculateVariance(results.drop(10))  // Skip initial samples
        assertTrue("Output should have low variance", outputVariance < 0.5f)
        
        // 3. Preserve signal characteristics (mean should be close to base)
        val outputMean = results.drop(10).average().toFloat()
        assertTrue("Output mean should be reasonable", outputMean > 9.0f && outputMean < 11.0f)
    }
    
    private fun calculateVariance(values: List<Float>): Float {
        if (values.size <= 1) return 0f
        
        val mean = values.average().toFloat()
        val sumSquaredDiff = values.map { (it - mean) * (it - mean) }.sum().toDouble()
        return (sumSquaredDiff / values.size).toFloat()
    }
}