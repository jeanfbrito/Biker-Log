package com.motosensorlogger.data

import com.motosensorlogger.calibration.CalibrationData
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.util.regex.Pattern
import kotlin.math.max

/**
 * High-performance CSV parser optimized for Moto Sensor Log format
 * Handles large files efficiently with streaming and progress reporting
 */
class CsvParser {

    companion object {
        private val COMMA_PATTERN = Pattern.compile(",")
        private val CALIBRATION_PATTERN = Pattern.compile("""# Calibration: \{""")
        private val JSON_FIELD_PATTERN = Pattern.compile(""""([^"]+)":\s*([^,}\]]+|"[^"]*"|\[[^\]]*\])""")
    }

    /**
     * Parse result containing all extracted data
     */
    data class ParseResult(
        val sensorData: Map<SensorType, List<SensorEvent>>,
        val calibrationData: CalibrationData?,
        val startTime: Long,
        val endTime: Long,
        val deviceInfo: String,
        val schemaVersion: String,
        val totalSamples: Int
    )

    /**
     * Parse a CSV log file with progress reporting
     */
    suspend fun parseFile(
        file: File,
        progressCallback: ((Float) -> Unit)? = null
    ): ParseResult = withContext(Dispatchers.IO) {
        
        if (!file.exists() || !file.canRead()) {
            throw DataProcessingException("Cannot read file: ${file.absolutePath}")
        }

        val fileSize = file.length()
        var bytesRead = 0L
        var lastProgress = 0f

        val sensorDataMaps = mutableMapOf<SensorType, MutableList<SensorEvent>>().apply {
            SensorType.values().forEach { type ->
                this[type] = mutableListOf()
            }
        }

        var calibrationData: CalibrationData? = null
        var deviceInfo = "Unknown"
        var schemaVersion = "1.0"
        var startTime = 0L
        var endTime = 0L
        var lineCount = 0
        var headerLineCount = 0

        try {
            BufferedReader(FileReader(file), 64 * 1024).use { reader ->
                var line: String?
                var inHeader = true
                val headerLines = mutableListOf<String>()

                // Read file line by line
                while (reader.readLine().also { line = it } != null) {
                    line?.let { currentLine ->
                        bytesRead += currentLine.length + 1 // +1 for newline
                        lineCount++

                        // Report progress every 1000 lines to avoid overhead
                        if (lineCount % 1000 == 0) {
                            val progress = bytesRead.toFloat() / fileSize
                            if (progress - lastProgress > 0.05f) { // Update every 5%
                                progressCallback?.invoke(progress)
                                lastProgress = progress
                                yield() // Allow other coroutines to run
                            }
                        }

                        if (inHeader) {
                            if (currentLine.startsWith("#") || currentLine.startsWith("timestamp,")) {
                                headerLines.add(currentLine)
                                headerLineCount++
                                
                                // Parse header information
                                if (currentLine.contains("Device:")) {
                                    deviceInfo = currentLine.substringAfter("Device:").trim().removePrefix("# ")
                                }
                                
                                if (currentLine == "timestamp,sensor_type,data1,data2,data3,data4,data5,data6") {
                                    inHeader = false
                                    // Parse calibration from collected header lines
                                    calibrationData = parseCalibrationFromHeader(headerLines.joinToString("\n"))
                                    continue
                                }
                            }
                        } else {
                            // Parse sensor data line
                            val sensorEvent = parseSensorDataLine(currentLine)
                            sensorEvent?.let { event ->
                                sensorDataMaps[event.sensorType]?.add(event)
                                
                                // Track time bounds
                                if (startTime == 0L || event.timestamp < startTime) {
                                    startTime = event.timestamp
                                }
                                if (event.timestamp > endTime) {
                                    endTime = event.timestamp
                                }
                            }
                        }
                    }
                }
            }

            // Final progress update
            progressCallback?.invoke(1.0f)

            // Validate parsed data
            if (sensorDataMaps.values.all { it.isEmpty() }) {
                throw DataProcessingException("No valid sensor data found in file")
            }

            if (startTime == 0L || endTime == 0L) {
                throw DataProcessingException("Invalid timestamp data in file")
            }

            // Sort all sensor data by timestamp for consistent processing
            val sortedSensorData = sensorDataMaps.mapValues { (_, events) ->
                events.sortedBy { it.timestamp }
            }

            val totalSamples = sortedSensorData.values.sumOf { it.size }

            ParseResult(
                sensorData = sortedSensorData,
                calibrationData = calibrationData,
                startTime = startTime,
                endTime = endTime,
                deviceInfo = deviceInfo,
                schemaVersion = schemaVersion,
                totalSamples = totalSamples
            )

        } catch (e: Exception) {
            throw DataProcessingException("Failed to parse CSV file: ${e.message}", e)
        }
    }

