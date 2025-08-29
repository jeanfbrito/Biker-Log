package com.motosensorlogger.views

import android.content.Context
import android.graphics.*
import android.util.AttributeSet
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import java.util.Locale

/**
 * Motorcycle visual inclinometer showing bike profile for pitch and rear view for roll
 * with realistic ground line representation
 */
class MotoInclinometerView
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

        // Colors
        var backgroundPanelColor = Color.BLACK
        var groundLineColor = Color.WHITE
        var textColor = Color.WHITE
        var angleTextBackground = Color.argb(100, 50, 50, 50)
        var motorcycleColor = Color.WHITE
        var gridLineColor = Color.argb(50, 255, 255, 255)

        // Paint objects
        private val groundLinePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                strokeWidth = 3f
                style = Paint.Style.STROKE
            }

        private val motorcyclePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                strokeWidth = 3f
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
            }

        private val textPaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 24f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            }

        private val valuePaint =
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                textSize = 36f
                textAlign = Paint.Align.CENTER
                typeface = Typeface.MONOSPACE
            }

        private val backgroundTextPaint =
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

            // Draw black background
            canvas.drawColor(backgroundPanelColor)

            // Split screen - left for pitch (profile), right for roll (rear view)
            val halfWidth = width / 2f

            // Draw pitch view (left side - profile)
            canvas.save()
            canvas.clipRect(0f, 0f, halfWidth, height)
            drawPitchView(canvas, halfWidth / 2f, height / 2f, min(halfWidth, height) * 0.35f)
            canvas.restore()

            // Draw divider line
            val dividerPaint =
                Paint().apply {
                    color = Color.argb(100, 255, 255, 255)
                    strokeWidth = 1f
                }
            canvas.drawLine(halfWidth, 0f, halfWidth, height, dividerPaint)

            // Draw roll view (right side - rear)
            canvas.save()
            canvas.clipRect(halfWidth, 0f, width, height)
            drawRollView(canvas, halfWidth + halfWidth / 2f, height / 2f, min(halfWidth, height) * 0.35f)
            canvas.restore()

            // Draw calibration indicator
            if (pitchOffset != 0f || rollOffset != 0f) {
                textPaint.color = Color.GREEN
                textPaint.textSize = 18f
                canvas.drawText("CAL", width / 2f, 30f, textPaint)
            }
        }

        private fun drawPitchView(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            size: Float,
        ) {
            // Draw background
            canvas.drawColor(backgroundPanelColor)

            // Draw ground line at an angle
            canvas.save()
            canvas.translate(centerX, centerY)
            canvas.rotate(pitch) // Rotate for slope

            // Draw ground line
            groundLinePaint.color = groundLineColor
            groundLinePaint.strokeWidth = 3f
            canvas.drawLine(-size * 1.5f, 0f, size * 1.5f, 0f, groundLinePaint)

            // Draw grid lines below ground
            groundLinePaint.strokeWidth = 1f
            groundLinePaint.color = gridLineColor
            for (i in 1..5) {
                val y = i * 15f
                canvas.drawLine(-size * 1.5f, y, size * 1.5f, y, groundLinePaint)
            }

            // Draw motorcycle that follows the ground angle
            drawMotorcycleProfile(canvas, size * 0.8f)

            canvas.restore()

            // Draw pitch value
            drawAngleText(canvas, centerX, centerY + size + 40f, "PITCH", pitch)
        }

        private fun drawRollView(
            canvas: Canvas,
            centerX: Float,
            centerY: Float,
            size: Float,
        ) {
            // Draw background
            canvas.drawColor(backgroundPanelColor)

            // Draw level ground line (doesn't tilt for roll)
            groundLinePaint.color = groundLineColor
            groundLinePaint.strokeWidth = 3f
            canvas.drawLine(centerX - size * 1.5f, centerY, centerX + size * 1.5f, centerY, groundLinePaint)

            // Draw grid lines below ground
            groundLinePaint.strokeWidth = 1f
            groundLinePaint.color = gridLineColor
            for (i in 1..5) {
                val y = centerY + i * 15f
                canvas.drawLine(centerX - size * 1.5f, y, centerX + size * 1.5f, y, groundLinePaint)
            }

            // Draw motorcycle rear view
            canvas.save()
            canvas.translate(centerX, centerY)

            // Rotate the motorcycle based on roll (positive roll = lean right)
            canvas.rotate(roll)
            drawMotorcycleRear(canvas, size * 0.8f)

            canvas.restore()

            // Draw roll value
            drawAngleText(canvas, centerX, centerY + size + 40f, "ROLL", roll)
        }

        private fun drawMotorcycleProfile(
            canvas: Canvas,
            size: Float,
        ) {
            // Simplified motorcycle profile (side view)
            val path = Path()

            // Scale factor
            val s = size / 100f

            // Wheels
            motorcyclePaint.style = Paint.Style.STROKE
            motorcyclePaint.color = motorcycleColor
            motorcyclePaint.strokeWidth = 3f * s

            // Front wheel
            canvas.drawCircle(-35f * s, 25f * s, 20f * s, motorcyclePaint)
            // Rear wheel
            canvas.drawCircle(35f * s, 25f * s, 20f * s, motorcyclePaint)

            // Body outline
            path.moveTo(-35f * s, 5f * s) // Front wheel top
            path.lineTo(-25f * s, -10f * s) // Front fork
            path.lineTo(-15f * s, -25f * s) // Handlebars
            path.lineTo(-5f * s, -20f * s) // Tank front
            path.lineTo(10f * s, -25f * s) // Tank top
            path.lineTo(25f * s, -20f * s) // Seat
            path.lineTo(35f * s, -15f * s) // Tail
            path.lineTo(35f * s, 5f * s) // Rear wheel top

            canvas.drawPath(path, motorcyclePaint)

            // Engine/body fill
            path.reset()
            path.moveTo(-20f * s, 5f * s)
            path.lineTo(-10f * s, -15f * s)
            path.lineTo(20f * s, -15f * s)
            path.lineTo(30f * s, 5f * s)
            path.close()

            // Don't fill, just stroke in white
            motorcyclePaint.style = Paint.Style.STROKE

            // Rider silhouette
            val riderPath = Path()
            riderPath.moveTo(0f, -25f * s) // Seat
            riderPath.lineTo(0f, -40f * s) // Back
            riderPath.lineTo(-10f * s, -45f * s) // Shoulders
            riderPath.lineTo(-15f * s, -35f * s) // Arms to handlebars

            motorcyclePaint.strokeWidth = 2f * s
            canvas.drawPath(riderPath, motorcyclePaint)

            // Helmet
            canvas.drawCircle(-10f * s, -50f * s, 8f * s, motorcyclePaint)
        }

        private fun drawMotorcycleRear(
            canvas: Canvas,
            size: Float,
        ) {
            // Simplified motorcycle rear view
            val s = size / 100f

            motorcyclePaint.style = Paint.Style.STROKE
            motorcyclePaint.color = motorcycleColor
            motorcyclePaint.strokeWidth = 3f * s

            // Wheels (viewed from behind)
            val wheelWidth = 15f * s
            val wheelHeight = 35f * s
            val wheelY = 20f * s

            // Left wheel
            val leftWheel =
                RectF(
                    -35f * s - wheelWidth / 2,
                    wheelY - wheelHeight / 2,
                    -35f * s + wheelWidth / 2,
                    wheelY + wheelHeight / 2,
                )
            canvas.drawOval(leftWheel, motorcyclePaint)

            // Right wheel
            val rightWheel =
                RectF(
                    35f * s - wheelWidth / 2,
                    wheelY - wheelHeight / 2,
                    35f * s + wheelWidth / 2,
                    wheelY + wheelHeight / 2,
                )
            canvas.drawOval(rightWheel, motorcyclePaint)

            // Body
            val bodyPath = Path()
            bodyPath.moveTo(-30f * s, 0f)
            bodyPath.lineTo(-25f * s, -20f * s)
            bodyPath.lineTo(-15f * s, -30f * s)
            bodyPath.lineTo(15f * s, -30f * s)
            bodyPath.lineTo(25f * s, -20f * s)
            bodyPath.lineTo(30f * s, 0f)
            bodyPath.lineTo(30f * s, 15f * s)
            bodyPath.lineTo(-30f * s, 15f * s)
            bodyPath.close()

            // Just stroke the body outline
            motorcyclePaint.style = Paint.Style.STROKE
            canvas.drawPath(bodyPath, motorcyclePaint)

            // Handlebars
            canvas.drawLine(-40f * s, -25f * s, -20f * s, -25f * s, motorcyclePaint)
            canvas.drawLine(20f * s, -25f * s, 40f * s, -25f * s, motorcyclePaint)

            // Rider (viewed from behind)
            val riderPath = Path()
            riderPath.moveTo(0f, -30f * s) // Seat level
            riderPath.lineTo(0f, -45f * s) // Back
            riderPath.lineTo(-10f * s, -40f * s) // Left shoulder
            riderPath.moveTo(0f, -45f * s)
            riderPath.lineTo(10f * s, -40f * s) // Right shoulder

            // Arms to handlebars
            riderPath.moveTo(-10f * s, -40f * s)
            riderPath.lineTo(-25f * s, -25f * s)
            riderPath.moveTo(10f * s, -40f * s)
            riderPath.lineTo(25f * s, -25f * s)

            motorcyclePaint.strokeWidth = 2f * s
            canvas.drawPath(riderPath, motorcyclePaint)

            // Helmet
            canvas.drawCircle(0f, -52f * s, 10f * s, motorcyclePaint)
        }

        private fun drawAngleText(
            canvas: Canvas,
            x: Float,
            y: Float,
            label: String,
            angle: Float,
        ) {
            // Background for text
            backgroundTextPaint.color = angleTextBackground
            val padding = 10f
            val textWidth = 150f
            val textHeight = 60f

            val rect = RectF(x - textWidth / 2, y - textHeight / 2, x + textWidth / 2, y + textHeight / 2)
            canvas.drawRoundRect(rect, 10f, 10f, backgroundTextPaint)

            // Label
            textPaint.color = Color.GRAY
            textPaint.textSize = 16f
            canvas.drawText(label, x, y - 10f, textPaint)

            // Value
            valuePaint.color =
                when {
                    abs(angle) > 35 -> Color.RED
                    abs(angle) > 25 -> Color.YELLOW
                    else -> Color.GREEN
                }
            valuePaint.textSize = 28f
            canvas.drawText(String.format(Locale.getDefault(), "%+.1f°", angle), x, y + 20f, valuePaint)
        }
    }
