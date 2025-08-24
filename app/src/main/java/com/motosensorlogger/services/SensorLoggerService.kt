package com.motosensorlogger.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.*
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.motosensorlogger.MainActivity
import com.motosensorlogger.calibration.CalibrationData
import com.motosensorlogger.calibration.CalibrationService
import com.motosensorlogger.data.*
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean

class SensorLoggerService : Service(), SensorEventListener {
    companion object {
        const val ACTION_START_LOGGING = "com.motosensorlogger.START_LOGGING"
        const val ACTION_STOP_LOGGING = "com.motosensorlogger.STOP_LOGGING"
        const val ACTION_PAUSE_LOGGING = "com.motosensorlogger.PAUSE_LOGGING"
        const val ACTION_RESUME_LOGGING = "com.motosensorlogger.RESUME_LOGGING"

        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sensor_logger_channel"

        // Default sensor sampling rates (microseconds)
        // These will be overridden by user settings
        private const val DEFAULT_IMU_SAMPLING_PERIOD = 20000 // 50Hz (optimized default)
        private const val MAG_SAMPLING_PERIOD = 40000 // 25Hz
        private const val BARO_SAMPLING_PERIOD = 40000 // 25Hz

        // GPS update intervals
        private const val GPS_UPDATE_INTERVAL = 200L // 5Hz default
        private const val GPS_HIGH_RATE_INTERVAL = 100L // 10Hz for cornering
        private const val CORNERING_ENTER_THRESHOLD = 0.3f // ~17 deg/s to enter high rate
        private const val CORNERING_EXIT_THRESHOLD = 0.25f // ~14 deg/s to exit high rate (hysteresis)
        private const val RATE_CHANGE_DEBOUNCE_MS = 500L // Debounce period for rate changes
    }

    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var csvLogger: CsvLogger
    private lateinit var wakeLock: PowerManager.WakeLock
    private lateinit var settingsManager: SettingsManager

    private val binder = LocalBinder()
    private val isLogging = AtomicBoolean(false)
    private val isPaused = AtomicBoolean(false)

    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private var magnetometer: Sensor? = null
    private var barometer: Sensor? = null

    private val serviceScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    // Temporary storage for sensor fusion
    private var lastAccel = floatArrayOf(0f, 0f, 0f)
    private var lastGyro = floatArrayOf(0f, 0f, 0f)

    // Adaptive GPS sampling
    private var isHighRateGps = false
    private var lastGyroMagnitude = 0f
    private var currentGpsInterval = GPS_UPDATE_INTERVAL
    private var lastRateChangeTime = 0L
    private val gpsUpdateLock = Object()

    // Calibration service
    private lateinit var calibrationService: CalibrationService
    private var calibrationData: CalibrationData? = null
    
    // Sensor data filter for noise reduction
    private lateinit var sensorFilter: SensorDataFilter
    private var enableFiltering: Boolean = true // TODO: Make this configurable in settings

    inner class LocalBinder : Binder() {
        fun getService(): SensorLoggerService = this@SensorLoggerService
    }

