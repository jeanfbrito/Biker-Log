package com.motosensorlogger.data

import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.Assert.*
import java.io.File
import java.io.FileWriter

class RideDataProcessorTest {

    private val processor = RideDataProcessor()

    @Test
    fun `test empty CSV file processing`() = runBlocking {
        val tempFile = createTempCSVFile("")
        
        try {
            processor.processRideData(tempFile)
            fail("Should throw exception for empty file")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("Invalid or empty file") ?: false)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test basic CSV file processing`() = runBlocking {
        val csvContent = """
            # Moto Sensor Logger v1.0
            # Format: timestamp,sensor_type,data1,data2,data3,data4,data5,data6
            1000,GPS,37.7749,-122.4194,100.0,25.0,90.0,5.0
            1100,IMU,0.1,0.2,9.8,0.01,0.02,0.03
            2000,GPS,37.7750,-122.4195,101.0,26.0,91.0,4.5
        """.trimIndent()
        
        val tempFile = createTempCSVFile(csvContent)
        
        try {
            val result = processor.processRideData(tempFile)
            
            assertNotNull(result)
            assertTrue(result.statistics.duration > 0)
            assertNotNull(result.dataQuality)
            assertTrue(result.processingTimeMs >= 0)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test CSV with calibration data`() = runBlocking {
        val csvContent = """
            # Moto Sensor Logger v1.0 - Calibrated
            # Calibration: reference_pitch=2.5 reference_roll=1.2
            # Format: timestamp,sensor_type,data1,data2,data3,data4,data5,data6
            1000,GPS,37.7749,-122.4194,100.0,25.0,90.0,5.0
            1100,IMU,0.1,0.2,9.8,0.01,0.02,0.03
        """.trimIndent()
        
        val tempFile = createTempCSVFile(csvContent)
        
        try {
            val result = processor.processRideData(tempFile)
            
            assertNotNull(result)
            assertEquals(CalibrationStatus.CALIBRATED, result.dataQuality.calibrationStatus)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test performance target under 2 seconds`() = runBlocking {
        // Generate larger CSV for performance testing
        val csvContent = buildString {
            appendLine("# Moto Sensor Logger Performance Test")
            appendLine("# Format: timestamp,sensor_type,data1,data2,data3,data4,data5,data6")
            
            // Generate 1000 GPS points and 5000 IMU points (simulating ~1 minute of data)
            for (i in 0 until 1000) {
                val timestamp = 1000L + i * 60 // GPS at ~1Hz
                appendLine("$timestamp,GPS,37.7749,-122.4194,100.0,25.0,90.0,5.0")
            }
            
            for (i in 0 until 5000) {
                val timestamp = 1000L + i * 10 // IMU at ~100Hz
                appendLine("$timestamp,IMU,0.1,0.2,9.8,0.01,0.02,0.03")
            }
        }
        
        val tempFile = createTempCSVFile(csvContent)
        
        try {
            val result = processor.processRideData(tempFile)
            
            // Verify performance target
            assertTrue("Processing took ${result.processingTimeMs}ms, should be under 2000ms", 
                      result.processingTimeMs < 2000)
            
            assertNotNull(result.statistics)
            assertTrue(result.statistics.duration > 0)
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun `test ride statistics calculation`() = runBlocking {
        val csvContent = """
            # Test ride with known metrics
            1000,GPS,37.7749,-122.4194,100.0,0.0,0.0,5.0
            2000,GPS,37.7750,-122.4194,100.0,10.0,90.0,5.0
            3000,GPS,37.7751,-122.4194,100.0,20.0,90.0,5.0
            1500,IMU,2.0,0.5,9.8,0.1,0.2,0.3
            2500,IMU,4.0,1.0,9.8,0.2,0.3,0.4
        """.trimIndent()
        
        val tempFile = createTempCSVFile(csvContent)
        
        try {
            val result = processor.processRideData(tempFile)
            val stats = result.statistics
            
            assertEquals(2000L, stats.duration) // 3000 - 1000 = 2000ms
            assertTrue(stats.distance > 0) // Should have calculated some distance
            assertTrue(stats.maxSpeed > 0) // Should have detected max speed
            assertTrue(stats.maxAcceleration >= 0) // Should have acceleration data
            assertNotNull(stats.startLocation)
            assertNotNull(stats.endLocation)
        } finally {
            tempFile.delete()
        }
    }

    private fun createTempCSVFile(content: String): File {
        val tempFile = File.createTempFile("test_ride", ".csv")
        FileWriter(tempFile).use { writer ->
            writer.write(content)
        }
        return tempFile
    }
}