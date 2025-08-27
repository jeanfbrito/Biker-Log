package com.motosensorlogger.data

import android.content.Context
import android.os.Environment
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.atomic.AtomicBoolean

/**
 * High-performance CSV logger with event-based sparse format
 * Uses coroutines and channels for non-blocking I/O
 */
class CsvLogger(private val context: Context) {
    private var logFile: File? = null
    private var writer: BufferedWriter? = null
    private val isLogging = AtomicBoolean(false)
    private val eventChannel = Channel<SensorEvent>(Channel.UNLIMITED)
    private var writerJob: Job? = null

    private val logScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    companion object {
        private const val LOG_DIR = "MotoSensorLogs"
        private const val BUFFER_SIZE = 32 * 1024 // 32KB buffer for efficient writes
    }

    /**
     * Start logging session with new file
     * @param calibrationHeader Optional calibration header to prepend
     */
    fun startLogging(calibrationHeader: String = ""): Boolean {
        if (isLogging.get()) return false

        try {
            // Create log directory
            val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            val logDir = File(documentsDir, LOG_DIR)
            if (!logDir.exists()) {
                logDir.mkdirs()
            }

            // Create new log file with timestamp
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            logFile = File(logDir, "moto_log_$timestamp.csv")

            // Initialize writer with large buffer
            writer = BufferedWriter(FileWriter(logFile), BUFFER_SIZE)

            // Build the complete header with all metadata
            // The calibrationHeader parameter already contains the calibration section
            // So we build the rest of the header around it

            val dateStr = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US).format(Date())
            val deviceStr = "${android.os.Build.MANUFACTURER} ${android.os.Build.MODEL}"

            // Write complete header with calibration section if provided
            val completeHeader = """
# Moto Sensor Log v1.1
# Device: $deviceStr
# Date: $dateStr
${calibrationHeader.ifEmpty {
"""# Calibration: {
#   "status": "uncalibrated",
#   "warning": "No calibration performed. Sensor data is in device coordinates."
# }"""
}}
# Schema: {
#   "version": "1.0",
#   "events": {
#     "GPS": {
#       "description": "GPS positioning data",
#       "frequency": "5-10Hz (adaptive)",
#       "fields": [
#         {"name": "data1", "type": "double", "unit": "degrees", "description": "latitude"},
#         {"name": "data2", "type": "double", "unit": "degrees", "description": "longitude"},
#         {"name": "data3", "type": "double", "unit": "meters", "description": "altitude_GPS"},
#         {"name": "data4", "type": "float", "unit": "m/s", "description": "speed"},
#         {"name": "data5", "type": "float", "unit": "degrees", "description": "bearing"},
#         {"name": "data6", "type": "float", "unit": "meters", "description": "accuracy"}
#       ]
#     },
#     "IMU": {
#       "description": "Inertial Measurement Unit data",
#       "frequency": "50-100Hz",
#       "fields": [
#         {"name": "data1", "type": "float", "unit": "m/s²", "description": "accel_X"},
#         {"name": "data2", "type": "float", "unit": "m/s²", "description": "accel_Y"},
#         {"name": "data3", "type": "float", "unit": "m/s²", "description": "accel_Z"},
#         {"name": "data4", "type": "float", "unit": "°/s", "description": "gyro_roll"},
#         {"name": "data5", "type": "float", "unit": "°/s", "description": "gyro_pitch"},
#         {"name": "data6", "type": "float", "unit": "°/s", "description": "gyro_yaw"}
#       ]
#     },
#     "BARO": {
#       "description": "Barometric pressure and altitude",
#       "frequency": "10-25Hz",
#       "fields": [
#         {"name": "data1", "type": "float", "unit": "meters", "description": "altitude_baro"},
#         {"name": "data2", "type": "float", "unit": "hPa", "description": "pressure"}
#       ]
#     },
#     "MAG": {
#       "description": "Magnetometer data",
#       "frequency": "10-25Hz",
#       "fields": [
#         {"name": "data1", "type": "float", "unit": "μT", "description": "mag_X"},
#         {"name": "data2", "type": "float", "unit": "μT", "description": "mag_Y"},
#         {"name": "data3", "type": "float", "unit": "μT", "description": "mag_Z"}
#       ]
#     }
#   }
# }
timestamp,sensor_type,data1,data2,data3,data4,data5,data6
                """.trimIndent()

            writer?.write(completeHeader)
            writer?.newLine()

            // Start writer coroutine
            isLogging.set(true)
            startWriterCoroutine()

            return true
        } catch (e: Exception) {
            e.printStackTrace()
            return false
        }
    }

    /**
     * Stop logging and close file
     */
    suspend fun stopLogging() {
        if (!isLogging.get()) return

        isLogging.set(false)
        eventChannel.close()

        // Wait for writer to finish
        writerJob?.join()

        // Close writer
        writer?.flush()
        writer?.close()
        writer = null
        logFile = null
    }

    /**
     * Write event to log (non-blocking)
     */
    fun logEvent(event: SensorEvent) {
        if (!isLogging.get()) return

        // Non-blocking send to channel
        eventChannel.trySend(event)
    }

    /**
     * Batch write events for efficiency
     */
    fun logEvents(events: List<SensorEvent>) {
        if (!isLogging.get()) return

        events.forEach { event ->
            eventChannel.trySend(event)
        }
    }

    /**
     * Get current log file path
     */
    fun getCurrentLogFile(): File? = logFile

    /**
     * Get all log files
     */
    fun getAllLogFiles(): List<File> {
        val documentsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val logDir = File(documentsDir, LOG_DIR)
        return logDir.listFiles { file -> file.extension == "csv" }?.toList() ?: emptyList()
    }

    /**
     * Background coroutine for writing events to file
     */
    private fun startWriterCoroutine() {
        writerJob =
            logScope.launch {
                val batch = mutableListOf<String>()

                while (isLogging.get() || !eventChannel.isEmpty) {
                    try {
                        // Collect events in batch for efficient writing
                        val event =
                            withTimeoutOrNull(100) {
                                eventChannel.receive()
                            }

                        if (event != null) {
                            batch.add(event.toCsvRow())
                        }

                        // Write batch when size threshold reached or timeout
                        if (batch.size >= 100 || (event == null && batch.isNotEmpty())) {
                            writer?.let { w ->
                                batch.forEach { line ->
                                    w.write(line)
                                    w.newLine()
                                }

                                // Flush periodically for data safety
                                if (batch.size >= 1000) {
                                    w.flush()
                                }
                            }
                            batch.clear()
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Final flush
                writer?.flush()
            }
    }

    /**
     * Clean up resources
     */
    fun cleanup() {
        runBlocking {
            stopLogging()
        }
        logScope.cancel()
    }
}
