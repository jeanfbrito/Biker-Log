package com.motosensorlogger.filters

/**
 * Adaptive filter chain that applies optimal filter combinations per sensor type.
 * 
 * Based on motorcycle telemetry research, different sensors require different
 * filtering strategies:
 * 
 * IMU (100Hz): Butterworth (25Hz) → EMA (α=0.2)
 *   - Removes engine/road vibrations while preserving dynamics
 * 
 * GPS (5Hz): Median (3 samples) → EMA (α=0.4) 
 *   - Removes GPS spikes while maintaining responsiveness
 * 
 * Barometer (25Hz): EMA (α=0.5) only
 *   - Simple smoothing for stable pressure readings
 * 
 * Magnetometer (25Hz): Median (5 samples) → EMA (α=0.3)
 *   - Handles magnetic interference and electrical noise
 */
class AdaptiveFilterChain private constructor(
    private val filters: List<FilterStrategy>,
    private val sensorType: SensorType
) : FilterStrategy {
    
    private var totalProcessingTimeNs: Long = 0L
    
    enum class SensorType {
        IMU_ACCELEROMETER,
        IMU_GYROSCOPE, 
        GPS_LOCATION,
        BAROMETER,
        MAGNETOMETER,
        CUSTOM
    }
    
    override fun filter(value: Float, timestamp: Long): Float {
        val startTime = System.nanoTime()
        
        var result = value
        for (filter in filters) {
            result = filter.filter(result, timestamp)
        }
        
        totalProcessingTimeNs = System.nanoTime() - startTime
        return result
    }
    
    override fun filter(values: FloatArray, timestamp: Long): FloatArray {
        val startTime = System.nanoTime()
        
        var result = values.copyOf()
        for (filter in filters) {
            result = filter.filter(result, timestamp)
        }
        
        totalProcessingTimeNs = System.nanoTime() - startTime
        return result
    }
    
    override fun reset() {
        filters.forEach { it.reset() }
        totalProcessingTimeNs = 0L
    }
    
    override fun getLatencyMs(): Float {
        return totalProcessingTimeNs / 1_000_000f
    }
    
    override fun isReady(): Boolean {
        return filters.all { it.isReady() }
    }
    
    override fun getName(): String {
        val filterNames = filters.joinToString(" → ") { it.getName() }
        return "${sensorType.name}[$filterNames]"
    }
    
    /**
     * Get the total processing overhead for all filters in the chain
     */
    fun getTotalLatencyMs(): Float {
        return filters.sumOf { it.getLatencyMs().toDouble() }.toFloat()
    }
    
    /**
     * Get the number of filters in the chain
     */
    fun getFilterCount(): Int = filters.size
    
    /**
     * Get individual filter performance metrics
     */
    fun getFilterMetrics(): List<FilterMetric> {
        return filters.map { filter ->
            FilterMetric(
                name = filter.getName(),
                latencyMs = filter.getLatencyMs(),
                isReady = filter.isReady()
            )
        }
    }
    
    data class FilterMetric(
        val name: String,
        val latencyMs: Float,
        val isReady: Boolean
    )
    
    companion object {
        
        /**
         * Create optimized filter chain for IMU accelerometer data (100Hz)
         * Butterworth (25Hz cutoff) → EMA (α=0.2) for smooth but responsive telemetry
         */
        fun forIMUAccelerometer(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    MultiAxisButterworthFilter(cutoffHz = 25f, sampleRateHz = 100f, numAxes = 3),
                    MultiAxisEMAFilter(alpha = 0.2f, numAxes = 3)
                ),
                sensorType = SensorType.IMU_ACCELEROMETER
            )
        }
        
        /**
         * Create optimized filter chain for IMU gyroscope data (100Hz)
         * Similar to accelerometer but slightly more responsive
         */
        fun forIMUGyroscope(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    MultiAxisButterworthFilter(cutoffHz = 30f, sampleRateHz = 100f, numAxes = 3),
                    MultiAxisEMAFilter(alpha = 0.25f, numAxes = 3)
                ),
                sensorType = SensorType.IMU_GYROSCOPE
            )
        }
        
        /**
         * Create optimized filter chain for GPS location data (5Hz)
         * Median filter removes spikes → EMA provides smoothing
         */
        fun forGPS(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    MultiAxisMedianFilter(windowSize = 3, numAxes = 2), // Lat/Lon only
                    MultiAxisEMAFilter(alpha = 0.4f, numAxes = 2)
                ),
                sensorType = SensorType.GPS_LOCATION
            )
        }
        
        /**
         * Create optimized filter chain for barometer data (25Hz)  
         * Simple EMA smoothing for stable pressure readings
         */
        fun forBarometer(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    ExponentialMovingAverageFilter(alpha = 0.5f)
                ),
                sensorType = SensorType.BAROMETER
            )
        }
        
        /**
         * Create optimized filter chain for magnetometer data (25Hz)
         * Median filter handles interference → EMA provides smoothing
         */
        fun forMagnetometer(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    MultiAxisMedianFilter(windowSize = 5, numAxes = 3),
                    MultiAxisEMAFilter(alpha = 0.3f, numAxes = 3)
                ),
                sensorType = SensorType.MAGNETOMETER
            )
        }
        
        /**
         * Create lightweight filter chain for real-time telemetry display
         * Optimized for <5ms processing time while maintaining visual smoothness
         */
        fun forTelemetryDisplay(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    // Single-stage EMA for minimal latency
                    MultiAxisEMAFilter(alpha = 0.3f, numAxes = 3)
                ),
                sensorType = SensorType.CUSTOM
            )
        }
        
        /**
         * Create aggressive smoothing chain for data logging
         * Higher quality filtering when processing time is less critical
         */
        fun forDataLogging(): AdaptiveFilterChain {
            return AdaptiveFilterChain(
                filters = listOf(
                    MultiAxisMedianFilter(windowSize = 5, numAxes = 3),
                    MultiAxisButterworthFilter(cutoffHz = 20f, sampleRateHz = 100f, numAxes = 3),
                    MultiAxisEMAFilter(alpha = 0.15f, numAxes = 3)
                ),
                sensorType = SensorType.CUSTOM
            )
        }
        
        /**
         * Create custom filter chain with specified filters
         */
        fun custom(filters: List<FilterStrategy>, sensorType: SensorType = SensorType.CUSTOM): AdaptiveFilterChain {
            require(filters.isNotEmpty()) { "Filter chain cannot be empty" }
            return AdaptiveFilterChain(filters, sensorType)
        }
        
        /**
         * Create filter chain optimized for specific performance requirements
         */
        fun forPerformance(
            maxLatencyMs: Float,
            targetSmoothness: SmoothLevel,
            sensorFreqHz: Float
        ): AdaptiveFilterChain {
            
            val filters = mutableListOf<FilterStrategy>()
            
            when (targetSmoothness) {
                SmoothLevel.MINIMAL -> {
                    // Just basic EMA for minimal processing
                    filters.add(MultiAxisEMAFilter(alpha = 0.5f))
                }
                
                SmoothLevel.MODERATE -> {
                    if (maxLatencyMs > 3f) {
                        // Add Butterworth if we have latency budget
                        val cutoff = minOf(sensorFreqHz / 4f, 25f)
                        filters.add(MultiAxisButterworthFilter(cutoff, sensorFreqHz))
                    }
                    filters.add(MultiAxisEMAFilter(alpha = 0.3f))
                }
                
                SmoothLevel.AGGRESSIVE -> {
                    if (maxLatencyMs > 2f) {
                        // Add median filter for spike removal
                        filters.add(MultiAxisMedianFilter(3))
                    }
                    if (maxLatencyMs > 4f) {
                        // Add Butterworth for frequency domain filtering
                        val cutoff = minOf(sensorFreqHz / 5f, 20f)
                        filters.add(MultiAxisButterworthFilter(cutoff, sensorFreqHz))
                    }
                    filters.add(MultiAxisEMAFilter(alpha = 0.2f))
                }
            }
            
            return AdaptiveFilterChain(filters, SensorType.CUSTOM)
        }
    }
    
    enum class SmoothLevel {
        MINIMAL,    // Fast response, minimal smoothing
        MODERATE,   // Balanced smoothing and responsiveness  
        AGGRESSIVE  // Maximum smoothing for professional appearance
    }
}

