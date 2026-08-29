package com.primaloptima.scribe.util

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.primaloptima.scribe.R
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlin.math.roundToInt

/**
 * Phase 2-C: ThemeManager migrated away from direct prefs access.
 *
 * ThemeManager is called synchronously from non-coroutine contexts
 * (ThemeViewModel.reload(), applyThemeToActivity()). Rather than blocking
 * on DataStore, we keep an in-memory cache that is seeded once at startup
 * by ScribeApp.seedThemeManagerCache() and updated by ViewModels after
 * every write.
 *
 * Phase 7 complete: all prefs fallbacks removed. Cache is the sole source of truth.
 */
class ThemeManager(private val context: Context) {

    // ── In-memory cache seeded by ScribeApp.seedThemeManagerCache() ──────────
    // Volatile so reads from any thread see the latest write.
    @Volatile private var cachedCustomThemesJson: String? = null
    @Volatile private var cachedActiveThemeId: String? = null

    /**
     * Called once from ScribeApp after DataStore has emitted its first values.
     * After this, allThemes() and activeTheme() read from the cache instead of prefs.
     */
    fun onDataStoreReady(customJson: String, activeId: String) {
        cachedCustomThemesJson = customJson
        cachedActiveThemeId = activeId
    }

    /**
     * Called by ThemeViewModel after every save/delete/setActive so the cache
     * stays in sync without a round-trip through DataStore.
     */
    fun updateCache(customJson: String, activeId: String) {
        cachedCustomThemesJson = customJson
        cachedActiveThemeId = activeId
    }

    // ── Theme accessors ───────────────────────────────────────────────────────

    /** All themes = built-ins + custom themes. Reads from in-memory cache. */
    fun allThemes(): List<AppTheme> {
        val json = cachedCustomThemesJson ?: "[]"
        val custom = try {
            AppJson.decodeFromString<List<AppTheme>>(json)
        } catch (_: Exception) { emptyList() }
        val builtInIds = DefaultThemes.all.map { it.id }.toSet()
        val customMap = custom.associateBy { it.id }
        val updatedBuiltIns = DefaultThemes.all.map { builtIn -> customMap[builtIn.id] ?: builtIn }
        val newCustoms = custom.filter { it.id !in builtInIds }
        return (updatedBuiltIns + newCustoms).distinctBy { it.id }
    }

    fun activeTheme(): AppTheme {
        val id = cachedActiveThemeId ?: "paper"
        return allThemes().firstOrNull { it.id == id } ?: DefaultThemes.all.first()
    }

    /** Write to prefs AND update the cache (called from ThemeViewModel coroutine scope). */
    fun setActiveTheme(id: String) {
        cachedActiveThemeId = id
    }

    fun saveCustomTheme(theme: AppTheme) {
        val list = allCustomThemes().toMutableList()
        val idx = list.indexOfFirst { it.id == theme.id }
        if (idx >= 0) list[idx] = theme else list.add(theme)
        val json = AppJson.encodeToString(list)
        cachedCustomThemesJson = json
    }

    fun deleteCustomTheme(id: String) {
        val list = allCustomThemes().filter { it.id != id }
        val json = AppJson.encodeToString(list)
        cachedCustomThemesJson = json
        if (cachedActiveThemeId == id) {
            cachedActiveThemeId = "paper"
        }
    }

    fun duplicateTheme(id: String): AppTheme? {
        val source = allThemes().firstOrNull { it.id == id } ?: return null
        val copy = source.copy(
            id = System.currentTimeMillis().toString() + Math.random().toString().takeLast(6),
            name = "${source.name} Copy",
            builtIn = false
        )
        saveCustomTheme(copy)
        return copy
    }

    fun allCustomThemes(): List<AppTheme> {
        val json = cachedCustomThemesJson ?: "[]"
        return try {
            AppJson.decodeFromString<List<AppTheme>>(json)
        } catch (_: Exception) { emptyList() }
    }

    // ── Activity theming (unchanged) ──────────────────────────────────────────

