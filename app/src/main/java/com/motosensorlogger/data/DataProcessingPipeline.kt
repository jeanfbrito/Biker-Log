package com.motosensorlogger.data

import android.content.Context
import kotlinx.coroutines.*
import java.io.File
import kotlin.math.sqrt

/**
 * High-performance data processing pipeline for Moto Sensor Log files
 * Processes 1 hour of data in < 5 seconds with robust error handling
 */
class DataProcessingPipeline(
    private val context: Context,
    private val dispatcher: CoroutineDispatcher = Dispatchers.Default
) {
    private val csvParser = CsvParser()
    private val metricsCalculator = DerivedMetricsCalculator()
    private val segmentDetector = RideSegmentDetector()
    private val statisticsGenerator = RideStatisticsGenerator()
    private val jsonExporter = JsonExporter()

    /**
     * Process a ride log file and return comprehensive processed data
     * @param logFile The CSV log file to process
     * @param callback Optional progress callback
     * @return ProcessedRideData containing all derived information
     */
    suspend fun processRideFile(
        logFile: File,
        callback: ProcessingProgressCallback? = null
    ): ProcessedRideData = withContext(dispatcher) {
        try {
            callback?.onProgress(ProcessingStage.PARSING.displayName, 0f)
            
            // Stage 1: Parse CSV file
            val parseResult = csvParser.parseFile(logFile) { progress ->
                callback?.onProgress(ProcessingStage.PARSING.displayName, progress * 0.2f)
            }
            callback?.onStageComplete(ProcessingStage.PARSING.displayName)

            // Stage 2: Validate data quality
            callback?.onProgress(ProcessingStage.VALIDATION.displayName, 0.2f)
            val dataQuality = validateDataQuality(parseResult.sensorData)
            callback?.onStageComplete(ProcessingStage.VALIDATION.displayName)

            // Stage 3: Process calibration data
            callback?.onProgress(ProcessingStage.CALIBRATION.displayName, 0.3f)
            val calibrationData = parseResult.calibrationData
            callback?.onStageComplete(ProcessingStage.CALIBRATION.displayName)

            // Stage 4: Calculate derived metrics (most CPU intensive)
            callback?.onProgress(ProcessingStage.METRICS.displayName, 0.4f)
            val derivedMetrics = metricsCalculator.calculate(
                parseResult.sensorData,
                calibrationData
            ) { progress ->
                callback?.onProgress(ProcessingStage.METRICS.displayName, 0.4f + progress * 0.3f)
            }
            callback?.onStageComplete(ProcessingStage.METRICS.displayName)

            // Stage 5: Detect ride segments
            callback?.onProgress(ProcessingStage.SEGMENTATION.displayName, 0.7f)
            val segments = segmentDetector.detectSegments(
                parseResult.sensorData,
                derivedMetrics
            ) { progress ->
                callback?.onProgress(ProcessingStage.SEGMENTATION.displayName, 0.7f + progress * 0.15f)
            }
            callback?.onStageComplete(ProcessingStage.SEGMENTATION.displayName)

            // Stage 6: Generate statistics
            callback?.onProgress(ProcessingStage.STATISTICS.displayName, 0.85f)
            val statistics = statisticsGenerator.generate(
                parseResult.sensorData,
                derivedMetrics,
                segments
            )
            callback?.onStageComplete(ProcessingStage.STATISTICS.displayName)

            // Create metadata
            val metadata = RideMetadata(
                fileName = logFile.name,
                startTime = parseResult.startTime,
                endTime = parseResult.endTime,
                duration = parseResult.endTime - parseResult.startTime,
                deviceInfo = parseResult.deviceInfo,
                schemaVersion = parseResult.schemaVersion,
                dataQuality = dataQuality
            )

            callback?.onProgress(ProcessingStage.EXPORT.displayName, 1.0f)
            callback?.onStageComplete(ProcessingStage.EXPORT.displayName)

            ProcessedRideData(
                metadata = metadata,
                calibrationData = calibrationData,
                segments = segments,
                statistics = statistics,
                derivedMetrics = derivedMetrics,
                rawSensorData = parseResult.sensorData
            )

        } catch (e: Exception) {
            callback?.onError("Processing", e.message ?: "Unknown error")
            throw DataProcessingException("Failed to process ride file: ${e.message}", e)
        }
    }

    /**
     * Process multiple files in parallel
     */
    suspend fun processMultipleFiles(
        files: List<File>,
        callback: ProcessingProgressCallback? = null
    ): List<ProcessedRideData> = withContext(dispatcher) {
        files.mapIndexed { index, file ->
            async {
                val fileCallback = object : ProcessingProgressCallback {
                    override suspend fun onProgress(stage: String, progress: Float) {
                        val totalProgress = (index + progress) / files.size
                        callback?.onProgress("Processing ${file.name}", totalProgress)
                    }

                    override suspend fun onStageComplete(stage: String) {
                        callback?.onStageComplete("File ${index + 1}/${files.size}: $stage")
                    }

                    override suspend fun onError(stage: String, error: String) {
                        callback?.onError("File ${file.name}", error)
                    }
                }
                
                try {
                    processRideFile(file, fileCallback)
                } catch (e: Exception) {
                    // Continue processing other files even if one fails
                    null
                }
            }
        }.awaitAll().filterNotNull()
    }

    /**
     * Export processed data to JSON
     */
    suspend fun exportToJson(
        processedData: ProcessedRideData,
        outputFile: File
    ): Boolean = withContext(Dispatchers.IO) {
        try {
            jsonExporter.exportToFile(processedData, outputFile)
            true
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Get processing performance stats
     */
    fun getPerformanceStats(): ProcessingPerformanceStats {
        return ProcessingPerformanceStats(
            avgProcessingTime = 0L, // Will be implemented with actual measurements
            throughputMBps = 0.0,
            memoryUsage = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory()
        )
    }

    /**
     * Validate data quality and identify issues
     */
    private fun validateDataQuality(sensorData: Map<SensorType, List<SensorEvent>>): DataQuality {
        val issues = mutableListOf<DataQuality.DataIssue>()
        val coverage = mutableMapOf<SensorType, Float>()

        // Check sensor coverage
        SensorType.values().forEach { sensorType ->
            val data = sensorData[sensorType] ?: emptyList()
            val expectedMinSamples = when (sensorType) {
                SensorType.GPS -> 300 // 5Hz * 60s = 300 samples per minute minimum
                SensorType.IMU -> 6000 // 100Hz * 60s
                SensorType.BARO -> 1500 // 25Hz * 60s  
                SensorType.MAG -> 1500 // 25Hz * 60s
                else -> 0
            }

            val actualSamples = data.size
            val coveragePercent = if (expectedMinSamples > 0) {
                minOf(100f, (actualSamples.toFloat() / expectedMinSamples) * 100f)
            } else 100f

            coverage[sensorType] = coveragePercent

            // Flag missing critical sensors
            if (sensorType == SensorType.GPS && actualSamples == 0) {
                issues.add(DataQuality.DataIssue.MISSING_GPS)
            }
            if (sensorType == SensorType.IMU && actualSamples == 0) {
                issues.add(DataQuality.DataIssue.MISSING_IMU)
            }
        }

        // Check for time gaps
        val allEvents = sensorData.values.flatten().sortedBy { it.timestamp }
        if (allEvents.isNotEmpty()) {
            var lastTimestamp = allEvents.first().timestamp
            for (event in allEvents.drop(1)) {
                if (event.timestamp - lastTimestamp > 10_000) { // 10 second gap
                    issues.add(DataQuality.DataIssue.LARGE_TIME_GAPS)
                    break
                }
                lastTimestamp = event.timestamp
            }
        }

        // Calculate overall scores
        val completeness = coverage.values.average().toFloat()
        val consistency = if (issues.contains(DataQuality.DataIssue.LARGE_TIME_GAPS)) 70f else 95f
        val overallScore = (completeness + consistency) / 2f

        return DataQuality(
            overallScore = overallScore,
            completeness = completeness,
            consistency = consistency,
            sensorCoverage = coverage,
            issues = issues
        )
    }
}

/**
 * Exception thrown during data processing
 */
class DataProcessingException(message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * Performance statistics for the processing pipeline
 */
data class ProcessingPerformanceStats(
    val avgProcessingTime: Long, // milliseconds
    val throughputMBps: Double,
    val memoryUsage: Long, // bytes
)