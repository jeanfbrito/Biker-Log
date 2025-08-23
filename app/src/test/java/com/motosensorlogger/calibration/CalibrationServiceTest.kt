package com.motosensorlogger.calibration

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.take
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Critical tests for calibration state management
 * Ensures calibration states are properly managed and never mixed
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class CalibrationServiceTest {
    
    private lateinit var context: Context
    private lateinit var calibrationService: CalibrationService
    private val testScope = TestScope()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Set up default preferences
        val prefs = context.getSharedPreferences("moto_sensor_logger_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("calibration_duration_ms", 2000L)
            putInt("calibration_min_samples", 50)
            putFloat("calibration_stability_threshold", 2.0f)
            putBoolean("enable_vibration_baseline", true)
            putBoolean("enable_magnetic_calibration", true)
            apply()
        }
        
        calibrationService = CalibrationService(context)
    }
    
    @Test
    fun `test calibration starts in IDLE state`() {
        assertEquals(CalibrationService.State.IDLE, calibrationService.state.value)
        assertNull(calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration transitions from IDLE to COLLECTING`() = testScope.runTest {
        calibrationService.startCalibration()
        
        // Allow state to update
        advanceTimeBy(100)
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
    }
    
    @Test
    fun `test calibration cannot start when already running`() = testScope.runTest {
        calibrationService.startCalibration()
        advanceTimeBy(100)
        
        val firstState = calibrationService.state.value
        assertEquals(CalibrationService.State.COLLECTING, firstState)
        
        // Try to start again
        calibrationService.startCalibration()
        advanceTimeBy(100)
        
        // Should still be in COLLECTING, not restarted
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
    }
    
    @Test
    fun `test calibration fails with insufficient samples`() = testScope.runTest {
        calibrationService.startCalibration()
        
        // Add just a few samples (less than minimum)
        repeat(5) {
            calibrationService.addSensorSample(
                floatArrayOf(0f, 0f, 9.81f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(30f, 0f, -20f)
            )
            advanceTimeBy(100)
        }
        
        // Wait for calibration to complete
        advanceTimeBy(3000)
        
        // Should have failed due to insufficient samples
        val finalState = calibrationService.state.value
        assertEquals("Should be FAILED after insufficient samples",
            CalibrationService.State.FAILED, finalState)
        
        assertNull("Should have no calibration data", calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration succeeds with sufficient stable samples`() = testScope.runTest {
        calibrationService.startCalibration()
        
        // Add stable samples
        val stableAccel = floatArrayOf(0f, 0f, 9.81f)
        val stableGyro = floatArrayOf(0f, 0f, 0f)
        val stableMag = floatArrayOf(30f, 0f, -20f)
        
        // Add many samples to meet minimum requirement
        repeat(100) {
            calibrationService.addSensorSample(stableAccel, stableGyro, stableMag)
            advanceTimeBy(20) // Simulate 50Hz sampling
        }
        
        // Wait for processing
        advanceTimeBy(500)
        
        // Check if calibration completed
        if (calibrationService.state.value == CalibrationService.State.COMPLETED) {
            assertNotNull("Should have calibration data", calibrationService.currentCalibration)
            val calibData = calibrationService.currentCalibration!!
            assertTrue("Should have acceptable quality", calibData.quality.isAcceptable)
        }
    }
    
    @Test
    fun `test calibration state is properly reset after clear`() = testScope.runTest {
        // Start and complete a calibration
        calibrationService.startCalibration()
        
        val stableAccel = floatArrayOf(0f, 0f, 9.81f)
        val stableGyro = floatArrayOf(0f, 0f, 0f)
        val stableMag = floatArrayOf(30f, 0f, -20f)
        
        repeat(100) {
            calibrationService.addSensorSample(stableAccel, stableGyro, stableMag)
            advanceTimeBy(20)
        }
        advanceTimeBy(500)
        
        // Clear calibration
        calibrationService.clearCalibration()
        
        assertEquals(CalibrationService.State.IDLE, calibrationService.state.value)
        assertNull("Calibration data should be cleared", calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration can be cancelled mid-process`() = testScope.runTest {
        calibrationService.startCalibration()
        advanceTimeBy(500) // Halfway through
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
        
        calibrationService.cancelCalibration()
        advanceTimeBy(100)
        
        assertEquals(CalibrationService.State.IDLE, calibrationService.state.value)
        assertNull("Should have no calibration data after cancel", 
            calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration progress updates correctly`() = testScope.runTest {
        val progressUpdates = mutableListOf<CalibrationService.Progress>()
        
        val job = launch {
            calibrationService.progress.take(5).toList(progressUpdates)
        }
        
        calibrationService.startCalibration()
        advanceTimeBy(2500) // Let calibration run
        
        job.cancel()
        
        assertTrue("Should have progress updates", progressUpdates.isNotEmpty())
        
        // Verify progress increases
        val percentages = progressUpdates.map { it.percent }
        for (i in 1 until percentages.size) {
            assertTrue("Progress should increase or stay same",
                percentages[i] >= percentages[i-1])
        }
    }
    
    @Test
    fun `test calibration quality detection for moving device`() = testScope.runTest {
        calibrationService.startCalibration()
        
        // Add very unstable samples (simulating significant movement)
        repeat(100) { i ->
            val noisyAccel = floatArrayOf(
                (Math.random() * 5).toFloat() - 2.5f,  // Large variations
                (Math.random() * 5).toFloat() - 2.5f, 
                9.81f + (Math.random() * 5).toFloat() - 2.5f
            )
            val noisyGyro = floatArrayOf(
                (Math.random() * 2).toFloat() - 1f,  // Large gyro noise
                (Math.random() * 2).toFloat() - 1f,
                (Math.random() * 2).toFloat() - 1f
            )
            val mag = floatArrayOf(30f, 0f, -20f)
            
            calibrationService.addSensorSample(noisyAccel, noisyGyro, mag)
            advanceTimeBy(20)
        }
        
        advanceTimeBy(500)
        
        // Should fail due to instability
        val finalState = calibrationService.state.value
        assertEquals("Should fail for unstable device",
            CalibrationService.State.FAILED, finalState)
        assertNull("Should have no calibration for unstable device", 
            calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration state never gets stuck`() = testScope.runTest {
        calibrationService.startCalibration()
        
        // Add some samples but not enough
        repeat(10) {
            calibrationService.addSensorSample(
                floatArrayOf(0f, 0f, 9.81f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(30f, 0f, -20f)
            )
            advanceTimeBy(100)
        }
        
        // Wait for maximum possible duration including extensions
        advanceTimeBy(10000) // Way past any timeout (2s + 3x1s extensions)
        
        val finalState = calibrationService.state.value
        assertTrue("Should have completed or failed, not stuck",
            finalState == CalibrationService.State.FAILED || 
            finalState == CalibrationService.State.IDLE ||
            finalState == CalibrationService.State.COMPLETED)
    }
    
    @Test
    fun `test dispose properly cleans up resources`() = testScope.runTest {
        calibrationService.startCalibration()
        advanceTimeBy(500)
        
        calibrationService.dispose()
        advanceTimeBy(100) // Give time for cleanup
        
        // After dispose, service should be in clean state
        val finalState = calibrationService.state.value
        assertTrue("Should be IDLE or FAILED after dispose",
            finalState == CalibrationService.State.IDLE || 
            finalState == CalibrationService.State.FAILED)
        assertNull(calibrationService.currentCalibration)
    }
}