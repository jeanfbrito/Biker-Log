package com.motosensorlogger.data

import android.util.Log
import com.motosensorlogger.calibration.CalibrationData
import com.opencsv.CSVReader
import kotlinx.coroutines.yield
import java.io.File
import java.io.FileReader
import java.io.IOException

/**
 * High-performance CSV parser for motorcycle sensor data
 * Handles the sparse event-based format with embedded schema
 */
class CsvParser {
    
    companion object {
        private const val TAG = "CsvParser"
        private const val HEADER_CALIBRATION_PREFIX = "# CALIBRATION:"
        private const val HEADER_SCHEMA_PREFIX = "# SCHEMA:"
        private const val HEADER_VERSION_PREFIX = "# VERSION:"
        private const val EXPECTED_COLUMNS = 8 // timestamp, sensor_type, data1-6
    }
    
    /**
     * Parse result containing sensor data and metadata
     */
    data class ParseResult(
        val sensorData: Map<SensorType, List<SensorEvent>>,
        val calibrationData: CalibrationData?,
        val recordingStartTime: Long,
        val recordingEndTime: Long,
        val sampleCounts: Map<SensorType, Int>,
        val errors: List<ProcessingError>
    )
    
    /**
     * Parse a CSV file with progress reporting
     */
    suspend fun parseFile(
        csvFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): ParseResult {
        
        if (!csvFile.exists()) {
            throw IOException("File does not exist: ${csvFile.absolutePath}")
        }
        
        if (csvFile.length() == 0L) {
            throw IOException("File is empty: ${csvFile.absolutePath}")
        }
        
        Log.d(TAG, "Parsing CSV file: ${csvFile.name} (${csvFile.length()} bytes)")
        
        val errors = mutableListOf<ProcessingError>()
        val sensorDataMap = mutableMapOf<SensorType, MutableList<SensorEvent>>()
        var calibrationData: CalibrationData? = null
        var recordingStartTime = Long.MAX_VALUE
        var recordingEndTime = Long.MIN_VALUE
        
        // Initialize sensor data lists
        SensorType.values().forEach { sensorType ->
            sensorDataMap[sensorType] = mutableListOf()
        }
        
        try {
            CSVReader(FileReader(csvFile)).use { reader ->
                var lineNumber = 0
                var dataLines = 0
                val estimatedLines = estimateLineCount(csvFile)
                
                // Parse header and data
                var line: Array<String>?
                while (reader.readNext().also { line = it } != null) {
                    lineNumber++
                    val currentLine = line!!
                    
                    try {
                        when {
                            currentLine[0].startsWith("#") -> {
                                // Header line
                                parseHeaderLine(currentLine, calibrationData, errors, lineNumber)?.let {
                                    calibrationData = it
                                }
                            }
                            currentLine.size >= 2 -> {
                                // Data line
                                val sensorEvent = parseDataLine(currentLine, errors, lineNumber)
                                sensorEvent?.let { event ->
                                    sensorDataMap[event.sensorType]?.add(event)
                                    recordingStartTime = minOf(recordingStartTime, event.timestamp)
                                    recordingEndTime = maxOf(recordingEndTime, event.timestamp)
                                }
                                dataLines++
                                
                                // Report progress every 1000 lines
                                if (dataLines % 1000 == 0) {
                                    yield()
                                    if (estimatedLines > 0) {
                                        progressCallback?.invoke(dataLines.toFloat() / estimatedLines)
                                    }
                                }
                            }
                            else -> {
                                // Invalid line
                                if (currentLine.isNotEmpty() && !currentLine[0].isBlank()) {
                                    errors.add(ProcessingError(
                                        timestamp = System.currentTimeMillis(),
                                        errorType = ProcessingError.ErrorType.CORRUPTED_DATA,
                                        message = "Invalid line format at line $lineNumber: ${currentLine.joinToString(",")}",
                                        severity = ProcessingError.Severity.WARNING
                                    ))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Error parsing line $lineNumber", e)
                        errors.add(ProcessingError(
                            timestamp = System.currentTimeMillis(),
                            errorType = ProcessingError.ErrorType.CORRUPTED_DATA,
                            message = "Parse error at line $lineNumber: ${e.message}",
                            severity = ProcessingError.Severity.WARNING
                        ))
                    }
                }
                
                Log.d(TAG, "Parsed $dataLines data lines from ${csvFile.name}")
            }
        } catch (e: IOException) {
            Log.e(TAG, "IO error reading file", e)
            throw e
        }
        
        // Validate and sort data
        val finalSensorData = validateAndSortData(sensorDataMap, errors)
        val sampleCounts = finalSensorData.mapValues { it.value.size }
        
        // Handle missing timestamps
        if (recordingStartTime == Long.MAX_VALUE) {
            recordingStartTime = 0L
        }
        if (recordingEndTime == Long.MIN_VALUE) {
            recordingEndTime = 0L
        }
        
        Log.d(TAG, "Parsing complete. Sample counts: ${sampleCounts.filter { it.value > 0 }}")
        
        return ParseResult(
            sensorData = finalSensorData,
            calibrationData = calibrationData,
            recordingStartTime = recordingStartTime,
            recordingEndTime = recordingEndTime,
            sampleCounts = sampleCounts,
            errors = errors
        )
    }
    
    /**
     * Parse header line for calibration data
     */
    private fun parseHeaderLine(
        line: Array<String>,
        currentCalibration: CalibrationData?,
        errors: MutableList<ProcessingError>,
        lineNumber: Int
    ): CalibrationData? {
        
        if (line.isEmpty()) return currentCalibration
        
        val headerContent = line[0]
        
        when {
            headerContent.startsWith(HEADER_CALIBRATION_PREFIX) -> {
                try {
                    return parseCalibrationHeader(headerContent, errors, lineNumber)
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to parse calibration header at line $lineNumber", e)
                    errors.add(ProcessingError(
                        timestamp = System.currentTimeMillis(),
                        errorType = ProcessingError.ErrorType.MISSING_CALIBRATION,
                        message = "Invalid calibration header at line $lineNumber: ${e.message}",
                        severity = ProcessingError.Severity.WARNING
                    ))
                }
            }
            headerContent.startsWith(HEADER_SCHEMA_PREFIX) -> {
                Log.d(TAG, "Schema header: $headerContent")
            }
            headerContent.startsWith(HEADER_VERSION_PREFIX) -> {
                Log.d(TAG, "Version header: $headerContent")
            }
        }
        
        return currentCalibration
    }
    
    /**
     * Parse calibration information from header
     */
    private fun parseCalibrationHeader(
        headerContent: String,
        errors: MutableList<ProcessingError>,
        lineNumber: Int
    ): CalibrationData? {
        // Parse calibration data from header comment
        // Expected format: # CALIBRATION: quality=GOOD,pitch=-2.1,roll=0.3,matrix=[...],timestamp=123456789
        
        try {
            val calibrationString = headerContent.removePrefix(HEADER_CALIBRATION_PREFIX).trim()
            val parts = calibrationString.split(",")
            
            var quality = CalibrationData.Quality.UNKNOWN
            var referencePitch = 0f
            var referenceRoll = 0f
            var referenceRotationMatrix = FloatArray(9) { if (it % 4 == 0) 1f else 0f } // Identity matrix
            var timestamp = System.currentTimeMillis()
            
            for (part in parts) {
                val keyValue = part.split("=", limit = 2)
                if (keyValue.size != 2) continue
                
                val key = keyValue[0].trim()
                val value = keyValue[1].trim()
                
                when (key.lowercase()) {
                    "quality" -> {
                        quality = try {
                            CalibrationData.Quality.valueOf(value.uppercase())
                        } catch (e: IllegalArgumentException) {
                            CalibrationData.Quality.UNKNOWN
                        }
                    }
                    "pitch" -> {
                        referencePitch = value.toFloatOrNull() ?: 0f
                    }
                    "roll" -> {
                        referenceRoll = value.toFloatOrNull() ?: 0f
                    }
                    "timestamp" -> {
                        timestamp = value.toLongOrNull() ?: System.currentTimeMillis()
                    }
                    "matrix" -> {
                        // Parse rotation matrix from string like "[1,0,0,0,1,0,0,0,1]"
                        val matrixStr = value.removePrefix("[").removeSuffix("]")
                        val matrixValues = matrixStr.split(",").mapNotNull { it.trim().toFloatOrNull() }
                        if (matrixValues.size == 9) {
                            referenceRotationMatrix = matrixValues.toFloatArray()
                        }
                    }
                }
            }
            
            return CalibrationData(
                quality = quality,
                referencePitch = referencePitch,
                referenceRoll = referenceRoll,
                referenceRotationMatrix = referenceRotationMatrix,
                timestamp = timestamp
            )
            
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse calibration data", e)
            return null
        }
    }
    
    /**
     * Parse a data line into a sensor event
     */
    private fun parseDataLine(
        line: Array<String>,
        errors: MutableList<ProcessingError>,
        lineNumber: Int
    ): SensorEvent? {
        
        if (line.size < 2) {
            return null
        }
        
        try {
            val timestamp = line[0].toLongOrNull() ?: return null
            val sensorTypeStr = line[1].trim()
            
            val sensorType = SensorType.values().find { it.code == sensorTypeStr }
                ?: return null
            
            // Parse based on sensor type
            return when (sensorType) {
                SensorType.GPS -> parseGpsEvent(timestamp, line)
                SensorType.IMU -> parseImuEvent(timestamp, line)
                SensorType.BARO -> parseBaroEvent(timestamp, line)
                SensorType.MAG -> parseMagEvent(timestamp, line)
                else -> null
            }
            
        } catch (e: Exception) {
            Log.w(TAG, "Error parsing data line $lineNumber", e)
            return null
        }
    }
    
    /**
     * Parse GPS event from CSV line
     */
    private fun parseGpsEvent(timestamp: Long, line: Array<String>): GpsEvent? {
        if (line.size < 8) return null
        
        try {
            return GpsEvent(
                timestamp = timestamp,
                latitude = line[2].toDoubleOrNull() ?: return null,
                longitude = line[3].toDoubleOrNull() ?: return null,
                altitude = line[4].toDoubleOrNull() ?: 0.0,
                speed = line[5].toFloatOrNull() ?: 0f,
                bearing = line[6].toFloatOrNull() ?: 0f,
                accuracy = line[7].toFloatOrNull() ?: Float.MAX_VALUE
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse GPS event", e)
            return null
        }
    }
    
    /**
     * Parse IMU event from CSV line
     */
    private fun parseImuEvent(timestamp: Long, line: Array<String>): ImuEvent? {
        if (line.size < 8) return null
        
        try {
            return ImuEvent(
                timestamp = timestamp,
                accelX = line[2].toFloatOrNull() ?: return null,
                accelY = line[3].toFloatOrNull() ?: return null,
                accelZ = line[4].toFloatOrNull() ?: return null,
                gyroX = line[5].toFloatOrNull() ?: return null,
                gyroY = line[6].toFloatOrNull() ?: return null,
                gyroZ = line[7].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse IMU event", e)
            return null
        }
    }
    
    /**
     * Parse barometer event from CSV line
     */
    private fun parseBaroEvent(timestamp: Long, line: Array<String>): BaroEvent? {
        if (line.size < 4) return null
        
        try {
            return BaroEvent(
                timestamp = timestamp,
                altitudeBaro = line[2].toFloatOrNull() ?: return null,
                pressure = line[3].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse BARO event", e)
            return null
        }
    }
    
    /**
     * Parse magnetometer event from CSV line
     */
    private fun parseMagEvent(timestamp: Long, line: Array<String>): MagEvent? {
        if (line.size < 5) return null
        
        try {
            return MagEvent(
                timestamp = timestamp,
                magX = line[2].toFloatOrNull() ?: return null,
                magY = line[3].toFloatOrNull() ?: return null,
                magZ = line[4].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse MAG event", e)
            return null
        }
    }
    
    /**
     * Validate and sort parsed data
     */
    private fun validateAndSortData(
        sensorDataMap: Map<SensorType, MutableList<SensorEvent>>,
        errors: MutableList<ProcessingError>
    ): Map<SensorType, List<SensorEvent>> {
        
        val result = mutableMapOf<SensorType, List<SensorEvent>>()
        
        sensorDataMap.forEach { (sensorType, events) ->
            // Sort by timestamp
            val sortedEvents = events.sortedBy { it.timestamp }
            
            // Validate timestamps are reasonable
            val validEvents = sortedEvents.filter { event ->
                val isValid = event.timestamp > 0 && 
                             event.timestamp < System.currentTimeMillis() + 86400000L // Not more than 24h in future
                if (!isValid) {
                    errors.add(ProcessingError(
                        timestamp = System.currentTimeMillis(),
                        errorType = ProcessingError.ErrorType.CORRUPTED_DATA,
                        message = "Invalid timestamp for $sensorType: ${event.timestamp}",
                        severity = ProcessingError.Severity.WARNING
                    ))
                }
                isValid
            }
            
            result[sensorType] = validEvents
            
            if (validEvents.isNotEmpty()) {
                Log.d(TAG, "$sensorType: ${validEvents.size} valid samples")
            }
        }
        
        return result
    }
    
    /**
     * Estimate line count for progress reporting
     */
    private fun estimateLineCount(file: File): Long {
        return try {
            // Rough estimate: assume average line length of 50 characters
            file.length() / 50
        } catch (e: Exception) {
            0L
        }
    }
}