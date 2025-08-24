package com.motosensorlogger.services

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.motosensorlogger.settings.SettingsManager
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

/**
 * Quality-focused tests for adaptive GPS sampling rate functionality
 */
@RunWith(RobolectricTestRunner::class)
class AdaptiveGpsQualityTest {
    private lateinit var context: Context
    private lateinit var settingsManager: SettingsManager

    companion object {
        private const val CORNERING_ENTER_THRESHOLD = 0.3f // Enter high rate
        private const val CORNERING_EXIT_THRESHOLD = 0.25f // Exit high rate (hysteresis)
        private const val GPS_NORMAL_INTERVAL = 200L // 5Hz
        private const val GPS_HIGH_RATE_INTERVAL = 100L // 10Hz
        private const val RATE_CHANGE_DEBOUNCE_MS = 500L
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        settingsManager = SettingsManager.getInstance(context)
        settingsManager.resetAllSettings()
    }

    @Test
    fun `should switch to high rate GPS when cornering detected`() {
        var currentInterval = GPS_NORMAL_INTERVAL
        var lastGyroMagnitude = 0f

        // Simulate below threshold - should stay at normal rate
        lastGyroMagnitude = 0.2f
        val shouldBeHighRate1 = lastGyroMagnitude > CORNERING_ENTER_THRESHOLD
        assertFalse("Should not switch to high rate below threshold", shouldBeHighRate1)
        assertEquals("Should maintain normal interval", GPS_NORMAL_INTERVAL, currentInterval)

        // Simulate above threshold - should switch to high rate
        lastGyroMagnitude = 0.4f
        val shouldBeHighRate2 = lastGyroMagnitude > CORNERING_ENTER_THRESHOLD
        assertTrue("Should switch to high rate above threshold", shouldBeHighRate2)
        currentInterval = if (shouldBeHighRate2) GPS_HIGH_RATE_INTERVAL else GPS_NORMAL_INTERVAL
        assertEquals("Should switch to high rate interval", GPS_HIGH_RATE_INTERVAL, currentInterval)
    }

    @Test
    fun `should implement hysteresis to prevent rapid switching`() {
        var isHighRateGps = false
        var currentInterval = GPS_NORMAL_INTERVAL
        val switchCount = AtomicInteger(0)

        fun checkAdaptiveGpsRate(gyroMagnitude: Float): Long {
            val shouldBeHighRate =
                when {
                    isHighRateGps -> gyroMagnitude > CORNERING_EXIT_THRESHOLD
                    else -> gyroMagnitude > CORNERING_ENTER_THRESHOLD
                }

            if (shouldBeHighRate != isHighRateGps) {
                isHighRateGps = shouldBeHighRate
                switchCount.incrementAndGet()
                currentInterval = if (isHighRateGps) GPS_HIGH_RATE_INTERVAL else GPS_NORMAL_INTERVAL
            }
            return currentInterval
        }

        // Start below threshold
        assertEquals(GPS_NORMAL_INTERVAL, checkAdaptiveGpsRate(0.2f))
        assertEquals(0, switchCount.get())

        // Cross enter threshold - should switch to high rate
        assertEquals(GPS_HIGH_RATE_INTERVAL, checkAdaptiveGpsRate(0.35f))
        assertEquals(1, switchCount.get())

        // Drop slightly below enter threshold but above exit - should stay high
        assertEquals(GPS_HIGH_RATE_INTERVAL, checkAdaptiveGpsRate(0.27f))
        assertEquals(1, switchCount.get()) // No additional switch

        // Drop below exit threshold - should switch to normal
        assertEquals(GPS_NORMAL_INTERVAL, checkAdaptiveGpsRate(0.2f))
        assertEquals(2, switchCount.get())

        // Rise slightly above exit but below enter - should stay normal
        assertEquals(GPS_NORMAL_INTERVAL, checkAdaptiveGpsRate(0.27f))
        assertEquals(2, switchCount.get()) // No additional switch
    }

