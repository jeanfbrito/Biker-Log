package com.motosensorlogger.data

import java.util.*

/**
 * Data class representing search and filter criteria for log files
 */
data class LogFileSearchFilter(
    val searchQuery: String = "",
    val dateFrom: Date? = null,
    val dateTo: Date? = null,
    val minFileSizeBytes: Long = 0L,
    val maxFileSizeBytes: Long = Long.MAX_VALUE,
    val sortBy: SortBy = SortBy.DATE,
    val sortOrder: SortOrder = SortOrder.DESCENDING
) {
    enum class SortBy {
        NAME, DATE, SIZE
    }
    
    enum class SortOrder {
        ASCENDING, DESCENDING
    }
    
    /**
     * Check if any filters are active (non-default values)
     */
    fun hasActiveFilters(): Boolean {
        return searchQuery.isNotEmpty() ||
                dateFrom != null ||
                dateTo != null ||
                minFileSizeBytes > 0L ||
                maxFileSizeBytes < Long.MAX_VALUE
    }
    
    /**
     * Reset all filters to default values
     */
    fun reset(): LogFileSearchFilter {
        return LogFileSearchFilter()
    }
    
    companion object {
        /**
         * Create a default filter with no criteria applied
         */
        fun default(): LogFileSearchFilter = LogFileSearchFilter()
    }
}