package com.motosensorlogger.settings

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Centralized settings manager using SharedPreferences
 * Provides reactive settings with StateFlow
 */
class SettingsManager private constructor(context: Context) {
    
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )
    
    companion object {
        private const val PREFS_NAME = "moto_sensor_settings"
        
        // Setting keys
        private const val KEY_CALIBRATION_DURATION = "calibration_duration_ms"
        private const val KEY_CALIBRATION_MIN_SAMPLES = "calibration_min_samples"
        private const val KEY_CALIBRATION_STABILITY_THRESHOLD = "calibration_stability_threshold"
        private const val KEY_ENABLE_VIBRATION_BASELINE = "enable_vibration_baseline"
        private const val KEY_ENABLE_MAGNETIC_CALIBRATION = "enable_magnetic_calibration"
        private const val KEY_SENSOR_SAMPLING_RATE = "sensor_sampling_rate"
        private const val KEY_GPS_UPDATE_INTERVAL = "gps_update_interval"
        private const val KEY_CSV_BUFFER_SIZE = "csv_buffer_size"
        private const val KEY_AUTO_STOP_ON_LOW_BATTERY = "auto_stop_low_battery"
        private const val KEY_LOW_BATTERY_THRESHOLD = "low_battery_threshold"
        
        // Default values
        const val DEFAULT_CALIBRATION_DURATION_MS = 2000L
        const val DEFAULT_CALIBRATION_MIN_SAMPLES = 50
        const val DEFAULT_CALIBRATION_STABILITY = 2.0f  // More forgiving for handheld
        const val DEFAULT_VIBRATION_BASELINE = true
        const val DEFAULT_MAGNETIC_CALIBRATION = true
        const val DEFAULT_SENSOR_SAMPLING_RATE = 100 // Hz
        const val DEFAULT_GPS_UPDATE_INTERVAL = 200L // ms (5Hz)
        const val DEFAULT_CSV_BUFFER_SIZE = 32 * 1024 // 32KB
        const val DEFAULT_AUTO_STOP_LOW_BATTERY = true
        const val DEFAULT_LOW_BATTERY_THRESHOLD = 15 // percent
        
        @Volatile
        private var INSTANCE: SettingsManager? = null
        
        fun getInstance(context: Context): SettingsManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: SettingsManager(context.applicationContext).also {
                    INSTANCE = it
                }
            }
        }
    }
    
    // Calibration Settings
    data class CalibrationSettings(
        val durationMs: Long = DEFAULT_CALIBRATION_DURATION_MS,
        val minSamples: Int = DEFAULT_CALIBRATION_MIN_SAMPLES,
        val stabilityThreshold: Float = DEFAULT_CALIBRATION_STABILITY,
        val captureVibrationBaseline: Boolean = DEFAULT_VIBRATION_BASELINE,
        val captureMagneticBaseline: Boolean = DEFAULT_MAGNETIC_CALIBRATION
    )
    
    // Sensor Settings
    data class SensorSettings(
        val samplingRateHz: Int = DEFAULT_SENSOR_SAMPLING_RATE,
        val gpsUpdateIntervalMs: Long = DEFAULT_GPS_UPDATE_INTERVAL,
        val csvBufferSize: Int = DEFAULT_CSV_BUFFER_SIZE
    )
    
    // Power Settings
    data class PowerSettings(
        val autoStopOnLowBattery: Boolean = DEFAULT_AUTO_STOP_LOW_BATTERY,
        val lowBatteryThreshold: Int = DEFAULT_LOW_BATTERY_THRESHOLD
    )
    
    // Observable settings
    private val _calibrationSettings = MutableStateFlow(loadCalibrationSettings())
    val calibrationSettings: StateFlow<CalibrationSettings> = _calibrationSettings
    
    private val _sensorSettings = MutableStateFlow(loadSensorSettings())
    val sensorSettings: StateFlow<SensorSettings> = _sensorSettings
    
    private val _powerSettings = MutableStateFlow(loadPowerSettings())
    val powerSettings: StateFlow<PowerSettings> = _powerSettings
    
    // Load settings from SharedPreferences
    private fun loadCalibrationSettings(): CalibrationSettings {
        return CalibrationSettings(
            durationMs = prefs.getLong(KEY_CALIBRATION_DURATION, DEFAULT_CALIBRATION_DURATION_MS),
            minSamples = prefs.getInt(KEY_CALIBRATION_MIN_SAMPLES, DEFAULT_CALIBRATION_MIN_SAMPLES),
            stabilityThreshold = prefs.getFloat(KEY_CALIBRATION_STABILITY_THRESHOLD, DEFAULT_CALIBRATION_STABILITY),
            captureVibrationBaseline = prefs.getBoolean(KEY_ENABLE_VIBRATION_BASELINE, DEFAULT_VIBRATION_BASELINE),
            captureMagneticBaseline = prefs.getBoolean(KEY_ENABLE_MAGNETIC_CALIBRATION, DEFAULT_MAGNETIC_CALIBRATION)
        )
    }
    
    private fun loadSensorSettings(): SensorSettings {
        return SensorSettings(
            samplingRateHz = prefs.getInt(KEY_SENSOR_SAMPLING_RATE, DEFAULT_SENSOR_SAMPLING_RATE),
            gpsUpdateIntervalMs = prefs.getLong(KEY_GPS_UPDATE_INTERVAL, DEFAULT_GPS_UPDATE_INTERVAL),
            csvBufferSize = prefs.getInt(KEY_CSV_BUFFER_SIZE, DEFAULT_CSV_BUFFER_SIZE)
        )
    }
    
    private fun loadPowerSettings(): PowerSettings {
        return PowerSettings(
            autoStopOnLowBattery = prefs.getBoolean(KEY_AUTO_STOP_ON_LOW_BATTERY, DEFAULT_AUTO_STOP_LOW_BATTERY),
            lowBatteryThreshold = prefs.getInt(KEY_LOW_BATTERY_THRESHOLD, DEFAULT_LOW_BATTERY_THRESHOLD)
        )
    }
    
    // Update calibration duration
    fun setCalibrationDuration(durationMs: Long) {
        prefs.edit().putLong(KEY_CALIBRATION_DURATION, durationMs).apply()
        _calibrationSettings.value = _calibrationSettings.value.copy(durationMs = durationMs)
    }
    
    // Update minimum samples
    fun setCalibrationMinSamples(samples: Int) {
        prefs.edit().putInt(KEY_CALIBRATION_MIN_SAMPLES, samples).apply()
        _calibrationSettings.value = _calibrationSettings.value.copy(minSamples = samples)
    }
    
    // Update stability threshold
    fun setCalibrationStabilityThreshold(threshold: Float) {
        prefs.edit().putFloat(KEY_CALIBRATION_STABILITY_THRESHOLD, threshold).apply()
        _calibrationSettings.value = _calibrationSettings.value.copy(stabilityThreshold = threshold)
    }
    
    // Update vibration baseline capture
    fun setVibrationBaselineEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_VIBRATION_BASELINE, enabled).apply()
        _calibrationSettings.value = _calibrationSettings.value.copy(captureVibrationBaseline = enabled)
    }
    
    // Update magnetic calibration
    fun setMagneticCalibrationEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_ENABLE_MAGNETIC_CALIBRATION, enabled).apply()
        _calibrationSettings.value = _calibrationSettings.value.copy(captureMagneticBaseline = enabled)
    }
    
    // Update sensor sampling rate
    fun setSensorSamplingRate(rateHz: Int) {
        prefs.edit().putInt(KEY_SENSOR_SAMPLING_RATE, rateHz).apply()
        _sensorSettings.value = _sensorSettings.value.copy(samplingRateHz = rateHz)
    }
    
    // Update GPS interval
    fun setGpsUpdateInterval(intervalMs: Long) {
        prefs.edit().putLong(KEY_GPS_UPDATE_INTERVAL, intervalMs).apply()
        _sensorSettings.value = _sensorSettings.value.copy(gpsUpdateIntervalMs = intervalMs)
    }
    
    // Update auto-stop on low battery
    fun setAutoStopOnLowBattery(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO_STOP_ON_LOW_BATTERY, enabled).apply()
        _powerSettings.value = _powerSettings.value.copy(autoStopOnLowBattery = enabled)
    }
    
    // Update low battery threshold
    fun setLowBatteryThreshold(threshold: Int) {
        prefs.edit().putInt(KEY_LOW_BATTERY_THRESHOLD, threshold).apply()
        _powerSettings.value = _powerSettings.value.copy(lowBatteryThreshold = threshold)
    }
    
    // Reset to defaults
    fun resetCalibrationSettings() {
        setCalibrationDuration(DEFAULT_CALIBRATION_DURATION_MS)
        setCalibrationMinSamples(DEFAULT_CALIBRATION_MIN_SAMPLES)
        setCalibrationStabilityThreshold(DEFAULT_CALIBRATION_STABILITY)
        setVibrationBaselineEnabled(DEFAULT_VIBRATION_BASELINE)
        setMagneticCalibrationEnabled(DEFAULT_MAGNETIC_CALIBRATION)
    }
    
    fun resetAllSettings() {
        prefs.edit().clear().apply()
        _calibrationSettings.value = loadCalibrationSettings()
        _sensorSettings.value = loadSensorSettings()
        _powerSettings.value = loadPowerSettings()
    }
}