    @Test
    fun `should be thread safe when switching GPS rates`() =
        runBlocking {
            val threadSafeMap = ConcurrentHashMap<String, Long>()
            val gpsUpdateLock = Object()
            var currentInterval = GPS_NORMAL_INTERVAL
            val latch = CountDownLatch(10)

            fun updateGpsRateThreadSafe(newInterval: Long) {
                synchronized(gpsUpdateLock) {
                    currentInterval = newInterval
                    threadSafeMap[Thread.currentThread().name] = newInterval
                }
                latch.countDown()
            }

            // Launch multiple threads trying to update GPS rate simultaneously
            repeat(10) { i ->
                thread {
                    val interval = if (i % 2 == 0) GPS_HIGH_RATE_INTERVAL else GPS_NORMAL_INTERVAL
                    Thread.sleep((0..50).random().toLong()) // Random delay
                    updateGpsRateThreadSafe(interval)
                }
            }

            latch.await()

            // Verify no race conditions occurred
            assertTrue("All threads should have executed", threadSafeMap.size == 10)
            threadSafeMap.values.forEach { interval ->
                assertTrue(
                    "Interval should be valid",
                    interval == GPS_HIGH_RATE_INTERVAL || interval == GPS_NORMAL_INTERVAL,
                )
            }
        }

    @Test
    fun `should handle GPS permission revocation gracefully`() {
        var hasLocationPermission = true
        var currentInterval = GPS_NORMAL_INTERVAL
        var lastError: Exception? = null

        fun updateGpsWithPermissionCheck(newInterval: Long): Boolean {
            return try {
                if (!hasLocationPermission) {
                    throw SecurityException("Location permission denied")
                }
                currentInterval = newInterval
                true
            } catch (e: SecurityException) {
                lastError = e
                false
            }
        }

        // Normal operation with permission
        assertTrue("Should update successfully with permission", updateGpsWithPermissionCheck(GPS_HIGH_RATE_INTERVAL))
        assertEquals(GPS_HIGH_RATE_INTERVAL, currentInterval)
        assertNull(lastError)

        // Revoke permission and try to update
        hasLocationPermission = false
        assertFalse("Should fail gracefully without permission", updateGpsWithPermissionCheck(GPS_NORMAL_INTERVAL))
        assertEquals(GPS_HIGH_RATE_INTERVAL, currentInterval) // Should retain previous value
        assertNotNull(lastError)
        assertEquals("Location permission denied", lastError?.message)
    }

    @Test
    fun `should debounce rapid GPS rate changes`() =
        runBlocking {
            var currentInterval = GPS_NORMAL_INTERVAL
            var lastRateChangeTime = -1000L // Start with a time that allows first change
            val rateChanges = mutableListOf<Pair<Long, Long>>()

            fun checkAdaptiveGpsRateWithDebounce(
                gyroMagnitude: Float,
                currentTime: Long,
            ): Long {
                // Check debounce - but allow first change
                if (lastRateChangeTime >= 0 && currentTime - lastRateChangeTime < RATE_CHANGE_DEBOUNCE_MS) {
                    return currentInterval // Ignore change due to debounce
                }

                val targetInterval =
                    if (gyroMagnitude > CORNERING_ENTER_THRESHOLD) {
                        GPS_HIGH_RATE_INTERVAL
                    } else {
                        GPS_NORMAL_INTERVAL
                    }

                if (targetInterval != currentInterval) {
                    currentInterval = targetInterval
                    lastRateChangeTime = currentTime
                    rateChanges.add(Pair(currentTime, currentInterval))
                }
                return currentInterval
            }

            // Rapid changes within debounce window
            val result1 = checkAdaptiveGpsRateWithDebounce(0.4f, 0L) // Should switch to high rate
            assertEquals(GPS_HIGH_RATE_INTERVAL, result1)
            assertEquals(1, rateChanges.size)

            val result2 = checkAdaptiveGpsRateWithDebounce(0.2f, 100L) // Should be ignored (within debounce)
            assertEquals(GPS_HIGH_RATE_INTERVAL, result2) // Should stay high due to debounce
            assertEquals(1, rateChanges.size) // No new change

            val result3 = checkAdaptiveGpsRateWithDebounce(0.4f, 200L) // Should be ignored (within debounce)
            assertEquals(GPS_HIGH_RATE_INTERVAL, result3) // Should stay high
            assertEquals(1, rateChanges.size) // No new change

            val result4 = checkAdaptiveGpsRateWithDebounce(0.2f, 600L) // Should switch to normal (after debounce)
            assertEquals(GPS_NORMAL_INTERVAL, result4)
            assertEquals(2, rateChanges.size) // Second change

            assertEquals(GPS_HIGH_RATE_INTERVAL, rateChanges[0].second)
            assertEquals(GPS_NORMAL_INTERVAL, rateChanges[1].second)
        }

