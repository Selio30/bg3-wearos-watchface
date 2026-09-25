package com.bg3.watchface.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.view.SurfaceHolder
import androidx.wear.watchface.CanvasType
import androidx.wear.watchface.ComplicationSlot
import androidx.wear.watchface.RenderParameters
import androidx.wear.watchface.Renderer
import androidx.wear.watchface.TapEvent
import androidx.wear.watchface.TapType
import androidx.wear.watchface.WatchFace
import androidx.wear.watchface.WatchState
import androidx.wear.watchface.style.CurrentUserStyleRepository
import com.bg3.watchface.controller.D20RollController
import com.bg3.watchface.controller.ParticleSystem
import com.bg3.watchface.model.RollState
import com.bg3.watchface.sensor.BatteryMonitor
import com.bg3.watchface.sensor.StepSensorManager
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.cos
import kotlin.math.sin

/**
 * Custom CanvasRenderer for Baldur's Gate 3 Watch Face.
 * Handles rendering the interactive D20, arcane runic circles, HP & XP gauges,
 * and high-efficiency Always-On Display (AOD).
 */
class BG3CanvasRenderer(
    private val context: Context,
    surfaceHolder: SurfaceHolder,
    currentUserStyleRepository: CurrentUserStyleRepository,
    watchState: WatchState,
    canvasType: Int = CanvasType.HARDWARE
) : Renderer.CanvasRenderer(
    surfaceHolder = surfaceHolder,
    currentUserStyleRepository = currentUserStyleRepository,
    watchState = watchState,
    canvasType = canvasType,
    interactiveDrawModeUpdateDelayMillis = 33L // ~30 FPS for smooth breathing and particle animations
), WatchFace.TapListener {

    private val paints = BG3Theme.PaintCache()
    private val d20Geometry = D20Geometry()
    private val particleSystem = ParticleSystem(maxParticles = 120)
    val rollController = D20RollController(context, particleSystem)

    // Sensors
    private var batteryLevel: Float = 1.0f
    private var isBatteryCharging: Boolean = false
    private val batteryMonitor = BatteryMonitor(context) { level, isCharging ->
        batteryLevel = level
        isBatteryCharging = isCharging
        invalidate()
    }

    private var stepCount: Int = 5420
    private var stepProgress: Float = 0.54f
    private val stepSensorManager = StepSensorManager(context) { steps, progress ->
        stepCount = steps
        stepProgress = progress
        invalidate()
    }

    // Geometry & bounding caches
    private val arcBounds = RectF()
    private val textBounds = Rect()
    private var radialBackgroundShader: RadialGradient? = null
    private var lastWidth = -1f
    private var lastHeight = -1f

    private var lastFrameTimeMs = System.currentTimeMillis()

    // Formatters
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())
    private val aodDateFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.getDefault())

    init {
        batteryMonitor.start()
        stepSensorManager.start()
    }

    override fun onDestroy() {
        batteryMonitor.stop()
        stepSensorManager.stop()
        super.onDestroy()
    }

    override fun render(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()
        val centerX = bounds.exactCenterX()
        val centerY = bounds.exactCenterY()
        val d20Radius = width * 0.165f

        val nowMs = System.currentTimeMillis()
        val deltaSeconds = ((nowMs - lastFrameTimeMs).coerceIn(1L, 100L)).toFloat() / 1000f
        lastFrameTimeMs = nowMs

        val isAmbient = renderParameters.drawMode == RenderParameters.DrawMode.AMBIENT

        if (isAmbient) {
            renderAmbientMode(canvas, bounds, zonedDateTime, centerX, centerY, d20Radius)
        } else {
            // Update animations & physics in active mode
            rollController.update(nowMs, centerX, centerY, deltaSeconds)
            renderInteractiveMode(canvas, bounds, zonedDateTime, centerX, centerY, d20Radius, nowMs)
        }
    }

    /**
     * Renders energy-efficient Always-On Display (AOD).
     * Strictly limits active pixels (<10% OPR) with pure black AMOLED background.
     */
    private fun renderAmbientMode(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        centerX: Float,
        centerY: Float,
        d20Radius: Float
    ) {
        // Pure AMOLED black
        canvas.drawColor(BG3Theme.COLOR_AOD_BLACK)

        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // 1. Digital Clock (clean, subdued, non-burn-in)
        paints.aodTimePaint.textSize = width * 0.125f
        val timeStr = zonedDateTime.format(timeFormatter)
        canvas.drawText(timeStr, centerX, height * 0.22f, paints.aodTimePaint)

        // 2. Wireframe D20 outline
        d20Geometry.drawAmbientD20(canvas, centerX, centerY, d20Radius * 0.95f, paints.aodD20StrokePaint)

        // Center number in AOD
        paints.aodSubtextPaint.textSize = d20Radius * 0.48f
        val d20Text = when (val state = rollController.currentState) {
            is RollState.Settled -> state.value.toString()
            is RollState.CriticalSuccess -> "20"
            is RollState.CriticalFailure -> "1"
            else -> "20"
        }
        val textYOffset = paints.aodSubtextPaint.textSize * 0.35f
        canvas.drawText(d20Text, centerX, centerY + textYOffset, paints.aodSubtextPaint)

        // 3. Minimal Date and Battery at bottom
        paints.aodSubtextPaint.textSize = width * 0.042f
        val batteryPct = (batteryLevel * 100).toInt()
        val dateStr = zonedDateTime.format(aodDateFormatter).uppercase(Locale.getDefault())
        val bottomInfo = "$dateStr  •  HP $batteryPct%"
        canvas.drawText(bottomInfo, centerX, height * 0.84f, paints.aodSubtextPaint)
    }

    /**
     * Renders full interactive graphical experience at 30 FPS.
     */
    private fun renderInteractiveMode(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        centerX: Float,
        centerY: Float,
        d20Radius: Float,
        nowMs: Long
    ) {
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        // Update shader if dimensions changed
        if (radialBackgroundShader == null || lastWidth != width || lastHeight != height) {
            lastWidth = width
            lastHeight = height
            radialBackgroundShader = RadialGradient(
                centerX, centerY, width * 0.55f,
                intArrayOf(Color.parseColor("#151224"), BG3Theme.COLOR_BACKGROUND_DARK, BG3Theme.COLOR_BACKGROUND_VOID),
                floatArrayOf(0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }

        // 1. Arcane Void Background
        val bgPaint = paints.d20FillPaint
        bgPaint.shader = radialBackgroundShader
        canvas.drawRect(0f, 0f, width, height, bgPaint)
        bgPaint.shader = null

        // Calculate smooth breathing glow (sine wave ~0.4 Hz)
        val elapsedSec = nowMs.toFloat() / 1000f
        val breathingFactor = (cos(elapsedSec * 2.2f) * 0.15f + 0.85f).coerceIn(0.6f, 1.0f)

        // 2. Outer Arcane Summoning Ring & Runes
        renderSummoningRing(canvas, centerX, centerY, width, breathingFactor)

        // 3. Left Arc: Hit Points (HP) / Battery Gauge
        renderHpGauge(canvas, centerX, centerY, width, height)

        // 4. Right Arc: Experience (XP) / Steps Gauge
        renderXpGauge(canvas, centerX, centerY, width, height)

        // 5. Top: Stylized Digital 24H Clock
        renderDigitalClock(canvas, zonedDateTime, centerX, height, width)

        // 6. Bottom: Date & Heart Rate slot
        renderBottomStatus(canvas, zonedDateTime, centerX, height, width, breathingFactor)

        // 7. Central D20 Die & Roll Animations
        renderCentralD20(canvas, centerX, centerY, d20Radius, breathingFactor)

        // 8. Particle System overlays (Sparks / Smoke)
        particleSystem.render(canvas)
    }

    /**
     * Draws the outer arcane ring etched with Elder Futhark runes.
     */
    private fun renderSummoningRing(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        width: Float,
        breathingFactor: Float
    ) {
        val ringRadius = width * 0.445f
        paints.ringStrokePaint.color = BG3Theme.COLOR_GOLD_DARK
        paints.ringStrokePaint.alpha = (140 * breathingFactor).toInt()
        paints.ringStrokePaint.strokeWidth = 1.2f
        canvas.drawCircle(centerX, centerY, ringRadius, paints.ringStrokePaint)

        val innerRingRadius = width * 0.405f
        paints.ringStrokePaint.strokeWidth = 0.8f
        paints.ringStrokePaint.alpha = (90 * breathingFactor).toInt()
        canvas.drawCircle(centerX, centerY, innerRingRadius, paints.ringStrokePaint)

        // Draw runes around circle
        val runes = BG3Theme.RUNIC_SYMBOLS
        val runeRadius = width * 0.425f
        paints.runePaint.textSize = width * 0.034f
        paints.runePaint.color = BG3Theme.COLOR_GOLD_LIGHT
        paints.runePaint.alpha = (160 * breathingFactor).toInt()

        val stepAngle = 360f / runes.size
        for (i in runes.indices) {
            val angleDeg = i * stepAngle - 90f
            // Skip runes in areas covered by gauges and time
            if (angleDeg in 125f..235f || angleDeg in -55f..55f || angleDeg in -110f..-70f) {
                continue
            }
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val rx = (centerX + runeRadius * cos(angleRad)).toFloat()
            val ry = (centerY + runeRadius * sin(angleRad)).toFloat()

            canvas.save()
            canvas.rotate(angleDeg + 90f, rx, ry)
            canvas.drawText(runes[i], rx, ry + paints.runePaint.textSize * 0.35f, paints.runePaint)
            canvas.restore()
        }
    }

    /**
     * Draws the Hit Points (HP) Gauge on the left arc (representing battery).
     */
    private fun renderHpGauge(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float) {
        val radius = width * 0.42f
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val strokeW = width * 0.024f

        val startAngle = 138f
        val sweepAngle = 84f

        // Track background
        paints.arcTrackPaint.strokeWidth = strokeW
        paints.arcTrackPaint.color = BG3Theme.COLOR_HP_DARK
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, paints.arcTrackPaint)

        // Active HP Progress
        val progressSweep = sweepAngle * batteryLevel.coerceIn(0f, 1f)
        if (progressSweep > 0.5f) {
            // Ruby glow underlay
            paints.arcGlowPaint.strokeWidth = strokeW * 1.6f
            paints.arcGlowPaint.color = BG3Theme.COLOR_HP_GLOW
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcGlowPaint)

            // Core ruby bar
            paints.arcProgressPaint.strokeWidth = strokeW
            paints.arcProgressPaint.color = BG3Theme.COLOR_HP_RUBY
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcProgressPaint)
        }

        // HP Label and Percentage
        paints.subtextPaint.textSize = width * 0.032f
        paints.subtextPaint.color = BG3Theme.COLOR_HP_RUBY
        val hpPct = (batteryLevel * 100).toInt()
        val textAngleRad = Math.toRadians(180.0)
        val textR = radius - width * 0.055f
        val tx = (centerX + textR * cos(textAngleRad)).toFloat()
        val ty = (centerY + textR * sin(textAngleRad)).toFloat()
        canvas.drawText("HP $hpPct%", tx, ty + paints.subtextPaint.textSize * 0.35f, paints.subtextPaint)
    }

    /**
     * Draws the Experience (XP) Gauge on the right arc (representing daily steps).
     */
    private fun renderXpGauge(canvas: Canvas, centerX: Float, centerY: Float, width: Float, height: Float) {
        val radius = width * 0.42f
        arcBounds.set(centerX - radius, centerY - radius, centerX + radius, centerY + radius)
        val strokeW = width * 0.024f

        val startAngle = -42f
        val sweepAngle = 84f

        // Track background
        paints.arcTrackPaint.strokeWidth = strokeW
        paints.arcTrackPaint.color = BG3Theme.COLOR_XP_DARK
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, paints.arcTrackPaint)

        // Active XP Progress
        val progressSweep = sweepAngle * stepProgress.coerceIn(0f, 1f)
        if (progressSweep > 0.5f) {
            // Violet glow underlay
            paints.arcGlowPaint.strokeWidth = strokeW * 1.6f
            paints.arcGlowPaint.color = BG3Theme.COLOR_XP_GLOW
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcGlowPaint)

            // Core sapphire/cyan bar
            paints.arcProgressPaint.strokeWidth = strokeW
            paints.arcProgressPaint.color = BG3Theme.COLOR_XP_CYAN
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcProgressPaint)
        }

        // XP Label and step count
        paints.subtextPaint.textSize = width * 0.032f
        paints.subtextPaint.color = BG3Theme.COLOR_XP_CYAN
        val stepStr = if (stepCount >= 1000) "${String.format(Locale.US, "%.1f", stepCount / 1000f)}k" else "$stepCount"
        val textAngleRad = Math.toRadians(0.0)
        val textR = radius - width * 0.055f
        val tx = (centerX + textR * cos(textAngleRad)).toFloat()
        val ty = (centerY + textR * sin(textAngleRad)).toFloat()
        canvas.drawText("$stepStr XP", tx, ty + paints.subtextPaint.textSize * 0.35f, paints.subtextPaint)
    }

    /**
     * Draws the stylized digital clock at the top in 24h format.
     */
    private fun renderDigitalClock(
        canvas: Canvas,
        zonedDateTime: ZonedDateTime,
        centerX: Float,
        height: Float,
        width: Float
    ) {
        val timeStr = zonedDateTime.format(timeFormatter)
        val timeY = height * 0.20f

        paints.timePaint.textSize = width * 0.128f
        paints.timeShadowPaint.textSize = width * 0.128f

        // Drop shadow for fantasy depth
        canvas.drawText(timeStr, centerX + 2f, timeY + 2.5f, paints.timeShadowPaint)
        // Radiant Arcane Gold Time
        canvas.drawText(timeStr, centerX, timeY, paints.timePaint)

        // Decorative 24h badge
        paints.subtextPaint.textSize = width * 0.026f
        paints.subtextPaint.color = BG3Theme.COLOR_GOLD_DARK
        canvas.drawText("‹ 24h ›", centerX, timeY - paints.timePaint.textSize * 0.85f, paints.subtextPaint)
    }

    /**
     * Draws the bottom status section: Date and Heart Rate slot.
     */
    private fun renderBottomStatus(
        canvas: Canvas,
        zonedDateTime: ZonedDateTime,
        centerX: Float,
        height: Float,
        width: Float,
        breathingFactor: Float
    ) {
        val dateY = height * 0.77f
        val hrY = height * 0.85f

        // Date in medieval uppercase style
        val dateStr = zonedDateTime.format(dateFormatter).uppercase(Locale.getDefault())
        paints.subtextPaint.textSize = width * 0.038f
        paints.subtextPaint.color = BG3Theme.COLOR_GOLD_LIGHT
        canvas.drawText(dateStr, centerX, dateY, paints.subtextPaint)

        // Heart Rate / Vitality slot with pulsating bleeding heart
        paints.heartRatePaint.textSize = width * 0.036f
        val heartAlpha = (180 + (75 * breathingFactor)).toInt().coerceIn(0, 255)
        paints.heartRatePaint.alpha = heartAlpha
        canvas.drawText("♥ 72 BPM", centerX, hrY, paints.heartRatePaint)
    }

    /**
     * Draws the central D20 die, including rotation, shaking, result numbers, and banners.
     */
    private fun renderCentralD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        breathingFactor: Float
    ) {
        var drawX = centerX
        var drawY = centerY
        var rotation = 0f
        var displayNum = 20

        when (val state = rollController.currentState) {
            is RollState.Idle -> {
                displayNum = 20
                // Subtle breathing glow around resting D20
                paints.d20StrokePaint.alpha = (210 * breathingFactor).toInt()
            }
            is RollState.Rolling -> {
                drawX += state.shakeOffsetX
                drawY += state.shakeOffsetY
                rotation = state.rotationDegrees
                displayNum = state.displayValue
                paints.d20StrokePaint.alpha = 255
            }
            is RollState.CriticalSuccess -> {
                displayNum = 20
                paints.d20StrokePaint.alpha = 255
                // Radiant golden glow halo behind D20
                canvas.drawCircle(centerX, centerY, radius * 1.35f, paints.arcGlowPaint.apply {
                    color = BG3Theme.COLOR_GOLD_GLOW
                    strokeWidth = radius * 0.3f
                })
                // Critical Success Banner
                renderBanner(canvas, centerX, centerY - radius * 1.35f, context.getString(com.bg3.watchface.R.string.critical_success), BG3Theme.COLOR_GOLD_PRIMARY)
            }
            is RollState.CriticalFailure -> {
                displayNum = 1
                paints.d20StrokePaint.alpha = 255
                // Dark crimson necrotic glow halo behind D20
                canvas.drawCircle(centerX, centerY, radius * 1.35f, paints.arcGlowPaint.apply {
                    color = BG3Theme.COLOR_HP_GLOW
                    strokeWidth = radius * 0.3f
                })
                // Critical Failure Banner
                renderBanner(canvas, centerX, centerY - radius * 1.35f, context.getString(com.bg3.watchface.R.string.critical_failure), BG3Theme.COLOR_HP_RUBY)
            }
            is RollState.Settled -> {
                displayNum = state.value
                paints.d20StrokePaint.alpha = 255
            }
        }

        // Draw the 3D-shaded metallic D20
        d20Geometry.drawActiveD20(
            canvas = canvas,
            centerX = drawX,
            centerY = drawY,
            radius = radius,
            rotationDegrees = rotation,
            fillPaint = paints.d20FillPaint,
            strokePaint = paints.d20StrokePaint,
            innerStrokePaint = paints.d20InnerStrokePaint
        )

        // Draw central roll number
        renderCentralNumber(canvas, drawX, drawY, radius, displayNum)
    }

    /**
     * Draws the number in the center face of the D20 with drop-shadow and metallic gold finish.
     */
    private fun renderCentralNumber(canvas: Canvas, x: Float, y: Float, radius: Float, number: Int) {
        val numStr = number.toString()
        paints.d20NumberPaint.textSize = radius * 0.65f
        paints.d20NumberPaint.getTextBounds(numStr, 0, numStr.length, textBounds)
        val textY = y + (textBounds.height() * 0.38f)

        // Drop shadow for depth
        paints.d20NumberPaint.color = Color.BLACK
        canvas.drawText(numStr, x + 1.8f, textY + 2.2f, paints.d20NumberPaint)

        // Front text color depends on roll result
        paints.d20NumberPaint.color = when (number) {
            20 -> BG3Theme.COLOR_GOLD_LIGHT
            1 -> BG3Theme.COLOR_HP_RUBY
            else -> BG3Theme.COLOR_GOLD_PRIMARY
        }
        canvas.drawText(numStr, x, textY, paints.d20NumberPaint)
    }

    /**
     * Draws ornate status banners for Critical Hit / Critical Miss.
     */
    private fun renderBanner(canvas: Canvas, x: Float, y: Float, text: String, accentColor: Int) {
        paints.bannerTextPaint.textSize = 15f
        paints.bannerTextPaint.getTextBounds(text, 0, text.length, textBounds)
        val bannerW = textBounds.width() + 32f
        val bannerH = textBounds.height() + 14f

        val rect = RectF(x - bannerW / 2f, y - bannerH / 2f, x + bannerW / 2f, y + bannerH / 2f)

        // Banner background
        paints.bannerBgPaint.color = Color.parseColor("#E60A0A0E")
        canvas.drawRoundRect(rect, 6f, 6f, paints.bannerBgPaint)

        // Banner border
        paints.d20InnerStrokePaint.color = accentColor
        canvas.drawRoundRect(rect, 6f, 6f, paints.d20InnerStrokePaint)

        // Banner text
        paints.bannerTextPaint.color = accentColor
        canvas.drawText(text, x, y + textBounds.height() * 0.35f, paints.bannerTextPaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {
        // Accessibility highlight layer (no-op unless complication is selected)
    }

    override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        if (tapType == TapType.UP) {
            val bounds = surfaceHolder.surfaceFrame
            val centerX = bounds.exactCenterX()
            val centerY = bounds.exactCenterY()
            val d20Radius = bounds.width() * 0.165f

            // Check if user tapped inside the central D20 region
            if (rollController.isD20Tapped(tapEvent.xPos.toFloat(), tapEvent.yPos.toFloat(), centerX, centerY, d20Radius)) {
                rollController.triggerRoll(System.currentTimeMillis(), centerX, centerY)
                invalidate()
            }
        }
    }
}
