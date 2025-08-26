package com.motosensorlogger.filters

/**
 * Interface for pluggable sensor data filters.
 * Allows different filtering strategies to be applied to sensor data.
 */
interface FilterStrategy {
    
    /**
     * Apply the filter to a single data value
     * @param value The raw sensor value
     * @param timestamp The timestamp in nanoseconds
     * @return The filtered value
     */
    fun filter(value: Float, timestamp: Long = System.nanoTime()): Float
    
    /**
     * Apply the filter to multiple values (for multi-axis sensors)
     * @param values Array of raw sensor values
     * @param timestamp The timestamp in nanoseconds
     * @return Array of filtered values
     */
    fun filter(values: FloatArray, timestamp: Long = System.nanoTime()): FloatArray {
        return FloatArray(values.size) { index ->
            filter(values[index], timestamp)
        }
    }
    
    /**
     * Reset the filter state (clear history, buffers, etc.)
     */
    fun reset()
    
    /**
     * Get the filter's processing latency in milliseconds
     */
    fun getLatencyMs(): Float = 0f
    
    /**
     * Check if the filter is ready to produce stable output
     * (e.g., after accumulating enough samples)
     */
    fun isReady(): Boolean = true
    
    /**
     * Get filter name for debugging/logging
     */
    fun getName(): String = this::class.simpleName ?: "UnknownFilter"
}