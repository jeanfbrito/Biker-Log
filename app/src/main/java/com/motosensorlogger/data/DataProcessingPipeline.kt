package com.motosensorlogger.data

import android.util.Log
import com.motosensorlogger.calibration.CalibrationData
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File
import java.io.IOException
import kotlin.system.measureTimeMillis

/**
 * High-performance data processing pipeline for motorcycle sensor data
 * Processes CSV files into structured metrics, segments, and statistics
 * 
 * Performance target: Process 1 hour of data in < 5 seconds
 */
class DataProcessingPipeline {
    
    companion object {
        private const val TAG = "DataProcessingPipeline"
        private const val MAX_PROCESSING_TIME_MS = 30_000L // 30 seconds max
        private const val PERFORMANCE_TARGET_MS = 5_000L // 5 seconds target
    }

    private val metricsCalculator = DerivedMetricsCalculator()
    private val segmentDetector = RideSegmentDetector()
    private val statisticsGenerator = RideStatisticsGenerator()
    private val csvParser = CsvParser()

    /**
     * Process a CSV file with progress reporting
     */
    suspend fun processFile(
        csvFile: File,
        progressCallback: ((Float) -> Unit)? = null
    ): ProcessingResult = withContext(Dispatchers.IO) {
        
        val startTime = System.currentTimeMillis()
        val errors = mutableListOf<ProcessingError>()
        
        try {
            progressCallback?.invoke(0f)
            
            // Step 1: Parse CSV file (20% of progress)
            Log.d(TAG, "Starting CSV parsing for file: ${csvFile.name}")
            val parseResult = csvParser.parseFile(csvFile) { progress ->
                progressCallback?.invoke(progress * 0.2f)
            }
            
            if (parseResult.errors.isNotEmpty()) {
                errors.addAll(parseResult.errors)
                Log.w(TAG, "CSV parsing completed with ${parseResult.errors.size} errors")
            }
            
            progressCallback?.invoke(0.2f)
            
            // Step 2: Calculate derived metrics (40% of progress)
            Log.d(TAG, "Calculating derived metrics...")
            val derivedMetrics = metricsCalculator.calculate(
                sensorData = parseResult.sensorData,
                calibrationData = parseResult.calibrationData
            ) { progress ->
                progressCallback?.invoke(0.2f + progress * 0.4f)
            }
            
            progressCallback?.invoke(0.6f)
            
            // Step 3: Detect ride segments (20% of progress)
            Log.d(TAG, "Detecting ride segments...")
            val segments = segmentDetector.detectSegments(
                sensorData = parseResult.sensorData,
                derivedMetrics = derivedMetrics
            ) { progress ->
                progressCallback?.invoke(0.6f + progress * 0.2f)
            }
            
            progressCallback?.invoke(0.8f)
            
            // Step 4: Generate statistics (20% of progress)
            Log.d(TAG, "Generating ride statistics...")
            val statistics = statisticsGenerator.generate(
                sensorData = parseResult.sensorData,
                derivedMetrics = derivedMetrics,
                segments = segments
            )
            
            progressCallback?.invoke(1.0f)
            
            val processingTime = System.currentTimeMillis() - startTime
            Log.d(TAG, "Processing completed in ${processingTime}ms for file ${csvFile.name}")
            
            // Log performance
            if (processingTime > PERFORMANCE_TARGET_MS) {
                Log.w(TAG, "Processing time ${processingTime}ms exceeded target ${PERFORMANCE_TARGET_MS}ms")
            }
            
            val fileInfo = FileInfo(
                fileName = csvFile.name,
                fileSizeBytes = csvFile.length(),
                recordingStartTime = parseResult.recordingStartTime,
                recordingEndTime = parseResult.recordingEndTime,
                isCalibrated = parseResult.calibrationData != null,
                calibrationQuality = parseResult.calibrationData?.quality?.getQualityLevel()?.name
            )
            
            ProcessingResult(
                fileInfo = fileInfo,
                processingTime = processingTime,
                sampleCounts = parseResult.sampleCounts,
                derivedMetrics = derivedMetrics,
                segments = segments,
                statistics = statistics,
                errors = errors
            )
            
        } catch (e: Exception) {
            val processingTime = System.currentTimeMillis() - startTime
            Log.e(TAG, "Processing failed after ${processingTime}ms", e)
            
            errors.add(ProcessingError(
                timestamp = startTime,
                errorType = when (e) {
                    is IOException -> ProcessingError.ErrorType.CORRUPTED_DATA
                    is OutOfMemoryError -> ProcessingError.ErrorType.PROCESSING_TIMEOUT
                    else -> ProcessingError.ErrorType.UNKNOWN_FORMAT
                },
                message = e.message ?: "Unknown error",
                severity = ProcessingError.Severity.CRITICAL
            ))
            
            // Return minimal result with error information
            ProcessingResult(
                fileInfo = FileInfo(
                    fileName = csvFile.name,
                    fileSizeBytes = csvFile.length(),
                    recordingStartTime = 0L,
                    recordingEndTime = 0L,
                    isCalibrated = false,
                    calibrationQuality = null
                ),
                processingTime = processingTime,
                sampleCounts = emptyMap(),
                derivedMetrics = DerivedMetrics(
                    leanAngle = emptyList(),
                    gForce = emptyList(),
                    acceleration = emptyList(),
                    velocity = emptyList(),
                    orientation = emptyList()
                ),
                segments = emptyList(),
                statistics = RideStatistics(
                    totalDistance = 0.0,
                    totalDuration = 0L,
                    ridingDuration = 0L,
                    maxSpeed = 0f,
                    avgSpeed = 0f,
                    maxLeanAngle = 0f,
                    maxGForce = 0f,
                    totalElevationGain = 0f,
                    totalElevationLoss = 0f,
                    segmentCount = 0,
                    specialEvents = emptyList()
                ),
                errors = errors
            )
        }
    }

