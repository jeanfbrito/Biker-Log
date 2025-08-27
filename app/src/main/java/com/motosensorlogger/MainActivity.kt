package com.motosensorlogger

import android.Manifest
import android.content.*
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.GnssStatus
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.*
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.motosensorlogger.adapters.LogFileAdapter
import com.motosensorlogger.calibration.CalibrationService
import com.motosensorlogger.databinding.ActivityMainBinding
import com.motosensorlogger.services.SensorLoggerService
import kotlinx.coroutines.*
import java.io.File
import java.util.*

class MainActivity : AppCompatActivity(), SensorEventListener, LocationListener {
    private lateinit var binding: ActivityMainBinding
    private var sensorService: SensorLoggerService? = null
    private var isServiceBound = false
    private lateinit var logFileAdapter: LogFileAdapter

    // Sensor monitoring
    private lateinit var sensorManager: SensorManager
    private lateinit var locationManager: LocationManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var barometer: Sensor? = null

    private var isSensorStatusExpanded = false
    private var updateJob: Job? = null
    private val updateScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var serviceStateJob: Job? = null

    // Sensor values
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var lastGyro = floatArrayOf(0f, 0f, 0f)
    private var lastMag = floatArrayOf(0f, 0f, 0f)
    private var lastPressure = 0f
    private var lastLocation: Location? = null

    // GPS detailed info
    private var satellitesInView = 0
    private var satellitesUsed = 0
    private var gnssCallback: GnssStatus.Callback? = null
    private var hdop = 0.0f
    private var vdop = 0.0f
    private var fixTime = 0L
    private var gpsProvider = "GPS"

    private var calibrationJob: Job? = null

