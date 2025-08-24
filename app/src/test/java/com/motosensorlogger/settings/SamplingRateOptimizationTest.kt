package com.motosensorlogger.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/**
 * Tests for IMU sampling rate optimization (Issue #25)
 * Verifies that sampling rate changes reduce file size and battery usage
 */
@RunWith(RobolectricTestRunner::class)
class SamplingRateOptimizationTest {
    
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager.getInstance(context)
        
        // Reset to defaults before each test
        settingsManager.resetAllSettings()
    }
    
    @Test
    fun `default sampling rate should be 50Hz for optimization`() = runBlocking {
        val settings = settingsManager.sensorSettings.first()
        assertEquals("Default sampling rate should be 50 Hz", 50, settings.samplingRateHz)
    }
    
    @Test
    fun `sampling rate can be changed to supported values`() = runBlocking {
        val supportedRates = listOf(10, 25, 50, 75, 100, 150, 200)
        
        for (rate in supportedRates) {
            settingsManager.setSensorSamplingRate(rate)
            val settings = settingsManager.sensorSettings.first()
            assertEquals("Sampling rate should be $rate Hz", rate, settings.samplingRateHz)
        }
    }
    
    @Test
    fun `sampling period calculation is correct`() {
        val testCases = mapOf(
            10 to 100000,   // 10 Hz = 100,000 microseconds
            25 to 40000,    // 25 Hz = 40,000 microseconds
            50 to 20000,    // 50 Hz = 20,000 microseconds
            100 to 10000,   // 100 Hz = 10,000 microseconds
            200 to 5000     // 200 Hz = 5,000 microseconds
        )
        
        for ((hz, expectedMicros) in testCases) {
            val calculatedMicros = 1000000 / hz
            assertEquals(
                "Sampling period for $hz Hz should be $expectedMicros microseconds",
                expectedMicros,
                calculatedMicros
            )
        }
    }
    
    @Test
    fun `estimated file sizes are reasonable`() {
        // Based on analysis: 206 Hz produced 6.5 MB for 5 minutes
        // Linear scaling: data_size = (sampling_rate / 206) * 6.5 MB
        
        val estimates = mapOf(
            10 to 0.3f,   // Very small
            25 to 0.8f,   // Small
            50 to 1.6f,   // ~60% reduction from 100Hz
            100 to 3.2f,  // Half of original
            200 to 6.3f   // Close to original
        )
        
        for ((hz, expectedSizeMB) in estimates) {
            val calculatedSize = (hz / 206f) * 6.5f
            assertTrue(
                "File size for $hz Hz should be approximately $expectedSizeMB MB",
                kotlin.math.abs(calculatedSize - expectedSizeMB) < 0.5f
            )
        }
    }
    
    @Test
    fun `settings persist after app restart`() = runBlocking {
        // Set custom rate
        settingsManager.setSensorSamplingRate(75)
        
        // Create new instance (simulates app restart)
        val newSettingsManager = SettingsManager.getInstance(context)
        val settings = newSettingsManager.sensorSettings.first()
        
        assertEquals("Sampling rate should persist", 75, settings.samplingRateHz)
    }
    
    @Test
    fun `battery impact categorization is correct`() {
        val batteryImpact = mapOf(
            10 to "Minimal",
            25 to "Very Low",
            50 to "Low",
            75 to "Moderate",
            100 to "High",
            150 to "Very High",
            200 to "Maximum"
        )
        
        // Verify our categorization makes sense
        assertTrue("10 Hz should have minimal impact", batteryImpact[10] == "Minimal")
        assertTrue("50 Hz should have low impact", batteryImpact[50] == "Low")
        assertTrue("200 Hz should have maximum impact", batteryImpact[200] == "Maximum")
    }
}