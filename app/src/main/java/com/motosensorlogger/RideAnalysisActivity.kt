package com.motosensorlogger

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.ProgressBar
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.lifecycleScope
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import com.motosensorlogger.data.ProcessingResult
import com.motosensorlogger.data.RideDataProcessor
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

class RideAnalysisActivity : AppCompatActivity() {
    
    companion object {
        const val EXTRA_FILE_PATH = "file_path"
        private const val DATE_FORMAT = "MMM dd, yyyy HH:mm"
    }
    
    private var currentFile: File? = null
    private var processingResult: ProcessingResult? = null
    private lateinit var progressBar: ProgressBar
    private lateinit var contentView: View
    private lateinit var bottomNavigation: BottomNavigationView
    
    // UI Components
    private lateinit var titleText: MaterialTextView
    private lateinit var subtitleText: MaterialTextView
    
    // Statistics Cards
    private lateinit var durationText: MaterialTextView
    private lateinit var distanceText: MaterialTextView
    private lateinit var avgSpeedText: MaterialTextView
    private lateinit var maxSpeedText: MaterialTextView
    private lateinit var maxLeanText: MaterialTextView
    private lateinit var maxGForceText: MaterialTextView
    private lateinit var elevationGainText: MaterialTextView
    private lateinit var elevationLossText: MaterialTextView
    
    // Event counts
    private lateinit var hardBrakingCountText: MaterialTextView
    private lateinit var hardAccelCountText: MaterialTextView
    private lateinit var cornersCountText: MaterialTextView
    private lateinit var specialEventsCountText: MaterialTextView
    
