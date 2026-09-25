package com.bg3.watchface.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PointF
import kotlin.math.cos
import kotlin.math.sin

/**
 * Mathematical projection and rendering of an ornate 20-sided icosahedron (D20) in 2D perspective.
 * Includes directional lighting, metallic face shading, intricate filigree engravings, and specular highlights.
 */
class D20Geometry {

    private val outerHexagonPath = Path()
    private val innerTrianglePath = Path()
    private val facetPath = Path()
    private val filigreePath = Path()

    private val outerPoints = Array(6) { PointF() }
    private val innerPoints = Array(3) { PointF() }

    // Facet definitions: list of point indices to create triangular facets
    // Points 0..5 are outer vertices, 6..8 are inner triangle vertices
    private val facetIndices = arrayOf(
        intArrayOf(6, 7, 8),      // Central main face (showing rolled number)
        intArrayOf(0, 1, 6),      // Top facet
        intArrayOf(1, 2, 7),      // Upper-right facet
        intArrayOf(2, 3, 7),      // Lower-right facet
        intArrayOf(3, 4, 8),      // Bottom facet
        intArrayOf(4, 5, 8),      // Lower-left facet
        intArrayOf(5, 0, 6),      // Upper-left facet
        intArrayOf(1, 6, 7),      // Intermediate face right
        intArrayOf(3, 7, 8),      // Intermediate face bottom
        intArrayOf(5, 8, 6)       // Intermediate face left
    )

    // Directional shading factors for simulated 3D ambient + directional light (top-left light source)
    private val facetLighting = floatArrayOf(
        1.10f, // Center (bright highlight)
        1.25f, // Top (high light)
        0.85f, // Upper-right (medium)
        0.65f, // Lower-right (shadow)
        0.60f, // Bottom (shadow)
        0.75f, // Lower-left (medium shadow)
        1.15f, // Upper-left (facing light)
        0.95f,
        0.70f,
        1.05f
    )

    fun computeVertices(centerX: Float, centerY: Float, radius: Float, rotationDegrees: Float) {
        val radOffset = Math.toRadians(rotationDegrees.toDouble())

        // 6 outer hexagon vertices
        for (i in 0 until 6) {
            val angle = radOffset + (i * Math.PI / 3.0) - (Math.PI / 6.0)
            outerPoints[i].set(
                (centerX + radius * cos(angle)).toFloat(),
                (centerY + radius * sin(angle)).toFloat()
            )
        }

        // 3 inner equilateral triangle vertices (radius ~ 0.52 of outer)
        val innerRadius = radius * 0.52f
        for (i in 0 until 3) {
            val angle = radOffset + (i * 2.0 * Math.PI / 3.0) - (Math.PI / 2.0)
            innerPoints[i].set(
                (centerX + innerRadius * cos(angle)).toFloat(),
                (centerY + innerRadius * sin(angle)).toFloat()
            )
        }
    }

