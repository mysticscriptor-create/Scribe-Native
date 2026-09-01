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
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.core.content.res.ResourcesCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.primaloptima.scribe.R
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors
import com.primaloptima.scribe.util.model.ThemeColorOverrides
import com.primaloptima.scribe.util.model.ThemeSourcePalette
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
            AppJson.decodeFromString<List<AppTheme>>(json).map { migrateTheme(it) }
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
        val migrated = migrateTheme(theme)
        val list = allCustomThemes().toMutableList()
        val idx = list.indexOfFirst { it.id == migrated.id }
        if (idx >= 0) list[idx] = migrated else list.add(migrated)
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
            AppJson.decodeFromString<List<AppTheme>>(json).map { migrateTheme(it) }
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
        /** Current schema version for theme serialization & migration */
        const val CURRENT_SCHEMA_VERSION = 1

        /**
         * Defensive centralized migration strategy.
         * Upgrades older theme instances to the latest schema version.
         * If the theme is already current or unrecognised, returns it safely without throwing.
         */
        fun migrateTheme(theme: AppTheme): AppTheme {
            return try {
                if (theme.schemaVersion >= CURRENT_SCHEMA_VERSION) {
                    theme
                } else {
                    // Migration path from legacy (schemaVersion 0 or missing):
                    // Derive full semantic tokens and set schemaVersion = CURRENT_SCHEMA_VERSION
                    val resolvedColors = resolveThemeColors(
                        bgHex = theme.colors.background,
                        textHex = theme.colors.text,
                        accentHex = theme.colors.accent,
                        isDark = theme.isDark,
                        overrides = theme.overrides
                    )
                    theme.copy(
                        schemaVersion = CURRENT_SCHEMA_VERSION,
                        colors = resolvedColors
                    )
                }
            } catch (_: Exception) {
                // Defensive fallback: return original theme unchanged
                theme
            }
        }

        fun parseColor(hex: String): Int = try {
            if (hex.startsWith("#")) {
                val clean = hex.removePrefix("#")
                when (clean.length) {
                    6 -> (0xFF000000.toInt()) or clean.toLong(16).toInt()
                    8 -> clean.toLong(16).toInt()
                    3 -> {
                        val r = clean[0].toString().repeat(2).toInt(16)
                        val g = clean[1].toString().repeat(2).toInt(16)
                        val b = clean[2].toString().repeat(2).toInt(16)
                        (0xFF000000.toInt()) or (r shl 16) or (g shl 8) or b
                    }
                    else -> Color.parseColor(hex)
                }
            } else {
                Color.parseColor(hex)
            }
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
            val r = sRgbToLinear(((colorInt shr 16) and 0xFF) / 255.0)
            val g = sRgbToLinear(((colorInt shr 8) and 0xFF) / 255.0)
            val b = sRgbToLinear((colorInt and 0xFF) / 255.0)

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

            return (0xFF shl 24) or (r shl 16) or (g shl 8) or b
        }

        fun oklchToHex(oklch: Oklch): String {
            val c = oklchToColor(oklch)
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            return String.format("#%02X%02X%02X", r, g, b)
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
         * Phase 1 Architecture:
         * Generates the deterministic default semantic tokens from Foundation Sources (background, text, accent).
         * Does not apply any user overrides.
         */
        fun generateThemeDefaults(sources: ThemeSourcePalette, isDark: Boolean): ThemeColors {
            return generateThemeDefaults(sources.background, sources.text, sources.accent, isDark)
        }

        fun generateThemeDefaults(
            bgHex: String,
            textHex: String,
            accentHex: String,
            isDark: Boolean
        ): ThemeColors {
            val bgInt = parseColor(bgHex)
            var textInt = parseColor(textHex)
            val accentInt = parseColor(accentHex)

            val bgOklch = colorToOklch(bgInt)
            var textOklch = colorToOklch(textInt)
            val accentOklch = colorToOklch(accentInt)

            // Polarity Auto-Adjustment: Ensure text color has strong perceptual contrast relative to background
            // ΔL between background and text must be sufficient for high APCA readability
            val effectiveTextHex: String
            if (isDark) {
                // Dark background: text must be perceptually light (L >= 0.75, ideal ~0.92)
                if (textOklch.l < 0.60) {
                    textOklch = Oklch(0.92, (textOklch.c * 0.4).coerceAtMost(0.04), textOklch.h)
                    effectiveTextHex = oklchToHex(textOklch)
                    textInt = parseColor(effectiveTextHex)
                } else {
                    effectiveTextHex = textHex
                }
            } else {
                // Light or medium-bright background: text must be perceptually dark (L <= 0.30, ideal ~0.16)
                if (textOklch.l > 0.40) {
                    textOklch = Oklch(0.16, (textOklch.c * 0.4).coerceAtMost(0.04), textOklch.h)
                    effectiveTextHex = oklchToHex(textOklch)
                    textInt = parseColor(effectiveTextHex)
                } else {
                    effectiveTextHex = textHex
                }
            }

            fun blend(c1: Int, c2: Int, ratio: Float): String {
                val r1 = (c1 shr 16) and 0xFF
                val g1 = (c1 shr 8) and 0xFF
                val b1 = c1 and 0xFF

                val r2 = (c2 shr 16) and 0xFF
                val g2 = (c2 shr 8) and 0xFF
                val b2 = c2 and 0xFF

                val r = (r1 * (1f - ratio) + r2 * ratio).toInt().coerceIn(0, 255)
                val g = (g1 * (1f - ratio) + g2 * ratio).toInt().coerceIn(0, 255)
                val b = (b1 * (1f - ratio) + b2 * ratio).toInt().coerceIn(0, 255)
                return String.format("#%02X%02X%02X", r, g, b)
            }

            return if (isDark) {
                // Dark Mode Elevation Ramp (preserving subtle hue and saturation with progressive lightness lift)
                val surfaceLowest = oklchToHex(Oklch((bgOklch.l + 0.025).coerceIn(0.01, 0.95), bgOklch.c * 0.95, bgOklch.h))
                val surface = oklchToHex(Oklch((bgOklch.l + 0.055).coerceIn(0.01, 0.95), bgOklch.c * 0.90, bgOklch.h))
                val surfaceRaised = oklchToHex(Oklch((bgOklch.l + 0.095).coerceIn(0.01, 0.95), bgOklch.c * 0.85, bgOklch.h))
                val surfaceOverlay = oklchToHex(Oklch((bgOklch.l + 0.145).coerceIn(0.01, 0.95), bgOklch.c * 0.80, bgOklch.h))

                // Content & Typography Hierarchy (calculated relative to text luminance)
                val mutedText = oklchToHex(Oklch((textOklch.l - 0.28).coerceIn(0.35, 0.85), (textOklch.c * 0.70).coerceAtLeast(0.0), textOklch.h))
                val subtleText = oklchToHex(Oklch((textOklch.l - 0.45).coerceIn(0.25, 0.70), (textOklch.c * 0.50).coerceAtLeast(0.0), textOklch.h))

                // Interactive & Secondary Harmonics (derived in OKLCH space from accent)
                val secondaryDefault = oklchToHex(Oklch((accentOklch.l - 0.04).coerceIn(0.30, 0.85), (accentOklch.c * 0.85).coerceAtLeast(0.0), (accentOklch.h + 20.0) % 360.0))
                val tertiaryDefault = oklchToHex(Oklch((accentOklch.l + 0.06).coerceIn(0.40, 0.90), (accentOklch.c * 0.75).coerceAtLeast(0.0), (accentOklch.h - 30.0 + 360.0) % 360.0))

                // Perceptually tuned Semantic Feedback Roles (APCA readable on dark surfaces)
                val successDefault = createOklchColor(0.76, 0.15, 142.0)
                val warningDefault = createOklchColor(0.82, 0.16, 85.0)
                val errorDefault = createOklchColor(0.72, 0.18, 25.0)
                val specialHighlightDefault = createOklchColor(0.86, 0.14, 88.0)

                // Containers & Selection
                val accentMuted = blend(accentInt, bgInt, 0.80f)
                val selection = blend(accentInt, bgInt, 0.65f)

                // Boundaries & Focus
                val borderSubtle = oklchToHex(Oklch((bgOklch.l + 0.08).coerceIn(0.01, 0.95), bgOklch.c * 0.75, bgOklch.h))
                val borderProminent = accentHex

                // Lexer & Writing Engine Syntactical Roles (Editorial)
                val dialogueDefault = createOklchColor(0.90, 0.13, 86.0)
                val monologueDefault = createOklchColor(0.80, 0.09, 255.0)
                val headingDefault = accentHex

                // ── Stage 4: Structured Output Assembly ───────────────────────────────
                ThemeColors(
                    background = bgHex,
                    surfaceLowest = surfaceLowest,
                    surface = surface,
                    surfaceRaised = surfaceRaised,
                    surfaceOverlay = surfaceOverlay,
                    text = effectiveTextHex,
                    mutedText = mutedText,
                    subtleText = subtleText,
                    accent = accentHex,
                    secondary = secondaryDefault,
                    tertiary = tertiaryDefault,
                    success = successDefault,
                    warning = warningDefault,
                    error = errorDefault,
                    specialHighlight = specialHighlightDefault,
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = dialogueDefault,
                    monologueText = monologueDefault,
                    headingText = headingDefault,
                    toolbar = surface,
                    toolbarText = effectiveTextHex
                )
            } else {
                // Light & Tinted Mode Elevation Ramp
                val surfaceLowest = oklchToHex(Oklch((bgOklch.l - 0.035).coerceIn(0.05, 0.98), bgOklch.c * 1.05, bgOklch.h))
                val surface = oklchToHex(Oklch((bgOklch.l + 0.045).coerceIn(0.05, 0.99), bgOklch.c * 0.88, bgOklch.h))
                val surfaceRaised = oklchToHex(Oklch((bgOklch.l + 0.085).coerceIn(0.05, 1.0), bgOklch.c * 0.75, bgOklch.h))
                val surfaceOverlay = oklchToHex(Oklch((bgOklch.l + 0.130).coerceIn(0.05, 1.0), bgOklch.c * 0.65, bgOklch.h))

                // Content & Typography Hierarchy (increasing lightness in OKLCH with reduced chroma)
                val mutedText = oklchToHex(Oklch((textOklch.l + 0.28).coerceIn(0.20, 0.75), (textOklch.c * 0.65).coerceAtLeast(0.0), textOklch.h))
                val subtleText = oklchToHex(Oklch((textOklch.l + 0.44).coerceIn(0.30, 0.85), (textOklch.c * 0.50).coerceAtLeast(0.0), textOklch.h))

                // Interactive & Secondary Harmonics
                val secondaryDefault = oklchToHex(Oklch((accentOklch.l + 0.08).coerceIn(0.20, 0.75), (accentOklch.c * 0.85).coerceAtLeast(0.0), (accentOklch.h + 15.0) % 360.0))
                val tertiaryDefault = oklchToHex(Oklch((accentOklch.l + 0.14).coerceIn(0.25, 0.80), (accentOklch.c * 0.75).coerceAtLeast(0.0), (accentOklch.h - 25.0 + 360.0) % 360.0))

                // Perceptually tuned Semantic Feedback Roles (APCA readable on light surfaces)
                val successDefault = createOklchColor(0.48, 0.16, 142.0)
                val warningDefault = createOklchColor(0.55, 0.16, 80.0)
                val errorDefault = createOklchColor(0.50, 0.20, 25.0)
                val specialHighlightDefault = createOklchColor(0.52, 0.15, 75.0)

                // Containers & Selection
                val accentMuted = blend(accentInt, bgInt, 0.88f)
                val selection = blend(accentInt, bgInt, 0.78f)

                // Boundaries & Focus (derived with adequate contrast against light background)
                val borderSubtle = oklchToHex(Oklch((bgOklch.l - 0.085).coerceIn(0.10, 0.98), bgOklch.c * 0.70, bgOklch.h))
                val borderProminent = accentHex

                // Lexer & Writing Engine Syntactical Roles
                val dialogueDefault = createOklchColor(0.45, 0.15, 65.0)
                val monologueDefault = createOklchColor(0.40, 0.12, 255.0)
                val headingDefault = accentHex

                ThemeColors(
                    background = bgHex,
                    surfaceLowest = surfaceLowest,
                    surface = surface,
                    surfaceRaised = surfaceRaised,
                    surfaceOverlay = surfaceOverlay,
                    text = effectiveTextHex,
                    mutedText = mutedText,
                    subtleText = subtleText,
                    accent = accentHex,
                    secondary = secondaryDefault,
                    tertiary = tertiaryDefault,
                    success = successDefault,
                    warning = warningDefault,
                    error = errorDefault,
                    specialHighlight = specialHighlightDefault,
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = dialogueDefault,
                    monologueText = monologueDefault,
                    headingText = headingDefault,
                    toolbar = surface,
                    toolbarText = effectiveTextHex
                )
            }
        }

        /**
         * Phase 1 Resolution Pipeline:
         * Resolves the full ThemeColors by layering explicit User Overrides onto Generated Defaults.
         *
         * Resolution precedence:
         * 1. User Override (if non-null and not blank)
         * 2. Generated Default (from OKLCH derivation)
         * 3. Fallback token
         */
        fun resolveThemeColors(
            sources: ThemeSourcePalette,
            overrides: ThemeColorOverrides? = null,
            isDark: Boolean
        ): ThemeColors {
            val defaults = generateThemeDefaults(sources, isDark)
            if (overrides == null || overrides.isEmpty()) {
                return defaults
            }
            return defaults.copy(
                surfaceLowest = overrides.surfaceLowest?.takeIf { it.isNotBlank() } ?: defaults.surfaceLowest,
                surface = overrides.surface?.takeIf { it.isNotBlank() } ?: defaults.surface,
                surfaceRaised = overrides.surfaceRaised?.takeIf { it.isNotBlank() } ?: defaults.surfaceRaised,
                surfaceOverlay = overrides.surfaceOverlay?.takeIf { it.isNotBlank() } ?: defaults.surfaceOverlay,
                mutedText = overrides.mutedText?.takeIf { it.isNotBlank() } ?: defaults.mutedText,
                subtleText = overrides.subtleText?.takeIf { it.isNotBlank() } ?: defaults.subtleText,
                secondary = overrides.secondary?.takeIf { it.isNotBlank() } ?: defaults.secondary,
                tertiary = overrides.tertiary?.takeIf { it.isNotBlank() } ?: defaults.tertiary,
                accentMuted = overrides.accentMuted?.takeIf { it.isNotBlank() } ?: defaults.accentMuted,
                selection = overrides.selection?.takeIf { it.isNotBlank() } ?: defaults.selection,
                borderSubtle = overrides.borderSubtle?.takeIf { it.isNotBlank() } ?: defaults.borderSubtle,
                border = overrides.borderSubtle?.takeIf { it.isNotBlank() } ?: defaults.borderSubtle,
                borderProminent = overrides.borderProminent?.takeIf { it.isNotBlank() } ?: defaults.borderProminent,
                success = overrides.success?.takeIf { it.isNotBlank() } ?: defaults.success,
                warning = overrides.warning?.takeIf { it.isNotBlank() } ?: defaults.warning,
                error = overrides.error?.takeIf { it.isNotBlank() } ?: defaults.error,
                specialHighlight = overrides.specialHighlight?.takeIf { it.isNotBlank() } ?: defaults.specialHighlight,
                dialogueText = overrides.dialogueText?.takeIf { it.isNotBlank() } ?: defaults.dialogueText,
                monologueText = overrides.monologueText?.takeIf { it.isNotBlank() } ?: defaults.monologueText,
                headingText = overrides.headingText?.takeIf { it.isNotBlank() } ?: defaults.headingText,
                toolbar = overrides.surface?.takeIf { it.isNotBlank() } ?: defaults.surface,
                toolbarText = defaults.toolbarText
            )
        }

        fun resolveThemeColors(
            bgHex: String,
            textHex: String,
            accentHex: String,
            isDark: Boolean,
            overrides: ThemeColorOverrides? = null
        ): ThemeColors {
            return resolveThemeColors(
                sources = ThemeSourcePalette(bgHex, textHex, accentHex),
                overrides = overrides,
                isDark = isDark
            )
        }

        /**
         * Resolves an entire AppTheme instance to its authoritative resolved colors.
         */
        fun resolveTheme(theme: AppTheme): AppTheme {
            val resolvedColors = resolveThemeColors(
                sources = theme.sourcePalette(),
                overrides = theme.overrides,
                isDark = theme.isDark
            )
            return theme.copy(colors = resolvedColors)
        }

        /**
         * Backward compatibility wrapper for deriveThemeColors.
         */
        fun deriveThemeColors(
            bgHex: String,
            textHex: String,
            accentHex: String,
            isDark: Boolean,
            base: ThemeColors? = null
        ): ThemeColors {
            return resolveThemeColors(
                bgHex = bgHex,
                textHex = textHex,
                accentHex = accentHex,
                isDark = isDark,
                overrides = null
            )
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

        fun validateSemanticContrast(
            bgHex: String,
            textHex: String,
            accentHex: String,
            dialogueHex: String? = null,
            monologueHex: String? = null,
            headingHex: String? = null
        ): com.primaloptima.scribe.ui.theme.ThemeSemanticContrastReport {
            val isDark = isDarkColor(bgHex)
            val overrides = ThemeColorOverrides(
                dialogueText = dialogueHex,
                monologueText = monologueHex,
                headingText = headingHex
            )
            val derived = resolveThemeColors(bgHex, textHex, accentHex, isDark, overrides)
            val bgCol = ComposeColor(parseColor(derived.background))
            val textCol = ComposeColor(parseColor(derived.text))
            val accentCol = ComposeColor(parseColor(derived.accent))
            val surfaceCol = ComposeColor(parseColor(derived.surface))
            val surfaceRaisedCol = ComposeColor(parseColor(derived.surfaceRaised))
            val surfaceOverlayCol = ComposeColor(parseColor(derived.surfaceOverlay))
            val mutedCol = ComposeColor(parseColor(derived.mutedText))
            val subtleCol = ComposeColor(parseColor(derived.subtleText))
            val borderCol = ComposeColor(parseColor(derived.borderSubtle))
            val focusBorderCol = ComposeColor(parseColor(derived.borderProminent))

            val dialogueCol = ComposeColor(parseColor(derived.dialogueText))
            val monologueCol = ComposeColor(parseColor(derived.monologueText))
            val headingCol = ComposeColor(parseColor(derived.headingText))

            val scribeColors = com.primaloptima.scribe.ui.theme.ScribeColors(
                surfaces = com.primaloptima.scribe.ui.theme.SurfaceColors(
                    background = bgCol,
                    surfaceLowest = ComposeColor(parseColor(derived.surfaceLowest)),
                    surface = surfaceCol,
                    surfaceRaised = surfaceRaisedCol,
                    surfaceOverlay = surfaceOverlayCol,
                    surfaceSelected = accentCol.copy(alpha = 0.15f),
                    surfacePressed = accentCol.copy(alpha = 0.25f)
                ),
                content = com.primaloptima.scribe.ui.theme.ContentColors(
                    primary = textCol,
                    secondary = mutedCol,
                    tertiary = subtleCol,
                    disabled = mutedCol.copy(alpha = 0.38f),
                    onAccent = if (isDarkColor(derived.accent)) ComposeColor.White else ComposeColor.Black
                ),
                interaction = com.primaloptima.scribe.ui.theme.InteractionColors(
                    primary = accentCol,
                    primaryContainer = accentCol.copy(alpha = 0.15f),
                    onPrimary = if (isDarkColor(derived.accent)) ComposeColor.White else ComposeColor.Black,
                    onPrimaryContainer = accentCol,
                    secondary = subtleCol,
                    tertiary = mutedCol,
                    selection = accentCol.copy(alpha = 0.25f),
                    focus = focusBorderCol,
                    link = accentCol
                ),
                semantic = com.primaloptima.scribe.ui.theme.SemanticStatusColors(
                    success = ComposeColor(0xFF10B981),
                    onSuccess = ComposeColor.White,
                    successContainer = ComposeColor(0xFF10B981).copy(alpha = 0.15f),
                    onSuccessContainer = ComposeColor(0xFF10B981),
                    warning = ComposeColor(0xFFF59E0B),
                    onWarning = ComposeColor.Black,
                    warningContainer = ComposeColor(0xFFF59E0B).copy(alpha = 0.15f),
                    onWarningContainer = ComposeColor(0xFFF59E0B),
                    error = ComposeColor(0xFFEF4444),
                    onError = ComposeColor.White,
                    errorContainer = ComposeColor(0xFFEF4444).copy(alpha = 0.15f),
                    onErrorContainer = ComposeColor(0xFFEF4444),
                    info = ComposeColor(0xFF3B82F6),
                    onInfo = ComposeColor.White,
                    infoContainer = ComposeColor(0xFF3B82F6).copy(alpha = 0.15f),
                    onInfoContainer = ComposeColor(0xFF3B82F6)
                ),
                writing = com.primaloptima.scribe.ui.theme.WritingColors(
                    prose = textCol,
                    dialogue = dialogueCol,
                    monologue = monologueCol,
                    heading = headingCol,
                    annotation = monologueCol,
                    highlight = dialogueCol
                ),
                analytics = com.primaloptima.scribe.ui.theme.AnalyticsColors(
                    positive = ComposeColor(0xFF10B981),
                    neutral = subtleCol,
                    negative = ComposeColor(0xFFEF4444),
                    series1 = accentCol,
                    series2 = ComposeColor(0xFF10B981),
                    series3 = ComposeColor(0xFFF59E0B),
                    target = accentCol,
                    warning = ComposeColor(0xFFF59E0B)
                ),
                borders = com.primaloptima.scribe.ui.theme.BorderColors(
                    subtle = borderCol,
                    normal = borderCol,
                    prominent = focusBorderCol
                ),
                world = com.primaloptima.scribe.ui.theme.WorldEntityColors(
                    character = accentCol,
                    location = ComposeColor(0xFF10B981),
                    faction = ComposeColor(0xFF3B82F6),
                    item = ComposeColor(0xFFF59E0B),
                    lore = ComposeColor(0xFF8B5CF6),
                    event = ComposeColor(0xFFEC4899),
                    relationship = accentCol
                ),
                isDark = isDark
            )
            return com.primaloptima.scribe.ui.theme.validateThemeSemanticContrast(scribeColors)
        }
    }
}