    override fun onCreate() {
        super.onCreate()

        // Initialize settings manager
        settingsManager = SettingsManager.getInstance(this)

        // Initialize current GPS interval from settings
        currentGpsInterval = settingsManager.sensorSettings.value.gpsUpdateIntervalMs

        // Initialize calibration service
        calibrationService = CalibrationService(this)

        // Initialize sensors
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        magnetometer = sensorManager.getDefaultSensor(Sensor.TYPE_MAGNETIC_FIELD)
        barometer = sensorManager.getDefaultSensor(Sensor.TYPE_PRESSURE)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize CSV logger
        csvLogger = CsvLogger(this)
        
        // Initialize sensor data filter with optimized parameters for motorcycle data
        sensorFilter = SensorDataFilter(
            windowSize = 5, // 5-sample moving average for good noise reduction without too much lag
            outlierSigmaThreshold = 3.0f, // 3-sigma rule for outlier detection
            enableOutlierDetection = true,
            enableMovingAverage = true
        )

        // Acquire wake lock for continuous operation
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock =
            powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "MotoSensorLogger::SensorWakeLock",
            )

        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int,
    ): Int {
        when (intent?.action) {
            ACTION_START_LOGGING -> {
                val skipCalibration = intent.getBooleanExtra("skip_calibration", false)
                startLogging(skipCalibration)
            }
            ACTION_STOP_LOGGING -> stopLogging()
            ACTION_PAUSE_LOGGING -> pauseLogging()
            ACTION_RESUME_LOGGING -> resumeLogging()
        }
        return START_STICKY
    }

    private fun startLogging(skipCalibration: Boolean = false) {
        if (isLogging.get()) return

        // CRITICAL: Always reset calibration state to prevent mixed data
        calibrationData = null
        calibrationService.clearCalibration()

        Log.d("SensorLogger", "Starting logging - skipCalibration=$skipCalibration, calibrationData=null")

        // Start foreground service
        startForeground(
            NOTIFICATION_ID,
            createNotification(
                if (skipCalibration) "Starting recording..." else "Calibrating sensors...",
            ),
        )

        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(12 * 60 * 60 * 1000L) // 12 hours max
        }

        if (skipCalibration) {
            // Start recording directly without calibration
            Log.d("SensorLogger", "User chose to skip calibration - recording without calibration")
            finishCalibration() // This will now correctly log as non-calibrated
        } else {
            // Start calibration process
            startCalibration()
        }
    }

    private fun startCalibration() {
        Log.d("SensorLogger", "Starting calibration")

        // Update state to calibrating
        updateServiceState()

        // Register sensors for calibration
        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Start calibration service
        calibrationService.startCalibration()

        // Monitor calibration progress in background
        serviceScope.launch {
            calibrationService.progress
                .takeWhile { progress ->
                    // Continue collecting until completed or failed
                    progress.state != CalibrationService.State.COMPLETED &&
                        progress.state != CalibrationService.State.FAILED
                }
                .collect { progress ->
                    Log.d("SensorLogger", "Calibration: ${progress.state} - ${progress.message}")
                    updateNotification(progress.message)
                }

            // Check final state
            val finalState = calibrationService.state.value
            when (finalState) {
                CalibrationService.State.COMPLETED -> {
                    Log.d("SensorLogger", "Calibration completed successfully")
                    calibrationData = calibrationService.currentCalibration
                    finishCalibration()
                    updateServiceState()
                }
                CalibrationService.State.FAILED -> {
                    Log.e("SensorLogger", "Calibration failed")
                    updateNotification("Calibration failed")
                    // Don't start recording automatically - wait for user decision
                    // The MainActivity will show the dialog and handle the user's choice

                    // Unregister sensors that were registered for calibration
                    sensorManager.unregisterListener(this@SensorLoggerService)

                    // Clean up calibration
                    calibrationService.clearCalibration()

                    // Update state to idle
                    updateServiceState()

                    // Stop foreground but keep service alive briefly for potential retry
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }

                    // Stop self after delay to allow for retry
                    serviceScope.launch {
                        delay(5000) // Wait 5 seconds for potential retry
                        if (!isLogging.get()) {
                            stopSelf()
                        }
                    }
                }
                else -> {
                    Log.w("SensorLogger", "Unexpected calibration state: $finalState")
                    // Don't start recording on unexpected state
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    if (wakeLock.isHeld) {
                        wakeLock.release()
                    }
                }
            }
        }
    }

    private fun finishCalibration() {
        // Update notification to show we're done calibrating
        updateNotification("Starting recording...")
        // Start actual logging with calibration data
        startLoggingWithCalibration()
    }

    private fun startLoggingWithCalibration() {
        // CRITICAL: Ensure we have consistent calibration state
        val validCalibrationData =
            calibrationData?.takeIf {
                it.referencePitch != null &&
                    it.referenceRoll != null &&
                    it.quality != null
            }

        val calibrationHeader =
            if (validCalibrationData != null) {
                Log.d(
                    "SensorLogger",
                    "Starting WITH calibration: pitch=${validCalibrationData.referencePitch}, roll=${validCalibrationData.referenceRoll}",
                )
                validCalibrationData.toCsvHeader()
            } else {
                Log.d("SensorLogger", "Starting WITHOUT calibration")
                // Explicitly clear any partial calibration data
                calibrationData = null
                """
                # Calibration: {
                #   "status": "not_calibrated",
                #   "reason": "User skipped calibration or calibration failed",
                #   "timestamp": ${System.currentTimeMillis()},
                #   "note": "Raw sensor data without calibration reference"
                # }
                """.trimIndent()
            }

        if (!csvLogger.startLogging(calibrationHeader)) {
            stopSelf()
            return
        }

        isLogging.set(true)
        isPaused.set(false)
        updateServiceState()
        
        // Reset sensor filter for new session to ensure clean state
        sensorFilter.reset()
        Log.d("SensorLogger", "Sensor filter reset for new logging session")

        // Re-register sensor listeners with user-configured sampling rates
        sensorManager.unregisterListener(this)

        // Get user-configured IMU sampling rate from settings
        val samplingRateHz = settingsManager.sensorSettings.value.samplingRateHz
        
        // Validate sampling rate to prevent division by zero
        val validSamplingRateHz = if (samplingRateHz > 0) samplingRateHz else 50 // Default to 50Hz if invalid
        val imuSamplingPeriod = (1000000 / validSamplingRateHz) // Convert Hz to microseconds

        if (samplingRateHz <= 0) {
            Log.w("SensorLogger", "Invalid sampling rate: $samplingRateHz Hz, using default 50 Hz")
        }
        Log.d("SensorLogger", "Setting IMU sampling rate to $validSamplingRateHz Hz (period: $imuSamplingPeriod μs)")

        accelerometer?.let {
            sensorManager.registerListener(this, it, imuSamplingPeriod)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, imuSamplingPeriod)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, MAG_SAMPLING_PERIOD)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, BARO_SAMPLING_PERIOD)
        }

        // Start GPS updates
        startLocationUpdates()

        // Update notification
        updateNotification("Recording sensor data...")

        // Log session start with calibration info - MUST match header state
        val validCalibration =
            calibrationData?.takeIf {
                it.referencePitch != null &&
                    it.referenceRoll != null &&
                    it.quality != null
            }

        val sessionMetadata =
            if (validCalibration != null) {
                "Calibrated: pitch=${"%.1f".format(
                    validCalibration.referencePitch,
                )}°, roll=${"%.1f".format(validCalibration.referenceRoll)}°, quality=${validCalibration.quality.getQualityLevel()}"
            } else {
                "Not calibrated - recording raw sensor data"
            }

        csvLogger.logEvent(
            SpecialEvent(
                System.currentTimeMillis(),
                SpecialEvent.EventType.SESSION_START,
                metadata = sessionMetadata,
            ),
        )
    }

    private fun stopLogging() {
        if (!isLogging.get()) return

        // Log session end
        csvLogger.logEvent(
            SpecialEvent(
                System.currentTimeMillis(),
                SpecialEvent.EventType.SESSION_END,
                metadata = "Logging stopped",
            ),
        )

        isLogging.set(false)
        updateServiceState()

        // Unregister sensor listeners
        sensorManager.unregisterListener(this)

        // Stop location updates
        fusedLocationClient.removeLocationUpdates(locationCallback)

        // Stop CSV logging
        serviceScope.launch {
            csvLogger.stopLogging()
        }

        // Release wake lock
        if (wakeLock.isHeld) {
            wakeLock.release()
        }

        // Stop foreground service
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun pauseLogging() {
        isPaused.set(true)
        updateServiceState()
        updateNotification("Logging paused")
    }

    private fun resumeLogging() {
        isPaused.set(false)
        updateServiceState()
        updateNotification("Recording sensor data...")
    }

    override fun onSensorChanged(event: SensorEvent) {
        // During calibration, feed samples to calibration service
        if (calibrationService.state.value == CalibrationService.State.COLLECTING) {
            when (event.sensor.type) {
                Sensor.TYPE_ACCELEROMETER -> {
                    lastAccel = event.values.clone()
                }
                Sensor.TYPE_GYROSCOPE -> {
                    lastGyro = event.values.clone()
                }
                Sensor.TYPE_MAGNETIC_FIELD -> {
                    // Send complete sensor set when we get magnetometer data
                    if (lastAccel.any { it != 0f }) {
                        calibrationService.addSensorSample(
                            lastAccel,
                            lastGyro,
                            event.values.clone(),
                        )
                    }
                }
            }
            return
        }

        if (!isLogging.get() || isPaused.get()) return

        val timestamp = System.currentTimeMillis()

        // Process sensor data with optional filtering
        // Raw data is preserved for calibration; filtered data is logged for better quality
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                // Process IMU data when both accel and gyro are available
                processImuData(timestamp)
            }

            Sensor.TYPE_GYROSCOPE -> {
                lastGyro = event.values.clone()
                
                // Calculate gyro magnitude for cornering detection (using raw data)
                lastGyroMagnitude = kotlin.math.sqrt(
                    lastGyro[0] * lastGyro[0] + 
                    lastGyro[1] * lastGyro[1] + 
                    lastGyro[2] * lastGyro[2]
                )
                
                // Check if we should switch GPS rate based on cornering
                checkAdaptiveGpsRate()
                
                // Process IMU data when both accel and gyro are available
                processImuData(timestamp)
            }

            Sensor.TYPE_MAGNETIC_FIELD -> {
                // Log raw magnetometer data
                csvLogger.logEvent(
                    MagEvent(
                        timestamp,
                        event.values[0],
                        event.values[1],
                        event.values[2],
                    ),
                )
            }

            Sensor.TYPE_PRESSURE -> {
                // Calculate altitude from pressure
                val altitude =
                    SensorManager.getAltitude(
                        SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                        event.values[0],
                    )
                csvLogger.logEvent(
                    BaroEvent(timestamp, altitude, event.values[0]),
                )
            }
        }
    }

    override fun onAccuracyChanged(
        sensor: Sensor?,
        accuracy: Int,
    ) {
        // Handle accuracy changes if needed
    }

    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }

        // Use current GPS interval (may be adaptive)
        val gpsInterval = currentGpsInterval

        val locationRequest =
            LocationRequest.Builder(
                Priority.PRIORITY_HIGH_ACCURACY,
                gpsInterval,
            ).apply {
                setMinUpdateIntervalMillis(gpsInterval)
                setWaitForAccurateLocation(false)
            }.build()

        try {
            fusedLocationClient.requestLocationUpdates(
                locationRequest,
                locationCallback,
                Looper.getMainLooper(),
            ).addOnFailureListener { e ->
                Log.e("SensorLogger", "Failed to request location updates", e)
            }
        } catch (e: SecurityException) {
            Log.e("SensorLogger", "Location permission denied", e)
        } catch (e: Exception) {
            Log.e("SensorLogger", "Error requesting location updates", e)
        }
    }

    private val locationCallback =
        object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                if (!isLogging.get() || isPaused.get()) return

                locationResult.lastLocation?.let { location ->
                    csvLogger.logEvent(
                        GpsEvent(
                            System.currentTimeMillis(),
                            location.latitude,
                            location.longitude,
                            location.altitude,
                            location.speed,
                            location.bearing,
                            location.accuracy,
                        ),
                    )
                }
            }
        }
    
    /**
     * Process IMU data with optional filtering and log to CSV.
     * Only logs when both accelerometer and gyroscope data are available.
     */
    private fun processImuData(timestamp: Long) {
        // Only process if we have both accel and gyro data
        if (lastAccel.all { it == 0f } || lastGyro.all { it == 0f }) {
            return
        }
        
        val (finalAccelX, finalAccelY, finalAccelZ, finalGyroX, finalGyroY, finalGyroZ) = if (enableFiltering) {
            // Apply sensor filtering for noise reduction
            val filteredData = sensorFilter.filterImuData(
                lastAccel[0], lastAccel[1], lastAccel[2],
                lastGyro[0], lastGyro[1], lastGyro[2]
            )
            
            // Log outlier detection for debugging if needed
            if (filteredData.outlierFlags.hasOutliers()) {
                Log.v("SensorFilter", "Outliers detected: ${filteredData.outlierFlags}")
            }
            
            Tuple6(
                filteredData.accelX, filteredData.accelY, filteredData.accelZ,
                filteredData.gyroX, filteredData.gyroY, filteredData.gyroZ
            )
        } else {
            // Use raw sensor data without filtering
            Tuple6(
                lastAccel[0], lastAccel[1], lastAccel[2],
                lastGyro[0], lastGyro[1], lastGyro[2]
            )
        }
        
        // Log the processed IMU event
        csvLogger.logEvent(
            ImuEvent(
                timestamp,
                finalAccelX,
                finalAccelY,
                finalAccelZ,
                finalGyroX,
                finalGyroY,
                finalGyroZ,
            ),
        )
    }
    
    /**
     * Data class to hold 6 float values (helper for IMU data)
     */
    private data class Tuple6(
        val v1: Float, val v2: Float, val v3: Float,
        val v4: Float, val v5: Float, val v6: Float
    )
    
    private fun checkAdaptiveGpsRate() {
        synchronized(gpsUpdateLock) {
            // Implement hysteresis to prevent oscillation
            val shouldBeHighRate =
                when {
                    isHighRateGps -> lastGyroMagnitude > CORNERING_EXIT_THRESHOLD
                    else -> lastGyroMagnitude > CORNERING_ENTER_THRESHOLD
                }

            // Check debounce period
            val now = System.currentTimeMillis()
            if (now - lastRateChangeTime < RATE_CHANGE_DEBOUNCE_MS) {
                return // Skip update due to debounce
            }

            // Determine target interval
            val targetInterval =
                if (shouldBeHighRate) {
                    GPS_HIGH_RATE_INTERVAL
                } else {
                    settingsManager.sensorSettings.value.gpsUpdateIntervalMs
                }

            // Only update if the rate has changed
            if (shouldBeHighRate != isHighRateGps || targetInterval != currentGpsInterval) {
                isHighRateGps = shouldBeHighRate
                currentGpsInterval = targetInterval
                lastRateChangeTime = now

                if (isLogging.get() && !isPaused.get()) {
                    // Safely stop and restart location updates with new interval
                    try {
                        fusedLocationClient.removeLocationUpdates(locationCallback)
                            .addOnCompleteListener { task ->
                                if (task.isSuccessful) {
                                    startLocationUpdates()
                                } else {
                                    Log.e("SensorLogger", "Failed to remove location updates", task.exception)
                                }
                            }
                    } catch (e: SecurityException) {
                        Log.e("SensorLogger", "Location permission revoked during rate switch", e)
                    } catch (e: Exception) {
                        Log.e("SensorLogger", "Error switching GPS rate", e)
                    }
                }
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel =
                NotificationChannel(
                    CHANNEL_ID,
                    "Sensor Logger Service",
                    NotificationManager.IMPORTANCE_LOW,
                ).apply {
                    description = "Notification for sensor logging service"
                    setShowBadge(false)
                }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(contentText: String): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent =
            PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Moto Sensor Logger")
            .setContentText(contentText)
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification(contentText: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(NOTIFICATION_ID, createNotification(contentText))
    }

    fun isCurrentlyLogging(): Boolean = isLogging.get()

    fun isCurrentlyPaused(): Boolean = isPaused.get()

    fun getCsvLogger(): CsvLogger = csvLogger

    fun getCalibrationData(): com.motosensorlogger.calibration.CalibrationData? = calibrationData

    fun getCalibrationService(): CalibrationService = calibrationService

    // Service state for UI synchronization
    enum class ServiceState {
        IDLE,
        CALIBRATING,
        RECORDING,
        PAUSED,
    }

    // Reactive state that UI can observe
    private val _serviceState = MutableStateFlow(ServiceState.IDLE)
    val serviceState: StateFlow<ServiceState> = _serviceState.asStateFlow()

    private fun updateServiceState() {
        val newState =
            when {
                !isLogging.get() && calibrationService.state.value == CalibrationService.State.IDLE -> ServiceState.IDLE
                calibrationService.state.value == CalibrationService.State.COLLECTING ||
                    calibrationService.state.value == CalibrationService.State.PROCESSING -> ServiceState.CALIBRATING
                isLogging.get() && isPaused.get() -> ServiceState.PAUSED
                isLogging.get() -> ServiceState.RECORDING
                else -> ServiceState.IDLE
            }
        _serviceState.value = newState
    }

    fun getCurrentState(): ServiceState = _serviceState.value

    override fun onDestroy() {
        super.onDestroy()
        stopLogging()
        csvLogger.cleanup()
        calibrationService.dispose()
        calibrationData = null // Clean up calibration data
        serviceScope.cancel()
    }
}
