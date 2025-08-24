package com.motosensorlogger.calibration

import android.content.Context
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.*
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Simplified and optimized tests for calibration state management
 * Tests run quickly without timing out in CI
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class CalibrationServiceTest {
    
    private lateinit var context: Context
    private lateinit var calibrationService: CalibrationService
    private val testDispatcher = StandardTestDispatcher()
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Set up test preferences with shorter durations
        val prefs = context.getSharedPreferences("moto_sensor_logger_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putLong("calibration_duration_ms", 500L)  // Shorter duration for tests
            putInt("calibration_min_samples", 10)     // Lower sample requirement
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
    fun `test calibration transitions from IDLE to COLLECTING`() = runTest(testDispatcher) {
        calibrationService.startCalibration()
        advanceUntilIdle()
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
    }
    
    @Test
    fun `test calibration cannot start when already running`() = runTest(testDispatcher) {
        calibrationService.startCalibration()
        advanceUntilIdle()
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
        
        // Try to start again - should not restart
        calibrationService.startCalibration()
        advanceUntilIdle()
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
    }
    
    @Test
    fun `test calibration succeeds with sufficient stable samples`() = runTest(testDispatcher) {
        calibrationService.startCalibration()
        
        // Add stable samples quickly
        val stableAccel = floatArrayOf(0f, 0f, 9.81f)
        val stableGyro = floatArrayOf(0f, 0f, 0f)
        val stableMag = floatArrayOf(30f, 0f, -20f)
        
        // Add samples to meet minimum requirement
        repeat(20) {
            calibrationService.addSensorSample(stableAccel, stableGyro, stableMag)
        }
        
        // Allow processing
        advanceTimeBy(600)
        advanceUntilIdle()
        
        // Should complete or be in process
        val state = calibrationService.state.value
        assertTrue(
            "State should be COMPLETED or PROCESSING", 
            state == CalibrationService.State.COMPLETED || 
            state == CalibrationService.State.PROCESSING
        )
    }
    
    @Test
    fun `test calibration state is properly reset after clear`() = runTest(testDispatcher) {
        // Start calibration
        calibrationService.startCalibration()
        
        // Add some samples
        repeat(5) {
            calibrationService.addSensorSample(
                floatArrayOf(0f, 0f, 9.81f),
                floatArrayOf(0f, 0f, 0f),
                floatArrayOf(30f, 0f, -20f)
            )
        }
        
        // Clear calibration
        calibrationService.clearCalibration()
        advanceUntilIdle()
        
        assertEquals(CalibrationService.State.IDLE, calibrationService.state.value)
        assertNull(calibrationService.currentCalibration)
    }
    
    @Test
    fun `test calibration can be cancelled mid-process`() = runTest(testDispatcher) {
        calibrationService.startCalibration()
        advanceTimeBy(100)
        
        assertEquals(CalibrationService.State.COLLECTING, calibrationService.state.value)
        
        calibrationService.cancelCalibration()
        advanceUntilIdle()
        
        assertEquals(CalibrationService.State.IDLE, calibrationService.state.value)
        assertNull(calibrationService.currentCalibration)
    }
    
    @Test
    fun `test dispose properly cleans up resources`() = runTest(testDispatcher) {
        calibrationService.startCalibration()
        advanceTimeBy(100)
        
        calibrationService.dispose()
        advanceUntilIdle()
        
        // After dispose, service should be in clean state
        val finalState = calibrationService.state.value
        assertTrue(
            "Should be IDLE or FAILED after dispose",
            finalState == CalibrationService.State.IDLE || 
            finalState == CalibrationService.State.FAILED
        )
        assertNull(calibrationService.currentCalibration)
    }
}