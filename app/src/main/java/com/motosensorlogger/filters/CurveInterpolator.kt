package com.motosensorlogger.filters

import android.graphics.Path
import android.graphics.PointF
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Professional curve interpolation for smooth telemetry visualization.
 * 
 * Implements cubic Bezier interpolation and Catmull-Rom splines to create
 * smooth, professional-looking curves from discrete sensor data points.
 * This provides the visual polish seen in GoPro/Garmin overlays.
 */
object CurveInterpolator {
    
    /**
     * Create a smooth Path using cubic Bezier interpolation between points.
     * This provides the smoothest visual appearance for telemetry trails.
     * 
     * @param points List of data points to interpolate
     * @param tension Controls curve smoothness (0.0 = sharp corners, 1.0 = very smooth)
     * @return Android Path object ready for Canvas drawing
     */
    fun createSmoothPath(points: List<PointF>, tension: Float = 0.4f): Path {
        val path = Path()
        
        if (points.isEmpty()) return path
        if (points.size == 1) {
            path.addCircle(points[0].x, points[0].y, 2f, Path.Direction.CW)
            return path
        }
        if (points.size == 2) {
            path.moveTo(points[0].x, points[0].y)
            path.lineTo(points[1].x, points[1].y)
            return path
        }
        
        // Start the path at the first point
        path.moveTo(points[0].x, points[0].y)
        
        // Calculate control points for cubic Bezier curves
        val controlPoints = calculateBezierControlPoints(points, tension)
        
        // Draw cubic Bezier segments between each pair of points
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            val cp1 = controlPoints[i * 2]     // First control point
            val cp2 = controlPoints[i * 2 + 1] // Second control point
            
            path.cubicTo(cp1.x, cp1.y, cp2.x, cp2.y, p2.x, p2.y)
        }
        
        return path
    }
    
    /**
     * Create a smooth trail path with fading effect for G-force visualization.
     * Recent points are more opaque, older points fade out.
     */
    fun createFadingTrailPath(
        points: List<PointF>, 
        maxPoints: Int = 50,
        tension: Float = 0.4f
    ): List<Pair<Path, Float>> {
        if (points.size < 2) return emptyList()
        
        // Take only the most recent points
        val recentPoints = if (points.size > maxPoints) {
            points.takeLast(maxPoints)
        } else {
            points
        }
        
        // Create segments with opacity based on age
        val segments = mutableListOf<Pair<Path, Float>>()
        
        // Group points into segments for fading effect
        val segmentSize = 5 // Points per segment
        for (i in 0 until recentPoints.size - segmentSize + 1 step segmentSize / 2) {
            val segmentPoints = recentPoints.subList(
                i, 
                minOf(i + segmentSize, recentPoints.size)
            )
            
            if (segmentPoints.size >= 2) {
                val path = createSmoothPath(segmentPoints, tension)
                // Calculate opacity: newer segments are more opaque
                val age = (recentPoints.size - i).toFloat() / recentPoints.size
                val opacity = (age * 0.8f + 0.2f).coerceIn(0.1f, 1.0f)
                
                segments.add(Pair(path, opacity))
            }
        }
        
        return segments.reversed() // Draw oldest first
    }
    
    /**
     * Calculate control points for cubic Bezier interpolation using Catmull-Rom approach.
     * This ensures C1 continuity (smooth first derivative) at all points.
     */
    private fun calculateBezierControlPoints(
        points: List<PointF>, 
        tension: Float
    ): List<PointF> {
        val controlPoints = mutableListOf<PointF>()
        
        for (i in 0 until points.size - 1) {
            val p0 = if (i > 0) points[i - 1] else points[i]
            val p1 = points[i]
            val p2 = points[i + 1]
            val p3 = if (i + 2 < points.size) points[i + 2] else points[i + 1]
            
            // Calculate tangent vectors
            val t1x = tension * (p2.x - p0.x)
            val t1y = tension * (p2.y - p0.y)
            val t2x = tension * (p3.x - p1.x) 
            val t2y = tension * (p3.y - p1.y)
            
            // Control points for cubic Bezier
            val cp1 = PointF(p1.x + t1x / 3f, p1.y + t1y / 3f)
            val cp2 = PointF(p2.x - t2x / 3f, p2.y - t2y / 3f)
            
            controlPoints.add(cp1)
            controlPoints.add(cp2)
        }
        
        return controlPoints
    }
    
    /**
     * Interpolate additional points between existing points for smoother animation.
     * Useful for upsampling low-frequency data (like GPS) for smooth visualization.
     */
    fun interpolatePoints(
        points: List<PointF>, 
        targetPointsPerSegment: Int = 4
    ): List<PointF> {
        if (points.size < 2) return points
        
        val interpolatedPoints = mutableListOf<PointF>()
        
        for (i in 0 until points.size - 1) {
            val p1 = points[i]
            val p2 = points[i + 1]
            
            interpolatedPoints.add(p1)
            
            // Add interpolated points between p1 and p2
            for (j in 1 until targetPointsPerSegment) {
                val t = j.toFloat() / targetPointsPerSegment
                val x = lerp(p1.x, p2.x, t)
                val y = lerp(p1.y, p2.y, t)
                interpolatedPoints.add(PointF(x, y))
            }
        }
        
        // Add the last point
        interpolatedPoints.add(points.last())
        
        return interpolatedPoints
    }
    
    /**
     * Create smooth circular interpolation for angular data (like heading, roll, pitch).
     * Handles angle wrapping correctly (e.g., 359° to 1° should go through 0°, not 358°).
     */
    fun interpolateAngles(
        angles: List<Float>,
        targetPoints: Int
    ): List<Float> {
        if (angles.size < 2) return angles
        
        val interpolated = mutableListOf<Float>()
        val pointsPerSegment = targetPoints / (angles.size - 1)
        
        for (i in 0 until angles.size - 1) {
            val angle1 = angles[i]
            val angle2 = angles[i + 1]
            
            interpolated.add(angle1)
            
            // Calculate shortest angular distance
            var deltaAngle = angle2 - angle1
            if (deltaAngle > 180f) deltaAngle -= 360f
            if (deltaAngle < -180f) deltaAngle += 360f
            
            // Interpolate angles
            for (j in 1 until pointsPerSegment) {
                val t = j.toFloat() / pointsPerSegment
                var interpolatedAngle = angle1 + deltaAngle * t
                
                // Normalize to [0, 360) range
                while (interpolatedAngle < 0f) interpolatedAngle += 360f
                while (interpolatedAngle >= 360f) interpolatedAngle -= 360f
                
                interpolated.add(interpolatedAngle)
            }
        }
        
        interpolated.add(angles.last())
        return interpolated
    }
    
    /**
     * Apply smoothing to a time series of values using cubic interpolation.
     * Maintains the original timestamps but provides smoother intermediate values.
     */
    fun smoothTimeSeries(
        values: List<Float>,
        timestamps: List<Long>,
        smoothingFactor: Float = 0.3f
    ): List<Float> {
        if (values.size < 3) return values
        
        val smoothed = values.toMutableList()
        
        // Apply cubic smoothing kernel
        for (i in 1 until values.size - 1) {
            val prev = values[i - 1]
            val curr = values[i]
            val next = values[i + 1]
            
            // Weighted average with cubic weighting
            smoothed[i] = curr * (1f - smoothingFactor) + 
                         (prev + next) * smoothingFactor / 2f
        }
        
        return smoothed
    }
    
    // Utility functions
    private fun lerp(a: Float, b: Float, t: Float): Float = a + t * (b - a)
    
    private fun distance(p1: PointF, p2: PointF): Float {
        val dx = p2.x - p1.x
        val dy = p2.y - p1.y
        return sqrt(dx * dx + dy * dy)
    }
}