/**
 * Factory for creating sensor-specific filter configurations
 * Used by the main application to get optimal filters for each sensor type
 */
object FilterFactory {
    
    /**
     * Get the recommended filter chain for telemetry display of a specific sensor
     */
    fun getTelemetryFilter(sensorType: AdaptiveFilterChain.SensorType): AdaptiveFilterChain {
        return when (sensorType) {
            AdaptiveFilterChain.SensorType.IMU_ACCELEROMETER -> AdaptiveFilterChain.forIMUAccelerometer()
            AdaptiveFilterChain.SensorType.IMU_GYROSCOPE -> AdaptiveFilterChain.forIMUGyroscope()
            AdaptiveFilterChain.SensorType.GPS_LOCATION -> AdaptiveFilterChain.forGPS()
            AdaptiveFilterChain.SensorType.BAROMETER -> AdaptiveFilterChain.forBarometer()
            AdaptiveFilterChain.SensorType.MAGNETOMETER -> AdaptiveFilterChain.forMagnetometer()
            AdaptiveFilterChain.SensorType.CUSTOM -> AdaptiveFilterChain.forTelemetryDisplay()
        }
    }
    
    /**
     * Get optimized filter for motorcycle telemetry with <5ms processing target
     */
    fun getMotorcycleTelemetryFilter(): AdaptiveFilterChain {
        return AdaptiveFilterChain.forPerformance(
            maxLatencyMs = 5f,
            targetSmoothness = AdaptiveFilterChain.SmoothLevel.MODERATE,
            sensorFreqHz = 100f
        )
    }
    
    /**
     * Get professional-grade filter for smooth GoPro/Garmin-style overlays
     */
    fun getProfessionalDisplayFilter(): AdaptiveFilterChain {
        return AdaptiveFilterChain.forPerformance(
            maxLatencyMs = 10f,
            targetSmoothness = AdaptiveFilterChain.SmoothLevel.AGGRESSIVE,  
            sensorFreqHz = 100f
        )
    }
}