    // Quality indicators
    private lateinit var dataQualityText: MaterialTextView
    private lateinit var gpsQualityText: MaterialTextView
    private lateinit var calibrationStatusText: MaterialTextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_ride_analysis)
        
        // Setup action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Ride Analysis"
        }
        
        initializeViews()
        setupBottomNavigation()
        
        // Get file path from intent
        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)
        if (filePath != null) {
            loadAndAnalyzeFile(filePath)
        } else {
            showError("No file specified")
        }
    }
    
    private fun initializeViews() {
        progressBar = findViewById(R.id.progressBar)
        contentView = findViewById(R.id.contentView)
        bottomNavigation = findViewById(R.id.bottomNavigation)
        
        titleText = findViewById(R.id.titleText)
        subtitleText = findViewById(R.id.subtitleText)
        
        // Basic statistics
        durationText = findViewById(R.id.durationText)
        distanceText = findViewById(R.id.distanceText)
        avgSpeedText = findViewById(R.id.avgSpeedText)
        maxSpeedText = findViewById(R.id.maxSpeedText)
        maxLeanText = findViewById(R.id.maxLeanText)
        maxGForceText = findViewById(R.id.maxGForceText)
        elevationGainText = findViewById(R.id.elevationGainText)
        elevationLossText = findViewById(R.id.elevationLossText)
        
        // Event counts
        hardBrakingCountText = findViewById(R.id.hardBrakingCountText)
        hardAccelCountText = findViewById(R.id.hardAccelCountText)
        cornersCountText = findViewById(R.id.cornersCountText)
        specialEventsCountText = findViewById(R.id.specialEventsCountText)
        
        // Quality indicators
        dataQualityText = findViewById(R.id.dataQualityText)
        gpsQualityText = findViewById(R.id.gpsQualityText)
        calibrationStatusText = findViewById(R.id.calibrationStatusText)
    }
    
    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_recording -> {
                    startActivity(Intent(this, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                    true
                }
                R.id.navigation_telemetry -> {
                    startActivity(Intent(this, TelemetryActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                    true
                }
                R.id.navigation_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    })
                    finish()
                    true
                }
                else -> false
            }
        }
        
        // Don't select any item by default as this is a standalone analysis view
    }
    
    private fun loadAndAnalyzeFile(filePath: String) {
        val file = File(filePath)
        currentFile = file
        
        // Show loading state
        progressBar.visibility = View.VISIBLE
        contentView.visibility = View.GONE
        
        // Update title with filename
        titleText.text = file.name
        subtitleText.text = "Analyzing ride data..."
        
        // Process file asynchronously
        lifecycleScope.launch {
            try {
                val processor = RideDataProcessor()
                val result = processor.processRideData(file)
                
                processingResult = result
                displayResults(result)
                
                // Hide loading, show content
                progressBar.visibility = View.GONE
                contentView.visibility = View.VISIBLE
                
            } catch (e: Exception) {
                progressBar.visibility = View.GONE
                showError("Failed to analyze file: ${e.message}")
            }
        }
    }
    
    private fun displayResults(result: ProcessingResult) {
        val stats = result.statistics
        val metrics = result.derivedMetrics
        val events = result.detectedEvents
        val quality = result.dataQuality
        
        // Update subtitle with processing time
        subtitleText.text = "Processed in ${result.processingTimeMs}ms • ${formatDate(stats.startTime)}"
        
        // Basic statistics
        durationText.text = stats.getDurationFormatted()
        distanceText.text = String.format("%.1f km", stats.getDistanceKm())
        avgSpeedText.text = String.format("%.1f km/h", stats.getAverageSpeedKmh())
        maxSpeedText.text = String.format("%.1f km/h", stats.getMaxSpeedKmh())
        maxLeanText.text = String.format("%.1f°", stats.maxLeanAngle)
        maxGForceText.text = String.format("%.1f g", stats.maxLateralG)
        elevationGainText.text = String.format("%.0f m", stats.elevationGain)
        elevationLossText.text = String.format("%.0f m", stats.elevationLoss)
        
        // Event counts
        val hardBrakingEvents = events.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.HARD_BRAKING }
        val hardAccelEvents = events.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.RAPID_ACCELERATION }
        val corneringEvents = events.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.AGGRESSIVE_CORNERING }
        val specialEvents = events.count { it.type in listOf(
            com.motosensorlogger.data.DetectedEvent.EventType.WHEELIE,
            com.motosensorlogger.data.DetectedEvent.EventType.JUMP,
            com.motosensorlogger.data.DetectedEvent.EventType.HIGH_LEAN_ANGLE
        )}
        
        hardBrakingCountText.text = hardBrakingEvents.toString()
        hardAccelCountText.text = hardAccelEvents.toString()
        cornersCountText.text = corneringEvents.toString()
        specialEventsCountText.text = specialEvents.toString()
        
        // Data quality
        dataQualityText.text = String.format("%.0f%%", quality.dataCompleteness * 100)
        gpsQualityText.text = quality.gpsAccuracy.name.lowercase().replaceFirstChar { it.uppercase() }
        calibrationStatusText.text = when (quality.calibrationStatus) {
            com.motosensorlogger.data.CalibrationStatus.CALIBRATED -> "Calibrated"
            com.motosensorlogger.data.CalibrationStatus.PARTIAL_CALIBRATION -> "Partial"
            com.motosensorlogger.data.CalibrationStatus.NOT_CALIBRATED -> "Not Calibrated"
        }
        
        // Color coding for quality indicators
        gpsQualityText.setTextColor(getQualityColor(quality.gpsAccuracy.name))
        calibrationStatusText.setTextColor(getCalibrationColor(quality.calibrationStatus))
    }
    
    private fun getQualityColor(quality: String): Int {
        return when (quality) {
            "EXCELLENT" -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            "GOOD" -> ContextCompat.getColor(this, android.R.color.holo_green_light)
            "MODERATE" -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            "POOR" -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
            else -> ContextCompat.getColor(this, android.R.color.primary_text_light)
        }
    }
    
    private fun getCalibrationColor(status: com.motosensorlogger.data.CalibrationStatus): Int {
        return when (status) {
            com.motosensorlogger.data.CalibrationStatus.CALIBRATED -> ContextCompat.getColor(this, android.R.color.holo_green_dark)
            com.motosensorlogger.data.CalibrationStatus.PARTIAL_CALIBRATION -> ContextCompat.getColor(this, android.R.color.holo_orange_dark)
            com.motosensorlogger.data.CalibrationStatus.NOT_CALIBRATED -> ContextCompat.getColor(this, android.R.color.holo_red_dark)
        }
    }
    
    private fun formatDate(timestamp: Long): String {
        return SimpleDateFormat(DATE_FORMAT, Locale.getDefault()).format(Date(timestamp))
    }
    
    private fun showError(message: String) {
        subtitleText.text = message
        subtitleText.setTextColor(ContextCompat.getColor(this, android.R.color.holo_red_dark))
        contentView.visibility = View.VISIBLE
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }
    
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menu.add(0, 1, 0, "Share Text").setIcon(android.R.drawable.ic_menu_share)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        menu.add(0, 2, 0, "Share Image").setIcon(android.R.drawable.ic_menu_camera)
            .setShowAsAction(MenuItem.SHOW_AS_ACTION_NEVER)
        return true
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                onBackPressedDispatcher.onBackPressed()
                true
            }
            1 -> {
                shareAsText()
                true
            }
            2 -> {
                shareAsImage()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    private fun shareAsText() {
        val result = processingResult ?: return
        val stats = result.statistics
        val file = currentFile ?: return
        
        val shareText = buildString {
            appendLine("🏍️ Ride Analysis - ${file.name}")
            appendLine("📅 ${formatDate(stats.startTime)}")
            appendLine()
            appendLine("📊 RIDE STATISTICS")
            appendLine("⏱️ Duration: ${stats.getDurationFormatted()}")
            appendLine("📏 Distance: ${String.format("%.1f km", stats.getDistanceKm())}")
            appendLine("⚡ Avg Speed: ${String.format("%.1f km/h", stats.getAverageSpeedKmh())}")
            appendLine("🚀 Max Speed: ${String.format("%.1f km/h", stats.getMaxSpeedKmh())}")
            appendLine("🏍️ Max Lean: ${String.format("%.1f°", stats.maxLeanAngle)}")
            appendLine("💪 Max G-Force: ${String.format("%.1f g", stats.maxLateralG)}")
            appendLine("⛰️ Elevation Gain: ${String.format("%.0f m", stats.elevationGain)}")
            appendLine("⬇️ Elevation Loss: ${String.format("%.0f m", stats.elevationLoss)}")
            appendLine()
            appendLine("🎯 EVENTS")
            val hardBraking = result.detectedEvents.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.HARD_BRAKING }
            val hardAccel = result.detectedEvents.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.RAPID_ACCELERATION }
            val cornering = result.detectedEvents.count { it.type == com.motosensorlogger.data.DetectedEvent.EventType.AGGRESSIVE_CORNERING }
            appendLine("🛑 Hard Braking: $hardBraking")
            appendLine("🚀 Hard Acceleration: $hardAccel")
            appendLine("🏁 Aggressive Cornering: $cornering")
            appendLine()
            appendLine("📱 Generated by Moto Sensor Logger")
        }
        
        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            putExtra(Intent.EXTRA_SUBJECT, "Ride Analysis - ${file.name}")
        }
        
        startActivity(Intent.createChooser(shareIntent, "Share Ride Analysis"))
    }
    
    private fun shareAsImage() {
        try {
            // Create bitmap from the content view
            val bitmap = createBitmapFromView(contentView)
            
            // Save to cache directory
            val imageFile = File(cacheDir, "ride_analysis_${System.currentTimeMillis()}.png")
            FileOutputStream(imageFile).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
            }
            
            val uri = FileProvider.getUriForFile(
                this,
                "$packageName.fileprovider",
                imageFile
            )
            
            val shareIntent = Intent(Intent.ACTION_SEND).apply {
                type = "image/png"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Ride Analysis - ${currentFile?.name}")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            
            startActivity(Intent.createChooser(shareIntent, "Share Ride Analysis Image"))
            
        } catch (e: Exception) {
            Toast.makeText(this, "Failed to create image: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
    
    private fun createBitmapFromView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val background: Drawable? = view.background
        
        if (background != null) {
            background.draw(canvas)
        } else {
            canvas.drawColor(Color.WHITE)
        }
        
        view.draw(canvas)
        return bitmap
    }
}