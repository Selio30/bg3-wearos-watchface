package com.bg3.watchface.renderer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Rect
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * Authentic 3D Regular Icosahedron (D20) projection and rendering for Wear OS.
 * Features:
 * - 12 normalized vertices and 20 triangular faces
 * - Canonical resting orientation (Face [0, 5, 1] dead-center, apex pointing up, horizontal base)
 * - 3D Euler rotation and perspective camera projection
 * - Backface culling via outward face normal cross-product
 * - Diffuse & Blinn-Phong specular directional lighting (upper-left arcana)
 * - Metallic beveled ridges, outer silhouette rim, and corner vertex rivets
 * - Canonical D20 numerals engraved onto facets in 3D perspective
 * - Minimalist celestial wireframe for Ambient (AOD) Mode (< 3% OPR)
 */
class D20Geometry {

    companion object {
        const val CAM_DIST = 4.2f

        // 12 Normalized 3D Vertices of a regular icosahedron in canonical resting orientation
        val ICO_BASE_VERTS = arrayOf(
            floatArrayOf(0.52573111f, -0.30353100f, 0.79465447f),   // V0
            floatArrayOf(-0.52573111f, -0.30353100f, 0.79465447f),  // V1
            floatArrayOf(0.52573111f, 0.30353100f, -0.79465447f),   // V2
            floatArrayOf(-0.52573111f, 0.30353100f, -0.79465447f),  // V3
            floatArrayOf(0.00000000f, 0.98224695f, -0.18759247f),   // V4
            floatArrayOf(0.00000000f, 0.60706200f, 0.79465447f),    // V5 (Apex pointing up)
            floatArrayOf(0.00000000f, -0.60706200f, -0.79465447f),  // V6
            floatArrayOf(0.00000000f, -0.98224695f, 0.18759247f),   // V7
            floatArrayOf(-0.85065081f, -0.49112347f, -0.18759247f), // V8
            floatArrayOf(-0.85065081f, 0.49112347f, 0.18759247f),   // V9
            floatArrayOf(0.85065081f, -0.49112347f, -0.18759247f),  // V10
            floatArrayOf(0.85065081f, 0.49112347f, 0.18759247f)     // V11
        )

        // 20 Triangular Faces (counter-clockwise winding from outside)
        val ICO_FACES = arrayOf(
            intArrayOf(0, 11, 5),
            intArrayOf(0, 5, 1),   // Face 1: Front-most central face (at rest)
            intArrayOf(0, 1, 7),
            intArrayOf(0, 7, 10),
            intArrayOf(0, 10, 11),
            intArrayOf(1, 5, 9),
            intArrayOf(5, 11, 4),
            intArrayOf(11, 10, 2),
            intArrayOf(10, 7, 6),
            intArrayOf(7, 1, 8),
            intArrayOf(3, 9, 4),
            intArrayOf(3, 4, 2),
            intArrayOf(3, 2, 6),   // Face 12: Opposite to Face 1 (Normal pointing directly away)
            intArrayOf(3, 6, 8),
            intArrayOf(3, 8, 9),
            intArrayOf(4, 9, 5),
            intArrayOf(2, 4, 11),
            intArrayOf(6, 2, 10),
            intArrayOf(8, 6, 7),
            intArrayOf(9, 8, 1)
        )

        // Canonical D20 Face Numbers (Opposite faces sum to 21)
        val D20_FACE_NUMBERS = intArrayOf(
            14, // F0
            20, // F1: Center face (dynamically displays the roll outcome)
            2,  // F2
            12, // F3
            10, // F4
            8,  // F5
            16, // F6
            15, // F7
            4,  // F8
            18, // F9
            17, // F10
            7,  // F11
            1,  // F12 (Opposite to 20)
            19, // F13
            9,  // F14
            13, // F15
            6,  // F16
            11, // F17
            3,  // F18
            5   // F19
        )
    }

    private class Point3D(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)
    private class Point2D(var x: Float = 0f, var y: Float = 0f, var z: Float = 0f)

