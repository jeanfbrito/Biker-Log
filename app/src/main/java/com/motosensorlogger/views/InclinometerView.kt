package com.motosensorlogger.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.min
import java.util.Locale

/**
 * Reusable Inclinometer view for displaying pitch and roll angles
 * Can be calibrated to zero at any phone position
 */
class InclinometerView
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
        var maxAngle = 40f
            set(value) {
                field = value
                invalidate()
            }

        // Colors (customizable)
        var horizonColor = Color.GREEN
        var centerLineColor = Color.YELLOW
        var gridColor = Color.argb(80, 255, 255, 255)
        var textColor = Color.WHITE
        var backgroundCircleColor = Color.argb(255, 20, 20, 20)
        var rollIndicatorColor = Color.CYAN

        // Paint objects
        private val horizonPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

        private val centerLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 4f
                style = Paint.Style.STROKE
            }

        private val gridPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 1f
                style = Paint.Style.STROKE
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 30f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.DEFAULT_BOLD
            }

        private val backgroundPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
            }

        private val rollIndicatorPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

        private val pointerPaint =
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
         * Get current calibration offsets
         */
        fun getCalibrationOffsets(): Pair<Float, Float> {
            return Pair(pitchOffset, rollOffset)
        }

        /**
         * Set calibration offsets (for restoring saved calibration)
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
            // Apply calibration offsets (no limits)
            pitch = rawPitch - pitchOffset
            roll = rawRoll - rollOffset
            invalidate()
        }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)

            // Update paint colors
            horizonPaint.color = horizonColor
            centerLinePaint.color = centerLineColor
            gridPaint.color = gridColor
            textPaint.color = textColor
            backgroundPaint.color = backgroundCircleColor
            rollIndicatorPaint.color = rollIndicatorColor
            pointerPaint.color = rollIndicatorColor

            val centerX = width / 2f
            val centerY = height / 2f
            val radius = min(width, height) / 2f * 0.9f

            // Draw background circle
            canvas.drawCircle(centerX, centerY, radius, backgroundPaint)

            // Draw angle grid lines
            val gridStep = 10
            val visibleRange = maxAngle.toInt()
            for (angle in -visibleRange..visibleRange step gridStep) {
                if (angle != 0) {
                    val alpha = if (angle % 20 == 0) 120 else 60
                    gridPaint.color = Color.argb(alpha, Color.red(gridColor), Color.green(gridColor), Color.blue(gridColor))

                    // Horizontal lines for pitch
                    val y = centerY - (angle * radius / maxAngle)
                    canvas.drawLine(centerX - radius * 0.8f, y, centerX + radius * 0.8f, y, gridPaint)

                    // Vertical lines for roll
                    val x = centerX + (angle * radius / maxAngle)
                    canvas.drawLine(x, centerY - radius * 0.8f, x, centerY + radius * 0.8f, gridPaint)

                    // Draw angle labels
                    if (angle % 20 == 0 && angle != 0) {
                        textPaint.textSize = 20f
                        textPaint.color = Color.argb(150, Color.red(textColor), Color.green(textColor), Color.blue(textColor))

                        // Pitch labels
                        canvas.drawText("$angle°", centerX - radius * 0.9f, y + 5, textPaint)

                        // Roll labels
                        canvas.save()
                        canvas.rotate(90f, x, centerY - radius * 0.9f)
                        canvas.drawText("$angle°", x, centerY - radius * 0.9f, textPaint)
                        canvas.restore()
                    }
                }
            }

            // Draw center crosshair
            canvas.drawLine(centerX - radius * 0.2f, centerY, centerX + radius * 0.2f, centerY, centerLinePaint)
            canvas.drawLine(centerX, centerY - radius * 0.2f, centerX, centerY + radius * 0.2f, centerLinePaint)

            // Draw center dot
            centerLinePaint.style = Paint.Style.FILL
            canvas.drawCircle(centerX, centerY, 5f, centerLinePaint)
            centerLinePaint.style = Paint.Style.STROKE

            // Draw artificial horizon (affected by roll)
            canvas.save()
            canvas.rotate(-roll, centerX, centerY)

            // Horizon line position based on pitch (scale to fit display)
            val scaledPitch = pitch.coerceIn(-maxAngle, maxAngle)
            val horizonY = centerY + (scaledPitch * radius / maxAngle)

            horizonPaint.strokeWidth = 3f
            canvas.drawLine(centerX - radius, horizonY, centerX + radius, horizonY, horizonPaint)

            // Draw pitch ladder
            horizonPaint.strokeWidth = 2f
            for (i in -2..2) {
                if (i != 0) {
                    val ladderY = horizonY + (i * radius * 0.15f)
                    val ladderWidth = radius * (if (i % 2 == 0) 0.4f else 0.25f)
                    canvas.drawLine(centerX - ladderWidth, ladderY, centerX + ladderWidth, ladderY, horizonPaint)
                }
            }

            canvas.restore()

            // Draw roll indicator arc
            val arcRect =
                RectF(
                    centerX - radius * 0.85f,
                    centerY - radius * 0.85f,
                    centerX + radius * 0.85f,
                    centerY + radius * 0.85f,
                )
            canvas.drawArc(arcRect, 180f, 180f, false, rollIndicatorPaint)

            // Draw roll scale marks (full range -90 to 90)
            for (angle in -90..90 step 10) {
                canvas.save()
                canvas.rotate(angle.toFloat(), centerX, centerY)

                val markLength = if (angle % 20 == 0) radius * 0.08f else radius * 0.04f
                val markWidth = if (angle % 20 == 0) 3f else 2f
                rollIndicatorPaint.strokeWidth = markWidth

                canvas.drawLine(
                    centerX,
                    centerY - radius * 0.85f,
                    centerX,
                    centerY - radius * 0.85f + markLength,
                    rollIndicatorPaint,
                )
                canvas.restore()
            }

            // Draw roll pointer (clamp display rotation to ±90 degrees)
            canvas.save()
            val displayRoll = roll.coerceIn(-90f, 90f)
            canvas.rotate(-displayRoll, centerX, centerY)
            val pointerPath =
                Path().apply {
                    moveTo(centerX, centerY - radius * 0.85f)
                    lineTo(centerX - 10, centerY - radius * 0.75f)
                    lineTo(centerX + 10, centerY - radius * 0.75f)
                    close()
                }
            canvas.drawPath(pointerPath, pointerPaint)
            canvas.restore()

            // Draw calibration indicator if calibrated
            if (pitchOffset != 0f || rollOffset != 0f) {
                textPaint.textSize = 20f
                textPaint.color = Color.argb(200, 0, 255, 0)
                canvas.drawText("CAL", centerX, centerY + radius * 0.7f, textPaint)
            }

            // Draw actual angle values when they exceed visual range
            textPaint.textSize = 25f
            if (kotlin.math.abs(pitch) > maxAngle) {
                textPaint.color = Color.RED
                canvas.drawText(String.format(Locale.getDefault(), "PITCH: %.1f°", pitch), centerX, centerY - radius - 40, textPaint)
            }
            if (kotlin.math.abs(roll) > 90f) {
                textPaint.color = Color.RED
                canvas.drawText(String.format(Locale.getDefault(), "ROLL: %.1f°", roll), centerX, centerY + radius + 60, textPaint)
            }
        }
    }
