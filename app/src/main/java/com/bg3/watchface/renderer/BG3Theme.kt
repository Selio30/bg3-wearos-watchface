package com.bg3.watchface.renderer

import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface

/**
 * Styling, colors, pre-cached paints, and typography constants for Baldur's Gate 3 aesthetic.
 */
object BG3Theme {

    // Palettes
    val COLOR_BACKGROUND_DARK = Color.parseColor("#0A0A0E")
    val COLOR_BACKGROUND_VOID = Color.parseColor("#040407")
    
    // Arcane Gold Accents
    val COLOR_GOLD_PRIMARY = Color.parseColor("#D4AF37")
    val COLOR_GOLD_LIGHT = Color.parseColor("#F5D77F")
    val COLOR_GOLD_DARK = Color.parseColor("#8C6F2D")
    val COLOR_GOLD_SHADOW = Color.parseColor("#4A3B18")
    val COLOR_GOLD_GLOW = Color.parseColor("#66D4AF37")
    
    // Health / HP Ruby Crimson Red
    val COLOR_HP_RUBY = Color.parseColor("#E63946")
    val COLOR_HP_CRIMSON = Color.parseColor("#8B0000")
    val COLOR_HP_DARK = Color.parseColor("#380505")
    val COLOR_HP_GLOW = Color.parseColor("#66E63946")
    
    // Experience / XP Arcane Sapphire & Illithid Violet
    val COLOR_XP_CYAN = Color.parseColor("#4CC9F0")
    val COLOR_XP_PURPLE = Color.parseColor("#7209B7")
    val COLOR_XP_DARK = Color.parseColor("#1A0738")
    val COLOR_XP_GLOW = Color.parseColor("#667209B7")
    
    // Ambient Mode (Strict AOD low-bit AMOLED compliance)
    val COLOR_AOD_BLACK = Color.parseColor("#000000")
    val COLOR_AOD_GOLD_DIM = Color.parseColor("#554522")
    val COLOR_AOD_TEXT = Color.parseColor("#A8A8A8")
    val COLOR_AOD_SUBTEXT = Color.parseColor("#666666")

    // Runic Alphabet symbols for the outer summoning circle
    val RUNIC_SYMBOLS = arrayOf(
        "ᚠ", "ᚢ", "ᚦ", "ᚨ", "ᚱ", "ᚲ", "ᚷ", "ᚹ", 
        "ᚺ", "ᚾ", "ᛁ", "ᛃ", "ᛇ", "ᛈ", "ᛉ", "ᛋ", 
        "ᛏ", "ᛒ", "ᛖ", "ᛗ", "ᛚ", "ᛜ", "ᛞ", "ᛟ"
    )

    // Typefaces
    val TYPEFACE_SERIF_BOLD: Typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
    val TYPEFACE_SERIF_NORMAL: Typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)

    /**
     * Creates reusable configured Paints to avoid allocations in onDraw / render.
     */
    class PaintCache {
        // Digital Clock
        val timePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GOLD_PRIMARY
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }
        val timeShadowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Subtext / Date / Complications
        val subtextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GOLD_LIGHT
            typeface = TYPEFACE_SERIF_NORMAL
            textAlign = Paint.Align.CENTER
        }
        val heartRatePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_HP_RUBY
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Arc Gauges (HP & XP)
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

        // Runes & Decorative Rings
        val runePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = COLOR_GOLD_DARK
            textAlign = Paint.Align.CENTER
        }
        val ringStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_GOLD_DARK
            strokeWidth = 1.5f
        }

        // D20 Die Drawing
        val d20FillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val d20StrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_GOLD_PRIMARY
            strokeWidth = 2.2f
        }
        val d20InnerStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            color = COLOR_GOLD_LIGHT
            strokeWidth = 1.0f
        }
        val d20NumberPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }
        val d20NumberGlowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Banners (Critical 20 / Critical 1)
        val bannerBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
        }
        val bannerTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = TYPEFACE_SERIF_BOLD
            textAlign = Paint.Align.CENTER
        }

        // Ambient (AOD) Paints
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