    /**
     * Draws the full shaded 3D-styled metallic D20 with ornate filigree for Active Mode.
     */
    fun drawActiveD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        rotationDegrees: Float,
        fillPaint: Paint,
        strokePaint: Paint,
        innerStrokePaint: Paint,
        filigreePaint: Paint,
        primaryColor: Int,
        lightColor: Int
    ) {
        computeVertices(centerX, centerY, radius, rotationDegrees)

        val prR = Color.red(primaryColor)
        val prG = Color.green(primaryColor)
        val prB = Color.blue(primaryColor)

        // 1. Draw shaded metallic facets
        for (i in facetIndices.indices) {
            val indices = facetIndices[i]
            val lightMultiplier = facetLighting[i]

            val r = (prR * lightMultiplier * 0.88f).toInt().coerceIn(15, 255)
            val g = (prG * lightMultiplier * 0.88f).toInt().coerceIn(15, 255)
            val b = (prB * lightMultiplier * 0.88f).toInt().coerceIn(15, 255)

            fillPaint.color = Color.rgb(r, g, b)

            facetPath.reset()
            val p0 = getVertex(indices[0])
            val p1 = getVertex(indices[1])
            val p2 = getVertex(indices[2])

            facetPath.moveTo(p0.x, p0.y)
            facetPath.lineTo(p1.x, p1.y)
            facetPath.lineTo(p2.x, p2.y)
            facetPath.close()

            canvas.drawPath(facetPath, fillPaint)
        }

        // 2. Draw subtle ornate filigree curves inside the peripheral facets
        filigreePaint.color = lightColor
        filigreePaint.alpha = 75
        for (i in 1..6) {
            val indices = facetIndices[i]
            val p0 = getVertex(indices[0])
            val p1 = getVertex(indices[1])
            val p2 = getVertex(indices[2])
            val mx = (p0.x + p1.x) / 2f
            val my = (p0.y + p1.y) / 2f

            filigreePath.reset()
            filigreePath.moveTo(mx, my)
            filigreePath.quadTo((mx + p2.x) / 2f, (my + p2.y) / 2f, p2.x, p2.y)
            canvas.drawPath(filigreePath, filigreePaint)
        }

        // 3. Draw facet wireframe borders (metallic gold ridges)
        innerStrokePaint.color = lightColor
        for (indices in facetIndices) {
            val p0 = getVertex(indices[0])
            val p1 = getVertex(indices[1])
            val p2 = getVertex(indices[2])

            canvas.drawLine(p0.x, p0.y, p1.x, p1.y, innerStrokePaint)
            canvas.drawLine(p1.x, p1.y, p2.x, p2.y, innerStrokePaint)
            canvas.drawLine(p2.x, p2.y, p0.x, p0.y, innerStrokePaint)
        }

        // 4. Draw outer rim with double bevel
        outerHexagonPath.reset()
        outerHexagonPath.moveTo(outerPoints[0].x, outerPoints[0].y)
        for (i in 1 until 6) {
            outerHexagonPath.lineTo(outerPoints[i].x, outerPoints[i].y)
        }
        outerHexagonPath.close()

        strokePaint.color = primaryColor
        strokePaint.strokeWidth = 2.4f
        canvas.drawPath(outerHexagonPath, strokePaint)

        // Corner metallic studs / rivets
        innerStrokePaint.color = lightColor
        innerStrokePaint.style = Paint.Style.FILL
        for (p in outerPoints) {
            canvas.drawCircle(p.x, p.y, 2.6f, innerStrokePaint)
        }
        innerStrokePaint.style = Paint.Style.STROKE
    }

    /**
     * Ultra-low-power wireframe D20 for Ambient (AOD) Mode (<10% OPR).
     */
    fun drawAmbientD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        aodStrokePaint: Paint
    ) {
        computeVertices(centerX, centerY, radius, 0f)

        outerHexagonPath.reset()
        outerHexagonPath.moveTo(outerPoints[0].x, outerPoints[0].y)
        for (i in 1 until 6) {
            outerHexagonPath.lineTo(outerPoints[i].x, outerPoints[i].y)
        }
        outerHexagonPath.close()
        canvas.drawPath(outerHexagonPath, aodStrokePaint)

        innerTrianglePath.reset()
        innerTrianglePath.moveTo(innerPoints[0].x, innerPoints[0].y)
        innerTrianglePath.lineTo(innerPoints[1].x, innerPoints[1].y)
        innerTrianglePath.lineTo(innerPoints[2].x, innerPoints[2].y)
        innerTrianglePath.close()
        canvas.drawPath(innerTrianglePath, aodStrokePaint)

        canvas.drawLine(outerPoints[0].x, outerPoints[0].y, innerPoints[0].x, innerPoints[0].y, aodStrokePaint)
        canvas.drawLine(outerPoints[2].x, outerPoints[2].y, innerPoints[1].x, innerPoints[1].y, aodStrokePaint)
        canvas.drawLine(outerPoints[4].x, outerPoints[4].y, innerPoints[2].x, innerPoints[2].y, aodStrokePaint)
    }

    private fun getVertex(index: Int): PointF {
        return if (index < 6) outerPoints[index] else innerPoints[index - 6]
    }
}
