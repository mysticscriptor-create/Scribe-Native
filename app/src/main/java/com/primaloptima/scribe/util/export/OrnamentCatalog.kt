package com.primaloptima.scribe.util.export

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF


/**
 * Single Source of Truth (SSoT) for all manuscript ornament divider styles across Scribe.
 * Maps ornament identifiers to:
 * 1. Plain text / Unicode divider strings (used in TXT and DOCX)
 * 2. Resolution-independent SVG vector markup (used in HTML and EPUB)
 * 3. Human-readable display labels
 */
object OrnamentCatalog {

    data class OrnamentEntry(
        val id: String,
        val displayName: String,
        val textDivider: String,
        val svgMarkup: String
    )

    val entries: List<OrnamentEntry> = listOf(
        OrnamentEntry(
            id = "classic_diamond",
            displayName = "Classic Diamond",
            textDivider = "─── ❖ ───",
            svgMarkup = """
                <svg width="160" height="20" viewBox="0 0 160 20" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="10" x2="68" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M80 3.5 L87 10 L80 16.5 L73 10 Z" fill="currentColor"/>
                  <circle cx="70" cy="10" r="1.3" fill="currentColor"/>
                  <circle cx="90" cy="10" r="1.3" fill="currentColor"/>
                  <line x1="92" y1="10" x2="140" y2="10" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "fleuron",
            displayName = "Fleuron",
            textDivider = "─── ❦ ───",
            svgMarkup = """
                <svg width="140" height="24" viewBox="0 0 140 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="55" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 4 C65 8, 62 14, 70 20 C78 14, 75 8, 70 4 Z" fill="currentColor"/>
                  <path d="M70 12 C64 12, 58 8, 62 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <path d="M70 12 C76 12, 82 8, 78 5" stroke="currentColor" stroke-width="1.2" stroke-linecap="round"/>
                  <line x1="85" y1="12" x2="125" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "asterism",
            displayName = "Asterism",
            textDivider = "─── ⁂ ───",
            svgMarkup = """
                <svg width="120" height="22" viewBox="0 0 120 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="45" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <text x="60" y="8" font-size="12" text-anchor="middle" fill="currentColor" font-family="serif">⁂</text>
                  <line x1="75" y1="11" x2="105" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "celtic_knot",
            displayName = "Celtic Knot",
            textDivider = "─── ⌘ ───",
            svgMarkup = """
                <svg width="150" height="22" viewBox="0 0 150 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="52" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <rect x="68" y="4" width="14" height="14" rx="2" transform="rotate(45 75 11)" stroke="currentColor" stroke-width="1.4" fill="none"/>
                  <circle cx="75" cy="11" r="4" stroke="currentColor" stroke-width="1" fill="none"/>
                  <line x1="98" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "minimal_rule",
            displayName = "Minimal Rule",
            textDivider = "────── • ──────",
            svgMarkup = """
                <svg width="160" height="12" viewBox="0 0 160 12" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="20" y1="6" x2="74" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                  <circle cx="80" cy="6" r="2" fill="currentColor"/>
                  <line x1="86" y1="6" x2="140" y2="6" stroke="currentColor" stroke-width="0.8" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "quatrefoil",
            displayName = "Quatrefoil",
            textDivider = "─── ✤ ───",
            svgMarkup = """
                <svg width="150" height="24" viewBox="0 0 150 24" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="12" x2="58" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <circle cx="75" cy="8" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="16" r="3.5" fill="currentColor"/>
                  <circle cx="71" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="79" cy="12" r="3.5" fill="currentColor"/>
                  <circle cx="75" cy="12" r="1.5" fill="white"/>
                  <line x1="92" y1="12" x2="135" y2="12" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "laurel_branch",
            displayName = "Laurel Branch",
            textDivider = "─── ⸙ ───",
            svgMarkup = """
                <svg width="160" height="22" viewBox="0 0 160 22" fill="none" xmlns="http://www.w3.org/2000/svg" aria-hidden="true">
                  <line x1="15" y1="11" x2="55" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                  <path d="M70 11 C72 7, 78 7, 80 11 C78 15, 72 15, 70 11 Z" fill="currentColor"/>
                  <circle cx="65" cy="11" r="2" fill="currentColor"/>
                  <circle cx="85" cy="11" r="2" fill="currentColor"/>
                  <line x1="95" y1="11" x2="135" y2="11" stroke="currentColor" stroke-width="1" stroke-linecap="round"/>
                </svg>
            """.trimIndent()
        ),
        OrnamentEntry(
            id = "none",
            displayName = "None",
            textDivider = "",
            svgMarkup = ""
        )
    )

    private val mapById = entries.associateBy { it.id }

    fun getById(id: String): OrnamentEntry =
        mapById[id] ?: mapById["classic_diamond"]!!

    fun fromId(id: String): OrnamentEntry? =
        mapById[id]

    fun getTextDivider(id: String): String =
        getById(id).textDivider

    fun getSvgMarkup(id: String): String =
        getById(id).svgMarkup

