package com.motosensorlogger.filters

/**
 * Median filter for removing spikes and outliers from sensor data.
 * 
 * Excellent for GPS and magnetometer data which can have sudden spikes
 * due to multipath, interference, or sensor glitches. Median filtering
 * preserves edges while effectively removing impulse noise.
 * 
 * Properties:
 * - Completely removes impulse noise shorter than (window_size + 1) / 2
 * - Preserves signal edges and transitions
 * - Non-linear filter (doesn't blur like linear filters)
 * - Introduces group delay of (window_size - 1) / 2 samples
 */
class MedianFilter(
    private val windowSize: Int
) : FilterStrategy {
    
    private val buffer = CircularBuffer(windowSize)
    private var processingTimeNs: Long = 0L
    
    init {
        require(windowSize > 0 && windowSize % 2 == 1) { 
            "Window size must be positive and odd, got: $windowSize" 
        }
    }
    
    override fun filter(value: Float, timestamp: Long): Float {
        val startTime = System.nanoTime()
        
        // Add new sample to circular buffer
        buffer.add(value)
        
        // Calculate median
        val result = if (buffer.isFull()) {
            calculateMedian(buffer.toArray())
        } else {
            // Not enough samples yet - return current value or simple average
            if (buffer.size() >= 3) {
                calculateMedian(buffer.toArray())
            } else {
                value // Pass through until we have enough samples
            }
        }
        
        processingTimeNs = System.nanoTime() - startTime
        return result
    }
    
    override fun reset() {
        buffer.clear()
        processingTimeNs = 0L
    }
    
    override fun getLatencyMs(): Float {
        return processingTimeNs / 1_000_000f
    }
    
    override fun isReady(): Boolean {
        return buffer.size() >= (windowSize + 1) / 2
    }
    
    override fun getName(): String {
        return "Median(w=$windowSize)"
    }
    
    /**
     * Get the group delay in samples
     */
    fun getGroupDelaySamples(): Float = (windowSize - 1) / 2f
    
    /**
     * Calculate median using efficient quickselect algorithm
     * Faster than full sorting for our use case
     */
    private fun calculateMedian(values: FloatArray): Float {
        if (values.isEmpty()) return 0f
        if (values.size == 1) return values[0]
        
        // Create a copy to avoid modifying original
        val copy = values.copyOf()
        
        return when {
            copy.size % 2 == 1 -> {
                // Odd number of elements - return middle element
                quickSelect(copy, copy.size / 2)
            }
            else -> {
                // Even number of elements - return average of two middle elements
                val mid1 = quickSelect(copy.copyOf(), copy.size / 2 - 1)
                val mid2 = quickSelect(copy.copyOf(), copy.size / 2)
                (mid1 + mid2) / 2f
            }
        }
    }
    
    /**
     * Efficient quickselect algorithm to find the k-th element
     * Average O(n) time complexity vs O(n log n) for full sorting
     */
    private fun quickSelect(array: FloatArray, k: Int): Float {
        var left = 0
        var right = array.size - 1
        
        while (left < right) {
            val pivotIndex = partition(array, left, right)
            
            when {
                pivotIndex == k -> return array[k]
                pivotIndex < k -> left = pivotIndex + 1
                else -> right = pivotIndex - 1
            }
        }
        
        return array[k]
    }
    
    /**
     * Partition function for quickselect
     */
    private fun partition(array: FloatArray, left: Int, right: Int): Int {
        val pivot = array[right]
        var i = left
        
        for (j in left until right) {
            if (array[j] <= pivot) {
                swap(array, i, j)
                i++
            }
        }
        
        swap(array, i, right)
        return i
    }
    
    private fun swap(array: FloatArray, i: Int, j: Int) {
        val temp = array[i]
        array[i] = array[j]
        array[j] = temp
    }
    
    companion object {
        /**
         * Create median filter optimized for GPS data
         * Small window to preserve responsiveness while removing spikes
         */
        fun forGPS(): MedianFilter = MedianFilter(3)
        
        /**
         * Create median filter optimized for magnetometer data
         * Larger window for better spike removal in noisy magnetic environments
         */
        fun forMagnetometer(): MedianFilter = MedianFilter(5)
        
        /**
         * Create median filter for general outlier removal
         */
        fun forOutlierRemoval(windowSize: Int = 5): MedianFilter = MedianFilter(windowSize)
    }
}

/**
 * Multi-axis median filter with separate state for each axis
 */
class MultiAxisMedianFilter(
    windowSize: Int,
    private val numAxes: Int = 3
) : FilterStrategy {
    
    private val axisFilters = Array(numAxes) { MedianFilter(windowSize) }
    
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
        return "MultiMedian(${numAxes}x, ${axisFilters[0].getName()})"
    }
    
    /**
     * Get the total group delay for all axes
     */
    fun getTotalGroupDelaySamples(): Float {
        return axisFilters[0].getGroupDelaySamples()
    }
}

/**
 * Efficient circular buffer for median filter implementation
 */
private class CircularBuffer(private val capacity: Int) {
    private val buffer = FloatArray(capacity)
    private var writeIndex = 0
    private var size = 0
    
    fun add(value: Float) {
        buffer[writeIndex] = value
        writeIndex = (writeIndex + 1) % capacity
        if (size < capacity) size++
    }
    
    fun size(): Int = size
    fun isFull(): Boolean = size == capacity
    
    fun toArray(): FloatArray {
        val result = FloatArray(size)
        
        if (size < capacity) {
            // Buffer not full yet - copy from start
            System.arraycopy(buffer, 0, result, 0, size)
        } else {
            // Buffer is full - handle wraparound
            val firstPart = capacity - writeIndex
            if (firstPart > 0) {
                System.arraycopy(buffer, writeIndex, result, 0, firstPart)
            }
            if (writeIndex > 0) {
                System.arraycopy(buffer, 0, result, firstPart, writeIndex)
            }
        }
        
        return result
    }
    
    fun clear() {
        writeIndex = 0
        size = 0
    }
}