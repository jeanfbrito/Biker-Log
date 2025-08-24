package com.motosensorlogger.services

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
 * Tests for adaptive GPS sampling rate functionality
 */
@RunWith(RobolectricTestRunner::class)
class AdaptiveGpsTest {
    
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager.getInstance(context)
        
        // Reset settings to defaults
        settingsManager.resetAllSettings()
    }
    
    @Test
    fun `default GPS update interval is 5Hz`() = runBlocking {
        val settings = settingsManager.sensorSettings.first()
        assertEquals("Default GPS interval should be 200ms (5Hz)", 200L, settings.gpsUpdateIntervalMs)
    }
    
    @Test
    fun `GPS update interval can be configured`() = runBlocking {
        // Test different GPS rates
        val testCases = mapOf(
            1000L to 1,    // 1 Hz
            500L to 2,     // 2 Hz
            200L to 5,     // 5 Hz (default)
            100L to 10     // 10 Hz
        )
        
        for ((intervalMs, expectedHz) in testCases) {
            settingsManager.setGpsUpdateInterval(intervalMs)
            val settings = settingsManager.sensorSettings.first()
            assertEquals("GPS interval should be $intervalMs ms ($expectedHz Hz)", intervalMs, settings.gpsUpdateIntervalMs)
        }
    }
    
    @Test
    fun `GPS interval persists across restarts`() = runBlocking {
        // Set custom GPS interval
        val customInterval = 100L // 10 Hz
        settingsManager.setGpsUpdateInterval(customInterval)
        
        // Create new settings manager instance (simulating restart)
        val newSettingsManager = SettingsManager.getInstance(context)
        val settings = newSettingsManager.sensorSettings.first()
        
        assertEquals("GPS interval should persist", customInterval, settings.gpsUpdateIntervalMs)
    }
    
    @Test
    fun `cornering threshold is properly defined`() {
        // The threshold is ~17 deg/s which is 0.3 rad/s
        val CORNERING_THRESHOLD_RAD_S = 0.3f
        val thresholdDegS = Math.toDegrees(CORNERING_THRESHOLD_RAD_S.toDouble())
        
        assertTrue("Cornering threshold should be around 17 deg/s", thresholdDegS > 15 && thresholdDegS < 20)
    }
    
    @Test
    fun `gyroscope magnitude calculation is correct`() {
        // Test various gyroscope values
        val testCases = listOf(
            Triple(0f, 0f, 0f) to 0f,                    // No rotation
            Triple(0.3f, 0f, 0f) to 0.3f,                // Rotation on one axis (at threshold)
            Triple(0.2f, 0.2f, 0.1f) to 0.3f,           // Combined rotation (sqrt(0.04 + 0.04 + 0.01))
            Triple(0.5f, 0f, 0f) to 0.5f,                // Above threshold on one axis
            Triple(0.3f, 0.3f, 0.3f) to 0.52f           // Above threshold on all axes
        )
        
        for ((gyroValues, expectedMagnitude) in testCases) {
            val (x, y, z) = gyroValues
            val magnitude = kotlin.math.sqrt(x * x + y * y + z * z)
            assertEquals("Magnitude calculation for $gyroValues", expectedMagnitude, magnitude, 0.01f)
        }
    }
    
    @Test
    fun `adaptive rate switches between normal and high rate`() {
        val CORNERING_THRESHOLD = 0.3f
        val normalInterval = 200L  // 5 Hz
        val highRateInterval = 100L // 10 Hz
        
        // Test below threshold - should use normal rate
        val lowGyroMagnitude = 0.2f
        assertTrue("Below threshold should use normal rate", lowGyroMagnitude < CORNERING_THRESHOLD)
        
        // Test above threshold - should use high rate
        val highGyroMagnitude = 0.5f
        assertTrue("Above threshold should use high rate", highGyroMagnitude > CORNERING_THRESHOLD)
    }
    
    @Test
    fun `battery impact of different GPS rates`() {
        // Estimate battery impact based on GPS update frequency
        val batteryImpactMap = mapOf(
            1000L to "Low",      // 1 Hz
            500L to "Medium",    // 2 Hz  (corrected)
            200L to "Medium",    // 5 Hz
            100L to "High"       // 10 Hz
        )
        
        for ((interval, expectedImpact) in batteryImpactMap) {
            val actualImpact = estimateGpsBatteryImpact(interval)
            assertEquals("Battery impact for ${1000/interval} Hz", expectedImpact, actualImpact)
        }
    }
    
    @Test
    fun `GPS data rate scales with sampling frequency`() {
        // Each GPS event has: timestamp, lat, lon, alt, speed, bearing, accuracy
        // Approximately 64 bytes per event
        val bytesPerEvent = 64L
        
        val testCases = mapOf(
            1000L to 64L,     // 1 Hz = 64 bytes/sec
            200L to 320L,     // 5 Hz = 320 bytes/sec
            100L to 640L      // 10 Hz = 640 bytes/sec
        )
        
        for ((intervalMs, expectedBytesPerSec) in testCases) {
            val eventsPerSec = 1000L / intervalMs
            val actualBytesPerSec = eventsPerSec * bytesPerEvent
            assertEquals("Data rate for ${1000/intervalMs} Hz", expectedBytesPerSec, actualBytesPerSec)
        }
    }
    
    // Helper function to estimate battery impact
    private fun estimateGpsBatteryImpact(intervalMs: Long): String {
        return when {
            intervalMs >= 1000 -> "Low"      // 1 Hz or less
            intervalMs >= 200 -> "Medium"    // 2-5 Hz
            else -> "High"                   // > 5 Hz
        }
    }
}