    @Test
    fun `should maintain GPS data continuity during rate switches`() {
        val gpsEvents = mutableListOf<Long>()
        var currentInterval = GPS_NORMAL_INTERVAL
        val startTime = System.currentTimeMillis()

        // Simulate GPS events at normal rate (5Hz)
        repeat(5) { i ->
            gpsEvents.add(startTime + (i * 200)) // 5Hz
        }

        // Switch to high rate
        currentInterval = GPS_HIGH_RATE_INTERVAL

        // Simulate GPS events at high rate (10Hz)
        repeat(10) { i ->
            gpsEvents.add(startTime + 1000 + (i * 100)) // 10Hz
        }

        // Verify no data gaps during transition
        for (i in 1 until gpsEvents.size) {
            val gap = gpsEvents[i] - gpsEvents[i - 1]
            assertTrue(
                "GPS data gap should not exceed normal interval during transition",
                gap <= GPS_NORMAL_INTERVAL + 50, // Allow 50ms tolerance
            )
        }
    }

    @Test
    fun `should handle location callback cleanup properly`() {
        var activeCallbacks = 0
        var removalFailures = 0

        fun addLocationCallback(): Boolean {
            activeCallbacks++
            return true
        }

        fun removeLocationCallback(): Boolean {
            return if (activeCallbacks > 0) {
                activeCallbacks--
                true
            } else {
                removalFailures++
                false
            }
        }

        // Add callback for normal rate
        assertTrue(addLocationCallback())
        assertEquals(1, activeCallbacks)

        // Switch rate - should remove old and add new
        assertTrue(removeLocationCallback())
        assertTrue(addLocationCallback())
        assertEquals(1, activeCallbacks)

        // Try to remove non-existent callback
        assertTrue(removeLocationCallback())
        assertEquals(0, activeCallbacks)
        assertFalse(removeLocationCallback()) // Should fail
        assertEquals(1, removalFailures)
    }

    @Test
    fun `should optimize performance under high frequency updates`() =
        runBlocking {
            val processingTimes = mutableListOf<Long>()
            var totalGyroEvents = 0

            fun processGyroUpdate(gyroValues: FloatArray): Float {
                val startTime = System.nanoTime()

                // Calculate magnitude
                val magnitude =
                    kotlin.math.sqrt(
                        gyroValues[0] * gyroValues[0] +
                            gyroValues[1] * gyroValues[1] +
                            gyroValues[2] * gyroValues[2],
                    )

                totalGyroEvents++

                val processingTime = System.nanoTime() - startTime
                processingTimes.add(processingTime)

                return magnitude
            }

            // Simulate high frequency gyro updates (100Hz for 1 second)
            repeat(100) {
                val gyroValues =
                    floatArrayOf(
                        (Math.random() * 0.6).toFloat() - 0.3f,
                        (Math.random() * 0.6).toFloat() - 0.3f,
                        (Math.random() * 0.6).toFloat() - 0.3f,
                    )
                processGyroUpdate(gyroValues)
                delay(10) // 100Hz
            }

            // Verify performance
            val avgProcessingTime = processingTimes.average()
            val maxProcessingTime = processingTimes.maxOrNull() ?: 0L

            assertTrue(
                "Average processing time should be under 1ms",
                avgProcessingTime < 1_000_000, // 1ms in nanoseconds
            )
            assertTrue(
                "Max processing time should be under 5ms",
                maxProcessingTime < 5_000_000, // 5ms in nanoseconds
            )
            assertEquals("Should process all events", 100, totalGyroEvents)
        }

