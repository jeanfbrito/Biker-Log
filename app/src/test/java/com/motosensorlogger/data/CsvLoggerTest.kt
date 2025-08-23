package com.motosensorlogger.data

import android.content.Context
import android.os.Environment
import com.motosensorlogger.calibration.CalibrationData
import com.motosensorlogger.calibration.CalibrationQuality
import org.junit.Assert.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import androidx.test.core.app.ApplicationProvider
import android.os.Build
import java.io.File
import java.io.BufferedReader
import java.io.FileReader
import kotlinx.coroutines.*
import kotlinx.coroutines.delay

/**
 * Comprehensive tests for CSV logging - the soul of our application
 * These tests ensure we NEVER corrupt CSV headers or mix calibration states
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [Build.VERSION_CODES.O_MR1])
class CsvLoggerTest {
    
    private lateinit var context: Context
    private lateinit var csvLogger: CsvLogger
    private lateinit var testDir: File
    
    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Setup test directory
        testDir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "MotoSensorLogs")
        testDir.mkdirs()
        
        csvLogger = CsvLogger(context)
    }
    
    @Test
    fun `test CSV header with successful calibration contains all required sections`() {
        // Create valid calibration data
        val calibrationData = createValidCalibrationData()
        val calibrationHeader = calibrationData.toCsvHeader()
        
        // Start logging with calibration
        val success = csvLogger.startLogging(calibrationHeader)
        assertTrue("CSV logging should start successfully", success)
        
        // Read the generated file
        val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
        assertNotNull("Log file should be created", logFiles)
        assertEquals("Should have exactly one log file", 1, logFiles?.size)
        
        val content = logFiles!![0].readText()
        
        // Verify header structure
        assertTrue("Should contain Moto Sensor Log header", content.contains("# Moto Sensor Log v1.1"))
        assertTrue("Should contain Device info", content.contains("# Device:"))
        assertTrue("Should contain Date info", content.contains("# Date:"))
        assertTrue("Should contain Calibration section", content.contains("# Calibration:"))
        assertTrue("Should contain format_version", content.contains("format_version"))
        assertTrue("Should contain reference pitch", content.contains("pitch"))
        assertTrue("Should contain reference roll", content.contains("roll"))
        assertTrue("Should contain Schema section", content.contains("# Schema:"))
        assertTrue("Should contain GPS event definition", content.contains("GPS"))
        assertTrue("Should contain IMU event definition", content.contains("IMU"))
        assertTrue("Should contain CSV columns", content.contains("timestamp,sensor_type,data1"))
        
        // Verify NO duplicate headers
        val headerCount = content.split("# Moto Sensor Log").size - 1
        assertEquals("Should have exactly ONE header", 1, headerCount)
        
        // Verify NO mixed calibration states
        assertFalse("Should NOT contain 'uncalibrated' when calibrated", 
            content.contains("uncalibrated"))
        assertFalse("Should NOT contain 'not_calibrated' when calibrated", 
            content.contains("not_calibrated"))
    }
    
    @Test
    fun `test CSV header without calibration contains uncalibrated status`() = runBlocking {
        // Start logging without calibration
        val noCalibrationHeader = """
# Calibration: {
#   "status": "not_calibrated",
#   "reason": "User skipped calibration or calibration failed",
#   "timestamp": ${System.currentTimeMillis()},
#   "note": "Raw sensor data without calibration reference"
# }""".trimIndent()
        
        val success = csvLogger.startLogging(noCalibrationHeader)
        assertTrue("CSV logging should start successfully", success)
        
        // Give time for file to be written
        delay(200)
        csvLogger.stopLogging()
        delay(200)
        
        // Read the generated file
        val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
        assertNotNull("Should have created log files", logFiles)
        assertTrue("Should have at least one log file", logFiles!!.isNotEmpty())
        
        val content = logFiles[0].readText()
        
        // Verify header structure - the status should be preserved as passed
        assertTrue("Should contain not_calibrated status: ${content.take(500)}", 
            content.contains("not_calibrated"))
        assertTrue("Should contain Schema section", content.contains("# Schema:"))
        assertTrue("Should contain all sensor definitions", content.contains("GPS") && 
                   content.contains("IMU") && content.contains("BARO") && content.contains("MAG"))
        
        // Verify NO calibration data when uncalibrated
        assertFalse("Should NOT contain reference pitch when uncalibrated", 
            content.contains("referencePitch"))
        assertFalse("Should NOT contain reference roll when uncalibrated", 
            content.contains("referenceRoll"))
        
        // Verify NO duplicate headers
        val headerCount = content.split("# Moto Sensor Log").size - 1
        assertEquals("Should have exactly ONE header", 1, headerCount)
    }
    
    @Test
    fun `test empty calibration header defaults to uncalibrated`() {
        // Start logging with empty calibration header
        val success = csvLogger.startLogging("")
        assertTrue("CSV logging should start successfully", success)
        
        val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
        val content = logFiles!![0].readText()
        
        // Should default to uncalibrated
        assertTrue("Should contain uncalibrated status", content.contains("uncalibrated"))
        assertTrue("Should contain warning about no calibration", 
            content.contains("No calibration performed"))
    }
    
    @Test
    fun `test header is never duplicated even with multiple write attempts`() = runBlocking {
        val calibrationHeader = createValidCalibrationData().toCsvHeader()
        
        // Start logging
        csvLogger.startLogging(calibrationHeader)
        
        // Try to write events (should not duplicate header)
        val testEvent = ImuEvent(
            System.currentTimeMillis(),
            1.0f, 2.0f, 3.0f,
            0.1f, 0.2f, 0.3f
        )
        
        csvLogger.logEvent(testEvent)
        csvLogger.logEvent(testEvent)
        
        delay(300) // Wait for async writes
        csvLogger.stopLogging()
        delay(200)
        
        val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
        assertNotNull("Should have created log files", logFiles)
        assertTrue("Should have at least one log file", logFiles!!.isNotEmpty())
        
        val content = logFiles[0].readText()
        
        // Count occurrences of header markers
        val headerCount = content.split("# Moto Sensor Log").size - 1
        val schemaCount = content.split("# Schema:").size - 1
        val calibrationCount = content.split("# Calibration:").size - 1
        
        assertEquals("Should have exactly ONE main header", 1, headerCount)
        assertEquals("Should have exactly ONE schema section", 1, schemaCount)
        assertEquals("Should have exactly ONE calibration section", 1, calibrationCount)
    }
    
    @Test
    fun `test calibration data validation prevents corrupt headers`() {
        // Test with various invalid calibration data
        val invalidCalibrations = listOf(
            null,
            "", 
            "invalid calibration data",
            "# Calibration: { corrupt",
            "# Calibration: { \"status\": null }"
        )
        
        for ((index, invalidHeader) in invalidCalibrations.withIndex()) {
            // Clean up previous test files
            testDir.listFiles()?.forEach { it.delete() }
            
            // Try to start logging with invalid calibration
            val success = csvLogger.startLogging(invalidHeader ?: "")
            assertTrue("Should handle invalid calibration $index", success)
            
            val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
            if (logFiles != null && logFiles.isNotEmpty()) {
                val content = logFiles[0].readText()
                
                // Should always have valid structure
                assertTrue("Should have valid header structure", 
                    content.contains("# Moto Sensor Log"))
                assertTrue("Should have Schema section", 
                    content.contains("# Schema:"))
                
                // Should never have mixed states
                val hasCalibrated = content.contains("referencePitch") || 
                                   content.contains("referenceRoll")
                val hasUncalibrated = content.contains("uncalibrated") || 
                                     content.contains("not_calibrated")
                
                assertFalse("Should not have both calibrated and uncalibrated states",
                    hasCalibrated && hasUncalibrated)
            }
        }
    }
    
    @Test
    fun `test sensor event logging preserves data integrity`() {
        csvLogger.startLogging("")
        
        // Log various sensor events
        val events = listOf(
            ImuEvent(1000L, 1.0f, 2.0f, 3.0f, 0.1f, 0.2f, 0.3f),
            GpsEvent(2000L, 37.7749, -122.4194, 100.0, 10.5f, 45.0f, 5.0f),
            BaroEvent(3000L, 150.5f, 1013.25f),
            MagEvent(4000L, 30.0f, -20.0f, 40.0f)
        )
        
        events.forEach { csvLogger.logEvent(it) }
        
        runBlocking {
            csvLogger.stopLogging()
            delay(500) // Wait for file write
        }
        
        val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
        val lines = logFiles!![0].readLines()
        
        // Find where data starts (after header)
        val dataStartIndex = lines.indexOfFirst { 
            it.startsWith("timestamp,sensor_type") 
        } + 1
        
        assertTrue("Should have logged events", lines.size > dataStartIndex)
        
        // Verify each event type
        val dataLines = lines.drop(dataStartIndex).filter { it.isNotBlank() }
        assertTrue("Should have IMU event", dataLines.any { it.contains(",IMU,") })
        assertTrue("Should have GPS event", dataLines.any { it.contains(",GPS,") })
        assertTrue("Should have BARO event", dataLines.any { it.contains(",BARO,") })
        assertTrue("Should have MAG event", dataLines.any { it.contains(",MAG,") })
    }
    
    @Test
    fun `test concurrent logging does not corrupt headers`() {
        runBlocking {
            val calibrationHeader = createValidCalibrationData().toCsvHeader()
            
            // Start multiple logging sessions concurrently
            val jobs = List(5) { index ->
                launch {
                    delay(index * 10L) // Stagger starts slightly
                    csvLogger.startLogging(calibrationHeader)
                }
            }
            
            jobs.forEach { it.join() }
            
            val logFiles = testDir.listFiles { _, name -> name.endsWith(".csv") }
            
            // Check each file for header integrity
            logFiles?.forEach { file ->
                val content = file.readText()
                
                // Each file should have exactly one header
                val headerCount = content.split("# Moto Sensor Log").size - 1
                assertEquals("File ${file.name} should have exactly ONE header", 
                    1, headerCount)
                
                // No mixed calibration states
                val hasCalibrated = content.contains("referencePitch")
                val hasUncalibrated = content.contains("uncalibrated")
                assertFalse("File ${file.name} should not have mixed states",
                    hasCalibrated && hasUncalibrated)
            }
        }
    }
    
    private fun createValidCalibrationData(): CalibrationData {
        return CalibrationData(
            referenceGravity = floatArrayOf(0f, 0f, 9.81f),
            referenceMagnetic = floatArrayOf(30f, 0f, -20f),
            referenceRotationMatrix = FloatArray(9) { it.toFloat() },
            referenceQuaternion = floatArrayOf(1f, 0f, 0f, 0f),
            referencePitch = 15.5f,
            referenceRoll = -5.2f,
            referenceAzimuth = 180f,
            gyroscopeBias = floatArrayOf(0.01f, 0.02f, 0.01f),
            timestamp = System.currentTimeMillis(),
            duration = 2000L,
            sampleCount = 200,
            quality = CalibrationQuality(
                overallScore = 85f,
                stabilityScore = 90f,
                magneticFieldQuality = 80f,
                gravityConsistency = 85f,
                isAcceptable = true
            )
        )
    }
    
    @After
    fun cleanup() {
        // Clean up test files
        testDir.listFiles()?.forEach { it.delete() }
        testDir.delete()
    }
}