    fun applyThemeToActivity(activity: AppCompatActivity, rootLayout: View? = null, bgImageView: ImageView? = null): AppTheme {
        val theme = activeTheme()
        val window = activity.window
        val bgColor = parseColor(theme.colors.background)
        val toolbarColor = parseColor(theme.colors.toolbar)
        val accentColor = parseColor(theme.colors.accent)
        val textColor = parseColor(theme.colors.text)
        val surfaceColor = parseColor(theme.colors.surface)

        rootLayout?.setBackgroundColor(bgColor)

        window.statusBarColor = toolbarColor
        window.navigationBarColor = surfaceColor

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        val isDarkTheme = theme.isDark || isColorDark(bgColor)
        controller.isAppearanceLightStatusBars = !isDarkTheme
        controller.isAppearanceLightNavigationBars = !isDarkTheme

        if (bgImageView != null) {
            val imageUriStr = theme.backgroundImageUri
            if (!imageUriStr.isNullOrEmpty()) {
                try {
                    bgImageView.visibility = View.VISIBLE
                    bgImageView.setImageURI(Uri.parse(imageUriStr))
                    bgImageView.alpha = theme.backgroundImageOpacity ?: 0.35f
                    bgImageView.scaleType = ImageView.ScaleType.CENTER_CROP
                } catch (_: Exception) {
                    bgImageView.visibility = View.GONE
                }
            } else {
                bgImageView.visibility = View.GONE
            }
        }

        return theme
    }

