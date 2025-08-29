package com.motosensorlogger.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import java.util.Locale

/**
 * Bar-style Inclinometer view with vertical pitch and horizontal roll bars
 * Inspired by professional aviation/marine inclinometers
 */
class BarInclinometerView
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
        defStyleAttr: Int = 0,
    ) : View(context, attrs, defStyleAttr) {
        // Current angles (raw from sensors)
        private var rawPitch = 0f
        private var rawRoll = 0f

        // Calibration offsets
        private var pitchOffset = 0f
        private var rollOffset = 0f

        // Display angles (after calibration)
        private var pitch = 0f
        private var roll = 0f

        // Display range
        var maxPitchAngle = 60f
        var maxRollAngle = 45f

        // Colors
        var backgroundPanelColor = Color.argb(255, 30, 30, 30)
        var barBackgroundColor = Color.argb(255, 10, 10, 10)
        var barFillColorNormal = Color.argb(255, 0, 255, 0)
        var barFillColorWarning = Color.argb(255, 255, 255, 0)
        var barFillColorDanger = Color.argb(255, 255, 0, 0)
        var scaleColor = Color.WHITE
        var textColor = Color.WHITE
        var centerLineColor = Color.YELLOW
        var gridColor = Color.argb(100, 255, 255, 255)

        // Paint objects
        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val barBackgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val barFillPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val scalePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 2f
                style = Paint.Style.STROKE
            }

        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 20f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

        private val valuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.MONOSPACE
            }

        private val centerLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

        private val bubblePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        /**
         * Set the raw angles from sensors
         */
        fun setAngles(
            pitch: Float,
            roll: Float,
        ) {
            this.rawPitch = pitch
            this.rawRoll = roll
            updateDisplayAngles()
        }

        /**
         * Zero/calibrate the inclinometer at current position
         */
        fun zeroCalibration() {
            pitchOffset = rawPitch
            rollOffset = rawRoll
            updateDisplayAngles()
        }

        /**
         * Reset calibration to default (no offset)
         */
        fun resetCalibration() {
            pitchOffset = 0f
            rollOffset = 0f
            updateDisplayAngles()
        }

        /**
         * Get calibration offsets
         */
        fun getCalibrationOffsets(): Pair<Float, Float> {
            return Pair(pitchOffset, rollOffset)
        }

        /**
         * Set calibration offsets
         */
        fun setCalibrationOffsets(
            pitchOffset: Float,
            rollOffset: Float,
        ) {
            this.pitchOffset = pitchOffset
            this.rollOffset = rollOffset
            updateDisplayAngles()
        }

        /**
         * Get current display angles (after calibration)
         */
        fun getDisplayAngles(): Pair<Float, Float> {
            return Pair(pitch, roll)
        }

        private fun updateDisplayAngles() {
            pitch = rawPitch - pitchOffset
            roll = rawRoll - rollOffset
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            val width = width.toFloat()
            val height = height.toFloat()
            val padding = 20f

            // Draw background panel
            backgroundPaint.color = backgroundPanelColor
            canvas.drawRect(0f, 0f, width, height, backgroundPaint)

            // Calculate dimensions
            val barWidth = 80f
            val centerX = width / 2f
            val centerY = height / 2f

            // Draw center cross with circle (like a target)
            drawCenterTarget(canvas, centerX, centerY)

            // Draw vertical pitch bar (right side)
            val pitchBarX = width - padding - barWidth - 20f
            val pitchBarHeight = height - padding * 4
            val pitchBarY = padding * 2
            drawVerticalPitchBar(canvas, pitchBarX, pitchBarY, barWidth, pitchBarHeight)

            // Draw horizontal roll bar (bottom)
            val rollBarWidth = width - padding * 4 - barWidth - 40f
            val rollBarX = padding * 2
            val rollBarY = height - padding * 2 - barWidth
            drawHorizontalRollBar(canvas, rollBarX, rollBarY, rollBarWidth, barWidth)

            // Draw digital readouts
            drawDigitalReadouts(canvas, width, height)

            // Draw calibration indicator
            if (pitchOffset != 0f || rollOffset != 0f) {
                textPaint.color = Color.argb(200, 0, 255, 0)
                textPaint.textSize = 18f
                canvas.drawText("CAL", centerX, padding + 20f, textPaint)
            }
        }

        private fun drawCenterTarget(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
        ) {
            // Draw center circle
            centerLinePaint.color = centerLineColor
            centerLinePaint.style = Paint.Style.STROKE
            canvas.drawCircle(centerX, centerY, 50f, centerLinePaint)
            canvas.drawCircle(centerX, centerY, 30f, centerLinePaint)

            // Draw crosshair
            canvas.drawLine(centerX - 60f, centerY, centerX + 60f, centerY, centerLinePaint)
            canvas.drawLine(centerX, centerY - 60f, centerX, centerY + 60f, centerLinePaint)

            // Draw bubble indicator showing both pitch and roll
            val bubbleX = centerX + (roll / maxRollAngle * 40f).coerceIn(-40f, 40f)
            val bubbleY = centerY - (pitch / maxPitchAngle * 40f).coerceIn(-40f, 40f)

            bubblePaint.color =
                when {
                    abs(pitch) > 30 || abs(roll) > 30 -> barFillColorDanger
                    abs(pitch) > 20 || abs(roll) > 20 -> barFillColorWarning
                    else -> barFillColorNormal
                }

            // Draw bubble with glow effect
            bubblePaint.alpha = 100
            canvas.drawCircle(bubbleX, bubbleY, 15f, bubblePaint)
            bubblePaint.alpha = 255
            canvas.drawCircle(bubbleX, bubbleY, 10f, bubblePaint)
        }

        private fun drawVerticalPitchBar(
            canvas: Canvas,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) {
            // Draw background
            barBackgroundPaint.color = barBackgroundColor
            canvas.drawRect(x, y, x + width, y + height, barBackgroundPaint)

            // Draw scale lines and labels
            scalePaint.color = scaleColor
            gridPaint.color = gridColor
            textPaint.color = textColor
            textPaint.textSize = 14f
            textPaint.textAlign = Paint.Align.RIGHT

            val centerY = y + height / 2f

            // Draw scale marks
            for (angle in -60..60 step 10) {
                val lineY = centerY - (angle.toFloat() / maxPitchAngle * height / 2f)

                if (lineY >= y && lineY <= y + height) {
                    val lineLength =
                        when {
                            angle == 0 -> width
                            angle % 30 == 0 -> width * 0.8f
                            angle % 20 == 0 -> width * 0.6f
                            else -> width * 0.4f
                        }

                    val paint = if (angle == 0) centerLinePaint else gridPaint
                    canvas.drawLine(x + width - lineLength, lineY, x + width, lineY, paint)

                    // Draw labels for major marks
                    if (angle % 20 == 0) {
                        canvas.drawText("${abs(angle)}°", x - 5f, lineY + 5f, textPaint)
                    }
                }
            }

            // Draw pitch indicator bar
            val clampedPitch = pitch.coerceIn(-maxPitchAngle, maxPitchAngle)
            val barHeight = abs(clampedPitch) / maxPitchAngle * height / 2f

            barFillPaint.color =
                when {
                    abs(pitch) > 40 -> barFillColorDanger
                    abs(pitch) > 25 -> barFillColorWarning
                    else -> barFillColorNormal
                }

            if (pitch > 0) {
                // Pitch up - bar goes up from center
                canvas.drawRect(x + 10f, centerY - barHeight, x + width - 10f, centerY, barFillPaint)
            } else if (pitch < 0) {
                // Pitch down - bar goes down from center
                canvas.drawRect(x + 10f, centerY, x + width - 10f, centerY + barHeight, barFillPaint)
            }

            // Draw center line
            centerLinePaint.color = centerLineColor
            canvas.drawLine(x, centerY, x + width, centerY, centerLinePaint)

            // Draw "PITCH" label
            canvas.save()
            canvas.rotate(-90f, x + width / 2f, y + height / 2f)
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 16f
            canvas.drawText("PITCH", x + width / 2f, y + height / 2f - width / 2f - 10f, textPaint)
            canvas.restore()
        }

        private fun drawHorizontalRollBar(
            canvas: Canvas,
            x: Float,
            y: Float,
            width: Float,
            height: Float,
        ) {
            // Draw background
            barBackgroundPaint.color = barBackgroundColor
            canvas.drawRect(x, y, x + width, y + height, barBackgroundPaint)

            // Draw scale lines and labels
            scalePaint.color = scaleColor
            gridPaint.color = gridColor
            textPaint.color = textColor
            textPaint.textSize = 14f
            textPaint.textAlign = Paint.Align.CENTER

            val centerX = x + width / 2f

            // Draw scale marks
            for (angle in -45..45 step 5) {
                val lineX = centerX + (angle.toFloat() / maxRollAngle * width / 2f)

                if (lineX >= x && lineX <= x + width) {
                    val lineLength =
                        when {
                            angle == 0 -> height
                            angle % 15 == 0 -> height * 0.8f
                            angle % 10 == 0 -> height * 0.6f
                            else -> height * 0.4f
                        }

                    val paint = if (angle == 0) centerLinePaint else gridPaint
                    canvas.drawLine(lineX, y + height - lineLength, lineX, y + height, paint)

                    // Draw labels for major marks
                    if (angle % 15 == 0) {
                        canvas.drawText("${abs(angle)}°", lineX, y + height + 20f, textPaint)
                    }
                }
            }

            // Draw roll indicator bar
            val clampedRoll = roll.coerceIn(-maxRollAngle, maxRollAngle)
            val barWidth = abs(clampedRoll) / maxRollAngle * width / 2f

            barFillPaint.color =
                when {
                    abs(roll) > 35 -> barFillColorDanger
                    abs(roll) > 25 -> barFillColorWarning
                    else -> barFillColorNormal
                }

            if (roll > 0) {
                // Roll right - bar goes right from center
                canvas.drawRect(centerX, y + 10f, centerX + barWidth, y + height - 10f, barFillPaint)
            } else if (roll < 0) {
                // Roll left - bar goes left from center
                canvas.drawRect(centerX - barWidth, y + 10f, centerX, y + height - 10f, barFillPaint)
            }

            // Draw center line
            centerLinePaint.color = centerLineColor
            canvas.drawLine(centerX, y, centerX, y + height, centerLinePaint)

            // Draw "ROLL" label
            textPaint.textAlign = Paint.Align.CENTER
            textPaint.textSize = 16f
            canvas.drawText("ROLL", centerX, y - 5f, textPaint)
        }

        private fun drawDigitalReadouts(
            canvas: Canvas,
            width: Float,
            height: Float,
        ) {
            valuePaint.textAlign = Paint.Align.CENTER

            // Pitch readout (top right)
            valuePaint.color =
                when {
                    abs(pitch) > 40 -> barFillColorDanger
                    abs(pitch) > 25 -> barFillColorWarning
                    else -> barFillColorNormal
                }
            valuePaint.textSize = 35f
            canvas.drawText(String.format(Locale.getDefault(), "%+.1f°", pitch), width - 80f, 50f, valuePaint)

            textPaint.color = Color.GRAY
            textPaint.textSize = 14f
            textPaint.textAlign = Paint.Align.CENTER
            canvas.drawText("PITCH", width - 80f, 70f, textPaint)

            // Roll readout (bottom center)
            valuePaint.color =
                when {
                    abs(roll) > 35 -> barFillColorDanger
                    abs(roll) > 25 -> barFillColorWarning
                    else -> barFillColorNormal
                }
            valuePaint.textSize = 35f
            canvas.drawText(String.format(Locale.getDefault(), "%+.1f°", roll), width / 2f, height - 100f, valuePaint)

            textPaint.color = Color.GRAY
            textPaint.textSize = 14f
            canvas.drawText("ROLL", width / 2f, height - 80f, textPaint)
        }
    }
