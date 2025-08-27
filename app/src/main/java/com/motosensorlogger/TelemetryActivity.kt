package com.motosensorlogger

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.graphics.Color
import android.graphics.Typeface
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Bundle
import android.os.IBinder
import android.view.MenuItem
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.motosensorlogger.data.SensorDataFilter
import com.motosensorlogger.services.SensorLoggerService
import com.motosensorlogger.views.BarInclinometerView
import com.motosensorlogger.views.GForceView
import com.motosensorlogger.views.InclinometerView
import com.motosensorlogger.views.MotoInclinometerView
import kotlinx.coroutines.*
import kotlin.math.*

class TelemetryActivity : AppCompatActivity(), SensorEventListener {
    private lateinit var sensorManager: SensorManager
    private lateinit var bottomNavigation: BottomNavigationView

    // Sensors
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    
    // Advanced noise filter for telemetry display
    private lateinit var telemetryFilter: SensorDataFilter

    // Custom views
    private lateinit var inclinometerView: InclinometerView
    private lateinit var barInclinometerView: BarInclinometerView
    private lateinit var motoInclinometerView: MotoInclinometerView
    private lateinit var gForceView: GForceView
    private var inclinometerStyle = 0 // 0=classic, 1=bar, 2=moto

    // Buttons
    private lateinit var btnZero: Button
    private lateinit var btnReset: Button

    // Text displays
    private lateinit var tvPitchValue: TextView
    private lateinit var tvRollValue: TextView
    private lateinit var tvGForceX: TextView
    private lateinit var tvGForceY: TextView
    private lateinit var tvGForceZ: TextView
    private lateinit var tvGForceMag: TextView

    // Sensor values
    private val gravity = FloatArray(3)
    private val linearAcceleration = FloatArray(3)

    // Filtered values
    private var pitch = 0f
    private var roll = 0f
    private var gForceX = 0f
    private var gForceY = 0f
    private var gForceZ = 0f

    // Update job
    private var updateJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Low-pass filter alpha
    private val ALPHA = 0.8f

    // Service binding
    private var sensorService: SensorLoggerService? = null
    private var serviceBound = false

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as SensorLoggerService.LocalBinder
                sensorService = binder.getService()
                serviceBound = true