    /**
     * Parse a single sensor data line from CSV
     */
    private fun parseSensorDataLine(line: String): SensorEvent? {
        if (line.isBlank() || line.startsWith("#")) return null

        return try {
            val parts = COMMA_PATTERN.split(line, 8) // Limit to 8 parts for efficiency
            
            if (parts.size < 3) return null

            val timestamp = parts[0].toLongOrNull() ?: return null
            val sensorTypeStr = parts[1].trim()

            when (sensorTypeStr) {
                "GPS" -> parseGpsEvent(timestamp, parts)
                "IMU" -> parseImuEvent(timestamp, parts)
                "BARO" -> parseBaroEvent(timestamp, parts)
                "MAG" -> parseMagEvent(timestamp, parts)
                "EVENT" -> parseSpecialEvent(timestamp, parts)
                else -> null
            }
        } catch (e: Exception) {
            // Skip malformed lines rather than failing entire file
            null
        }
    }

    /**
     * Parse GPS event from CSV parts
     */
    private fun parseGpsEvent(timestamp: Long, parts: Array<String>): GpsEvent? {
        return try {
            if (parts.size < 8) return null
            
            GpsEvent(
                timestamp = timestamp,
                latitude = parts[2].toDoubleOrNull() ?: return null,
                longitude = parts[3].toDoubleOrNull() ?: return null,
                altitude = parts[4].toDoubleOrNull() ?: 0.0,
                speed = parts[5].toFloatOrNull() ?: 0f,
                bearing = parts[6].toFloatOrNull() ?: 0f,
                accuracy = parts[7].toFloatOrNull() ?: 0f
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse IMU event from CSV parts
     */
    private fun parseImuEvent(timestamp: Long, parts: Array<String>): ImuEvent? {
        return try {
            if (parts.size < 8) return null
            
            ImuEvent(
                timestamp = timestamp,
                accelX = parts[2].toFloatOrNull() ?: return null,
                accelY = parts[3].toFloatOrNull() ?: return null,
                accelZ = parts[4].toFloatOrNull() ?: return null,
                gyroX = parts[5].toFloatOrNull() ?: return null,
                gyroY = parts[6].toFloatOrNull() ?: return null,
                gyroZ = parts[7].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse barometric event from CSV parts
     */
    private fun parseBaroEvent(timestamp: Long, parts: Array<String>): BaroEvent? {
        return try {
            if (parts.size < 4) return null
            
            BaroEvent(
                timestamp = timestamp,
                altitudeBaro = parts[2].toFloatOrNull() ?: return null,
                pressure = parts[3].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse magnetometer event from CSV parts
     */
    private fun parseMagEvent(timestamp: Long, parts: Array<String>): MagEvent? {
        return try {
            if (parts.size < 5) return null
            
            MagEvent(
                timestamp = timestamp,
                magX = parts[2].toFloatOrNull() ?: return null,
                magY = parts[3].toFloatOrNull() ?: return null,
                magZ = parts[4].toFloatOrNull() ?: return null
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse special event from CSV parts
     */
    private fun parseSpecialEvent(timestamp: Long, parts: Array<String>): SpecialEvent? {
        return try {
            if (parts.size < 4) return null
            
            val eventTypeName = parts[2].trim()
            val eventType = try {
                SpecialEvent.EventType.valueOf(eventTypeName)
            } catch (e: IllegalArgumentException) {
                return null
            }
            
            SpecialEvent(
                timestamp = timestamp,
                eventType = eventType,
                duration = parts.getOrNull(3)?.toLongOrNull() ?: 0L,
                maxValue = parts.getOrNull(4)?.toFloatOrNull() ?: 0f,
                metadata = parts.getOrNull(5)?.trim('"') ?: ""
            )
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Parse calibration data from CSV header
     */
    private fun parseCalibrationFromHeader(header: String): CalibrationData? {
        if (!header.contains("# Calibration: {")) {
            return null
        }

        return try {
            // Simple parser for the JSON-like calibration data in header
            // This is a simplified version - full implementation would use a proper JSON parser
            val calibrationSection = header.substringAfter("# Calibration: {").substringBefore("# }")
            
            // Extract basic values using regex (simplified approach)
            val timestampMatch = """\"timestamp\":\s*(\d+)""".toRegex().find(calibrationSection)
            val timestamp = timestampMatch?.groupValues?.get(1)?.toLongOrNull() ?: System.currentTimeMillis()

            // For now, return null to indicate uncalibrated data
            // Full implementation would parse the complete calibration structure
            null
            
        } catch (e: Exception) {
            null
        }
    }
}

/**
 * Extension functions for safe parsing
 */
private fun String.toDoubleOrNull(): Double? {
    return try {
        if (this.isBlank()) null else this.toDouble()
    } catch (e: NumberFormatException) {
        null
    }
}

private fun String.toFloatOrNull(): Float? {
    return try {
        if (this.isBlank()) null else this.toFloat()
    } catch (e: NumberFormatException) {
        null
    }
}

private fun String.toLongOrNull(): Long? {
    return try {
        if (this.isBlank()) null else this.toLong()
    } catch (e: NumberFormatException) {
        null
    }
}