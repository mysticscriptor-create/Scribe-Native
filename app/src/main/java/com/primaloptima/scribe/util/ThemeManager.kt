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
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

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

        fun isColorDark(color: Int): Boolean {
            val darkness = 1 - (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255
            return darkness >= 0.4
        }

        /**
         * Automatically calculates a 5-tier elevation ramp, semantic typography tokens,
         * and boundary tokens from base background, text, and accent colors.
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

            fun shiftLightness(c: Int, delta: Float): String {
                val outHsl = FloatArray(3)
                androidx.core.graphics.ColorUtils.colorToHSL(c, outHsl)
                outHsl[2] = (outHsl[2] + delta).coerceIn(0f, 1f)
                val res = androidx.core.graphics.ColorUtils.HSLToColor(outHsl)
                return String.format("#%02X%02X%02X", Color.red(res), Color.green(res), Color.blue(res))
            }

            return if (isDark) {
                val surfaceLowest = shiftLightness(bgInt, 0.02f)
                val surface = shiftLightness(bgInt, 0.05f)
                val surfaceRaised = shiftLightness(bgInt, 0.09f)
                val surfaceOverlay = shiftLightness(bgInt, 0.14f)
                val mutedText = blend(textInt, bgInt, 0.38f)
                val subtleText = blend(textInt, bgInt, 0.58f)
                val accentMuted = blend(accentInt, bgInt, 0.82f)
                val selection = blend(accentInt, bgInt, 0.70f)
                val borderSubtle = shiftLightness(bgInt, 0.08f)
                val borderProminent = blend(accentInt, textInt, 0.40f)
                val monologueText = blend(textInt, bgInt, 0.25f)

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
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = base?.dialogueText?.takeIf { it != base.accent } ?: accentHex,
                    monologueText = monologueText,
                    headingText = accentHex,
                    toolbar = surface,
                    toolbarText = textHex
                )
            } else {
                val surfaceLowest = shiftLightness(bgInt, -0.03f)
                val surface = "#FFFFFF"
                val surfaceRaised = "#FFFFFF"
                val surfaceOverlay = "#FFFFFF"
                val mutedText = blend(textInt, bgInt, 0.42f)
                val subtleText = blend(textInt, bgInt, 0.62f)
                val accentMuted = blend(accentInt, bgInt, 0.88f)
                val selection = blend(accentInt, bgInt, 0.78f)
                val borderSubtle = shiftLightness(bgInt, -0.08f)
                val borderProminent = accentHex
                val monologueText = blend(textInt, bgInt, 0.25f)

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
                    accentMuted = accentMuted,
                    selection = selection,
                    border = borderSubtle,
                    borderSubtle = borderSubtle,
                    borderProminent = borderProminent,
                    dialogueText = base?.dialogueText?.takeIf { it != base.accent } ?: accentHex,
                    monologueText = monologueText,
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