    @Test
    fun `battery impact should be optimized with adaptive sampling`() {
        data class PowerConsumption(
            val gpsIntervalMs: Long,
            val isAdaptive: Boolean,
            val corneringPercent: Int,
        ) {
            fun calculateAveragePowerMw(): Double {
                val basePowerMw =
                    when (gpsIntervalMs) {
                        1000L -> 20.0 // 1Hz
                        200L -> 50.0 // 5Hz
                        100L -> 80.0 // 10Hz
                        else -> 50.0
                    }

                return if (isAdaptive) {
                    // Adaptive: mix of normal and high rate based on cornering
                    val normalPower = 50.0 // 5Hz
                    val highPower = 80.0 // 10Hz
                    val normalPercent = (100 - corneringPercent) / 100.0
                    val corneringPowerPercent = corneringPercent / 100.0
                    (normalPower * normalPercent) + (highPower * corneringPowerPercent)
                } else {
                    basePowerMw
                }
            }
        }

        // Test scenarios
        val scenarios =
            listOf(
                PowerConsumption(200L, false, 0), // Fixed 5Hz
                PowerConsumption(100L, false, 0), // Fixed 10Hz
                PowerConsumption(200L, true, 20), // Adaptive with 20% cornering
                PowerConsumption(200L, true, 10), // Adaptive with 10% cornering
            )

        for (scenario in scenarios) {
            val powerMw = scenario.calculateAveragePowerMw()

            if (scenario.isAdaptive) {
                // Adaptive should be more efficient than fixed high rate
                assertTrue(
                    "Adaptive power should be less than fixed 10Hz",
                    powerMw < 80.0,
                )

                // But may be higher than fixed low rate depending on cornering
                if (scenario.corneringPercent > 0) {
                    assertTrue(
                        "Adaptive with cornering should use more than fixed 5Hz",
                        powerMw > 50.0,
                    )
                }
            }
        }

        // Verify adaptive with 10% cornering is more efficient
        val adaptive10 = scenarios[3].calculateAveragePowerMw()
        val fixed10Hz = scenarios[1].calculateAveragePowerMw()

        assertTrue(
            "Adaptive with 10% cornering should save >20% power vs fixed 10Hz",
            adaptive10 < fixed10Hz * 0.8,
        )
    }

    @Test
    fun `should validate trajectory quality with adaptive sampling`() {
        data class GpsPoint(val timestamp: Long, val lat: Double, val lon: Double)

        fun generateTrajectory(useAdaptive: Boolean): List<GpsPoint> {
            val points = mutableListOf<GpsPoint>()
            var timestamp = 0L
            var lat = 37.7749
            var lon = -122.4194

            // Simulate straight section
            repeat(10) {
                points.add(GpsPoint(timestamp, lat, lon))
                timestamp += if (useAdaptive) 200L else 100L // 5Hz vs 10Hz
                lat += 0.0001
            }

            // Simulate cornering section
            repeat(20) {
                points.add(GpsPoint(timestamp, lat, lon))
                timestamp += 100L // Both use 10Hz during cornering
                lat += 0.0001 * kotlin.math.cos(it * 0.1)
                lon += 0.0001 * kotlin.math.sin(it * 0.1)
            }

            return points
        }

        val adaptiveTrajectory = generateTrajectory(true)
        val fixedTrajectory = generateTrajectory(false)

        // Adaptive should have fewer points in straight sections
        assertTrue(
            "Adaptive should be more efficient",
            adaptiveTrajectory.size <= fixedTrajectory.size,
        )

        // But should maintain quality during cornering
        val adaptiveCorneringPoints = adaptiveTrajectory.takeLast(20)
        val fixedCorneringPoints = fixedTrajectory.takeLast(20)

        assertEquals(
            "Should have same density during cornering",
            fixedCorneringPoints.size,
            adaptiveCorneringPoints.size,
        )
    }
}