    companion object {
        private const val PERMISSION_REQUEST_CODE = 1001

        private val REQUIRED_PERMISSIONS =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
                )
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.HIGH_SAMPLING_RATE_SENSORS,
                )
            } else {
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                )
            }
    }

    private val serviceConnection =
        object : ServiceConnection {
            override fun onServiceConnected(
                name: ComponentName?,
                service: IBinder?,
            ) {
                val binder = service as SensorLoggerService.LocalBinder
                sensorService = binder.getService()
                isServiceBound = true

                // Start observing service state reactively
                observeServiceState()

                updateUI()
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                serviceStateJob?.cancel()
                sensorService = null
                isServiceBound = false
                updateUI()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        setupUI()
        setupSensorStatusCard()
        setupBottomNavigation()
        checkPermissions()
        setupRecyclerView()
    }

    private fun setupUI() {
        // Start/Stop button
        binding.btnStartStop.setOnClickListener {
            if (sensorService?.isCurrentlyLogging() == true) {
                stopLogging()
            } else {
                startLogging()
            }
        }

        // Pause/Resume button
        binding.btnPauseResume.setOnClickListener {
            if (sensorService?.isCurrentlyPaused() == true) {
                resumeLogging()
            } else {
                pauseLogging()
            }
        }

        // Refresh logs button
        binding.btnRefreshLogs.setOnClickListener {
            refreshLogsList()
        }

        updateUI()
    }

    private fun setupBottomNavigation() {
        binding.bottomNavigation.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.navigation_recording -> {
                    // Already on recording tab
                    true
                }
                R.id.navigation_telemetry -> {
                    // Launch telemetry activity
                    val intent = Intent(this, TelemetryActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                R.id.navigation_settings -> {
                    // Launch settings activity
                    val intent = Intent(this, SettingsActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
                    startActivity(intent)
                    finish()
                    true
                }
                else -> false
            }
        }

        // Set recording as default selected
        binding.bottomNavigation.selectedItemId = R.id.navigation_recording
    }

    private fun setupSensorStatusCard() {
        // Handle collapse/expand
        binding.sensorStatusHeader.setOnClickListener {
            isSensorStatusExpanded = !isSensorStatusExpanded

            if (isSensorStatusExpanded) {
                binding.sensorStatusContent.visibility = View.VISIBLE
                binding.ivExpandCollapse.rotation = 180f
                startSensorMonitoring()
            } else {
                binding.sensorStatusContent.visibility = View.GONE
                binding.ivExpandCollapse.rotation = 0f
                stopSensorMonitoring()
            }
        }

        // Update sensor availability text
        updateSensorAvailability()
    }

    private fun updateSensorAvailability() {
        binding.tvBaroStatus.text = if (barometer != null) "Pressure: -- hPa | Altitude: -- m" else "Not available"
        binding.tvMagStatus.text = if (magnetometer != null) "Mag: 0.0, 0.0, 0.0 μT" else "Not available"
    }

    private fun startSensorMonitoring() {
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
        barometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }

        // Start location updates
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            locationManager.requestLocationUpdates(
                LocationManager.GPS_PROVIDER,
                1000, // 1 second
                0f, // 0 meters
                this,
            )

            // Register GNSS status callback for satellite info
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                gnssCallback =
                    object : GnssStatus.Callback() {
                        override fun onSatelliteStatusChanged(status: GnssStatus) {
                            super.onSatelliteStatusChanged(status)
                            satellitesInView = status.satelliteCount
                            satellitesUsed = 0

                            for (i in 0 until status.satelliteCount) {
                                if (status.usedInFix(i)) {
                                    satellitesUsed++
                                }
                            }
                        }

                        override fun onStarted() {
                            super.onStarted()
                        }

                        override fun onStopped() {
                            super.onStopped()
                            satellitesInView = 0
                            satellitesUsed = 0
                        }

                        override fun onFirstFix(ttffMillis: Int) {
                            super.onFirstFix(ttffMillis)
                            fixTime = ttffMillis.toLong()
                        }
                    }

                // Register GNSS callback (requires API 30+)
                locationManager.registerGnssStatusCallback(mainExecutor, gnssCallback!!)
            }
        }

        // Start UI update coroutine
        updateJob =
            updateScope.launch {
                while (isActive) {
                    updateSensorUI()
                    delay(100) // Update UI every 100ms
                }
            }
    }

    private fun stopSensorMonitoring() {
        // Unregister sensor listeners
        sensorManager.unregisterListener(this)

        // Stop location updates
        locationManager.removeUpdates(this)

        // Unregister GNSS callback
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            gnssCallback?.let {
                locationManager.unregisterGnssStatusCallback(it)
            }
        }

        // Cancel update job
        updateJob?.cancel()
        updateJob = null
    }

    private fun updateSensorUI() {
        // Update IMU
        binding.tvImuStatus.text =
            String.format(
                "Accel: %.1f, %.1f, %.1f m/s²",
                lastAccel[0], lastAccel[1], lastAccel[2],
            )
        binding.tvGyroStatus.text =
            String.format(
                "Gyro: %.1f, %.1f, %.1f °/s",
                lastGyro[0], lastGyro[1], lastGyro[2],
            )

        // Update Magnetometer
        if (magnetometer != null) {
            binding.tvMagStatus.text =
                String.format(
                    "Mag: %.1f, %.1f, %.1f μT",
                    lastMag[0], lastMag[1], lastMag[2],
                )
        }

        // Update Barometer
        if (barometer != null && lastPressure > 0) {
            val altitude =
                SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    lastPressure,
                )
            binding.tvBaroStatus.text =
                String.format(
                    "Pressure: %.1f hPa | Altitude: %.1f m",
                    lastPressure, altitude,
                )
        }

        // Update GPS
        lastLocation?.let { loc ->
            // Calculate HDOP/VDOP approximation from accuracy
            hdop = loc.accuracy / 5.0f // Rough approximation

            // Determine fix quality
            val fixQuality =
                when {
                    loc.accuracy < 5 -> "RTK/DGPS"
                    loc.accuracy < 10 -> "Excellent"
                    loc.accuracy < 20 -> "Good"
                    loc.accuracy < 50 -> "Moderate"
                    else -> "Poor"
                }

            // Speed in km/h
            val speedKmh = loc.speed * 3.6f

            // Provider info
            gpsProvider = loc.provider ?: "Unknown"

            // First line: Fix quality and technical details
            binding.tvGpsStatus.text =
                String.format(
                    "Fix: %s | Accuracy: %.1fm | HDOP: %.1f | Speed: %.1f km/h",
                    fixQuality, loc.accuracy, hdop, speedKmh,
                )

            // Second line: Satellites and coordinates
            binding.tvGpsDetails.text =
                String.format(
                    "Sats: %d/%d | Lat: %.6f° | Lon: %.6f° | Alt: %.0fm | Provider: %s",
                    satellitesUsed, satellitesInView, loc.latitude, loc.longitude, loc.altitude, gpsProvider,
                )
        } ?: run {
            binding.tvGpsStatus.text = "Status: No fix | Searching for satellites..."
            binding.tvGpsDetails.text =
                String.format(
                    "Sats: %d/%d visible | Waiting for lock...",
                    satellitesUsed, satellitesInView,
                )
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> lastAccel = event.values.clone()
            Sensor.TYPE_GYROSCOPE -> lastGyro = event.values.clone()
            Sensor.TYPE_MAGNETIC_FIELD -> lastMag = event.values.clone()
            Sensor.TYPE_PRESSURE -> lastPressure = event.values[0]
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Not needed for this implementation
    }

    override fun onLocationChanged(location: Location) {
        lastLocation = location

        // Extract additional GPS metrics if available
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            location.extras?.let { bundle ->
                // Try to get satellite count from extras (provider-specific)
                val sats = bundle.getInt("satellites", -1)
                if (sats > 0) {
                    satellitesUsed = sats
                }
            }
        }
    }

    override fun onProviderEnabled(provider: String) {}

    override fun onProviderDisabled(provider: String) {}

    @Deprecated("Deprecated in LocationListener")
    override fun onStatusChanged(
        provider: String?,
        status: Int,
        extras: Bundle?,
    ) {}

    private fun setupRecyclerView() {
        logFileAdapter =
            LogFileAdapter(
                onItemClick = { file -> viewLogFile(file) },
                onDeleteClick = { file -> confirmDeleteFile(file) },
            )

        binding.recyclerViewLogs.apply {
            layoutManager = LinearLayoutManager(this@MainActivity)
            adapter = logFileAdapter
        }

        refreshLogsList()
    }

    private fun checkPermissions() {
        val missingPermissions =
            REQUIRED_PERMISSIONS.filter {
                ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
            }

        if (missingPermissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                missingPermissions.toTypedArray(),
                PERMISSION_REQUEST_CODE,
            )
        }

        // Check for background location permission separately (Android 10+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_BACKGROUND_LOCATION,
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                MaterialAlertDialogBuilder(this)
                    .setTitle("Background Location Required")
                    .setMessage(
                        "This app needs background location access to log GPS data while riding. Please grant 'Allow all the time' permission.",
                    )
                    .setPositiveButton("Grant") { _, _ ->
                        ActivityCompat.requestPermissions(
                            this,
                            arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                            PERMISSION_REQUEST_CODE + 1,
                        )
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        }
    }

    private fun startLogging() {
        if (!checkAllPermissions()) {
            Toast.makeText(this, "Please grant all required permissions", Toast.LENGTH_LONG).show()
            checkPermissions()
            return
        }

        // First bind to service to ensure synchronization
        bindToService()

        val serviceIntent =
            Intent(this, SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_START_LOGGING
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Update UI immediately to show calibration state
        binding.btnStartStop.text = "Stop Recording"
        binding.btnStartStop.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_dark),
        )
        binding.tvStatus.text = "Calibrating..."
        binding.btnPauseResume.isEnabled = false // Disable pause during calibration

        // Start monitoring calibration progress - this will wait for binding
        monitorCalibrationProgress()

        Toast.makeText(this, "Starting calibration...", Toast.LENGTH_SHORT).show()
    }

    private fun stopLogging() {
        val serviceIntent =
            Intent(this, SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_STOP_LOGGING
            }
        startService(serviceIntent) // This sends the STOP action to the service

        // Update UI immediately
        binding.btnStartStop.text = "Start Recording"
        binding.btnStartStop.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark),
        )
        binding.tvStatus.text = "Status: Idle"
        binding.btnPauseResume.isEnabled = false
        binding.btnPauseResume.text = "Pause"

        // Unbind and refresh list after a delay
        Handler(Looper.getMainLooper()).postDelayed({
            unbindFromService()
            refreshLogsList()
        }, 200)

        Toast.makeText(this, "Logging stopped", Toast.LENGTH_SHORT).show()
    }

    private fun pauseLogging() {
        val serviceIntent =
            Intent(this, SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_PAUSE_LOGGING
            }
        startService(serviceIntent)

        // Update UI immediately
        binding.btnPauseResume.text = "Resume"
        binding.tvStatus.text = "Status: Paused"

        Toast.makeText(this, "Logging paused", Toast.LENGTH_SHORT).show()
    }

    private fun resumeLogging() {
        val serviceIntent =
            Intent(this, SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_RESUME_LOGGING
            }
        startService(serviceIntent)

        // Update UI immediately
        binding.btnPauseResume.text = "Pause"
        binding.tvStatus.text = "Status: Recording"

        Toast.makeText(this, "Logging resumed", Toast.LENGTH_SHORT).show()
    }

    private fun bindToService() {
        if (!isServiceBound) {
            val intent = Intent(this, SensorLoggerService::class.java)
            bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
        }
    }

    private fun unbindFromService() {
        if (isServiceBound) {
            unbindService(serviceConnection)
            isServiceBound = false
            sensorService = null
        }
    }

    private fun updateUI() {
        runOnUiThread {
            val state = sensorService?.getCurrentState() ?: SensorLoggerService.ServiceState.IDLE

            when (state) {
                SensorLoggerService.ServiceState.IDLE -> {
                    binding.btnStartStop.text = "Start Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    )
                    binding.btnPauseResume.isEnabled = false
                    binding.btnPauseResume.text = "Pause"
                    binding.tvStatus.text = "Status: Idle"
                }
                SensorLoggerService.ServiceState.CALIBRATING -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = false
                    binding.tvStatus.text = "Status: Calibrating"
                }
                SensorLoggerService.ServiceState.RECORDING -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = true
                    binding.btnPauseResume.text = "Pause"
                    binding.tvStatus.text = "Status: Recording"
                }
                SensorLoggerService.ServiceState.PAUSED -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = true
                    binding.btnPauseResume.text = "Resume"
                    binding.tvStatus.text = "Status: Paused"
                }
            }
        }
    }

    private fun refreshLogsList() {
        val logFiles = getLogFiles()
        Log.d("MainActivity", "Found ${logFiles.size} log files")
        logFileAdapter.updateFiles(logFiles)

        binding.tvLogsCount.text = "Log Files: ${logFiles.size}"
    }

    private fun getLogFiles(): List<File> {
        val documentsDir = getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
        val logDir = File(documentsDir, "MotoSensorLogs")

        return logDir.listFiles { file -> file.extension == "csv" }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
    }

    private fun viewLogFile(file: File) {
        val intent =
            Intent(this, LogViewerActivity::class.java).apply {
                putExtra(LogViewerActivity.EXTRA_FILE_PATH, file.absolutePath)
            }
        startActivity(intent)
    }

    private fun shareLogFile(file: File) {
        try {
            val uri =
                FileProvider.getUriForFile(
                    this,
                    "$packageName.fileprovider",
                    file,
                )

            val shareIntent =
                Intent(Intent.ACTION_SEND).apply {
                    type = "text/csv"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Moto Sensor Log: ${file.name}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }

            startActivity(Intent.createChooser(shareIntent, "Share log file"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing file: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun confirmDeleteFile(file: File) {
        MaterialAlertDialogBuilder(this)
            .setTitle("Delete Log File")
            .setMessage("Are you sure you want to delete ${file.name}?")
            .setPositiveButton("Delete") { _, _ ->
                deleteLogFile(file)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun deleteLogFile(file: File) {
        if (file.delete()) {
            Toast.makeText(this, "File deleted", Toast.LENGTH_SHORT).show()
            refreshLogsList()
        } else {
            Toast.makeText(this, "Failed to delete file", Toast.LENGTH_SHORT).show()
        }
    }

    private fun checkAllPermissions(): Boolean {
        return REQUIRED_PERMISSIONS.all {
            ContextCompat.checkSelfPermission(this, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray,
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onStart() {
        super.onStart()
        bindToService()
    }

    override fun onStop() {
        super.onStop()
        unbindFromService()
    }

    private fun monitorCalibrationProgress() {
        calibrationJob?.cancel()
        calibrationJob =
            updateScope.launch {
                // Wait a bit for service to bind
                delay(100)

                while (!isServiceBound) {
                    delay(50)
                }

                sensorService?.let { service ->
                    // Get calibration service from sensor service
                    val calibrationService = service.getCalibrationService()

                    // Show calibration UI
                    binding.calibrationProgressLayout.visibility = View.VISIBLE

                    // Monitor calibration progress
                    calibrationService.progress.collect { progress ->
                        when (progress.state) {
                            CalibrationService.State.COLLECTING -> {
                                binding.tvCalibrationStatus.text = progress.message
                                binding.calibrationProgressBar.progress = progress.percent.toInt()
                                binding.tvCalibrationCountdown.text = "${(progress.remainingTimeMs / 1000) + 1}s"

                                // Update quality indicator with color
                                when (progress.stabilityLevel) {
                                    CalibrationService.StabilityLevel.EXCELLENT -> {
                                        binding.tvCalibrationQuality.text = "Quality: Excellent ✓"
                                        binding.tvCalibrationQuality.setTextColor(
                                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_dark),
                                        )
                                    }
                                    CalibrationService.StabilityLevel.GOOD -> {
                                        binding.tvCalibrationQuality.text = "Quality: Good"
                                        binding.tvCalibrationQuality.setTextColor(
                                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_green_light),
                                        )
                                    }
                                    CalibrationService.StabilityLevel.POOR -> {
                                        binding.tvCalibrationQuality.text = "Quality: Poor - Hold steadier"
                                        binding.tvCalibrationQuality.setTextColor(
                                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_orange_dark),
                                        )
                                        if (progress.canExtend) {
                                            binding.tvCalibrationStatus.text = "Extending time..."
                                        }
                                    }
                                    CalibrationService.StabilityLevel.BAD -> {
                                        binding.tvCalibrationQuality.text = "Quality: Too much movement!"
                                        binding.tvCalibrationQuality.setTextColor(
                                            ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark),
                                        )
                                    }
                                    CalibrationService.StabilityLevel.UNKNOWN -> {
                                        binding.tvCalibrationQuality.text = "Quality: Checking..."
                                        binding.tvCalibrationQuality.setTextColor(
                                            ContextCompat.getColor(this@MainActivity, android.R.color.darker_gray),
                                        )
                                    }
                                }
                            }
                            CalibrationService.State.PROCESSING -> {
                                binding.tvCalibrationStatus.text = "Processing..."
                                binding.calibrationProgressBar.progress = 100
                                binding.tvCalibrationCountdown.text = "..."
                            }
                            CalibrationService.State.COMPLETED -> {
                                // Hide calibration UI and show recording state
                                runOnUiThread {
                                    binding.calibrationProgressLayout.visibility = View.GONE
                                    binding.tvCalibrationQuality.text = ""
                                    binding.tvStatus.text = "Status: Recording"
                                    binding.btnStartStop.text = "Stop Recording"
                                    binding.btnStartStop.setBackgroundColor(
                                        ContextCompat.getColor(this@MainActivity, android.R.color.holo_red_dark),
                                    )
                                    binding.btnPauseResume.isEnabled = true
                                }
                                Toast.makeText(this@MainActivity, "Calibration complete!", Toast.LENGTH_SHORT).show()
                                calibrationJob?.cancel()
                            }
                            CalibrationService.State.FAILED -> {
                                // Hide calibration UI
                                binding.calibrationProgressLayout.visibility = View.GONE

                                // Cancel job first
                                calibrationJob?.cancel()

                                // Unbind from service since it's stopping
                                unbindFromService()

                                // Show calibration failure dialog after a small delay
                                val failureReason = progress.message
                                Handler(Looper.getMainLooper()).postDelayed({
                                    showCalibrationFailureDialog(failureReason)
                                }, 200)
                            }
                            else -> {}
                        }
                    }
                }
            }
    }

    private fun showCalibrationFailureDialog(failureReason: String) {
        // First reset UI state since calibration failed
        binding.tvStatus.text = "Status: Idle"
        binding.btnStartStop.text = "Start Recording"
        binding.btnStartStop.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_green_dark),
        )
        binding.btnPauseResume.isEnabled = false

        MaterialAlertDialogBuilder(this)
            .setTitle("Calibration Failed")
            .setMessage(
                "$failureReason\n\nWith the current settings, the calibration didn't meet the required quality.\n\nWhat would you like to do?",
            )
            .setPositiveButton("Retry") { _, _ ->
                // Small delay to ensure service is fully stopped before retry
                Handler(Looper.getMainLooper()).postDelayed({
                    startLogging()
                }, 500)
            }
            .setNeutralButton("Settings") { _, _ ->
                // Open settings to adjust calibration parameters
                val intent = Intent(this, SettingsActivity::class.java)
                intent.putExtra("open_calibration_settings", true)
                startActivity(intent)
            }
            .setNegativeButton("Skip Calibration") { _, _ ->
                // Start recording without calibration
                startLoggingWithoutCalibration()
            }
            .setCancelable(false)
            .show()
    }

    private fun startLoggingWithoutCalibration() {
        // Send a special action to start without calibration
        val serviceIntent =
            Intent(this, SensorLoggerService::class.java).apply {
                action = SensorLoggerService.ACTION_START_LOGGING
                putExtra("skip_calibration", true)
            }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent)
        } else {
            startService(serviceIntent)
        }

        // Update UI
        binding.btnStartStop.text = "Stop Recording"
        binding.btnStartStop.setBackgroundColor(
            ContextCompat.getColor(this, android.R.color.holo_red_dark),
        )
        binding.tvStatus.text = "Status: Recording (no calibration)"
        binding.btnPauseResume.isEnabled = true

        Toast.makeText(this, "Recording without calibration", Toast.LENGTH_SHORT).show()
    }

    private fun observeServiceState() {
        serviceStateJob?.cancel()
        serviceStateJob =
            updateScope.launch {
                sensorService?.serviceState?.collect { state ->
                    Log.d("MainActivity", "Service state changed to: $state")
                    updateUIForState(state)
                }
            }
    }

    private fun updateUIForState(state: SensorLoggerService.ServiceState) {
        runOnUiThread {
            when (state) {
                SensorLoggerService.ServiceState.IDLE -> {
                    binding.btnStartStop.text = "Start Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_green_dark),
                    )
                    binding.btnPauseResume.isEnabled = false
                    binding.btnPauseResume.text = "Pause"
                    binding.tvStatus.text = "Status: Idle"
                }
                SensorLoggerService.ServiceState.CALIBRATING -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = false
                    binding.tvStatus.text = "Status: Calibrating"
                }
                SensorLoggerService.ServiceState.RECORDING -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = true
                    binding.btnPauseResume.text = "Pause"
                    binding.tvStatus.text = "Status: Recording"
                }
                SensorLoggerService.ServiceState.PAUSED -> {
                    binding.btnStartStop.text = "Stop Recording"
                    binding.btnStartStop.setBackgroundColor(
                        ContextCompat.getColor(this, android.R.color.holo_red_dark),
                    )
                    binding.btnPauseResume.isEnabled = true
                    binding.btnPauseResume.text = "Resume"
                    binding.tvStatus.text = "Status: Paused"
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSensorMonitoring()
        calibrationJob?.cancel()
        serviceStateJob?.cancel()
        updateScope.cancel()
    }
}