                // Apply calibration from service if it's recording
                if (sensorService?.isCurrentlyLogging() == true) {
                    sensorService?.getCalibrationData()?.let { calibData ->
                        // Apply the same calibration offsets to the telemetry views
                        inclinometerView.setCalibrationOffsets(calibData.referencePitch, calibData.referenceRoll)
                        barInclinometerView.setCalibrationOffsets(calibData.referencePitch, calibData.referenceRoll)
                        motoInclinometerView.setCalibrationOffsets(calibData.referencePitch, calibData.referenceRoll)
                        showCalibrationStatus(true)
                    }
                }
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                sensorService = null
                serviceBound = false
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_telemetry)
        
        // Initialize bottom navigation
        bottomNavigation = findViewById(R.id.bottomNavigation)
        setupBottomNavigation()
        
        // Initialize advanced noise filter for smooth telemetry display
        // Use slightly less aggressive filtering for telemetry to maintain responsiveness
        telemetryFilter = SensorDataFilter(
            windowSize = 7,  // Slightly larger window for smoother display
            outlierSigmaThreshold = 2.5f,  // More aggressive outlier removal for display
            enableOutlierDetection = true,
            enableMovingAverage = true
        )

        // Get the content container from the layout
        val telemetryContent = findViewById<LinearLayout>(R.id.telemetryContent)
        
        // Create layout programmatically inside the content container
        val mainLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setBackgroundColor(Color.BLACK)
                setPadding(0, 0, 0, 0)
            }

        // Title for Inclinometer
        val inclinometerTitle =
            TextView(this).apply {
                text = "INCLINOMETER"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 8)
                gravity = android.view.Gravity.CENTER
            }
        mainLayout.addView(inclinometerTitle)

        // Container for inclinometer views (declare early for button access)
        val inclinometerContainer =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        0,
                    ).apply {
                        weight = 1f
                    }
            }

        // Classic inclinometer view
        inclinometerView = InclinometerView(this)
        val inclinometerParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT,
            )

        // Bar inclinometer view
        barInclinometerView = BarInclinometerView(this)

        // Motorcycle inclinometer view
        motoInclinometerView = MotoInclinometerView(this)

        // Initially show classic view
        inclinometerContainer.addView(inclinometerView, inclinometerParams)

        // Calibration buttons layout
        val calibrationLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = android.view.Gravity.CENTER
                setPadding(0, 8, 0, 8)
            }

        btnZero =
            Button(this).apply {
                text = "ZERO"
                setBackgroundColor(Color.argb(255, 0, 150, 0))
                setTextColor(Color.WHITE)
                setPadding(40, 20, 40, 20)
                setOnClickListener {
                    inclinometerView.zeroCalibration()
                    barInclinometerView.zeroCalibration()
                    motoInclinometerView.zeroCalibration()
                    showCalibrationStatus(true)
                }
            }
        calibrationLayout.addView(btnZero)

        // Add spacing between buttons
        val spacer =
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 1)
            }
        calibrationLayout.addView(spacer)

        btnReset =
            Button(this).apply {
                text = "RESET"
                setBackgroundColor(Color.argb(255, 150, 0, 0))
                setTextColor(Color.WHITE)
                setPadding(40, 20, 40, 20)
                setOnClickListener {
                    inclinometerView.resetCalibration()
                    barInclinometerView.resetCalibration()
                    motoInclinometerView.resetCalibration()
                    // Also reset the noise filter to clear any accumulated data
                    telemetryFilter.reset()
                    showCalibrationStatus(false)
                }
            }
        calibrationLayout.addView(btnReset)

        // Add spacer
        val spacer2 =
            View(this).apply {
                layoutParams = LinearLayout.LayoutParams(40, 1)
            }
        calibrationLayout.addView(spacer2)

        // Add style toggle button
        val btnToggleStyle =
            Button(this).apply {
                text = "STYLE"
                setBackgroundColor(Color.argb(255, 0, 100, 150))
                setTextColor(Color.WHITE)
                setPadding(40, 20, 40, 20)
                setOnClickListener {
                    inclinometerStyle = (inclinometerStyle + 1) % 3
                    inclinometerContainer.removeAllViews()
                    when (inclinometerStyle) {
                        0 -> inclinometerContainer.addView(inclinometerView, inclinometerParams)
                        1 -> inclinometerContainer.addView(barInclinometerView, inclinometerParams)
                        2 -> inclinometerContainer.addView(motoInclinometerView, inclinometerParams)
                    }
                }
            }
        calibrationLayout.addView(btnToggleStyle)

        mainLayout.addView(calibrationLayout)
        mainLayout.addView(inclinometerContainer)

        // Pitch and Roll text displays
        val angleLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }

        val pitchLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val pitchLabel =
            TextView(this).apply {
                text = "PITCH"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        pitchLayout.addView(pitchLabel)

        tvPitchValue =
            TextView(this).apply {
                text = "0.0°"
                textSize = 24f
                setTextColor(Color.GREEN)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        pitchLayout.addView(tvPitchValue)

        val rollLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val rollLabel =
            TextView(this).apply {
                text = "ROLL (LEAN)"
                textSize = 14f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        rollLayout.addView(rollLabel)

        tvRollValue =
            TextView(this).apply {
                text = "0.0°"
                textSize = 24f
                setTextColor(Color.CYAN)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        rollLayout.addView(tvRollValue)

        angleLayout.addView(pitchLayout)
        angleLayout.addView(rollLayout)
        mainLayout.addView(angleLayout)

        // Divider
        val divider =
            View(this).apply {
                setBackgroundColor(Color.GRAY)
                layoutParams =
                    LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        2,
                    ).apply {
                        setMargins(0, 16, 0, 16)
                    }
            }
        mainLayout.addView(divider)

        // Title for G-Force
        val gForceTitle =
            TextView(this).apply {
                text = "G-FORCE METER"
                textSize = 20f
                setTextColor(Color.WHITE)
                setPadding(0, 16, 0, 8)
                gravity = android.view.Gravity.CENTER
            }
        mainLayout.addView(gForceTitle)

        // G-Force view
        gForceView =
            GForceView(this).apply {
                // Configure G-Force view settings
                maxGForce = 2f
                showTrail = true
                showVerticalBar = true
                showLabels = true
                showGrid = true
            }
        val gForceParams =
            LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                0,
            ).apply {
                weight = 1f
            }
        mainLayout.addView(gForceView, gForceParams)

        // G-Force text displays
        val gForceTextLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(0, 16, 0, 16)
            }

        // Lateral G (X - left/right)
        val lateralLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val lateralLabel =
            TextView(this).apply {
                text = "LATERAL"
                textSize = 12f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        lateralLayout.addView(lateralLabel)

        tvGForceX =
            TextView(this).apply {
                text = "0.0g"
                textSize = 18f
                setTextColor(Color.RED)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        lateralLayout.addView(tvGForceX)

        // Longitudinal G (Y - accel/brake)
        val longitudinalLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val longitudinalLabel =
            TextView(this).apply {
                text = "ACCEL/BRAKE"
                textSize = 12f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        longitudinalLayout.addView(longitudinalLabel)

        tvGForceY =
            TextView(this).apply {
                text = "0.0g"
                textSize = 18f
                setTextColor(Color.GREEN)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        longitudinalLayout.addView(tvGForceY)

        // Vertical G (Z - bumps/jumps)
        val verticalLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val verticalLabel =
            TextView(this).apply {
                text = "VERTICAL"
                textSize = 12f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        verticalLayout.addView(verticalLabel)

        tvGForceZ =
            TextView(this).apply {
                text = "1.0g"
                textSize = 18f
                setTextColor(Color.BLUE)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        verticalLayout.addView(tvGForceZ)

        // Total G
        val totalLayout =
            LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
            }

        val totalLabel =
            TextView(this).apply {
                text = "TOTAL"
                textSize = 12f
                setTextColor(Color.GRAY)
                gravity = android.view.Gravity.CENTER
            }
        totalLayout.addView(totalLabel)

        tvGForceMag =
            TextView(this).apply {
                text = "1.0g"
                textSize = 18f
                setTextColor(Color.YELLOW)
                gravity = android.view.Gravity.CENTER
                typeface = Typeface.MONOSPACE
            }
        totalLayout.addView(tvGForceMag)

        gForceTextLayout.addView(lateralLayout)
        gForceTextLayout.addView(longitudinalLayout)
        gForceTextLayout.addView(verticalLayout)
        gForceTextLayout.addView(totalLayout)
        mainLayout.addView(gForceTextLayout)

        // Add the main layout to the content container
        telemetryContent.removeAllViews()
        telemetryContent.addView(mainLayout)

        // Hide action bar since we're using bottom navigation
        supportActionBar?.hide()

        // Initialize sensor manager
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager

        // Get sensors
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)

        // Start monitoring
        startSensorMonitoring()
        startUIUpdates()

        // Bind to sensor service if it's running
        val serviceIntent = Intent(this, SensorLoggerService::class.java)
        bindService(serviceIntent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun showCalibrationStatus(calibrated: Boolean) {
        val message = if (calibrated) "Calibration Set" else "Calibration Reset"
        // The inclinometer view will show "CAL" indicator when calibrated

        // Update button appearances
        if (calibrated) {
            btnZero.alpha = 0.6f
            btnReset.alpha = 1.0f
        } else {
            btnZero.alpha = 1.0f
            btnReset.alpha = 0.6f
        }
    }

    private fun startSensorMonitoring() {
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    private fun startUIUpdates() {
        updateJob =
            updateScope.launch {
                while (isActive) {
                    updateUI()
                    delay(50) // Update at 20Hz for smooth visualization
                }
            }
    }

    private fun updateUI() {
        // Update all inclinometers
        inclinometerView.setAngles(pitch, roll)
        barInclinometerView.setAngles(pitch, roll)
        motoInclinometerView.setAngles(pitch, roll)

        // Get display angles (after calibration) from active view
        val displayAngles =
            when (inclinometerStyle) {
                1 -> barInclinometerView.getDisplayAngles()
                2 -> motoInclinometerView.getDisplayAngles()
                else -> inclinometerView.getDisplayAngles()
            }
        val displayPitch = displayAngles.first
        val displayRoll = displayAngles.second

        // Update angle text displays
        tvPitchValue.text = String.format("%+.1f°", displayPitch)
        tvRollValue.text = String.format("%+.1f°", displayRoll)

        // Color code based on angle severity
        tvPitchValue.setTextColor(
            when {
                abs(displayPitch) > 30 -> Color.RED
                abs(displayPitch) > 20 -> Color.YELLOW
                else -> Color.GREEN
            },
        )

        tvRollValue.setTextColor(
            when {
                abs(displayRoll) > 35 -> Color.RED
                abs(displayRoll) > 25 -> Color.YELLOW
                else -> Color.CYAN
            },
        )

        // Update G-force
        gForceView.setGForce(gForceX, gForceY, gForceZ)

        // Update G-force text displays
        tvGForceX.text = String.format("%+.2fg", gForceX)
        tvGForceY.text = String.format("%+.2fg", gForceY)
        tvGForceZ.text = String.format("%.2fg", gForceZ)

        val magnitude = gForceView.getTotalMagnitude()
        tvGForceMag.text = String.format("%.2fg", magnitude)

        // Color code G-forces using the view's color logic
        tvGForceX.setTextColor(gForceView.getColorForMagnitude(abs(gForceX)))
        tvGForceY.setTextColor(gForceView.getColorForMagnitude(abs(gForceY)))
        tvGForceZ.setTextColor(
            when {
                abs(gForceZ - 1.0f) > 0.5f -> Color.RED
                abs(gForceZ - 1.0f) > 0.3f -> Color.YELLOW
                else -> Color.WHITE
            },
        )
        tvGForceMag.setTextColor(gForceView.getColorForMagnitude(magnitude))
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                // For telemetry display, we don't filter accelerometer alone
                // We need both accel and gyro for proper IMU filtering
                // So we'll use raw values here and rely on the low-pass filter below
                val rawValues = event.values
                
                // Apply low-pass filter to isolate gravity (on raw values)
                gravity[0] = ALPHA * gravity[0] + (1 - ALPHA) * rawValues[0]
                gravity[1] = ALPHA * gravity[1] + (1 - ALPHA) * rawValues[1]
                gravity[2] = ALPHA * gravity[2] + (1 - ALPHA) * rawValues[2]

                // Remove gravity to get linear acceleration
                linearAcceleration[0] = rawValues[0] - gravity[0]
                linearAcceleration[1] = rawValues[1] - gravity[1]
                linearAcceleration[2] = rawValues[2] - gravity[2]

                // Convert to G-force (divide by 9.81)
                gForceX = linearAcceleration[0] / 9.81f
                gForceY = linearAcceleration[1] / 9.81f
                gForceZ = rawValues[2] / 9.81f // Use raw Z for vertical G including gravity

                // Calculate pitch and roll from gravity vector
                // Pitch: rotation around X axis (forward/backward tilt)
                pitch =
                    Math.toDegrees(
                        atan2(
                            gravity[1].toDouble(),
                            sqrt((gravity[0] * gravity[0] + gravity[2] * gravity[2]).toDouble()),
                        ),
                    ).toFloat()

                // Roll: rotation around Y axis (left/right lean)
                roll = Math.toDegrees(atan2(-gravity[0].toDouble(), gravity[2].toDouble())).toFloat()

                // No limits - full range of motion
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Not needed
    }

    private fun setupBottomNavigation() {
        bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_recording -> {
                    // Navigate back to main activity
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    overridePendingTransition(0, 0) // No animation for tab-like navigation
                    finish()
                    true
                }
                R.id.navigation_telemetry -> {
                    // Already on telemetry tab
                    true
                }
                R.id.navigation_settings -> {
                    // Launch settings activity
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    overridePendingTransition(0, 0) // No animation for tab-like navigation
                    finish()
                    true
                }
                else -> false
            }
        }

        // Set telemetry as selected
        bottomNavigation.selectedItemId = R.id.navigation_telemetry
    }

    override fun onResume() {
        super.onResume()
        // Reset filter when resuming to avoid stale data
        telemetryFilter.reset()
        
        // Register sensor listeners
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }
    
    override fun onPause() {
        super.onPause()
        // Unregister sensor listeners to save battery
        sensorManager.unregisterListener(this)
    }

    override fun onDestroy() {
        super.onDestroy()
        updateJob?.cancel()
        updateScope.cancel()
        sensorManager.unregisterListener(this)

        // Unbind from service
        if (serviceBound) {
            unbindService(serviceConnection)
            serviceBound = false
        }
    }
}
