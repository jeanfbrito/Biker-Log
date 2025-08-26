package com.motosensorlogger.filters

import kotlin.math.*

/**
 * Second-order Butterworth low-pass filter for removing high-frequency vibrations.
 * 
 * Ideal for motorcycle telemetry to filter out engine and road vibrations (20-50Hz+)
 * while preserving the actual vehicle dynamics (0-10Hz). Butterworth filters provide
 * maximally flat passband response with no ripple.
 * 
 * The filter uses the bilinear transform (Tustin's method) for digital implementation:
 * - Excellent stability characteristics
 * - Linear phase response in passband
 * - -12dB/octave rolloff (2nd order)
 */
class ButterworthLowPassFilter(
    private val cutoffHz: Float,
    private val sampleRateHz: Float
) : FilterStrategy {
    
    // Filter coefficients
    private val a0: Float
    private val a1: Float 
    private val a2: Float
    private val b1: Float
    private val b2: Float
    
    // Filter state variables (Direct Form II)
    private var x1: Float = 0f  // Input delay 1
    private var x2: Float = 0f  // Input delay 2
    private var y1: Float = 0f  // Output delay 1
    private var y2: Float = 0f  // Output delay 2
    
    private var processingTimeNs: Long = 0L
    private var sampleCount: Int = 0
    
    init {
        require(cutoffHz > 0f) { "Cutoff frequency must be positive: $cutoffHz" }
        require(sampleRateHz > 0f) { "Sample rate must be positive: $sampleRateHz" }
        require(cutoffHz < sampleRateHz / 2f) { 
            "Cutoff frequency ($cutoffHz Hz) must be less than Nyquist frequency (${sampleRateHz / 2f} Hz)" 
        }
        
        // Calculate filter coefficients using bilinear transform
        val coefficients = calculateCoefficients(cutoffHz, sampleRateHz)
        
        a0 = coefficients[0]
        a1 = coefficients[1]
        a2 = coefficients[2]
        b1 = coefficients[3]
        b2 = coefficients[4]
    }
    
    override fun filter(value: Float, timestamp: Long): Float {
        val startTime = System.nanoTime()
        
        // Direct Form II implementation (more numerically stable)
        // y[n] = a0*x[n] + a1*x[n-1] + a2*x[n-2] - b1*y[n-1] - b2*y[n-2]
        val output = a0 * value + a1 * x1 + a2 * x2 - b1 * y1 - b2 * y2
        
        // Update state variables
        x2 = x1
        x1 = value
        y2 = y1
        y1 = output
        
        sampleCount++
        processingTimeNs = System.nanoTime() - startTime
        
        return output
    }
    
    override fun reset() {
        x1 = 0f
        x2 = 0f
        y1 = 0f
        y2 = 0f
        sampleCount = 0
        processingTimeNs = 0L
    }
    
    override fun getLatencyMs(): Float {
        return processingTimeNs / 1_000_000f
    }
    
    override fun isReady(): Boolean {
        // Butterworth filter needs a few samples to stabilize
        return sampleCount >= 5
    }
    
    override fun getName(): String {
        return "Butterworth2nd(fc=${cutoffHz}Hz@${sampleRateHz}Hz)"
    }
    
    /**
     * Get the filter's group delay in samples.
     * For 2nd order Butterworth, this is approximately 1 sample.
     */
    fun getGroupDelaySamples(): Float = 1f
    
    /**
     * Get the current filter state for debugging
     */
    fun getFilterState(): FilterState {
        return FilterState(
            x1 = x1, x2 = x2,
            y1 = y1, y2 = y2,
            sampleCount = sampleCount
        )
    }
    
    data class FilterState(
        val x1: Float, val x2: Float,
        val y1: Float, val y2: Float,
        val sampleCount: Int
    )
    
    companion object {
        /**
         * Calculate Butterworth 2nd order filter coefficients using bilinear transform
         * Returns [a0, a1, a2, b1, b2] where:
         * y[n] = a0*x[n] + a1*x[n-1] + a2*x[n-2] - b1*y[n-1] - b2*y[n-2]
         */
        private fun calculateCoefficients(cutoffHz: Float, sampleRateHz: Float): FloatArray {
            // Pre-warp the cutoff frequency for bilinear transform
            val omega = 2f * PI.toFloat() * cutoffHz
            val omegaD = 2f * sampleRateHz * tan((omega / (2f * sampleRateHz)).toDouble()).toFloat()
            
            // Butterworth 2nd order prototype has poles at s = -1±j (normalized)
            val k = omegaD * omegaD
            val sqrt2 = sqrt(2f)
            val a = 1f + sqrt2 * omegaD + k
            
            // Digital filter coefficients
            val a0 = k / a
            val a1 = 2f * k / a
            val a2 = k / a
            val b1 = (2f * k - 2f) / a
            val b2 = (1f - sqrt2 * omegaD + k) / a
            
            return floatArrayOf(a0, a1, a2, b1, b2)
        }
        
        /**
         * Create filter optimized for motorcycle IMU data (100Hz sample rate)
         * Removes engine vibrations (25-100Hz+) while preserving vehicle dynamics
         */
        fun forMotorcycleIMU(cutoffHz: Float = 25f): ButterworthLowPassFilter {
            return ButterworthLowPassFilter(cutoffHz, 100f)
        }
        
        /**
         * Create filter optimized for GPS data (5Hz sample rate)
         * Removes GPS jitter while preserving actual movement
         */
        fun forGPS(cutoffHz: Float = 2f): ButterworthLowPassFilter {
            return ButterworthLowPassFilter(cutoffHz, 5f)
        }
        
        /**
         * Create filter optimized for barometer data (25Hz sample rate)
         * Removes pressure fluctuations while preserving altitude changes
         */
        fun forBarometer(cutoffHz: Float = 5f): ButterworthLowPassFilter {
            return ButterworthLowPassFilter(cutoffHz, 25f)
        }
    }
}

/**
 * Multi-axis Butterworth filter that maintains separate state for each axis
 */
class MultiAxisButterworthFilter(
    cutoffHz: Float,
    sampleRateHz: Float,
    private val numAxes: Int = 3
) : FilterStrategy {
    
    private val axisFilters = Array(numAxes) { 
        ButterworthLowPassFilter(cutoffHz, sampleRateHz) 
    }
    
    override fun filter(value: Float, timestamp: Long): Float {
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
        return "MultiButter(${numAxes}x, ${axisFilters[0].getName()})"
    }
    
    /**
     * Get the total group delay for all axes
     */
    fun getTotalGroupDelaySamples(): Float {
        return axisFilters[0].getGroupDelaySamples()
    }
}