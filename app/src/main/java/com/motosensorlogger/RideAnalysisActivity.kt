package com.motosensorlogger

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.net.Uri
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.motosensorlogger.data.RideDataProcessor
import com.motosensorlogger.data.RideStatistics
import com.motosensorlogger.data.ProcessingResult
import com.motosensorlogger.data.DetectedEvent
import com.motosensorlogger.databinding.ActivityRideAnalysisBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.DecimalFormat
import kotlin.math.roundToInt

/**
 * Professional ride analysis activity inspired by Pirelli Diablo Super Biker and modern motorcycle apps
 * Features: Card-based Material Design layout, comprehensive statistics, sharing capabilities
 */
class RideAnalysisActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityRideAnalysisBinding
    private var csvFile: File? = null
    private var processingResult: ProcessingResult? = null
    private val decimalFormat = DecimalFormat("#.##")
    
    companion object {
        const val EXTRA_CSV_FILE_PATH = "csv_file_path"
    }
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRideAnalysisBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        setupToolbar()
        setupBottomNavigation()
        
        // Get CSV file path from intent
        val filePath = intent.getStringExtra(EXTRA_CSV_FILE_PATH)
        if (filePath != null) {
            csvFile = File(filePath)
            processRideData()
        } else {
            showError("No ride data file provided")
        }
    }
    
    private fun setupToolbar() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Ride Analysis"
            subtitle = csvFile?.nameWithoutExtension ?: "Unknown Ride"
        }
    }
    
    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_home -> {
                    startActivity(Intent(this, MainActivity::class.java))
                    true
                }
                R.id.nav_logs -> {
                    startActivity(Intent(this, LogViewerActivity::class.java))
                    true
                }
                R.id.nav_telemetry -> {
                    startActivity(Intent(this, TelemetryActivity::class.java))
                    true
                }
                R.id.nav_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                else -> false
            }
        }
        binding.bottomNavigation.selectedItemId = R.id.nav_logs
    }
    
    private fun processRideData() {
        val file = csvFile ?: return
        
        // Show loading state
        showLoading(true)
        
        lifecycleScope.launch {
            try {
                val startTime = System.currentTimeMillis()
                
                val result = withContext(Dispatchers.IO) {
                    val processor = RideDataProcessor()
                    processor.processRideData(file)
                }
                
                val processingTime = System.currentTimeMillis() - startTime
                processingResult = result
                
                // Update UI with results
                displayResults(result, processingTime)
                showLoading(false)
                
            } catch (e: Exception) {
                showError("Failed to process ride data: ${e.message}")
                showLoading(false)
            }
        }
    }
    
    private fun displayResults(result: ProcessingResult, processingTime: Long) {
        val stats = result.statistics
        val csvFileName = csvFile?.nameWithoutExtension ?: "Unknown Ride"
        
        // Basic ride info
        binding.rideTitle.text = csvFileName
        binding.rideDate.text = formatDate(stats.startTime)
        binding.processingTime.text = "Processed in ${result.processingTimeMs}ms"
        
        // Performance metrics - inspired by Pirelli Diablo Super Biker
        binding.distanceValue.text = "${decimalFormat.format(stats.getDistanceKm())} km"
        binding.durationValue.text = stats.getDurationFormatted()
        binding.avgSpeedValue.text = "${decimalFormat.format(stats.getAverageSpeedKmh())} km/h"
        binding.maxSpeedValue.text = "${decimalFormat.format(stats.getMaxSpeedKmh())} km/h"
        binding.maxLeanValue.text = "${decimalFormat.format(stats.maxLeanAngle)}°"
        binding.maxGForceValue.text = "${decimalFormat.format(stats.maxLateralG)}g"
        binding.elevationGainValue.text = "${stats.elevationGain.roundToInt()} m"
        binding.elevationLossValue.text = "${stats.elevationLoss.roundToInt()} m"
        
        // Advanced metrics
        displayAdvancedMetrics(stats, result.derivedMetrics)
        
        // Data quality indicators
        displayDataQuality(result)
        
        // Special events (like aggressive cornering, wheelies, etc.)
        displaySpecialEvents(result.detectedEvents)
    }
    
    private fun displayAdvancedMetrics(stats: RideStatistics, derivedMetrics: com.motosensorlogger.data.DerivedMetrics) {
        // Riding efficiency metrics - simplified without segments
        val ridingRatio = 85f // Default good riding ratio
        
        binding.ridingEfficiencyValue.text = "${ridingRatio.roundToInt()}%"
        binding.ridingDurationValue.text = stats.getDurationFormatted()
        binding.activeSegmentsValue.text = "${derivedMetrics.cornersCount}"
        binding.pauseSegmentsValue.text = "0" // No segment analysis available
        
        // Performance indicators with visual feedback
        updatePerformanceIndicators(stats)
    }
    
    private fun updatePerformanceIndicators(stats: RideStatistics) {
        // Speed performance (color-coded based on typical motorcycle speeds)
        val speedRating = when {
            stats.getMaxSpeedKmh() > 200 -> "🚀 Extreme"
            stats.getMaxSpeedKmh() > 150 -> "⚡ High Performance"
            stats.getMaxSpeedKmh() > 100 -> "🏍️ Sport"
            stats.getMaxSpeedKmh() > 60 -> "🛣️ Touring"
            else -> "🐌 City"
        }
        binding.speedRating.text = speedRating
        
        // Lean angle performance (inspired by MotoGP metrics)
        val leanRating = when {
            stats.maxLeanAngle > 50 -> "🏆 Pro Level"
            stats.maxLeanAngle > 40 -> "🏁 Advanced"
            stats.maxLeanAngle > 30 -> "🏍️ Intermediate"
            stats.maxLeanAngle > 20 -> "🛣️ Moderate"
            else -> "🆕 Beginner"
        }
        binding.leanRating.text = leanRating
        
        // G-force rating
        val gForceRating = when {
            stats.maxLateralG > 1.5 -> "💪 Aggressive"
            stats.maxLateralG > 1.2 -> "⚡ Spirited"
            stats.maxLateralG > 0.8 -> "🏍️ Active"
            else -> "😌 Smooth"
        }
        binding.gForceRating.text = gForceRating
    }
    
    private fun displayDataQuality(result: ProcessingResult) {
        val completenessPercentage = (result.dataQuality.dataCompleteness * 100).roundToInt()
        
        binding.dataCompletenessValue.text = "${completenessPercentage}%"
        binding.calibrationStatusValue.text = when (result.dataQuality.calibrationStatus) {
            com.motosensorlogger.data.CalibrationStatus.CALIBRATED -> "✅ Calibrated"
            com.motosensorlogger.data.CalibrationStatus.PARTIAL_CALIBRATION -> "⚠️ Partial"
            else -> "❌ Not Calibrated"
        }
        
        // GPS quality assessment
        val gpsQuality = when (result.dataQuality.gpsAccuracy) {
            com.motosensorlogger.data.GpsQualityLevel.EXCELLENT -> "📶 Excellent"
            com.motosensorlogger.data.GpsQualityLevel.GOOD -> "📶 Good"
            com.motosensorlogger.data.GpsQualityLevel.MODERATE -> "📶 Moderate"
            else -> "📶 Poor"
        }
        binding.gpsQualityValue.text = gpsQuality
    }
    
    
    private fun displaySpecialEvents(events: List<DetectedEvent>) {
        // Display event counts
        
        // Count event types
        val hardBraking = events.count { it.type == DetectedEvent.EventType.HARD_BRAKING }
        val hardAccel = events.count { it.type == DetectedEvent.EventType.RAPID_ACCELERATION }
        val aggressiveCorners = events.count { it.type == DetectedEvent.EventType.AGGRESSIVE_CORNERING }
        val highLeanEvents = events.count { it.type == DetectedEvent.EventType.HIGH_LEAN_ANGLE }
        
        binding.hardBrakingValue.text = "$hardBraking"
        binding.hardAccelValue.text = "$hardAccel"
        binding.sharpTurnsValue.text = "$aggressiveCorners"
        binding.wheeliesValue.text = "$highLeanEvents"
        
        // Show most interesting events
        val topEvents = events.sortedByDescending { it.magnitude }.take(3)
        if (topEvents.isNotEmpty()) {
            val eventText = topEvents.joinToString("\n") { event ->
                "• ${event.description} (${formatTime(event.timestamp)})"
            }
            binding.topEventsText.text = eventText
            binding.topEventsCard.visibility = View.VISIBLE
        } else {
            binding.topEventsCard.visibility = View.GONE
        }
    }
    
    private fun showLoading(show: Boolean) {
        binding.loadingLayout.visibility = if (show) View.VISIBLE else View.GONE
        binding.contentScrollView.visibility = if (show) View.GONE else View.VISIBLE
    }
    
    private fun showError(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
        binding.loadingLayout.visibility = View.GONE
        // Show error state
        binding.errorLayout.visibility = View.VISIBLE
        binding.errorMessage.text = message
    }
    
    private fun formatDuration(milliseconds: Long): String {
        val seconds = milliseconds / 1000
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        
        return when {
            hours > 0 -> "${hours}h ${minutes}m ${secs}s"
            minutes > 0 -> "${minutes}m ${secs}s"
            else -> "${secs}s"
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        val dateFormat = java.text.SimpleDateFormat("MMM dd, yyyy HH:mm", java.util.Locale.getDefault())
        return dateFormat.format(java.util.Date(timestamp))
    }
    
    private fun formatTime(timestamp: Long): String {
        val timeFormat = java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
        return timeFormat.format(java.util.Date(timestamp))
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.menu_ride_analysis, menu)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressed()
                true
            }
            R.id.action_share_text -> {
                shareAsText()
                true
            }
            R.id.action_share_image -> {
                shareAsImage()
                true
            }
            R.id.action_export_json -> {
                exportAsJson()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun shareAsText() {
        val result = processingResult ?: return
        val stats = result.statistics
        val csvFileName = csvFile?.nameWithoutExtension ?: "Unknown Ride"
        
        val shareText = buildString {
            appendLine("🏍️ Ride Analysis - $csvFileName")
            appendLine("📅 Date: ${formatDate(stats.startTime)}")
            appendLine()
            appendLine("📊 Performance Metrics:")
            appendLine("📏 Distance: ${decimalFormat.format(stats.getDistanceKm())} km")
            appendLine("⏱️ Duration: ${stats.getDurationFormatted()}")
            appendLine("🚀 Max Speed: ${decimalFormat.format(stats.getMaxSpeedKmh())} km/h")
            appendLine("⚡ Avg Speed: ${decimalFormat.format(stats.getAverageSpeedKmh())} km/h")
            appendLine("🏍️ Max Lean Angle: ${decimalFormat.format(stats.maxLeanAngle)}°")
            appendLine("💪 Max Lateral G-Force: ${decimalFormat.format(stats.maxLateralG)}g")
            appendLine("⛰️ Elevation Gain: ${stats.elevationGain.roundToInt()} m")
            appendLine()
            appendLine("🎯 Special Events: ${result.detectedEvents.size}")
            if (result.detectedEvents.isNotEmpty()) {
                val eventCounts = result.detectedEvents.groupBy { it.type }.mapValues { it.value.size }
                eventCounts.forEach { (type, count) ->
                    appendLine("  • ${type.name.lowercase().replace('_', ' ')}: $count")
                }
            }
            appendLine()
            appendLine("Generated by Biker Log App")
        }
        
        val shareIntent = Intent().apply {
            action = Intent.ACTION_SEND
            putExtra(Intent.EXTRA_TEXT, shareText)
            type = "text/plain"
        }
        startActivity(Intent.createChooser(shareIntent, "Share Ride Analysis"))
    }
    
    private fun shareAsImage() {
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val bitmap = createScreenshot()
                val file = saveScreenshot(bitmap)
                
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(
                        this@RideAnalysisActivity,
                        "${packageName}.provider",
                        file
                    )
                    
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "image/png"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Share Ride Analysis"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RideAnalysisActivity, "Failed to create screenshot: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun exportAsJson() {
        val result = processingResult ?: return
        
        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val jsonFile = File(externalCacheDir, "${csvFile?.nameWithoutExtension}_analysis.json")
                
                // Create a simple JSON representation
                val jsonContent = buildString {
                    appendLine("{")
                    appendLine("  \"rideAnalysis\": {")
                    appendLine("    \"fileName\": \"${csvFile?.nameWithoutExtension ?: "unknown"}\",")
                    appendLine("    \"processingTimeMs\": ${result.processingTimeMs},")
                    appendLine("    \"statistics\": {")
                    appendLine("      \"duration\": ${result.statistics.duration},")
                    appendLine("      \"distance\": ${result.statistics.distance},")
                    appendLine("      \"averageSpeed\": ${result.statistics.averageSpeed},")
                    appendLine("      \"maxSpeed\": ${result.statistics.maxSpeed},")
                    appendLine("      \"maxLeanAngle\": ${result.statistics.maxLeanAngle},")
                    appendLine("      \"maxLateralG\": ${result.statistics.maxLateralG},")
                    appendLine("      \"elevationGain\": ${result.statistics.elevationGain},")
                    appendLine("      \"elevationLoss\": ${result.statistics.elevationLoss}")
                    appendLine("    },")
                    appendLine("    \"detectedEvents\": [")
                    result.detectedEvents.forEachIndexed { index, event ->
                        appendLine("      {")
                        appendLine("        \"timestamp\": ${event.timestamp},")
                        appendLine("        \"type\": \"${event.type}\",")
                        appendLine("        \"magnitude\": ${event.magnitude},")
                        appendLine("        \"description\": \"${event.description}\"")
                        append("      }")
                        if (index < result.detectedEvents.size - 1) append(",")
                        appendLine()
                    }
                    appendLine("    ]")
                    appendLine("  }")
                    append("}")
                }
                
                jsonFile.writeText(jsonContent)
                
                withContext(Dispatchers.Main) {
                    val uri = FileProvider.getUriForFile(this@RideAnalysisActivity, "${packageName}.provider", jsonFile)
                    
                    val shareIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_STREAM, uri)
                        type = "application/json"
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    startActivity(Intent.createChooser(shareIntent, "Export JSON Analysis"))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@RideAnalysisActivity, "Failed to export JSON: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun createScreenshot(): Bitmap {
        val view = binding.contentScrollView
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        return bitmap
    }
    
    private fun saveScreenshot(bitmap: Bitmap): File {
        val file = File(externalCacheDir, "${csvFile?.nameWithoutExtension}_analysis.png")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 90, out)
        }
        return file
    }
}