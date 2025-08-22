package com.motosensorlogger.data

import java.nio.ByteBuffer

/**
 * High-performance sensor event data classes optimized for minimal allocation
 * and maximum throughput. Uses primitive types and byte arrays for efficiency.
 */

enum class SensorType(val code: String) {
    GPS("GPS"),
    IMU("IMU"),
    BARO("BARO"),
    MAG("MAG"),
    BATTERY("BATT"),
    EVENT("EVENT")
}

/**
 * Base sensor event using ByteBuffer for zero-allocation data storage
 */
sealed class SensorEvent {
    abstract val timestamp: Long
    abstract val sensorType: SensorType
    abstract fun toCsvRow(): String
    abstract fun toByteArray(): ByteArray
}

/**
 * GPS event: latitude, longitude, altitude, speed, bearing, accuracy
 */
data class GpsEvent(
    override val timestamp: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val speed: Float,
    val bearing: Float,
    val accuracy: Float
) : SensorEvent() {
    override val sensorType = SensorType.GPS
    
    override fun toCsvRow(): String = 
        "$timestamp,${sensorType.code},$latitude,$longitude,$altitude,$speed,$bearing,$accuracy"
    
    override fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(44) // 8 + 8 + 8 + 8 + 4 + 4 + 4
        buffer.putLong(timestamp)
        buffer.putDouble(latitude)
        buffer.putDouble(longitude)
        buffer.putDouble(altitude)
        buffer.putFloat(speed)
        buffer.putFloat(bearing)
        buffer.putFloat(accuracy)
        return buffer.array()
    }
}

/**
 * IMU event: 3-axis accelerometer + 3-axis gyroscope
 * Using float arrays for SIMD optimization potential
 */
data class ImuEvent(
    override val timestamp: Long,
    val accelX: Float,
    val accelY: Float,
    val accelZ: Float,
    val gyroX: Float,
    val gyroY: Float,
    val gyroZ: Float
) : SensorEvent() {
    override val sensorType = SensorType.IMU
    
    override fun toCsvRow(): String = 
        "$timestamp,${sensorType.code},$accelX,$accelY,$accelZ,$gyroX,$gyroY,$gyroZ"
    
    override fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(32) // 8 + 6*4
        buffer.putLong(timestamp)
        buffer.putFloat(accelX)
        buffer.putFloat(accelY)
        buffer.putFloat(accelZ)
        buffer.putFloat(gyroX)
        buffer.putFloat(gyroY)
        buffer.putFloat(gyroZ)
        return buffer.array()
    }
}

/**
 * Barometric event: altitude and pressure
 */
data class BaroEvent(
    override val timestamp: Long,
    val altitudeBaro: Float,
    val pressure: Float
) : SensorEvent() {
    override val sensorType = SensorType.BARO
    
    override fun toCsvRow(): String = 
        "$timestamp,${sensorType.code},$altitudeBaro,$pressure,,,,"
    
    override fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(16) // 8 + 4 + 4
        buffer.putLong(timestamp)
        buffer.putFloat(altitudeBaro)
        buffer.putFloat(pressure)
        return buffer.array()
    }
}

/**
 * Magnetometer event: 3-axis magnetic field
 */
data class MagEvent(
    override val timestamp: Long,
    val magX: Float,
    val magY: Float,
    val magZ: Float
) : SensorEvent() {
    override val sensorType = SensorType.MAG
    
    override fun toCsvRow(): String = 
        "$timestamp,${sensorType.code},$magX,$magY,$magZ,,,"
    
    override fun toByteArray(): ByteArray {
        val buffer = ByteBuffer.allocate(20) // 8 + 3*4
        buffer.putLong(timestamp)
        buffer.putFloat(magX)
        buffer.putFloat(magY)
        buffer.putFloat(magZ)
        return buffer.array()
    }
}

/**
 * Special events: wheelies, jumps, aggressive maneuvers
 */
data class SpecialEvent(
    override val timestamp: Long,
    val eventType: EventType,
    val duration: Long = 0,
    val maxValue: Float = 0f,
    val metadata: String = ""
) : SensorEvent() {
    override val sensorType = SensorType.EVENT
    
    enum class EventType {
        WHEELIE_START,
        WHEELIE_END,
        JUMP_START,
        JUMP_END,
        AGGRESSIVE_TURN,
        HARD_BRAKING,
        HARD_ACCELERATION,
        SESSION_START,
        SESSION_END
    }
    
    override fun toCsvRow(): String = 
        "$timestamp,${sensorType.code},${eventType.name},$duration,$maxValue,\"$metadata\",,"
    
    override fun toByteArray(): ByteArray {
        val metaBytes = metadata.toByteArray()
        val buffer = ByteBuffer.allocate(24 + metaBytes.size)
        buffer.putLong(timestamp)
        buffer.putInt(eventType.ordinal)
        buffer.putLong(duration)
        buffer.putFloat(maxValue)
        buffer.putInt(metaBytes.size)
        buffer.put(metaBytes)
        return buffer.array()
    }
}

/**
 * Ring buffer for lock-free high-performance event storage
 */
class SensorEventBuffer(capacity: Int = 10000) {
    private val buffer = arrayOfNulls<SensorEvent>(capacity)
    @Volatile private var writeIndex = 0
    @Volatile private var readIndex = 0
    
    fun write(event: SensorEvent): Boolean {
        val nextWrite = (writeIndex + 1) % buffer.size
        if (nextWrite == readIndex) return false // Buffer full
        
        buffer[writeIndex] = event
        writeIndex = nextWrite
        return true
    }
    
    fun read(): SensorEvent? {
        if (readIndex == writeIndex) return null // Buffer empty
        
        val event = buffer[readIndex]
        readIndex = (readIndex + 1) % buffer.size
        return event
    }
    
    fun size(): Int {
        val write = writeIndex
        val read = readIndex
        return if (write >= read) {
            write - read
        } else {
            buffer.size - read + write
        }
    }
    
    fun clear() {
        readIndex = 0
        writeIndex = 0
    }
}