    private class FaceData(
        var faceIndex: Int = 0,
        var cz: Float = 0f,
        var nz: Float = 0f,
        var intensity: Float = 1f,
        val p2d: Array<Point2D> = Array(3) { Point2D() },
        val indices: IntArray = IntArray(3)
    )

    private val rotVerts = Array(ICO_BASE_VERTS.size) { Point3D() }
    private val projVerts = Array(ICO_BASE_VERTS.size) { Point2D() }
    private val facePool = Array(ICO_FACES.size) { FaceData() }
    private val visibleFaces = ArrayList<FaceData>(ICO_FACES.size)

    private val facetPath = Path()
    private val filigreePath = Path()
    private val rimPath = Path()
    private val edgeMap = HashMap<Long, Int>(40)
    private val textBounds = Rect()

    // Blinn-Phong directional light vectors (normalized)
    private val lx = -0.354f
    private val ly = 0.607f
    private val lz = 0.708f
    private val hx = -0.187f
    private val hy = 0.320f
    private val hz = 0.928f

    private fun rotate3D(base: FloatArray, rx: Float, ry: Float, rz: Float, out: Point3D) {
        var x = base[0]
        var y = base[1]
        var z = base[2]

        // Rotate X
        if (rx != 0f) {
            val c = cos(rx)
            val s = sin(rx)
            val y1 = y * c - z * s
            val z1 = y * s + z * c
            y = y1
            z = z1
        }
        // Rotate Y
        if (ry != 0f) {
            val c = cos(ry)
            val s = sin(ry)
            val x1 = x * c + z * s
            val z1 = -x * s + z * c
            x = x1
            z = z1
        }
        // Rotate Z
        if (rz != 0f) {
            val c = cos(rz)
            val s = sin(rz)
            val x1 = x * c - y * s
            val y1 = x * s + y * c
            x = x1
            y = y1
        }
        out.x = x
        out.y = y
        out.z = z
    }