    /**
     * Process multiple files concurrently
     */
    suspend fun processFiles(
        csvFiles: List<File>,
        progressCallback: ((Float) -> Unit)? = null
    ): List<ProcessingResult> = withContext(Dispatchers.IO) {
        
        val results = mutableListOf<ProcessingResult>()
        
        csvFiles.forEachIndexed { index, file ->
            val fileProgress = index.toFloat() / csvFiles.size
            
            val result = processFile(file) { progress ->
                progressCallback?.invoke(fileProgress + progress / csvFiles.size)
            }
            
            results.add(result)
        }
        
        results
    }

    /**
     * Export processing result to JSON
     */
    suspend fun exportToJson(
        result: ProcessingResult,
        outputFile: File
    ) = withContext(Dispatchers.IO) {
        try {
            val exportData = createExportData(result)
            outputFile.writeText(exportData.toJson())
            Log.d(TAG, "Exported data to: ${outputFile.absolutePath}")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to export data", e)
            throw e
        }
    }

    /**
     * Create simplified export data for external analysis
     */
    private fun createExportData(result: ProcessingResult): ExportData {
        val rideInfo = RideInfo(
            fileName = result.fileInfo.fileName,
            startTime = result.fileInfo.recordingStartTime,
            endTime = result.fileInfo.recordingEndTime,
            duration = result.statistics.totalDuration,
            distance = result.statistics.totalDistance,
            isCalibrated = result.fileInfo.isCalibrated
        )
        
        val summaryStats = SummaryStats(
            maxSpeed = result.statistics.maxSpeed,
            avgSpeed = result.statistics.avgSpeed,
            maxLeanAngle = result.statistics.maxLeanAngle,
            maxGForce = result.statistics.maxGForce,
            elevationGain = result.statistics.totalElevationGain,
            elevationLoss = result.statistics.totalElevationLoss
        )
        
        // Sample time series data (every 10th sample for performance)
        val gpsPoints = result.derivedMetrics.velocity
            .filterIndexed { index, _ -> index % 10 == 0 }
            .map { velocity ->
                // Find corresponding GPS data
                GpsPoint(
                    timestamp = velocity.timestamp,
                    lat = 0.0, // Would need to correlate with actual GPS events
                    lon = 0.0,
                    alt = 0.0,
                    speed = velocity.speed
                )
            }
            
        val leanAnglePoints = result.derivedMetrics.leanAngle
            .filterIndexed { index, _ -> index % 10 == 0 }
            .map { lean ->
                AnglePoint(
                    timestamp = lean.timestamp,
                    roll = lean.rollAngle,
                    pitch = lean.pitchAngle
                )
            }
            
        val speedPoints = result.derivedMetrics.velocity
            .filterIndexed { index, _ -> index % 10 == 0 }
            .map { velocity ->
                SpeedPoint(
                    timestamp = velocity.timestamp,
                    speed = velocity.speed,
                    acceleration = velocity.acceleration
                )
            }
        
        val timeSeries = TimeSeriesData(
            gps = gpsPoints,
            leanAngles = leanAnglePoints,
            speeds = speedPoints
        )
        
        val events = result.statistics.specialEvents.map { event ->
            EventSummary(
                timestamp = event.timestamp,
                type = event.type.name,
                description = event.description,
                confidence = event.confidence
            )
        }
        
        return ExportData(
            rideInfo = rideInfo,
            summaryStats = summaryStats,
            timeSeries = timeSeries,
            events = events
        )
    }

    /**
     * Stream processing for real-time analysis
     */
    fun processStream(
        sensorDataFlow: Flow<SensorEvent>
    ): Flow<ProcessingResult> = flow {
        // Implementation would accumulate sensor events and process in chunks
        // This is a placeholder for real-time processing capability
        TODO("Stream processing not implemented in MVP")
    }
}

/**
 * Extension function to export data as JSON
 */
private fun ExportData.toJson(): String {
    // Simple JSON serialization for export data
    return buildString {
        append("{\n")
        append("  \"ride_info\": {\n")
        append("    \"fileName\": \"${rideInfo.fileName}\",\n")
        append("    \"distance\": ${rideInfo.distance}\n")
        append("  },\n")
        append("  \"summary_stats\": {\n")
        append("    \"maxSpeed\": ${summaryStats.maxSpeed},\n")
        append("    \"maxLeanAngle\": ${summaryStats.maxLeanAngle}\n")
        append("  },\n")
        append("  \"events_count\": ${events.size}\n")
        append("}")
    }
}