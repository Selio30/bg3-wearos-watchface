package com.bg3.watchface.renderer

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Origin Companion Theme Variants inspired by Baldur's Gate 3.
 */
enum class ThemeVariant(
    val title: String,
    val goldPrimary: Int,
    val goldLight: Int,
    val goldDark: Int,
    val hpRuby: Int,
    val hpDark: Int,
    val xpArcane: Int,
    val xpDark: Int,
    val backgroundCore: Int,
    val bannerTextSuccess: Int,
    val bannerTextFail: Int
) {
    CLASSIC_TAV(
        title = "Tav (Arcano)",
        goldPrimary = Color.parseColor("#D4AF37"),
        goldLight = Color.parseColor("#F5D77F"),
        goldDark = Color.parseColor("#8C6F2D"),
        hpRuby = Color.parseColor("#E63946"),
        hpDark = Color.parseColor("#380505"),
        xpArcane = Color.parseColor("#4CC9F0"),
        xpDark = Color.parseColor("#1A0738"),
        backgroundCore = Color.parseColor("#151224"),
        bannerTextSuccess = Color.parseColor("#D4AF37"),
        bannerTextFail = Color.parseColor("#E63946")
    ),
    ASTARION_VAMPIRE(
        title = "Astarion (Vampiro)",
        goldPrimary = Color.parseColor("#C0C0D0"), // Gothic Moonlit Silver
        goldLight = Color.parseColor("#FFFFFF"),
        goldDark = Color.parseColor("#606075"),
        hpRuby = Color.parseColor("#B30000"), // Blood Crimson
        hpDark = Color.parseColor("#2A0000"),
        xpArcane = Color.parseColor("#9D4EDD"), // Seductive shadow purple
        xpDark = Color.parseColor("#240046"),
        backgroundCore = Color.parseColor("#12080D"),
        bannerTextSuccess = Color.parseColor("#FFFFFF"),
        bannerTextFail = Color.parseColor("#B30000")
    ),
    SHADOWHEART_SHAR(
        title = "Shadowheart (Shar)",
        goldPrimary = Color.parseColor("#9381FF"), // Starlight Twilight
        goldLight = Color.parseColor("#B8B8FF"),
        goldDark = Color.parseColor("#4D4380"),
        hpRuby = Color.parseColor("#FF5470"),
        hpDark = Color.parseColor("#330A12"),
        xpArcane = Color.parseColor("#00F5D4"), // Shar Trickery Cyan
        xpDark = Color.parseColor("#0B2545"),
        backgroundCore = Color.parseColor("#0A091A"),
        bannerTextSuccess = Color.parseColor("#00F5D4"),
        bannerTextFail = Color.parseColor("#FF5470")
    ),
    KARLACH_INFERNAL(
        title = "Karlach (Infernal)",
        goldPrimary = Color.parseColor("#FF8500"), // Smoldering Engine Bronze
        goldLight = Color.parseColor("#FFB703"),
        goldDark = Color.parseColor("#9E2A2B"),
        hpRuby = Color.parseColor("#D90429"), // Burning Magma
        hpDark = Color.parseColor("#3F0008"),
        xpArcane = Color.parseColor("#FB8500"), // Engine Steam
        xpDark = Color.parseColor("#370617"),
        backgroundCore = Color.parseColor("#1C0A00"),
        bannerTextSuccess = Color.parseColor("#FFB703"),
        bannerTextFail = Color.parseColor("#D90429")
    );

    fun next(): ThemeVariant {
        val vals = values()
        return vals[(ordinal + 1) % vals.size]
    }
}

/**
 * Display format modes toggled via touch UX.
 */
enum class BatteryDisplayMode { PERCENTAGE, REMAINING_HOURS }
enum class StepsDisplayMode { STEPS_XP, DISTANCE_KM, CALORIES_KCAL }
enum class TimeDisplayMode { FORMAT_24H, FORMAT_12H }
enum class CalendarDisplayMode { GREGORIAN, FAERUN_LORE }

object BG3Theme {

    val COLOR_BACKGROUND_DARK = Color.parseColor("#0A0A0E")
    val COLOR_BACKGROUND_VOID = Color.parseColor("#040407")
    val COLOR_AOD_BLACK = Color.parseColor("#000000")
    val COLOR_AOD_GOLD_DIM = Color.parseColor("#554522")
    val COLOR_AOD_TEXT = Color.parseColor("#A8A8A8")
    val COLOR_AOD_SUBTEXT = Color.parseColor("#666666")

    // Faerûn / Forgotten Realms Calendar months
    val FAERUN_MONTHS = arrayOf(
        "Millofrío",        // Enero (Hammer)
        "Las Garras",       // Febrero (Alturiak)
        "La Puesta",        // Marzo (Ches)
        "Las Tormentas",    // Abril (Tarsakh)
        "El Deshielo",      // Mayo (Mirtul)
        "Las Flores",       // Junio (Kythorn)
        "Mareasol",         // Julio (Flamerule)
        "El Alto Sol",      // Agosto (Eleasias)
        "El Desvanecer",    // Septiembre (Eleint)
        "La Caída",         // Octubre (Marpenoth)
        "El Abrazo",        // Noviembre (Uktar)
        "El Dibujo"         // Diciembre (Nightal)
    )

    val RUNIC_SYMBOLS = arrayOf(
        "ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ", 
        "ᚺ", "ᚾ", "ᛁ", "ᛃ", "ᛇ", "ᛈ", "ᛉ", "ᛋ", 
        "ᛏ", "ᛒ", "ᛖ", "ᛗ", "ᛚ", "ᛜ", "ᛞ", "ᛟ"
    )

    val TYPEFACE_SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val TYPEFACE_SERIF_NORMAL: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    class PaintCache {
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }
        val timeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_NORMAL
            textAlign = Paint.Align.CENTER
        }
        val heartRatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        val arcTrackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val arcProgressPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }
        val arcGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
        }

        val runePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
        }
        val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.2f
        }

        val d20FillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val d20StrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 2.4f
        }
        val d20InnerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 1.0f
        }
        val d20FiligreePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = 0.8f
        }
        val d20NumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        val bannerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }
        val badgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        val aodTimePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_AOD_TEXT
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }
        val aodD20StrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_AOD_GOLD_DIM
            strokeWidth = 1.5f
        }
        val aodSubtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_AOD_SUBTEXT
            typeface = TYPEFACE_SERIF_NORMAL
            textAlign = Paint.Align.CENTER
        }
    }
}