    /**
     * Draws the true 3D shaded metallic D20 with directional Blinn-Phong lighting,
     * beveled metallic ridges, perimeter silhouette, vertex studs, and engraved numerals.
     */
    fun drawActiveD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        rotX: Float,
        rotY: Float,
        rotZ: Float,
        scaleX: Float,
        scaleY: Float,
        displayNum: Int,
        fillPaint: Paint,
        strokePaint: Paint,
        innerStrokePaint: Paint,
        filigreePaint: Paint,
        numberPaint: Paint,
        primaryColor: Int,
        lightColor: Int,
        failColor: Int
    ) {
        // 1. Transform all 12 vertices
        for (i in ICO_BASE_VERTS.indices) {
            rotate3D(ICO_BASE_VERTS[i], rotX, rotY, rotZ, rotVerts[i])
            val rv = rotVerts[i]
            val k = CAM_DIST / (CAM_DIST - rv.z)
            projVerts[i].x = centerX + rv.x * k * radius * scaleX
            projVerts[i].y = centerY - rv.y * k * radius * scaleY // Y inverted for screen coordinates
            projVerts[i].z = rv.z
        }

        // 2. Compute face normals, lighting, and cull backfaces
        visibleFaces.clear()
        edgeMap.clear()

        val prR = Color.red(primaryColor)
        val prG = Color.green(primaryColor)
        val prB = Color.blue(primaryColor)

        for (fIdx in ICO_FACES.indices) {
            val f = ICO_FACES[fIdx]
            val p0 = rotVerts[f[0]]
            val p1 = rotVerts[f[1]]
            val p2 = rotVerts[f[2]]

            // Cross product in 3D: (p1 - p0) x (p2 - p0)
            val u1 = p1.x - p0.x
            val u2 = p1.y - p0.y
            val u3 = p1.z - p0.z
            val v1 = p2.x - p0.x
            val v2 = p2.y - p0.y
            val v3 = p2.z - p0.z

            var nx = u2 * v3 - u3 * v2
            var ny = u3 * v1 - u1 * v3
            var nz = u1 * v2 - u2 * v1
            val nLen = hypot(hypot(nx, ny), nz)
            if (nLen < 0.0001f) continue
            nx /= nLen
            ny /= nLen
            nz /= nLen

            // Backface culling: only faces oriented towards the camera
            if (nz > 0.01f) {
                val cz = (p0.z + p1.z + p2.z) / 3f
                val diff = max(0f, nx * lx + ny * ly + nz * lz)
                val spec = max(0f, nx * hx + ny * hy + nz * hz).pow(14f)
                var intensity = 0.42f + 0.50f * diff + 0.38f * spec
                if (fIdx == 1) intensity += 0.08f // Radiant arcane boost on canonical target face

                val fd = facePool[fIdx]
                fd.faceIndex = fIdx
                fd.cz = cz
                fd.nz = nz
                fd.intensity = intensity
                fd.indices[0] = f[0]
                fd.indices[1] = f[1]
                fd.indices[2] = f[2]

                for (j in 0 until 3) {
                    val pv = projVerts[f[j]]
                    fd.p2d[j].x = pv.x
                    fd.p2d[j].y = pv.y
                    fd.p2d[j].z = pv.z

                    // Edge counting for outer silhouette boundary
                    val a = min(f[j], f[(j + 1) % 3])
                    val b = max(f[j], f[(j + 1) % 3])
                    val edgeKey = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
                    edgeMap[edgeKey] = (edgeMap[edgeKey] ?: 0) + 1
                }
                visibleFaces.add(fd)
            }
        }

        // 3. Sort visible faces from back to front (Painter's algorithm)
        visibleFaces.sortBy { it.cz }

        // 4. Render shaded facets
        for (vf in visibleFaces) {
            val r = (prR * vf.intensity).toInt().coerceIn(15, 255)
            val g = (prG * vf.intensity).toInt().coerceIn(15, 255)
            val b = (prB * vf.intensity).toInt().coerceIn(15, 255)
            fillPaint.color = Color.rgb(r, g, b)

            val p0 = vf.p2d[0]
            val p1 = vf.p2d[1]
            val p2 = vf.p2d[2]

            facetPath.reset()
            facetPath.moveTo(p0.x, p0.y)
            facetPath.lineTo(p1.x, p1.y)
            facetPath.lineTo(p2.x, p2.y)
            facetPath.close()
            canvas.drawPath(facetPath, fillPaint)

            // Inner facet metallic ridge borders
            innerStrokePaint.color = lightColor
            innerStrokePaint.alpha = (min(1f, 0.35f + vf.intensity * 0.45f) * 255).toInt()
            innerStrokePaint.strokeWidth = 1.1f
            canvas.drawPath(facetPath, innerStrokePaint)

            // Subtle filigree highlight on primary facing facets
            if (vf.nz > 0.55f) {
                filigreePaint.color = lightColor
                filigreePaint.alpha = (0.22f * vf.nz * 255).toInt()
                val mx = (p0.x + p1.x) / 2f
                val my = (p0.y + p1.y) / 2f
                filigreePath.reset()
                filigreePath.moveTo(mx, my)
                filigreePath.quadTo((mx + p2.x) / 2f, (my + p2.y) / 2f, p2.x, p2.y)
                canvas.drawPath(filigreePath, filigreePaint)
            }
        }

        // 5. Draw outer boundary silhouette rim
        strokePaint.color = primaryColor
        strokePaint.strokeWidth = 2.4f
        rimPath.reset()
        for ((edgeKey, count) in edgeMap) {
            if (count == 1) {
                val vA = (edgeKey ushr 32).toInt()
                val vB = (edgeKey and 0xFFFFFFFFL).toInt()
                rimPath.moveTo(projVerts[vA].x, projVerts[vA].y)
                rimPath.lineTo(projVerts[vB].x, projVerts[vB].y)
            }
        }
        canvas.drawPath(rimPath, strokePaint)

        // 6. Corner metallic rivets / studs on front-facing vertices
        innerStrokePaint.color = lightColor
        innerStrokePaint.style = Paint.Style.FILL
        for (pv in projVerts) {
            if (pv.z > -0.25f) {
                canvas.drawCircle(pv.x, pv.y, 2.2f, innerStrokePaint)
            }
        }
        innerStrokePaint.style = Paint.Style.STROKE

        // 7. Numerals on facets in 3D perspective
        // A. Subtle neighboring numbers on angled facets
        for (vf in visibleFaces) {
            if (vf.faceIndex != 1 && vf.nz > 0.28f) {
                val cxF = (vf.p2d[0].x + vf.p2d[1].x + vf.p2d[2].x) / 3f
                val cyF = (vf.p2d[0].y + vf.p2d[1].y + vf.p2d[2].y) / 3f
                val numStr = D20_FACE_NUMBERS[vf.faceIndex].toString()
                val numSize = max(8f, 13f * vf.nz * scaleY)

                numberPaint.textSize = numSize
                numberPaint.color = lightColor
                numberPaint.alpha = ((vf.nz - 0.2f) * 0.70f * 255).toInt().coerceIn(0, 255)
                numberPaint.getTextBounds(numStr, 0, numStr.length, textBounds)
                canvas.drawText(numStr, cxF, cyF + textBounds.height() * 0.38f, numberPaint)
            }
        }

        // B. Main rolled number engraved into Face 1
        val face1 = visibleFaces.find { it.faceIndex == 1 }
        if (face1 != null && face1.nz > 0.15f) {
            val cx1 = (face1.p2d[0].x + face1.p2d[1].x + face1.p2d[2].x) / 3f
            val cy1 = (face1.p2d[0].y + face1.p2d[1].y + face1.p2d[2].y) / 3f
            val numStr = displayNum.toString()
            val fontSize = max(16f, 32f * face1.nz * scaleY)

            numberPaint.textSize = fontSize
            numberPaint.getTextBounds(numStr, 0, numStr.length, textBounds)
            val textY = cy1 + textBounds.height() * 0.38f

            // Engraved dark offset shadow
            numberPaint.color = Color.BLACK
            numberPaint.alpha = 230
            canvas.drawText(numStr, cx1 + 1.8f, textY + 2.0f, numberPaint)

            // Engraved metallic numeral
            numberPaint.color = when (displayNum) {
                20 -> lightColor
                1 -> failColor
                else -> primaryColor
            }
            numberPaint.alpha = 255
            canvas.drawText(numStr, cx1, textY, numberPaint)
        }
    }

    /**
     * Ultra-low-power wireframe D20 for Ambient (AOD) Mode (< 3% OPR).
     * Renders clean icosahedral triangulation in canonical resting orientation.
     */
    fun drawAmbientD20(
        canvas: Canvas,
        centerX: Float,
        centerY: Float,
        radius: Float,
        aodStrokePaint: Paint
    ) {
        // Project canonical base vertices with perspective
        for (i in ICO_BASE_VERTS.indices) {
            val bv = ICO_BASE_VERTS[i]
            val k = CAM_DIST / (CAM_DIST - bv[2])
            projVerts[i].x = centerX + bv[0] * k * radius
            projVerts[i].y = centerY - bv[1] * k * radius
        }

        rimPath.reset()
        val drawnEdges = HashSet<Long>(30)

        for (f in ICO_FACES) {
            val p0 = ICO_BASE_VERTS[f[0]]
            val p1 = ICO_BASE_VERTS[f[1]]
            val p2 = ICO_BASE_VERTS[f[2]]

            val u1 = p1[0] - p0[0]
            val u2 = p1[1] - p0[1]
            val v1 = p2[0] - p0[0]
            val v2 = p2[1] - p0[1]
            val nz = u1 * v2 - u2 * v1

            // Draw front-facing edges only
            if (nz > 0.01f) {
                for (j in 0 until 3) {
                    val a = min(f[j], f[(j + 1) % 3])
                    val b = max(f[j], f[(j + 1) % 3])
                    val edgeKey = (a.toLong() shl 32) or (b.toLong() and 0xFFFFFFFFL)
                    if (drawnEdges.add(edgeKey)) {
                        rimPath.moveTo(projVerts[a].x, projVerts[a].y)
                        rimPath.lineTo(projVerts[b].x, projVerts[b].y)
                    }
                }
            }
        }
        canvas.drawPath(rimPath, aodStrokePaint)
    }
}
