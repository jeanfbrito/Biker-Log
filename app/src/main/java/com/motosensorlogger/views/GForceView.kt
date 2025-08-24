package com.motosensorlogger.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reusable G-Force meter view for displaying acceleration forces
 * Shows lateral, longitudinal, and vertical G-forces with visual trail
 */
class GForceView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        // Current G-force values
        private var gForceX = 0f // Lateral (left/right)
        private var gForceY = 0f // Longitudinal (accel/brake)
        private var gForceZ = 0f // Vertical (bumps/jumps)

        // Display settings
        var maxGForce = 2f
            set(value) {
                field = value
                invalidate()
            }

        var showTrail = true
            set(value) {
                field = value
                if (!value) trailPoints.clear()
                invalidate()
            }

        var showVerticalBar = true
            set(value) {
                field = value
                invalidate()
            }

        var showLabels = true
            set(value) {
                field = value
                invalidate()
            }

        var showGrid = true
            set(value) {
                field = value
                invalidate()
            }

        // Colors (customizable)
        var backgroundCircleColor = Color.argb(255, 20, 20, 20)
        var gridColor = Color.argb(60, 255, 255, 255)
        var axisColor = Color.WHITE
        var lateralLabelColor = Color.RED
        var longitudinalLabelColor = Color.GREEN
        var verticalLabelColor = Color.BLUE
        var trailColor = Color.argb(255, 255, 165, 0)

        // Trail settings
        private val trailPoints = mutableListOf<Triple<Float, Float, Float>>()
        var maxTrailPoints = 20
            set(value) {
                field = value
                while (trailPoints.size > value && trailPoints.isNotEmpty()) {
                    trailPoints.removeAt(0)
                }
            }

        // Paint objects
        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

        private val axisPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }

        private val gForcePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 25f
                textAlign = Paint.Align.CENTER
            }

        private val trailPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val barPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        /**
         * Set current G-force values
         */
        fun setGForce(
            x: Float,
            y: Float,
            z: Float,
        ) {
            gForceX = x.coerceIn(-maxGForce, maxGForce)
            gForceY = y.coerceIn(-maxGForce, maxGForce)
            gForceZ = z.coerceIn(-maxGForce, maxGForce)

            // Add to trail if enabled
            if (showTrail) {
                trailPoints.add(Triple(gForceX, gForceY, gForceZ))
                if (trailPoints.size > maxTrailPoints) {
                    trailPoints.removeAt(0)
                }
            }

            invalidate()
        }

        /**
         * Get current G-force values
         */
        fun getGForce(): Triple<Float, Float, Float> {
            return Triple(gForceX, gForceY, gForceZ)
        }

        /**
         * Get G-force magnitude (lateral + longitudinal)
         */
        fun getHorizontalMagnitude(): Float {
            return sqrt(gForceX * gForceX + gForceY * gForceY)
        }

        /**
         * Get total G-force magnitude
         */
        fun getTotalMagnitude(): Float {
            return sqrt(gForceX * gForceX + gForceY * gForceY + gForceZ * gForceZ)
        }

        /**
         * Clear the trail history
         */
        fun clearTrail() {
            trailPoints.clear()
            invalidate()
        }

        /**
         * Get color for G-force magnitude
         */
        fun getColorForMagnitude(magnitude: Float): Int {
            return when {
                magnitude > maxGForce * 0.75f -> Color.RED
                magnitude > maxGForce * 0.5f -> Color.YELLOW
                magnitude > maxGForce * 0.25f -> Color.argb(255, 255, 165, 0)
                else -> Color.GREEN
            }
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Update paint colors
            backgroundPaint.color = backgroundCircleColor
            gridPaint.color = gridColor
            axisPaint.color = axisColor

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(width, height) / 2f * 0.8f

            // Draw background circle
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

            // Draw grid circles for G levels
            if (showGrid) {
                for (g in 1..maxGForce.toInt()) {
                    val r = radius * g / maxGForce
                    gridPaint.strokeWidth = if (g == 1) 2f else 1f
                    gridPaint.alpha = if (g == 1) 150 else 60
                    canvas.drawCircle(centerX, centerY, r, gridPaint)

                    // Draw G labels
                    if (showLabels) {
                        textPaint.textSize = 20f
                        textPaint.color = Color.argb(150, 255, 255, 255)
                        canvas.drawText("${g}g", centerX + r + 20, centerY + 5, textPaint)
                    }
                }
            }

            // Draw axes
            canvas.drawLine(centerX - radius, centerY, centerX + radius, centerY, axisPaint)
            canvas.drawLine(centerX, centerY - radius, centerX, centerY + radius, axisPaint)

            // Draw axis labels
            if (showLabels) {
                textPaint.textSize = 25f

                // Lateral labels
                textPaint.color = lateralLabelColor
                canvas.drawText("L", centerX - radius - 30, centerY + 8, textPaint)
                canvas.drawText("R", centerX + radius + 30, centerY + 8, textPaint)

                // Longitudinal labels
                textPaint.color = longitudinalLabelColor
                canvas.drawText("ACCEL", centerX, centerY - radius - 10, textPaint)
                canvas.drawText("BRAKE", centerX, centerY + radius + 30, textPaint)
            }

            // Draw trail
            if (showTrail) {
                trailPoints.forEachIndexed { index, point ->
                    val alpha = 100 * index / trailPoints.size
                    trailPaint.color = Color.argb(alpha, Color.red(trailColor), Color.green(trailColor), Color.blue(trailColor))

                    val x = centerX + (point.first * radius / maxGForce)
                    val y = centerY - (point.second * radius / maxGForce)
                    val size = 5f + (5f * index / trailPoints.size)

                    canvas.drawCircle(x, y, size, trailPaint)
                }
            }

            // Draw current G-force point
            val currentX = centerX + (gForceX * radius / maxGForce)
            val currentY = centerY - (gForceY * radius / maxGForce)

            // Color based on magnitude
            val magnitude = getHorizontalMagnitude()
            gForcePaint.color = getColorForMagnitude(magnitude)

            // Draw point with glow effect
            val glowPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gForcePaint.color
                    alpha = 100
                    style = Paint.Style.FILL
                }
            canvas.drawCircle(currentX, currentY, 20f, glowPaint)
            canvas.drawCircle(currentX, currentY, 12f, gForcePaint)

            // Draw crosshair at current position
            val crosshairPaint =
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    color = gForcePaint.color
                    alpha = 150
                    strokeWidth = 2f
                    style = Paint.Style.STROKE
                }
            canvas.drawLine(currentX - 15, currentY, currentX + 15, currentY, crosshairPaint)
            canvas.drawLine(currentX, currentY - 15, currentX, currentY + 15, crosshairPaint)

            // Draw vertical G indicator on the side
            if (showVerticalBar) {
                val barWidth = 30f
                val barHeight = radius * 2
                val barX = width - 60f
                val barY = centerY - radius

                // Background for Z-axis bar
                val barBgPaint =
                    Paint(Paint.ANTI_ALIAS_FLAG).apply {
                        color = Color.argb(100, 255, 255, 255)
                        style = Paint.Style.FILL
                    }
                canvas.drawRect(barX, barY, barX + barWidth, barY + barHeight, barBgPaint)

                // Z-axis G-force bar
                val zNormalized = (gForceZ + maxGForce) / (2 * maxGForce) // Normalize to 0-1
                val zBarTop = barY + barHeight * (1 - zNormalized)

                barPaint.color =
                    when {
                        kotlin.math.abs(gForceZ - 1.0f) > 0.5f -> Color.RED
                        kotlin.math.abs(gForceZ - 1.0f) > 0.3f -> Color.YELLOW
                        else -> verticalLabelColor
                    }

                canvas.drawRect(barX, zBarTop, barX + barWidth, barY + barHeight, barPaint)

                // Draw reference lines
                val oneGLine = barY + barHeight * (1 - (1f + maxGForce) / (2 * maxGForce))
                val zeroGLine = barY + barHeight * (1 - maxGForce / (2 * maxGForce))

                axisPaint.strokeWidth = 2f
                canvas.drawLine(barX - 10, oneGLine, barX + barWidth + 10, oneGLine, axisPaint)
                axisPaint.strokeWidth = 1f
                axisPaint.alpha = 100
                canvas.drawLine(barX - 5, zeroGLine, barX + barWidth + 5, zeroGLine, axisPaint)
                axisPaint.alpha = 255

                if (showLabels) {
                    textPaint.textSize = 15f
                    textPaint.color = Color.WHITE
                    canvas.drawText("1g", barX + barWidth + 25, oneGLine + 5, textPaint)
                    canvas.drawText("0g", barX + barWidth + 25, zeroGLine + 5, textPaint)

                    // Current Z value
                    textPaint.color = barPaint.color
                    canvas.drawText(String.format("%.1fg", gForceZ), barX + barWidth / 2, barY - 10, textPaint)
                }
            }
        }
    }