    /**
     * Unified Single Source of Truth (SSoT) 2D vector renderer across Canvas engines (PDF & Compose).
     * Draws resolution-independent vector paths centered at (cx, cy).
     */
    fun drawOrnament(
        canvas: Canvas,
        ornamentId: String,
        cx: Float,
        cy: Float,
        color: Int,
        scale: Float = 1.0f
    ) {
        if (ornamentId == "none") return

        val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = 1.1f * scale
            strokeCap = Paint.Cap.ROUND
        }
        val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.FILL
        }

        when (ornamentId) {
            "classic_diamond" -> {
                val s = scale
                canvas.drawLine(cx - 65f * s, cy, cx - 18f * s, cy, strokePaint)
                canvas.drawLine(cx + 18f * s, cy, cx + 65f * s, cy, strokePaint)
                val diamondPath = Path().apply {
                    moveTo(cx, cy - 7f * s)
                    lineTo(cx + 7.5f * s, cy)
                    lineTo(cx, cy + 7f * s)
                    lineTo(cx - 7.5f * s, cy)
                    close()
                }
                canvas.drawPath(diamondPath, fillPaint)
                canvas.drawCircle(cx - 12f * s, cy, 1.8f * s, fillPaint)
                canvas.drawCircle(cx + 12f * s, cy, 1.8f * s, fillPaint)
            }
            "fleuron" -> {
                val s = scale
                canvas.drawLine(cx - 60f * s, cy, cx - 18f * s, cy, strokePaint)
                canvas.drawLine(cx + 18f * s, cy, cx + 60f * s, cy, strokePaint)
                val fleuronPath = Path().apply {
                    moveTo(cx, cy - 6f * s)
                    cubicTo(cx - 6f * s, cy - 2f * s, cx - 10f * s, cy + 4f * s, cx, cy + 8f * s)
                    cubicTo(cx + 10f * s, cy + 4f * s, cx + 6f * s, cy - 2f * s, cx, cy - 6f * s)
                    close()
                }
                canvas.drawPath(fleuronPath, fillPaint)
            }
            "asterism" -> {
                val s = scale
                canvas.drawLine(cx - 55f * s, cy, cx - 18f * s, cy, strokePaint)
                canvas.drawLine(cx + 18f * s, cy, cx + 55f * s, cy, strokePaint)
                val starPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = color
                    style = Paint.Style.STROKE
                    strokeWidth = 1.0f * s
                    strokeCap = Paint.Cap.ROUND
                }
                fun drawStarlet(scx: Float, scy: Float, r: Float) {
                    for (i in 0 until 3) {
                        val angle = Math.toRadians((i * 60.0))
                        val dx = (Math.cos(angle) * r).toFloat()
                        val dy = (Math.sin(angle) * r).toFloat()
                        canvas.drawLine(scx - dx, scy - dy, scx + dx, scy + dy, starPaint)
                    }
                }
                drawStarlet(cx, cy - 4f * s, 2.8f * s)
                drawStarlet(cx - 5.5f * s, cy + 4f * s, 2.8f * s)
                drawStarlet(cx + 5.5f * s, cy + 4f * s, 2.8f * s)
            }
            "celtic_knot" -> {
                val s = scale
                canvas.drawLine(cx - 65f * s, cy, cx - 22f * s, cy, strokePaint)
                canvas.drawLine(cx + 22f * s, cy, cx + 65f * s, cy, strokePaint)
                canvas.save()
                canvas.rotate(45f, cx, cy)
                val rect = RectF(cx - 7f * s, cy - 7f * s, cx + 7f * s, cy + 7f * s)
                canvas.drawRoundRect(rect, 2f * s, 2f * s, strokePaint)
                canvas.restore()
                canvas.drawCircle(cx, cy, 4f * s, strokePaint)
            }
            "minimal_rule" -> {
                val s = scale
                strokePaint.strokeWidth = 0.9f * s
                canvas.drawLine(cx - 70f * s, cy, cx - 8f * s, cy, strokePaint)
                canvas.drawLine(cx + 8f * s, cy, cx + 70f * s, cy, strokePaint)
                canvas.drawCircle(cx, cy, 2.5f * s, fillPaint)
            }
            "quatrefoil" -> {
                val s = scale
                canvas.drawLine(cx - 65f * s, cy, cx - 22f * s, cy, strokePaint)
                canvas.drawLine(cx + 22f * s, cy, cx + 65f * s, cy, strokePaint)
                val r = 3.5f * s
                canvas.drawCircle(cx, cy - 4f * s, r, fillPaint)
                canvas.drawCircle(cx, cy + 4f * s, r, fillPaint)
                canvas.drawCircle(cx - 4f * s, cy, r, fillPaint)
                canvas.drawCircle(cx + 4f * s, cy, r, fillPaint)
                val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    this.color = Color.WHITE
                    style = Paint.Style.FILL
                }
                canvas.drawCircle(cx, cy, 1.5f * s, whitePaint)
            }
            "laurel_branch" -> {
                val s = scale
                canvas.drawLine(cx - 65f * s, cy, cx - 22f * s, cy, strokePaint)
                canvas.drawLine(cx + 22f * s, cy, cx + 65f * s, cy, strokePaint)
                val leafPath = Path().apply {
                    moveTo(cx, cy - 7f * s)
                    cubicTo(cx - 7f * s, cy - 3f * s, cx - 7f * s, cy + 3f * s, cx, cy + 7f * s)
                    cubicTo(cx + 7f * s, cy + 3f * s, cx + 7f * s, cy - 3f * s, cx, cy - 7f * s)
                    close()
                }
                canvas.drawPath(leafPath, fillPaint)
                canvas.drawCircle(cx - 15f * s, cy, 2.0f * s, fillPaint)
                canvas.drawCircle(cx + 15f * s, cy, 2.0f * s, fillPaint)
            }
            else -> {
                val s = scale
                canvas.drawLine(cx - 65f * s, cy, cx - 18f * s, cy, strokePaint)
                canvas.drawLine(cx + 18f * s, cy, cx + 65f * s, cy, strokePaint)
                val diamondPath = Path().apply {
                    moveTo(cx, cy - 7f * s)
                    lineTo(cx + 7.5f * s, cy)
                    lineTo(cx, cy + 7f * s)
                    lineTo(cx - 7.5f * s, cy)
                    close()
                }
                canvas.drawPath(diamondPath, fillPaint)
            }
        }
    }
}
