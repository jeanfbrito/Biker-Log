package com.motosensorlogger

import android.graphics.Color
import android.os.Bundle
import android.text.InputType
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.addTextChangedListener
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect

class SettingsActivity : AppCompatActivity() {
    
    private lateinit var settingsManager: SettingsManager
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    
    // UI elements
    private lateinit var calibrationDurationSeekBar: SeekBar
    private lateinit var calibrationDurationValue: TextView
    private lateinit var calibrationMinSamplesValue: TextView
    private lateinit var vibrationBaselineSwitch: Switch
    private lateinit var magneticCalibrationSwitch: Switch
    private lateinit var samplingRateSpinner: Spinner
    private lateinit var autoStopLowBatterySwitch: Switch
    private lateinit var batteryThresholdSeekBar: SeekBar
    private lateinit var batteryThresholdValue: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        settingsManager = SettingsManager.getInstance(this)
        
        // Create UI
        val scrollView = ScrollView(this).apply {
            setBackgroundColor(Color.parseColor("#1a1a1a"))
        }
        
        val mainLayout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(20, 20, 20, 20)
        }
        
        // Title
        addSectionTitle(mainLayout, "CALIBRATION SETTINGS")
        
        // Calibration Duration
        addCalibrationDurationSetting(mainLayout)
        
        // Minimum Samples
        addMinimumSamplesSetting(mainLayout)
        
        // Vibration Baseline
        addVibrationBaselineSetting(mainLayout)
        
        // Magnetic Calibration
        addMagneticCalibrationSetting(mainLayout)
        
        addDivider(mainLayout)
        
        // Sensor Settings
        addSectionTitle(mainLayout, "SENSOR SETTINGS")
        
        // Sampling Rate
        addSamplingRateSetting(mainLayout)
        
        addDivider(mainLayout)
        
        // Power Settings
        addSectionTitle(mainLayout, "POWER SETTINGS")
        
        // Auto-stop on low battery
        addAutoStopSetting(mainLayout)
        
        // Battery threshold
        addBatteryThresholdSetting(mainLayout)
        
        addDivider(mainLayout)
        
        // Reset button
        addResetButton(mainLayout)
        
        scrollView.addView(mainLayout)
        setContentView(scrollView)
        
        // Setup action bar
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            title = "Settings"
        }
        
        // Load current settings
        loadCurrentSettings()
    }
    
    private fun addSectionTitle(layout: LinearLayout, title: String) {
        val titleView = TextView(this).apply {
            text = title
            textSize = 18f
            setTextColor(Color.WHITE)
            setPadding(0, 30, 0, 15)
            setTypeface(typeface, android.graphics.Typeface.BOLD)
        }
        layout.addView(titleView)
    }
    
    private fun addCalibrationDurationSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        
        // Label
        val labelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val label = TextView(this).apply {
            text = "Calibration Duration"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelLayout.addView(label)
        
        calibrationDurationValue = TextView(this).apply {
            text = "2.0s"
            textSize = 16f
            setTextColor(Color.CYAN)
        }
        labelLayout.addView(calibrationDurationValue)
        
        container.addView(labelLayout)
        
        // Description
        val description = TextView(this).apply {
            text = "How long to collect stationary data for phone position and vibration baseline"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 5, 0, 10)
        }
        container.addView(description)
        
        // SeekBar (1-10 seconds, in 0.5s steps)
        calibrationDurationSeekBar = SeekBar(this).apply {
            max = 18 // (10 - 1) / 0.5 = 18 steps
            progress = 2 // Default 2 seconds = (2-1)/0.5 = 2
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val seconds = 1.0f + (progress * 0.5f)
                        calibrationDurationValue.text = "${seconds}s"
                        settingsManager.setCalibrationDuration((seconds * 1000).toLong())
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(calibrationDurationSeekBar)
        
        layout.addView(container)
    }
    
    private fun addMinimumSamplesSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        
        val label = TextView(this).apply {
            text = "Minimum Samples"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(label)
        
        calibrationMinSamplesValue = TextView(this).apply {
            text = "50"
            textSize = 16f
            setTextColor(Color.CYAN)
            setPadding(10, 0, 10, 0)
        }
        container.addView(calibrationMinSamplesValue)
        
        val editButton = Button(this).apply {
            text = "EDIT"
            textSize = 12f
            setPadding(20, 5, 20, 5)
            setBackgroundColor(Color.parseColor("#333333"))
            setTextColor(Color.WHITE)
            
            setOnClickListener {
                showNumberInputDialog("Minimum Samples", calibrationMinSamplesValue.text.toString().toInt()) { value ->
                    if (value in 10..500) {
                        calibrationMinSamplesValue.text = value.toString()
                        settingsManager.setCalibrationMinSamples(value)
                    }
                }
            }
        }
        container.addView(editButton)
        
        layout.addView(container)
    }
    
    private fun addVibrationBaselineSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val label = TextView(this).apply {
            text = "Capture Vibration Baseline"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        switchLayout.addView(label)
        
        vibrationBaselineSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setVibrationBaselineEnabled(isChecked)
            }
        }
        switchLayout.addView(vibrationBaselineSwitch)
        
        container.addView(switchLayout)
        
        val description = TextView(this).apply {
            text = "Record motorcycle's idle vibration pattern during calibration"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 5, 0, 0)
        }
        container.addView(description)
        
        layout.addView(container)
    }
    
    private fun addMagneticCalibrationSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        
        val switchLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val label = TextView(this).apply {
            text = "Magnetic Field Calibration"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        switchLayout.addView(label)
        
        magneticCalibrationSwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setMagneticCalibrationEnabled(isChecked)
            }
        }
        switchLayout.addView(magneticCalibrationSwitch)
        
        container.addView(switchLayout)
        
        val description = TextView(this).apply {
            text = "Compensate for motorcycle's magnetic interference"
            textSize = 12f
            setTextColor(Color.GRAY)
            setPadding(0, 5, 0, 0)
        }
        container.addView(description)
        
        layout.addView(container)
    }
    
    private fun addSamplingRateSetting(layout: LinearLayout) {
        // Add helper text about sampling rates
        TextView(this).apply {
            text = "Lower rates save battery and reduce file size (~60% smaller at 50 Hz vs 100 Hz)"
            setTextColor(Color.parseColor("#888888"))
            textSize = 12f
            setPadding(0, 0, 0, 10)
            layout.addView(this)
        }
        
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        
        val label = TextView(this).apply {
            text = "IMU Sampling Rate"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(label)
        
        val rates = arrayOf("10 Hz (Ultra Low)", "25 Hz (Low)", "50 Hz (Recommended)", "75 Hz (Medium)", "100 Hz (High)", "150 Hz (Very High)", "200 Hz (Maximum)")
        val rateValues = intArrayOf(10, 25, 50, 75, 100, 150, 200)
        
        samplingRateSpinner = Spinner(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_spinner_dropdown_item, rates).apply {
                setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
            }
        }
        container.addView(samplingRateSpinner)
        
        layout.addView(container)
        
        // Add dynamic feedback text
        val feedbackText = TextView(this).apply {
            text = "Estimated file size: ~2.6 MB for 5 minutes"
            setTextColor(Color.parseColor("#00FF00"))
            textSize = 12f
            setPadding(0, 5, 0, 10)
        }
        layout.addView(feedbackText)
        
        // Update feedback when selection changes
        samplingRateSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                settingsManager.setSensorSamplingRate(rateValues[position])
                
                // Update feedback text based on selection
                val estimatedSizeMB = when (rateValues[position]) {
                    10 -> 0.5f
                    25 -> 1.3f
                    50 -> 2.6f  // Recommended
                    75 -> 3.9f
                    100 -> 5.2f
                    150 -> 7.8f
                    200 -> 10.4f
                    else -> 2.6f
                }
                
                val batteryImpact = when (rateValues[position]) {
                    10 -> "Minimal"
                    25 -> "Very Low"
                    50 -> "Low"  // Recommended
                    75 -> "Moderate"
                    100 -> "High"
                    150 -> "Very High"
                    200 -> "Maximum"
                    else -> "Low"
                }
                
                feedbackText.text = "Est. file size: ~${estimatedSizeMB} MB/5min | Battery impact: $batteryImpact"
                feedbackText.setTextColor(
                    when {
                        rateValues[position] <= 50 -> Color.parseColor("#00FF00") // Green for optimal
                        rateValues[position] <= 100 -> Color.parseColor("#FFFF00") // Yellow for moderate
                        else -> Color.parseColor("#FF8800") // Orange for high
                    }
                )
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }
    
    private fun addAutoStopSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(0, 10, 0, 10)
        }
        
        val label = TextView(this).apply {
            text = "Auto-stop on Low Battery"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        container.addView(label)
        
        autoStopLowBatterySwitch = Switch(this).apply {
            isChecked = true
            setOnCheckedChangeListener { _, isChecked ->
                settingsManager.setAutoStopOnLowBattery(isChecked)
                batteryThresholdSeekBar.isEnabled = isChecked
            }
        }
        container.addView(autoStopLowBatterySwitch)
        
        layout.addView(container)
    }
    
    private fun addBatteryThresholdSetting(layout: LinearLayout) {
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, 10, 0, 10)
        }
        
        val labelLayout = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        
        val label = TextView(this).apply {
            text = "Battery Threshold"
            textSize = 16f
            setTextColor(Color.WHITE)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        labelLayout.addView(label)
        
        batteryThresholdValue = TextView(this).apply {
            text = "15%"
            textSize = 16f
            setTextColor(Color.YELLOW)
        }
        labelLayout.addView(batteryThresholdValue)
        
        container.addView(labelLayout)
        
        batteryThresholdSeekBar = SeekBar(this).apply {
            max = 40 // 5% to 45%
            progress = 10 // Default 15%
            
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        val percent = progress + 5
                        batteryThresholdValue.text = "$percent%"
                        settingsManager.setLowBatteryThreshold(percent)
                    }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {}
            })
        }
        container.addView(batteryThresholdSeekBar)
        
        layout.addView(container)
    }
    
    private fun addResetButton(layout: LinearLayout) {
        val button = Button(this).apply {
            text = "RESET ALL SETTINGS"
            textSize = 14f
            setBackgroundColor(Color.parseColor("#CC0000"))
            setTextColor(Color.WHITE)
            setPadding(30, 20, 30, 20)
            
            setOnClickListener {
                AlertDialog.Builder(this@SettingsActivity)
                    .setTitle("Reset Settings")
                    .setMessage("Are you sure you want to reset all settings to defaults?")
                    .setPositiveButton("Reset") { _, _ ->
                        settingsManager.resetAllSettings()
                        loadCurrentSettings()
                        Toast.makeText(this@SettingsActivity, "Settings reset to defaults", Toast.LENGTH_SHORT).show()
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
        
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER
            setPadding(0, 40, 0, 20)
            addView(button)
        }
        
        layout.addView(buttonContainer)
    }
    
    private fun addDivider(layout: LinearLayout) {
        val divider = View(this).apply {
            setBackgroundColor(Color.parseColor("#333333"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                2
            ).apply {
                setMargins(0, 20, 0, 0)
            }
        }
        layout.addView(divider)
    }
    
    private fun showNumberInputDialog(title: String, currentValue: Int, onConfirm: (Int) -> Unit) {
        val editText = EditText(this).apply {
            inputType = InputType.TYPE_CLASS_NUMBER
            setText(currentValue.toString())
            setTextColor(Color.BLACK)
        }
        
        AlertDialog.Builder(this)
            .setTitle(title)
            .setView(editText)
            .setPositiveButton("OK") { _, _ ->
                val value = editText.text.toString().toIntOrNull()
                if (value != null) {
                    onConfirm(value)
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    
    private fun loadCurrentSettings() {
        scope.launch {
            settingsManager.calibrationSettings.collect { settings ->
                // Update calibration duration
                val seconds = settings.durationMs / 1000f
                calibrationDurationValue.text = "${seconds}s"
                calibrationDurationSeekBar.progress = ((seconds - 1) * 2).toInt()
                
                // Update other settings
                calibrationMinSamplesValue.text = settings.minSamples.toString()
                vibrationBaselineSwitch.isChecked = settings.captureVibrationBaseline
                magneticCalibrationSwitch.isChecked = settings.captureMagneticBaseline
            }
        }
        
        scope.launch {
            settingsManager.sensorSettings.collect { settings ->
                // Update sampling rate spinner
                val rate = settings.samplingRateHz
                val position = when (rate) {
                    10 -> 0
                    25 -> 1
                    50 -> 2  // Default/Recommended
                    75 -> 3
                    100 -> 4
                    150 -> 5
                    200 -> 6
                    else -> 2 // Default to 50Hz (recommended)
                }
                samplingRateSpinner.setSelection(position)
            }
        }
        
        scope.launch {
            settingsManager.powerSettings.collect { settings ->
                autoStopLowBatterySwitch.isChecked = settings.autoStopOnLowBattery
                batteryThresholdSeekBar.isEnabled = settings.autoStopOnLowBattery
                batteryThresholdValue.text = "${settings.lowBatteryThreshold}%"
                batteryThresholdSeekBar.progress = settings.lowBatteryThreshold - 5
            }
        }
    }
    
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            android.R.id.home -> {
                finish()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }
    
    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}