/**
 * Extension functions for easy integration with existing telemetry views
 */
fun List<PointF>.toSmoothPath(tension: Float = 0.4f): Path {
    return CurveInterpolator.createSmoothPath(this, tension)
}

fun List<PointF>.toFadingTrail(maxPoints: Int = 50, tension: Float = 0.4f): List<Pair<Path, Float>> {
    return CurveInterpolator.createFadingTrailPath(this, maxPoints, tension)
}

/**
 * Data class for storing interpolated telemetry data
 */
data class InterpolatedTelemetryPoint(
    val timestamp: Long,
    val x: Float,
    val y: Float,
    val value: Float,
    val interpolated: Boolean = false
)

/**
 * Professional telemetry curve renderer with advanced visual effects
 */
class TelemetryCurveRenderer {
    
    private val trailPoints = mutableListOf<PointF>()
    private val maxTrailPoints = 100
    
    fun addPoint(x: Float, y: Float) {
        trailPoints.add(PointF(x, y))
        
        // Maintain trail length
        if (trailPoints.size > maxTrailPoints) {
            trailPoints.removeAt(0)
        }
    }
    
    fun clearTrail() {
        trailPoints.clear()
    }
    
    fun getSmoothTrailPath(tension: Float = 0.4f): Path {
        return trailPoints.toSmoothPath(tension)
    }
    
    fun getFadingTrailSegments(tension: Float = 0.4f): List<Pair<Path, Float>> {
        return trailPoints.toFadingTrail(maxTrailPoints, tension)
    }
    
    /**
     * Get fading trail segments with coordinate transformation for custom views.
     * Transforms normalized coordinates (0-1) to screen coordinates.
     */
    fun getFadingTrailSegmentsTransformed(
        centerX: Float, 
        centerY: Float, 
        radius: Float,
        tension: Float = 0.4f
    ): List<Pair<Path, Float>> {
        if (trailPoints.size < 2) return emptyList()
        
        // Transform normalized coordinates to screen coordinates
        val screenPoints = trailPoints.map { point ->
            val screenX = centerX + (point.x - 0.5f) * 2f * radius  // 0-1 to screen coords
            val screenY = centerY - (point.y - 0.5f) * 2f * radius  // 0-1 to screen coords (inverted Y)
            PointF(screenX, screenY)
        }
        
        return screenPoints.toFadingTrail(maxTrailPoints, tension)
    }
    
    fun getTrailPointCount(): Int = trailPoints.size
}