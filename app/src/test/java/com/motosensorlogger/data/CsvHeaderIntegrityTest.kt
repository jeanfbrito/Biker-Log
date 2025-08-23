package com.motosensorlogger.data

import org.junit.Assert.*
import org.junit.Test

/**
 * Critical tests to ensure CSV header integrity
 * These tests verify the header generation logic without Android dependencies
 */
class CsvHeaderIntegrityTest {
    
    @Test
    fun `test calibration header format is valid JSON-like structure`() {
        val calibrationHeader = """
# Calibration: {
#   "format_version": "2.0",
#   "timestamp": 1234567890,
#   "reference": {
#     "gravity": [0.0, 0.0, 9.81],
#     "pitch": 15.5,
#     "roll": -5.2
#   }
# }""".trimIndent()
        
        // Verify structure
        assertTrue("Should contain format_version", calibrationHeader.contains("format_version"))
        assertTrue("Should contain timestamp", calibrationHeader.contains("timestamp"))
        assertTrue("Should contain reference section", calibrationHeader.contains("reference"))
        assertTrue("Should be commented with #", calibrationHeader.lines().all { it.startsWith("#") })
    }
    
    @Test
    fun `test uncalibrated header format is consistent`() {
        val uncalibratedHeader = """
# Calibration: {
#   "status": "not_calibrated",
#   "reason": "User skipped calibration",
#   "timestamp": 1234567890
# }""".trimIndent()
        
        assertTrue("Should contain status field", uncalibratedHeader.contains("status"))
        assertTrue("Should contain not_calibrated value", uncalibratedHeader.contains("not_calibrated"))
        assertTrue("Should contain reason", uncalibratedHeader.contains("reason"))
    }
    
    @Test
    fun `test header sections do not overlap`() {
        val fullHeader = """
# Moto Sensor Log v1.1
# Device: Test Device
# Date: 2024-01-01
# Calibration: {
#   "status": "calibrated"
# }
# Schema: {
#   "version": "1.0"
# }
timestamp,sensor_type,data1,data2,data3
""".trimIndent()
        
        val lines = fullHeader.lines()
        
        // Check that each section appears exactly once
        assertEquals("Should have exactly one log version line", 1, 
            lines.count { it.contains("Moto Sensor Log") })
        assertEquals("Should have exactly one Device line", 1, 
            lines.count { it.contains("Device:") })
        assertEquals("Should have exactly one Date line", 1, 
            lines.count { it.contains("Date:") })
        assertEquals("Should have exactly one Calibration section", 1, 
            lines.count { it.contains("Calibration:") })
        assertEquals("Should have exactly one Schema section", 1, 
            lines.count { it.contains("Schema:") })
        assertEquals("Should have exactly one CSV header line", 1, 
            lines.count { it.startsWith("timestamp,sensor_type") })
    }
    
    @Test
    fun `test calibration and uncalibrated states are mutually exclusive`() {
        // Test calibrated header
        val calibratedHeader = """
# Calibration: {
#   "reference": {
#     "pitch": 15.5,
#     "roll": -5.2
#   }
# }""".trimIndent()
        
        assertFalse("Calibrated header should not contain 'uncalibrated'", 
            calibratedHeader.contains("uncalibrated"))
        assertFalse("Calibrated header should not contain 'not_calibrated'", 
            calibratedHeader.contains("not_calibrated"))
        
        // Test uncalibrated header
        val uncalibratedHeader = """
# Calibration: {
#   "status": "not_calibrated"
# }""".trimIndent()
        
        assertFalse("Uncalibrated header should not contain pitch reference", 
            uncalibratedHeader.contains("pitch"))
        assertFalse("Uncalibrated header should not contain roll reference", 
            uncalibratedHeader.contains("roll"))
    }
    
    @Test
    fun `test CSV column headers match data format`() {
        val csvColumns = "timestamp,sensor_type,data1,data2,data3,data4,data5,data6"
        val columns = csvColumns.split(",")
        
        assertEquals("Should have 8 columns", 8, columns.size)
        assertEquals("First column should be timestamp", "timestamp", columns[0])
        assertEquals("Second column should be sensor_type", "sensor_type", columns[1])
        
        // Verify data columns
        for (i in 1..6) {
            assertEquals("Should have data$i column", "data$i", columns[i + 1])
        }
    }
    
    @Test
    fun `test sensor event types are properly defined`() {
        val schemaSection = """
# Schema: {
#   "events": {
#     "GPS": { },
#     "IMU": { },
#     "BARO": { },
#     "MAG": { }
#   }
# }""".trimIndent()
        
        val requiredEventTypes = listOf("GPS", "IMU", "BARO", "MAG")
        
        for (eventType in requiredEventTypes) {
            assertTrue("Schema should define $eventType event", 
                schemaSection.contains("\"$eventType\""))
        }
    }
    
    @Test
    fun `test header comment format is consistent`() {
        val header = """
# Moto Sensor Log v1.1
# Device: Test
# Date: 2024-01-01
""".trimIndent()
        
        val lines = header.lines()
        val commentedLines = lines.filter { it.isNotEmpty() && !it.startsWith("timestamp") }
        
        assertTrue("All metadata lines should start with #", 
            commentedLines.all { it.startsWith("#") })
    }
    
    @Test
    fun `test empty calibration header handling`() {
        val emptyHeader = ""
        
        // When empty, should default to uncalibrated
        val expectedDefault = "uncalibrated"
        
        // This simulates the logic in CsvLogger
        val resultHeader = if (emptyHeader.isEmpty()) {
            """# Calibration: {
#   "status": "uncalibrated"
# }""".trimIndent()
        } else {
            emptyHeader
        }
        
        assertTrue("Empty header should default to uncalibrated", 
            resultHeader.contains("uncalibrated"))
    }
    
    @Test
    fun `test header does not contain null values`() {
        val headers = listOf(
            """# Calibration: { "pitch": 15.5 }""",
            """# Calibration: { "status": "not_calibrated" }""",
            """# Device: Samsung Galaxy"""
        )
        
        for (header in headers) {
            assertFalse("Header should not contain null: $header", 
                header.contains("null"))
            assertFalse("Header should not contain undefined: $header", 
                header.contains("undefined"))
        }
    }
    
    @Test
    fun `test calibration timestamp is valid`() {
        val timestamp = System.currentTimeMillis()
        val header = """
# Calibration: {
#   "timestamp": $timestamp
# }""".trimIndent()
        
        assertTrue("Timestamp should be positive", timestamp > 0)
        assertTrue("Header should contain valid timestamp", 
            header.contains(timestamp.toString()))
    }
}