    companion object {

        fun parseColor(hex: String): Int = try {
            Color.parseColor(hex)
        } catch (_: Exception) { Color.BLACK }

        // ── OKLCH Perceptual Color Space Engine ──
        data class Oklch(val l: Double, val c: Double, val h: Double)

        private fun sRgbToLinear(c: Double): Double {
            return if (c >= 0.04045) {
                Math.pow((c + 0.055) / 1.055, 2.4)
            } else {
                c / 12.92
            }
        }

        private fun linearToSRgb(c: Double): Double {
            val clamped = c.coerceIn(0.0, 1.0)
            return if (clamped <= 0.0031308) {
                12.92 * clamped
            } else {
                1.055 * Math.pow(clamped, 1.0 / 2.4) - 0.055
            }
        }

        fun colorToOklch(colorInt: Int): Oklch {
            val r = sRgbToLinear(Color.red(colorInt) / 255.0)
            val g = sRgbToLinear(Color.green(colorInt) / 255.0)
            val b = sRgbToLinear(Color.blue(colorInt) / 255.0)

            val l = Math.cbrt(0.4122214708 * r + 0.5363325363 * g + 0.0514459929 * b)
            val m = Math.cbrt(0.2119034982 * r + 0.6806995451 * g + 0.1073969566 * b)
            val s = Math.cbrt(0.0883024619 * r + 0.2817188376 * g + 0.6299787005 * b)

            val L = 0.2104542553 * l + 0.7936177850 * m - 0.0040720468 * s
            val a = 1.9779984951 * l - 2.4285922050 * m + 0.4505937099 * s
            val bVal = 0.0259040371 * l + 0.7827717662 * m - 0.8086757660 * s

            val C = Math.sqrt(a * a + bVal * bVal)
            var h = Math.toDegrees(Math.atan2(bVal, a))
            if (h < 0.0) h += 360.0

            return Oklch(L.coerceIn(0.0, 1.0), C.coerceAtLeast(0.0), h)
        }

        fun oklchToColor(oklch: Oklch): Int {
            val hRad = Math.toRadians(oklch.h)
            val a = oklch.c * Math.cos(hRad)
            val bVal = oklch.c * Math.sin(hRad)

            val l_ = oklch.l + 0.3963377774 * a + 0.2158037573 * bVal
            val m_ = oklch.l - 0.1055613458 * a - 0.0638541728 * bVal
            val s_ = oklch.l - 0.0894841775 * a - 1.2914855480 * bVal

            val l = l_ * l_ * l_
            val m = m_ * m_ * m_
            val s = s_ * s_ * s_

            val rLin = +4.0767439362 * l - 3.3077115913 * m + 0.2309699292 * s
            val gLin = -1.2684380046 * l + 2.6097574011 * m - 0.3413193965 * s
            val bLin = -0.0041960863 * l - 0.7034186147 * m + 1.7076147010 * s

            val r = (linearToSRgb(rLin) * 255.0).roundToInt().coerceIn(0, 255)
            val g = (linearToSRgb(gLin) * 255.0).roundToInt().coerceIn(0, 255)
            val b = (linearToSRgb(bLin) * 255.0).roundToInt().coerceIn(0, 255)

            return Color.rgb(r, g, b)
        }

        fun oklchToHex(oklch: Oklch): String {
            val c = oklchToColor(oklch)
            return String.format("#%02X%02X%02X", Color.red(c), Color.green(c), Color.blue(c))
        }

        fun shiftOklch(colorInt: Int, deltaL: Double, chromaFactor: Double = 1.0): String {
            val oklch = colorToOklch(colorInt)
            val newL = (oklch.l + deltaL).coerceIn(0.01, 0.99)
            val newC = (oklch.c * chromaFactor).coerceAtLeast(0.0)
            return oklchToHex(Oklch(newL, newC, oklch.h))
        }

        fun createOklchColor(l: Double, c: Double, h: Double): String {
            return oklchToHex(Oklch(l.coerceIn(0.01, 0.99), c.coerceAtLeast(0.0), h % 360.0))
        }

        fun isColorDark(color: Int): Boolean {
            return colorToOklch(color).l < 0.45
        }

        fun isDarkColor(hex: String): Boolean {
            return isColorDark(parseColor(hex))
        }

        /**
         * Automatically calculates a 5-tier elevation ramp, semantic typography tokens,
         * and boundary tokens from base background, text, and accent colors using OKLCH.
         */
        fun deriveThemeColors(
            bgHex: String,
            textHex: String,
            accentHex: String,
            isDark: Boolean,
            base: ThemeColors? = null
        ): ThemeColors {
            val bgInt = parseColor(bgHex)
            val textInt = parseColor(textHex)
            val accentInt = parseColor(accentHex)

            fun blend(c1: Int, c2: Int, ratio: Float): String {
                val r = (Color.red(c1) * (1f - ratio) + Color.red(c2) * ratio).toInt().coerceIn(0, 255)
                val g = (Color.green(c1) * (1f - ratio) + Color.green(c2) * ratio).toInt().coerceIn(0, 255)
                val b = (Color.blue(c1) * (1f - ratio) + Color.blue(c2) * ratio).toInt().coerceIn(0, 255)
                return String.format("#%02X%02X%02X", r, g, b)
            }

            return if (isDark) {
                val surfaceLowest = shiftOklch(bgInt, +0.022, 0.95)
                val surface = shiftOklch(bgInt, +0.052, 0.92)
                val surfaceRaised = shiftOklch(bgInt, +0.095, 0.88)
                val surfaceOverlay = shiftOklch(bgInt, +0.155, 0.85)

                val mutedText = shiftOklch(textInt, -0.28, 0.70)
                val subtleText = shiftOklch(textInt, -0.46, 0.50)

                val secondaryDefault = shiftOklch(accentInt, -0.05, 0.85)
                val tertiaryDefault = shiftOklch(accentInt, +0.06, 0.75)
                val successDefault = createOklchColor(0.75, 0.14, 142.0)
                val warningDefault = createOklchColor(0.82, 0.15, 85.0)
                val errorDefault = createOklchColor(0.72, 0.18, 25.0)
                val specialHighlightDefault = createOklchColor(0.85, 0.14, 88.0)

                val accentMuted = blend(accentInt, bgInt, 0.80f)
                val selection = blend(accentInt, bgInt, 0.68f)

                val borderSubtle = shiftOklch(bgInt, +0.075, 0.75)
                val borderProminent = accentHex

                val dialogueDefault = createOklchColor(0.91, 0.12, 88.0)
                val monologueDefault = createOklchColor(0.80, 0.08, 255.0)

                ThemeColors(
                    background = bgHex,
                    surfaceLowest = base?.surfaceLowest?.takeIf { it != base.background } ?: surfaceLowest,
                    surface = surface,
                    surfaceRaised = base?.surfaceRaised?.takeIf { it != base.surface } ?: surfaceRaised,
                    surfaceOverlay = base?.surfaceOverlay?.takeIf { it != base.surface } ?: surfaceOverlay,
                    text = textHex,
                    mutedText = mutedText,
                    subtleText = subtleText,
                    accent = accentHex,
                    secondary = base?.secondary?.takeIf { it.isNotEmpty() && it != base.accent } ?: secondaryDefault,
                    tertiary = base?.tertiary?.takeIf { it.isNotEmpty() && it != base.accent } ?: tertiaryDefault,
                    success = base?.success?.takeIf { it.isNotEmpty() } ?: successDefault,
                    warning = base?.warning?.takeIf { it.isNotEmpty() } ?: warningDefault,
                    error = base?.error?.takeIf { it.isNotEmpty() } ?: errorDefault,
                    specialHighlight = base?.specialHighlight?.takeIf { it.isNotEmpty() } ?: specialHighlightDefault,
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = base?.dialogueText?.takeIf { it != base.accent } ?: dialogueDefault,
                    monologueText = base?.monologueText?.takeIf { it != base.text } ?: monologueDefault,
                    headingText = accentHex,
                    toolbar = surface,
                    toolbarText = textHex
                )
            } else {
                val surfaceLowest = shiftOklch(bgInt, -0.035, 1.05)
                val surface = "#FFFFFF"
                val surfaceRaised = "#FFFFFF"
                val surfaceOverlay = "#FFFFFF"

                val mutedText = shiftOklch(textInt, +0.32, 0.60)
                val subtleText = shiftOklch(textInt, +0.48, 0.50)

                val secondaryDefault = shiftOklch(accentInt, +0.08, 0.85)
                val tertiaryDefault = shiftOklch(accentInt, +0.14, 0.75)
                val successDefault = createOklchColor(0.48, 0.16, 142.0)
                val warningDefault = createOklchColor(0.55, 0.16, 80.0)
                val errorDefault = createOklchColor(0.50, 0.20, 25.0)
                val specialHighlightDefault = createOklchColor(0.52, 0.15, 75.0)

                val accentMuted = blend(accentInt, bgInt, 0.88f)
                val selection = blend(accentInt, bgInt, 0.78f)

                val borderSubtle = shiftOklch(bgInt, -0.09, 0.60)
                val borderProminent = accentHex

                val dialogueDefault = createOklchColor(0.42, 0.14, 75.0)
                val monologueDefault = createOklchColor(0.35, 0.12, 260.0)

                ThemeColors(
                    background = bgHex,
                    surfaceLowest = base?.surfaceLowest?.takeIf { it != base.background } ?: surfaceLowest,
                    surface = surface,
                    surfaceRaised = base?.surfaceRaised?.takeIf { it != base.surface } ?: surfaceRaised,
                    surfaceOverlay = base?.surfaceOverlay?.takeIf { it != base.surface } ?: surfaceOverlay,
                    text = textHex,
                    mutedText = mutedText,
                    subtleText = subtleText,
                    accent = accentHex,
                    secondary = base?.secondary?.takeIf { it.isNotEmpty() && it != base.accent } ?: secondaryDefault,
                    tertiary = base?.tertiary?.takeIf { it.isNotEmpty() && it != base.accent } ?: tertiaryDefault,
                    success = base?.success?.takeIf { it.isNotEmpty() } ?: successDefault,
                    warning = base?.warning?.takeIf { it.isNotEmpty() } ?: warningDefault,
                    error = base?.error?.takeIf { it.isNotEmpty() } ?: errorDefault,
                    specialHighlight = base?.specialHighlight?.takeIf { it.isNotEmpty() } ?: specialHighlightDefault,
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = base?.dialogueText?.takeIf { it != base.accent } ?: dialogueDefault,
                    monologueText = base?.monologueText?.takeIf { it != base.text } ?: monologueDefault,
                    headingText = accentHex,
                    toolbar = surface,
                    toolbarText = textHex
                )
            }
        }

        fun resolveTypeface(context: Context, fontFamilyKey: String): Typeface {
            val fontResId = when (fontFamilyKey) {
                "serif", "serif-medium", "serif-bold" -> R.font.playfair_display
                "sans", "sans-medium", "sans-semibold", "sans-bold" -> R.font.inter
                "mono", "mono-medium" -> R.font.jetbrains_mono
                else -> 0
            }
            if (fontResId != 0) {
                try {
                    val tf = ResourcesCompat.getFont(context, fontResId)
                    if (tf != null) {
                        return when (fontFamilyKey) {
                            "serif-bold", "sans-bold" ->
                                Typeface.create(tf, Typeface.BOLD)
                            "serif-medium", "sans-medium", "sans-semibold", "mono-medium" ->
                                if (Build.VERSION.SDK_INT >= 28)
                                    Typeface.create(tf, 500, false)
                                else Typeface.create(tf, Typeface.NORMAL)
                            else -> tf
                        }
                    }
                } catch (_: Exception) {}
            }
            return when {
                fontFamilyKey.startsWith("serif") -> Typeface.SERIF
                fontFamilyKey.startsWith("mono")  -> Typeface.MONOSPACE
                else -> Typeface.SANS_SERIF
            }
        }

        fun lineSpacingMultiplier(key: String): Float = when (key) {
            "compact"  -> 1.4f
            "spacious" -> 2.0f
            else       -> 1.7f  // comfortable
        }
    }
}
