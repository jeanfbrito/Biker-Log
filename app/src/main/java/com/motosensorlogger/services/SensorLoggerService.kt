package com.motosensorlogger.services

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.location.Location
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.*
import com.motosensorlogger.MainActivity
import com.motosensorlogger.R
import com.motosensorlogger.data.*
import kotlinx.coroutines.*
import java.util.concurrent.atomic.AtomicBoolean

class SensorLoggerService : Service(), SensorEventListener {
    
    companion object {
        const val ACTION_START_LOGGING = "com.motosensorlogger.START_LOGGING"
        const val ACTION_STOP_LOGGING = "com.motosensorlogger.STOP_LOGGING"
        const val ACTION_PAUSE_LOGGING = "com.motosensorlogger.PAUSE_LOGGING"
        const val ACTION_RESUME_LOGGING = "com.motosensorlogger.RESUME_LOGGING"
        
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "sensor_logger_channel"
        
        // Sensor sampling rates (microseconds)
        private const val IMU_SAMPLING_PERIOD = 10000 // 100Hz
        private const val MAG_SAMPLING_PERIOD = 40000 // 25Hz
        private const val BARO_SAMPLING_PERIOD = 40000 // 25Hz
        
        // GPS update interval
        private const val GPS_UPDATE_INTERVAL = 200L // 5Hz in milliseconds
    }
    
    private lateinit var sensorManager: SensorManager
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var csvLogger: CsvLogger
    private lateinit var wakeLock: PowerManager.WakeLock
    
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
    
    inner class LocalBinder : Binder() {
        fun getService(): SensorLoggerService = this@SensorLoggerService
    }
    
    override fun onCreate() {
        super.onCreate()
        
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
        
        // Acquire wake lock for continuous operation
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "MotoSensorLogger::SensorWakeLock"
        )
        
        createNotificationChannel()
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START_LOGGING -> startLogging()
            ACTION_STOP_LOGGING -> stopLogging()
            ACTION_PAUSE_LOGGING -> pauseLogging()
            ACTION_RESUME_LOGGING -> resumeLogging()
        }
        return START_STICKY
    }
    
    private fun startLogging() {
        if (isLogging.get()) return
        
        // Start foreground service
        startForeground(NOTIFICATION_ID, createNotification("Recording sensor data..."))
        
        // Acquire wake lock
        if (!wakeLock.isHeld) {
            wakeLock.acquire(12 * 60 * 60 * 1000L) // 12 hours max
        }
        
        // Start CSV logging
        if (!csvLogger.startLogging()) {
            stopSelf()
            return
        }
        
        isLogging.set(true)
        isPaused.set(false)
        
        // Register sensor listeners with high sampling rates
        accelerometer?.let {
            sensorManager.registerListener(this, it, IMU_SAMPLING_PERIOD)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, IMU_SAMPLING_PERIOD)
        }
        magnetometer?.let {
            sensorManager.registerListener(this, it, MAG_SAMPLING_PERIOD)
        }
        barometer?.let {
            sensorManager.registerListener(this, it, BARO_SAMPLING_PERIOD)
        }
        
        // Start GPS updates
        startLocationUpdates()
        
        // Log session start
        csvLogger.logEvent(
            SpecialEvent(
                System.currentTimeMillis(),
                SpecialEvent.EventType.SESSION_START,
                metadata = "Logging started"
            )
        )
    }
    
    private fun stopLogging() {
        if (!isLogging.get()) return
        
        // Log session end
        csvLogger.logEvent(
            SpecialEvent(
                System.currentTimeMillis(),
                SpecialEvent.EventType.SESSION_END,
                metadata = "Logging stopped"
            )
        )
        
        isLogging.set(false)
        
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
        updateNotification("Logging paused")
    }
    
    private fun resumeLogging() {
        isPaused.set(false)
        updateNotification("Recording sensor data...")
    }
    
    override fun onSensorChanged(event: SensorEvent) {
        if (!isLogging.get() || isPaused.get()) return
        
        val timestamp = System.currentTimeMillis()
        
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                lastAccel = event.values.clone()
                // Combine with gyro if available for IMU event
                if (lastGyro[0] != 0f || lastGyro[1] != 0f || lastGyro[2] != 0f) {
                    csvLogger.logEvent(
                        ImuEvent(
                            timestamp,
                            lastAccel[0], lastAccel[1], lastAccel[2],
                            lastGyro[0], lastGyro[1], lastGyro[2]
                        )
                    )
                }
            }
            
            Sensor.TYPE_GYROSCOPE -> {
                lastGyro = event.values.clone()
                // Combine with accel for IMU event
                csvLogger.logEvent(
                    ImuEvent(
                        timestamp,
                        lastAccel[0], lastAccel[1], lastAccel[2],
                        lastGyro[0], lastGyro[1], lastGyro[2]
                    )
                )
            }
            
            Sensor.TYPE_MAGNETIC_FIELD -> {
                csvLogger.logEvent(
                    MagEvent(
                        timestamp,
                        event.values[0], event.values[1], event.values[2]
                    )
                )
            }
            
            Sensor.TYPE_PRESSURE -> {
                // Calculate altitude from pressure
                val altitude = SensorManager.getAltitude(
                    SensorManager.PRESSURE_STANDARD_ATMOSPHERE,
                    event.values[0]
                )
                csvLogger.logEvent(
                    BaroEvent(timestamp, altitude, event.values[0])
                )
            }
        }
    }
    
    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // Handle accuracy changes if needed
    }
    
    private fun startLocationUpdates() {
        if (ContextCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_FINE_LOCATION
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        
        val locationRequest = LocationRequest.Builder(
            Priority.PRIORITY_HIGH_ACCURACY,
            GPS_UPDATE_INTERVAL
        ).apply {
            setMinUpdateIntervalMillis(GPS_UPDATE_INTERVAL)
            setWaitForAccurateLocation(false)
        }.build()
        
        fusedLocationClient.requestLocationUpdates(
            locationRequest,
            locationCallback,
            Looper.getMainLooper()
        )
    }
    
    private val locationCallback = object : LocationCallback() {
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
                        location.accuracy
                    )
                )
            }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Sensor Logger Service",
                NotificationManager.IMPORTANCE_LOW
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
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
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
    
    override fun onDestroy() {
        super.onDestroy()
        stopLogging()
        csvLogger.cleanup()
        serviceScope.cancel()
    }
}