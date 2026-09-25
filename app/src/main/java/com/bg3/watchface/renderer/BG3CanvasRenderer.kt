package com.bg3.watchface.renderer

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
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
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * Advanced Baldur's Gate 3 CanvasRenderer with multi-zone touch interactions,
 * Origin Companion Themes (Tav, Astarion, Shadowheart, Karlach), Faerûn lore calendar,
 * and D&D 5e DC ability checks.
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
    interactiveDrawModeUpdateDelayMillis = 33L
), WatchFace.TapListener {

    private val paints = BG3Theme.PaintCache()
    private val d20Geometry = D20Geometry()
    private val particleSystem = ParticleSystem(maxParticles = 120)
    val rollController = D20RollController(context, particleSystem)
    val weatherManager = com.bg3.watchface.sensor.WeatherManager(initialTempCelsius = 22)

    // UX Customization State
    var currentTheme: ThemeVariant = ThemeVariant.CLASSIC_TAV
        private set

    var batteryDisplayMode: BatteryDisplayMode = BatteryDisplayMode.PERCENTAGE
        private set

    var stepsDisplayMode: StepsDisplayMode = StepsDisplayMode.STEPS_XP
        private set

    var timeDisplayMode: TimeDisplayMode = TimeDisplayMode.FORMAT_24H
        private set

    var calendarDisplayMode: CalendarDisplayMode = CalendarDisplayMode.GREGORIAN
        private set

    // Sensors
    private var batteryLevel: Float = 1.0f
    private val batteryMonitor = BatteryMonitor(context) { level, _ ->
        batteryLevel = level
        invalidate()
    }

    private var stepCount: Int = 7420
    private var stepProgress: Float = 0.742f
    private val stepSensorManager = StepSensorManager(context) { steps, progress ->
        stepCount = steps
        stepProgress = progress
        invalidate()
    }

    // Geometry caches
    private val arcBounds = RectF()
    private val textBounds = Rect()
    private var radialBackgroundShader: RadialGradient? = null
    private var lastWidth = -1f
    private var lastHeight = -1f
    private var lastFrameTimeMs = System.currentTimeMillis()
    private var runeRingAngle = 0f

    // Formatters
    private val time24Formatter = DateTimeFormatter.ofPattern("HH:mm")
    private val time12Formatter = DateTimeFormatter.ofPattern("hh:mm a")
    private val dateFormatter = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.getDefault())

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
        val d20Radius = width * 0.138f

        val nowMs = System.currentTimeMillis()
        val deltaSeconds = ((nowMs - lastFrameTimeMs).coerceIn(1L, 100L)).toFloat() / 1000f
        lastFrameTimeMs = nowMs

        val isRolling = rollController.currentState is RollState.Rolling
        val spinSpeed = if (isRolling) 75f else 5.5f
        runeRingAngle = (runeRingAngle + spinSpeed * deltaSeconds) % 360f

        if (renderParameters.drawMode == RenderParameters.DrawMode.AMBIENT) {
            renderAmbientMode(canvas, bounds, zonedDateTime, centerX, centerY, d20Radius)
        } else {
            rollController.update(nowMs, centerX, centerY, deltaSeconds)
            renderInteractiveMode(canvas, bounds, zonedDateTime, centerX, centerY, d20Radius, nowMs)
        }
    }

    private fun renderAmbientMode(
        canvas: Canvas,
        bounds: Rect,
        zonedDateTime: ZonedDateTime,
        centerX: Float,
        centerY: Float,
        d20Radius: Float
    ) {
        canvas.drawColor(BG3Theme.COLOR_AOD_BLACK)
        val width = bounds.width().toFloat()
        val height = bounds.height().toFloat()

        paints.aodTimePaint.textSize = width * 0.11f
        val timeStr = if (timeDisplayMode == TimeDisplayMode.FORMAT_24H) {
            zonedDateTime.format(time24Formatter)
        } else {
            zonedDateTime.format(time12Formatter)
        }
        canvas.drawText(timeStr, centerX, height * 0.215f, paints.aodTimePaint)

        d20Geometry.drawAmbientD20(canvas, centerX, centerY, d20Radius * 0.95f, paints.aodD20StrokePaint)

        paints.aodSubtextPaint.textSize = d20Radius * 0.50f
        val d20Text = when (val state = rollController.currentState) {
            is RollState.Settled -> state.value.toString()
            is RollState.CriticalSuccess -> "20"
            is RollState.CriticalFailure -> "1"
            else -> "20"
        }
        canvas.drawText(d20Text, centerX, centerY + paints.aodSubtextPaint.textSize * 0.35f, paints.aodSubtextPaint)

        paints.aodSubtextPaint.textSize = width * 0.033f
        val weatherTemp = weatherManager.weatherInfo.displayTemp
        val bottomInfo = "${zonedDateTime.format(dateFormatter).uppercase()}  •  $weatherTemp  •  HP ${(batteryLevel * 100).toInt()}%"
        canvas.drawText(bottomInfo, centerX, height * 0.82f, paints.aodSubtextPaint)
    }

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

        if (radialBackgroundShader == null || lastWidth != width || lastHeight != height) {
            lastWidth = width
            lastHeight = height
            radialBackgroundShader = RadialGradient(
                centerX, centerY, width * 0.55f,
                intArrayOf(currentTheme.backgroundCore, BG3Theme.COLOR_BACKGROUND_DARK, BG3Theme.COLOR_BACKGROUND_VOID),
                floatArrayOf(0f, 0.65f, 1.0f),
                Shader.TileMode.CLAMP
            )
        }

        val bgPaint = paints.d20FillPaint
        bgPaint.shader = radialBackgroundShader
        canvas.drawRect(0f, 0f, width, height, bgPaint)
        bgPaint.shader = null

        val elapsedSec = nowMs.toFloat() / 1000f
        val breathing = (cos(elapsedSec * 2.2f) * 0.15f + 0.85f).coerceIn(0.6f, 1.0f)

        // 1. Outer Arcane Summoning Ring
        renderSummoningRing(canvas, centerX, centerY, width, breathing)

        // 2. Left Arc: HP / Battery Gauge
        renderHpGauge(canvas, centerX, centerY, width)

        // 3. Right Arc: XP / Steps Gauge
        renderXpGauge(canvas, centerX, centerY, width)

        // 4. Top: Stylized Digital Clock
        renderDigitalClock(canvas, zonedDateTime, centerX, height, width)

        // 5. Ability Check / DC Pill Badge above D20
        renderSkillCheckBadge(canvas, centerX, height * 0.30f, width)

        // 6. Central D20 Die & Rolls
        renderCentralD20(canvas, centerX, centerY, d20Radius, breathing)

        // 7. Bottom: Date & Heart Rate
        renderBottomStatus(canvas, zonedDateTime, centerX, height, width, breathing)

        // 8. Particle System overlays
        particleSystem.render(canvas)
    }

    private fun renderSummoningRing(canvas: Canvas, cx: Float, cy: Float, width: Float, breathing: Float) {
        val ringR = width * 0.472f
        paints.ringStrokePaint.color = currentTheme.goldDark
        paints.ringStrokePaint.alpha = (50 * breathing).toInt()
        paints.ringStrokePaint.strokeWidth = 0.9f
        canvas.drawCircle(cx, cy, ringR, paints.ringStrokePaint)

        val innerRingR = width * 0.430f
        paints.ringStrokePaint.strokeWidth = 0.6f
        paints.ringStrokePaint.alpha = (35 * breathing).toInt()
        canvas.drawCircle(cx, cy, innerRingR, paints.ringStrokePaint)

        val runes = BG3Theme.RUNIC_SYMBOLS
        val runeR = width * 0.451f
        paints.runePaint.textSize = width * 0.027f
        paints.runePaint.color = currentTheme.goldLight
        paints.runePaint.alpha = (150 * breathing).toInt()

        val stepAngle = 360f / runes.size
        for (i in runes.indices) {
            val angleDeg = (i * stepAngle - 90f + runeRingAngle) % 360f
            val angleRad = Math.toRadians(angleDeg.toDouble())
            val rx = (cx + runeR * cos(angleRad)).toFloat()
            val ry = (cy + runeR * sin(angleRad)).toFloat()

            canvas.save()
            canvas.rotate(angleDeg + 90f, rx, ry)
            canvas.drawText(runes[i], rx, ry + paints.runePaint.textSize * 0.35f, paints.runePaint)
            canvas.restore()
        }
    }

    private fun renderHpGauge(canvas: Canvas, cx: Float, cy: Float, width: Float) {
        val radius = width * 0.405f
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val strokeW = width * 0.024f
        val startAngle = 138f
        val sweepAngle = 84f

        paints.arcTrackPaint.strokeWidth = strokeW
        paints.arcTrackPaint.color = currentTheme.hpDark
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, paints.arcTrackPaint)

        val progressSweep = sweepAngle * batteryLevel.coerceIn(0f, 1f)
        if (progressSweep > 0.5f) {
            paints.arcGlowPaint.strokeWidth = strokeW * 1.6f
            paints.arcGlowPaint.color = currentTheme.hpRuby and 0x55FFFFFF or 0x66000000
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcGlowPaint)

            paints.arcProgressPaint.strokeWidth = strokeW
            paints.arcProgressPaint.color = currentTheme.hpRuby
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcProgressPaint)
        }

        paints.subtextPaint.textSize = width * 0.030f
        paints.subtextPaint.color = currentTheme.hpRuby
        val hpText = if (batteryDisplayMode == BatteryDisplayMode.PERCENTAGE) {
            "HP ${(batteryLevel * 100).toInt()}%"
        } else {
            val hours = (batteryLevel * 24).toInt()
            "${hours}h BAT"
        }
        val textR = radius * 0.62f
        canvas.drawText(hpText, cx - textR, cy + paints.subtextPaint.textSize * 0.35f, paints.subtextPaint)
    }

    private fun renderXpGauge(canvas: Canvas, cx: Float, cy: Float, width: Float) {
        val radius = width * 0.405f
        arcBounds.set(cx - radius, cy - radius, cx + radius, cy + radius)
        val strokeW = width * 0.024f
        val startAngle = -42f
        val sweepAngle = 84f

        paints.arcTrackPaint.strokeWidth = strokeW
        paints.arcTrackPaint.color = currentTheme.xpDark
        canvas.drawArc(arcBounds, startAngle, sweepAngle, false, paints.arcTrackPaint)

        val progressSweep = sweepAngle * stepProgress.coerceIn(0f, 1f)
        if (progressSweep > 0.5f) {
            paints.arcGlowPaint.strokeWidth = strokeW * 1.6f
            paints.arcGlowPaint.color = currentTheme.xpArcane and 0x55FFFFFF or 0x66000000
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcGlowPaint)

            paints.arcProgressPaint.strokeWidth = strokeW
            paints.arcProgressPaint.color = currentTheme.xpArcane
            canvas.drawArc(arcBounds, startAngle, progressSweep, false, paints.arcProgressPaint)
        }

        paints.subtextPaint.textSize = width * 0.030f
        paints.subtextPaint.color = currentTheme.xpArcane
        val xpText = when (stepsDisplayMode) {
            StepsDisplayMode.STEPS_XP -> if (stepCount >= 1000) "${String.format(Locale.US, "%.1f", stepCount / 1000f)}k XP" else "$stepCount XP"
            StepsDisplayMode.DISTANCE_KM -> String.format(Locale.US, "%.1f km", stepCount * 0.00075f)
            StepsDisplayMode.CALORIES_KCAL -> "${(stepCount * 0.045f).toInt()} kcal"
        }
        val textR = radius * 0.62f
        canvas.drawText(xpText, cx + textR, cy + paints.subtextPaint.textSize * 0.35f, paints.subtextPaint)
    }

    private fun renderDigitalClock(canvas: Canvas, zdt: ZonedDateTime, cx: Float, height: Float, width: Float) {
        val timeStr = if (timeDisplayMode == TimeDisplayMode.FORMAT_24H) {
            zdt.format(time24Formatter)
        } else {
            zdt.format(time12Formatter)
        }
        val timeY = height * 0.215f

        paints.timePaint.textSize = width * 0.11f
        paints.timeShadowPaint.textSize = width * 0.11f
        paints.timePaint.color = currentTheme.goldPrimary

        canvas.drawText(timeStr, cx + 2f, timeY + 2.5f, paints.timeShadowPaint)
        canvas.drawText(timeStr, cx, timeY, paints.timePaint)

        paints.subtextPaint.textSize = width * 0.024f
        paints.subtextPaint.color = currentTheme.goldDark
        val badge = if (timeDisplayMode == TimeDisplayMode.FORMAT_24H) "‹ 24h • ${currentTheme.title} ›" else "‹ 12h • ${currentTheme.title} ›"
        canvas.drawText(badge, cx, timeY - paints.timePaint.textSize * 0.82f, paints.subtextPaint)
    }

    private fun renderSkillCheckBadge(canvas: Canvas, cx: Float, y: Float, width: Float) {
        val ability = rollController.currentAbility
        val dc = rollController.currentDC
        val badgeText = "• ${ability.code} +${ability.defaultMod}  |  CD $dc •"

        paints.badgePaint.textSize = width * 0.030f
        paints.badgePaint.getTextBounds(badgeText, 0, badgeText.length, textBounds)
        val bw = textBounds.width() + 24f
        val bh = textBounds.height() + 10f

        val rect = RectF(cx - bw / 2f, y - bh / 2f, cx + bw / 2f, y + bh / 2f)
        paints.bannerBgPaint.color = Color.parseColor("#B30A0A0E")
        canvas.drawRoundRect(rect, 4f, 4f, paints.bannerBgPaint)

        paints.d20InnerStrokePaint.color = currentTheme.goldDark
        canvas.drawRoundRect(rect, 4f, 4f, paints.d20InnerStrokePaint)

        paints.badgePaint.color = currentTheme.goldLight
        canvas.drawText(badgeText, cx, y + textBounds.height() * 0.35f, paints.badgePaint)
    }

    private fun renderCentralD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        breathing: Float
    ) {
        var drawX = centerX
        var drawY = centerY
        val rotX = rollController.curRotX
        val rotY = rollController.curRotY
        val rotZ = rollController.curRotZ
        val hopY = rollController.hopY
        val scaleX = rollController.scaleX
        val scaleY = rollController.scaleY
        var displayNum = 20

        when (val state = rollController.currentState) {
            is RollState.Idle -> {
                displayNum = 20
                paints.d20StrokePaint.alpha = (210 * breathing).toInt()
            }
            is RollState.Rolling -> {
                drawX += state.shakeOffsetX
                drawY += state.shakeOffsetY
                displayNum = state.displayValue
                paints.d20StrokePaint.alpha = 255
            }
            is RollState.CriticalSuccess -> {
                displayNum = 20
                paints.d20StrokePaint.alpha = 255
                canvas.drawCircle(centerX, centerY, radius * 1.35f, paints.arcGlowPaint.apply {
                    color = currentTheme.goldLight and 0x55FFFFFF or 0x66000000
                    strokeWidth = radius * 0.3f
                })
                renderBanner(canvas, centerX, height * 0.74f, "¡ÉXITO CRÍTICO (20)!", currentTheme.bannerTextSuccess)
            }
            is RollState.CriticalFailure -> {
                displayNum = 1
                paints.d20StrokePaint.alpha = 255
                canvas.drawCircle(centerX, centerY, radius * 1.35f, paints.arcGlowPaint.apply {
                    color = currentTheme.hpRuby and 0x55FFFFFF or 0x66000000
                    strokeWidth = radius * 0.3f
                })
                renderBanner(canvas, centerX, height * 0.74f, "¡PIFIA CRÍTICA (1)!", currentTheme.bannerTextFail)
            }
            is RollState.Settled -> {
                displayNum = state.value
                paints.d20StrokePaint.alpha = 255
                val bannerMsg = if (state.isPassed) "¡SUPERADO! (${state.total} ≥ CD ${state.dc})" else "¡FALLADO! (${state.total} < CD ${state.dc})"
                val bannerColor = if (state.isPassed) currentTheme.bannerTextSuccess else currentTheme.bannerTextFail
                renderBanner(canvas, centerX, height * 0.74f, bannerMsg, bannerColor)
            }
        }

        // 1. Dynamic Drop Shadow underneath 3D die
        val shadowY = centerY + radius * 0.74f
        val hopNorm = (abs(hopY) / 24f).coerceIn(0f, 1f)
        val shadowRadiusX = radius * (1.08f - 0.22f * hopNorm) * scaleX
        val shadowRadiusY = 12f * (1.0f - 0.35f * hopNorm)
        val shadowAlpha = (0.52f * (1.0f - 0.42f * hopNorm) * 255).toInt().coerceIn(0, 255)

        val shadowBounds = RectF(centerX - shadowRadiusX, shadowY - shadowRadiusY, centerX + shadowRadiusX, shadowY + shadowRadiusY)
        paints.bannerBgPaint.color = Color.argb(shadowAlpha, 0, 0, 0)
        canvas.drawOval(shadowBounds, paints.bannerBgPaint)

        // 2. Arcane Impact Dual Shockwave Ripple
        if (rollController.shockwaveProgress < 1f) {
            val swProgress = rollController.shockwaveProgress
            val swRadius1 = 20f + (radius * 2.2f - 20f) * swProgress.pow(0.60f)
            val swRadius2 = 14f + (radius * 1.6f - 14f) * swProgress.pow(0.75f)
            val swAlpha = ((1f - swProgress).pow(1.8f) * 255).toInt().coerceIn(0, 255)

            paints.arcGlowPaint.strokeWidth = 3.6f * (1f - swProgress)
            val shockwaveColor = when (rollController.currentState) {
                is RollState.CriticalSuccess -> currentTheme.goldLight
                is RollState.CriticalFailure -> currentTheme.hpRuby
                else -> currentTheme.goldPrimary
            }
            paints.arcGlowPaint.color = (shockwaveColor and 0x00FFFFFF) or (swAlpha shl 24)
            canvas.drawCircle(centerX, centerY, swRadius1, paints.arcGlowPaint)

            paints.arcGlowPaint.strokeWidth = 1.8f * (1f - swProgress)
            paints.arcGlowPaint.color = (currentTheme.goldLight and 0x00FFFFFF) or ((swAlpha * 0.65f).toInt() shl 24)
            canvas.drawCircle(centerX, centerY, swRadius2, paints.arcGlowPaint)
        }

        // 3. Render 3D Icosahedron (D20)
        d20Geometry.drawActiveD20(
            canvas = canvas,
            centerX = drawX,
            centerY = drawY + hopY,
            radius = radius,
            rotX = rotX,
            rotY = rotY,
            rotZ = rotZ,
            scaleX = scaleX,
            scaleY = scaleY,
            displayNum = displayNum,
            fillPaint = paints.d20FillPaint,
            strokePaint = paints.d20StrokePaint,
            innerStrokePaint = paints.d20InnerStrokePaint,
            filigreePaint = paints.d20FiligreePaint,
            numberPaint = paints.d20NumberPaint,
            primaryColor = currentTheme.goldPrimary,
            lightColor = currentTheme.goldLight,
            failColor = currentTheme.hpRuby
        )
    }

    private fun renderBanner(canvas: Canvas, x: Float, y: Float, text: String, accentColor: Int) {
        paints.bannerTextPaint.textSize = 13.5f
        paints.bannerTextPaint.getTextBounds(text, 0, text.length, textBounds)
        val bannerW = textBounds.width() + 28f
        val bannerH = textBounds.height() + 12f

        val rect = RectF(x - bannerW / 2f, y - bannerH / 2f, x + bannerW / 2f, y + bannerH / 2f)
        paints.bannerBgPaint.color = Color.parseColor("#E60A0A0E")
        canvas.drawRoundRect(rect, 6f, 6f, paints.bannerBgPaint)

        paints.d20InnerStrokePaint.color = accentColor
        canvas.drawRoundRect(rect, 6f, 6f, paints.d20InnerStrokePaint)

        paints.bannerTextPaint.color = accentColor
        canvas.drawText(text, x, y + textBounds.height() * 0.35f, paints.bannerTextPaint)
    }

    private fun renderBottomStatus(
        canvas: Canvas,
        zdt: ZonedDateTime,
        cx: Float,
        height: Float,
        width: Float,
        breathing: Float
    ) {
        val isSettledOrCritical = rollController.currentState is RollState.Settled ||
                rollController.currentState is RollState.CriticalSuccess ||
                rollController.currentState is RollState.CriticalFailure

        // Only draw date when outcome banner is not active to prevent any visual collision
        if (!isSettledOrCritical) {
            val dateY = height * 0.76f
            val dateStr = if (calendarDisplayMode == CalendarDisplayMode.GREGORIAN) {
                zdt.format(dateFormatter).uppercase(Locale.getDefault())
            } else {
                val monthIdx = zdt.monthValue - 1
                "${zdt.dayOfMonth} ${BG3Theme.FAERUN_MONTHS[monthIdx].uppercase()}"
            }

            paints.subtextPaint.textSize = width * 0.034f
            paints.subtextPaint.color = currentTheme.goldLight
            canvas.drawText(dateStr, cx, dateY, paints.subtextPaint)
        }

        // Weather & Heart Rate dual status
        val weather = weatherManager.weatherInfo
        val weatherText = "${weather.condition.glyph} ${weather.displayTemp} ${weather.condition.shortName}"
        val bottomCombined = "$weatherText  •  ♥ 72 BPM"

        paints.heartRatePaint.textSize = width * 0.030f
        paints.heartRatePaint.color = currentTheme.goldLight
        canvas.drawText(bottomCombined, cx, statusY, paints.heartRatePaint)
    }

    override fun renderHighlightLayer(canvas: Canvas, bounds: Rect, zonedDateTime: ZonedDateTime) {}

    override fun onTapEvent(tapType: Int, tapEvent: TapEvent, complicationSlot: ComplicationSlot?) {
        if (tapType == TapType.UP) {
            val bounds = surfaceHolder.surfaceFrame
            val cx = bounds.exactCenterX()
            val cy = bounds.exactCenterY()
            val width = bounds.width().toFloat()
            val height = bounds.height().toFloat()
            val d20Radius = width * 0.138f

            val tx = tapEvent.xPos.toFloat()
            val ty = tapEvent.yPos.toFloat()

            when {
                // 1. D20 Tap -> Roll Dice
                rollController.isD20Tapped(tx, ty, cx, cy, d20Radius) -> {
                    rollController.triggerRoll(System.currentTimeMillis(), cx, cy)
                    invalidate()
                }

                // 2. Skill check / DC Badge Tap -> Cycle Ability
                ty in (height * 0.27f)..(height * 0.33f) && tx in (cx - width * 0.35f)..(cx + width * 0.35f) -> {
                    rollController.cycleAbility()
                    invalidate()
                }

                // 3. Top Clock Tap -> Toggle 24h / 12h & Theme
                ty < height * 0.28f -> {
                    timeDisplayMode = if (timeDisplayMode == TimeDisplayMode.FORMAT_24H) TimeDisplayMode.FORMAT_12H else TimeDisplayMode.FORMAT_24H
                    currentTheme = currentTheme.next()
                    radialBackgroundShader = null
                    invalidate()
                }

                // 4. Left Arc Tap -> Toggle Battery Mode
                tx < cx - width * 0.25f && ty in (height * 0.3f)..(height * 0.7f) -> {
                    batteryDisplayMode = if (batteryDisplayMode == BatteryDisplayMode.PERCENTAGE) BatteryDisplayMode.REMAINING_HOURS else BatteryDisplayMode.PERCENTAGE
                    invalidate()
                }

                // 5. Right Arc Tap -> Toggle Steps / Distance / Calories
                tx > cx + width * 0.25f && ty in (height * 0.3f)..(height * 0.7f) -> {
                    stepsDisplayMode = when (stepsDisplayMode) {
                        StepsDisplayMode.STEPS_XP -> StepsDisplayMode.DISTANCE_KM
                        StepsDisplayMode.DISTANCE_KM -> StepsDisplayMode.CALORIES_KCAL
                        StepsDisplayMode.CALORIES_KCAL -> StepsDisplayMode.STEPS_XP
                    }
                    invalidate()
                }

                // 6. Bottom Tap -> Left side toggles Weather unit/condition, Right side toggles Calendar
                ty > height * 0.72f -> {
                    if (tx < cx) {
                        weatherManager.toggleUnit()
                    } else {
                        calendarDisplayMode = if (calendarDisplayMode == CalendarDisplayMode.GREGORIAN) CalendarDisplayMode.FAERUN_LORE else CalendarDisplayMode.GREGORIAN
                    }
                    invalidate()
                }
            }
        }
    }
}
