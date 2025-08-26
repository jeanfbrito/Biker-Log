package com.motosensorlogger.filters

/**
 * Exponential Moving Average (EMA) filter for smooth sensor data.
 * 
 * EMA provides excellent noise reduction with low latency, making it ideal 
 * for real-time telemetry display. The alpha parameter controls responsiveness:
 * - Higher alpha (0.8-1.0): More responsive, less smooth
 * - Lower alpha (0.1-0.3): More smooth, less responsive
 * 
 * Formula: output = alpha * input + (1 - alpha) * previous_output
 */
class ExponentialMovingAverageFilter(
    private val alpha: Float
) : FilterStrategy {
    
    private var previousValue: Float = 0f
    private var isInitialized: Boolean = false
    private var lastUpdateTime: Long = 0L
    private var processingTimeNs: Long = 0L
    
    init {
        require(alpha in 0f..1f) { "Alpha must be between 0 and 1, got: $alpha" }
    }
    
    override fun filter(value: Float, timestamp: Long): Float {
        val startTime = System.nanoTime()
        
        val result = if (!isInitialized) {
            // First sample - initialize with the raw value
            previousValue = value
            isInitialized = true
            value
        } else {
            // Apply EMA formula: y[n] = α * x[n] + (1 - α) * y[n-1]
            val filtered = alpha * value + (1f - alpha) * previousValue
            previousValue = filtered
            filtered
        }
        
        lastUpdateTime = timestamp
        processingTimeNs = System.nanoTime() - startTime
        
        return result
    }
    
    override fun filter(values: FloatArray, timestamp: Long): FloatArray {
        // For multi-axis data, we need separate EMA state for each axis
        // This base implementation applies the same filter to all axes independently
        // Subclasses can override for more sophisticated multi-axis filtering
        return FloatArray(values.size) { index ->
            filter(values[index], timestamp)
        }
    }
    
    override fun reset() {
        previousValue = 0f
        isInitialized = false
        lastUpdateTime = 0L
        processingTimeNs = 0L
    }
    
    override fun getLatencyMs(): Float {
        return processingTimeNs / 1_000_000f // Convert nanoseconds to milliseconds
    }
    
    override fun isReady(): Boolean {
        return isInitialized
    }
    
    override fun getName(): String {
        return "EMA(α=$alpha)"
    }
    
    /**
     * Get the current filtered value (last output)
     */
    fun getCurrentValue(): Float = previousValue
    
    /**
     * Get the effective time constant in samples for this alpha value.
     * This represents roughly how many samples it takes for the filter
     * to reach ~63% of a step change.
     */
    fun getTimeConstantSamples(): Float = 1f / alpha
    
    /**
     * Calculate alpha from desired time constant in samples
     */
    companion object {
        /**
         * Create EMA filter with time constant in samples
         * @param timeConstantSamples Number of samples for ~63% response to step change
         */
        fun fromTimeConstant(timeConstantSamples: Float): ExponentialMovingAverageFilter {
            require(timeConstantSamples > 0f) { "Time constant must be positive, got: $timeConstantSamples" }
            val alpha = 1f / timeConstantSamples
            return ExponentialMovingAverageFilter(alpha.coerceIn(0.001f, 1f))
        }
        
        /**
         * Create EMA filter with cutoff frequency for given sample rate
         * @param cutoffHz Desired cutoff frequency in Hz
         * @param sampleRateHz Sample rate in Hz
         */
        fun fromCutoffFrequency(cutoffHz: Float, sampleRateHz: Float): ExponentialMovingAverageFilter {
            require(cutoffHz > 0f && sampleRateHz > 0f) { 
                "Frequencies must be positive: cutoff=$cutoffHz, sampleRate=$sampleRateHz" 
            }
            require(cutoffHz < sampleRateHz / 2f) { 
                "Cutoff frequency must be less than Nyquist frequency (${sampleRateHz / 2f} Hz)" 
            }
            
            // Convert cutoff frequency to alpha using RC filter approximation
            // alpha ≈ 2π * fc / fs for small fc/fs ratios
            val normalizedFreq = cutoffHz / sampleRateHz
            val alpha = (2f * Math.PI * normalizedFreq).toFloat().coerceIn(0.001f, 1f)
            
            return ExponentialMovingAverageFilter(alpha)
        }
    }
}

/**
 * Multi-axis EMA filter that maintains separate state for each axis.
 * Useful for IMU data where X, Y, Z axes should be filtered independently.
 */
class MultiAxisEMAFilter(
    private val alpha: Float,
    private val numAxes: Int = 3
) : FilterStrategy {
    
    private val axisFilters = Array(numAxes) { ExponentialMovingAverageFilter(alpha) }
    
    override fun filter(value: Float, timestamp: Long): Float {
        // Single value goes to first axis
        return axisFilters[0].filter(value, timestamp)
    }
    
    override fun filter(values: FloatArray, timestamp: Long): FloatArray {
        require(values.size <= numAxes) { 
            "Too many values (${values.size}) for ${numAxes}-axis filter" 
        }
        
        return FloatArray(values.size) { index ->
            axisFilters[index].filter(values[index], timestamp)
        }
    }
    
    override fun reset() {
        axisFilters.forEach { it.reset() }
    }
    
    override fun getLatencyMs(): Float {
        return axisFilters.maxOfOrNull { it.getLatencyMs() } ?: 0f
    }
    
    override fun isReady(): Boolean {
        return axisFilters.all { it.isReady() }
    }
    
    override fun getName(): String {
        return "MultiEMA(${numAxes}x, α=$alpha)"
    }
    
    /**
     * Get current filtered values for all axes
     */
    fun getCurrentValues(): FloatArray {
        return FloatArray(numAxes) { axisFilters[it].getCurrentValue() }
    }
}