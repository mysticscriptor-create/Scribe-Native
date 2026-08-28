package com.primaloptima.scribe.ui.theme

import android.app.Activity
import android.graphics.Bitmap
import coil3.BitmapImage

import android.os.Build
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.unit.dp
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.DpOffset
import androidx.compose.material3.DropdownMenu
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.window.PopupProperties
import androidx.compose.foundation.layout.ColumnScope

import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.ColorUtils
import androidx.core.view.WindowCompat
import coil3.ImageLoader
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.allowHardware
import com.primaloptima.scribe.util.DefaultThemes
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.util.ScribeDataStore
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.AppTheme
import androidx.compose.foundation.border
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.material3.ProvideTextStyle
import androidx.activity.compose.BackHandler
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import dev.chrisbanes.haze.hazeEffect
import dev.chrisbanes.haze.rememberHazeState
import dev.chrisbanes.haze.HazeStyle
import dev.chrisbanes.haze.HazeTint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.material3.LocalContentColor

val LocalHazeState = compositionLocalOf<HazeState?> { null }
val LocalAppTheme = compositionLocalOf<AppTheme?> { null }
val LocalBgAnalysisBitmap = compositionLocalOf<Bitmap?> { null }
val LocalScreenSize = compositionLocalOf { Pair(1080f, 1920f) }
val LocalRootGeometry = compositionLocalOf { Pair(0f, 0f) }
/**
 * True when the user has enabled the frosted glass effect for this theme.
 * Controls whether bars, panels, cards, and FABs use hazeEffect / one-shot blur.
 * When false all frosted modifiers fall back to a solid surface background.
 */
val LocalFrostedGlass = compositionLocalOf { true }

/** Whether the frosted glass tint overlay is enabled. false = pure blur, no colour wash. */
val LocalFrostedTint = compositionLocalOf { true }

/** Blur radius (dp) for Haze on API 31+. Pre-API-31: applied at bitmap-load time. */
val LocalFrostedBlurRadius = compositionLocalOf { 15f }

/**
 * Holds the one-shot blurred screenshot bitmap captured just before a panel/dialog
 * opens on pre-API-31 devices. Set by the screen that owns the drawer/dialog trigger,
 * consumed by [frostedPanel], [frostedCard], [frostedFab], [FrostedDialog].
 * Null when no capture has been taken or when running on API 31+.
 */
val LocalOneShotBitmap = compositionLocalOf<Bitmap?> { null }

/**
 * Pre-blurred version of the background image, derived from the already-loaded
 * Coil bitmap inside ScribeComposeTheme. Used by bars and FABs on API < 31 so
 * they get a frosted look without needing a live screen capture.
 *
 * Null on API 31+ (Haze handles blurring natively) and when no background image
 * is active. Never used by dialogs or drawers — those use [LocalOneShotBitmap].
 */
val LocalBarBlurBitmap = compositionLocalOf<Bitmap?> { null }

/**
 * Always holds the fully-opaque theme surface color, even when a background image
 * is active and the color scheme's surface is set to alpha=0 for glass effects.
 * Use this for Dropdowns, Dialogs, and any popup that must never be see-through.
 */
val LocalSolidSurface = compositionLocalOf { Color.White }

/**
 * The resolved, adaptive accent color for the active theme.
 *
 * Computed once inside [ScribeComposeTheme] from [adaptiveAccentColor] with the
 * real background luminance, then provided here so every screen reads a single
 * consistent value instead of each calling parseComposeColor + adaptiveAccentColor
 * independently.
 *
 * On plain-colour themes (no background image) this equals the raw accent from the
 * theme JSON. On image themes it is shifted if necessary to maintain 3:1 contrast
 * against the wallpaper luminance.
 *
 * Usage: val accent = LocalAccentColor.current
 */
val LocalAccentColor = compositionLocalOf { Color.Unspecified }

fun autoTextColor(bg: Color): Color {
    val luminance = bg.luminance()
    return if (luminance > 0.5f) Color.Black else Color.White
}

/**
 * Returns an accent colour that is always visually prominent against the actual
 * background image.
 *
 * When no background image is active the accent is returned unchanged.
 *
 * When a background image IS active:
 *  - If [savedBgLuminance] ≥ 0 (computed at crop-confirm time), contrast is checked
 *    against the *real* background luminance — not the theme surface colour. This fixes
 *    the core bug where baby-blue on a white background was invisible.
 *  - If [savedBgLuminance] == -1f (old theme), falls back to checking against
 *    [solidSurface] as before.
 *
 * Contrast target is 3.0:1 (WCAG minimum for UI components / large text). If the
 * accent already meets this threshold it is returned unchanged, preserving the user's
 * chosen colour exactly.
 *
 * When contrast is insufficient the accent is adjusted by shifting its HSL Lightness
 * using AndroidX [ColorUtils] — no custom math helpers needed. The hue and saturation
 * are preserved so the colour is always recognisably the same accent.  Only Lightness
 * moves: darker for a light background, lighter for a dark background.  This avoids
 * the jarring hue-rotation fallback that the previous approach used.
 */
fun adaptiveAccentColor(
    accent: Color,
    solidSurface: Color,
    hasBgImage: Boolean,
    savedBgLuminance: Float = -1f
): Color {
    if (!hasBgImage) return accent

    // Determine the luminance we're contrasting against.
    // savedBgLuminance is the real image average; solidSurface is the old fallback.
    val bgLum: Float = if (savedBgLuminance >= 0f) savedBgLuminance else solidSurface.luminance()

    val accentLum = accent.luminance()
    val lighter = maxOf(bgLum, accentLum)
    val darker = minOf(bgLum, accentLum)
    val contrastRatio = (lighter + 0.05f) / (darker + 0.05f)

    // Already readable enough — keep the user's exact colour.
    if (contrastRatio >= 3.0f) return accent

    // Shift HSL Lightness using the AndroidX utility that already lives in the project.
    val hsl = FloatArray(3)
    ColorUtils.colorToHSL(accent.toArgb(), hsl)

    // On a light background (bgLum > 0.5) darken the accent; on a dark background lighten it.
    // We step in increments of 0.05 until we reach 3.0:1 or exhaust the range.
    val step = if (bgLum > 0.5f) -0.05f else 0.05f
    repeat(18) { // max 18 steps covers the full 0–1 lightness range
        hsl[2] = (hsl[2] + step).coerceIn(0.05f, 0.95f)
        val candidate = Color(ColorUtils.HSLToColor(hsl) or (0xFF shl 24))
        val candLum = candidate.luminance()
        val cLighter = maxOf(bgLum, candLum)
        val cDarker = minOf(bgLum, candLum)
        if ((cLighter + 0.05f) / (cDarker + 0.05f) >= 3.0f) return candidate
    }

    // If we never hit 3.0:1 (extremely rare — means the hue itself is too close to the
    // background at all lightness levels), return the most-shifted candidate we have.
    return Color(ColorUtils.HSLToColor(hsl) or (0xFF shl 24))
}

@Composable
fun localHasBgImage(): Boolean {
    val theme = LocalAppTheme.current
    val frostedGlass = LocalFrostedGlass.current
    return theme?.backgroundImageUri?.isNotEmpty() == true &&
            (theme.bgMode == "image" || theme.bgMode == "blurred") &&
            frostedGlass
}

/**
 * Directional specular rim lighting.
 * Simulates physical overhead light catch via a vertical linear gradient.
 */
fun Modifier.specularGlassBorder(
    shape: Shape,
    isDark: Boolean,
    strokeWidth: Dp = 1.dp
): Modifier = this.border(
    width = strokeWidth,
    brush = Brush.verticalGradient(
        colors = if (isDark) {
            listOf(
                Color.White.copy(alpha = 0.22f), // Overhead light reflection
                Color.White.copy(alpha = 0.08f),
                Color.White.copy(alpha = 0.02f)  // Ambient bottom falloff
            )
        } else {
            listOf(
                Color.White.copy(alpha = 0.70f), // Crisp light sheen
                Color.White.copy(alpha = 0.25f),
                Color.Black.copy(alpha = 0.06f)
            )
        }
    ),
    shape = shape
)

/**
 * High-performance coordinate-mapped wallpaper blur renderer for pre-API 31 devices.
 * Samples the exact screen rectangle behind this composable from [bitmap] (pre-blurred on Dispatchers.IO),
 * overlays [tint], and paints the specular glass border. Zero main-thread CPU capture overhead.
 * Uses true global coordinate sampling so popups, menus, and dialogs in separate windows/layers
 * sample their exact localized slice of the wallpaper without squashing or distortion.
 */
@Composable
fun Modifier.drawWithBackdropBitmap(
    bitmap: Bitmap?,
    tint: Color,
    shape: Shape = RectangleShape,
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    fallbackColor: Color = LocalSolidSurface.current
): Modifier {
    if (bitmap == null) {
        return this
            .clip(shape)
            .background(fallbackColor, shape = shape)
            .specularGlassBorder(shape, isDark)
    }

    val view = LocalView.current
    val (screenWFromLocal, screenHFromLocal) = LocalScreenSize.current
    val (rootWFromLocal, rootHFromLocal) = LocalRootGeometry.current

    var screenOffset by remember { mutableStateOf(IntOffset.Zero) }

    return this
        .clip(shape)
        .onGloballyPositioned { coords ->
            val viewLocation = IntArray(2)
            view.getLocationOnScreen(viewLocation)
            val windowPos = coords.positionInWindow()

            // True physical screen position regardless of Popup, Window, or Dialog layer
            val globalX = viewLocation[0] + windowPos.x
            val globalY = viewLocation[1] + windowPos.y

            screenOffset = IntOffset(globalX.toInt(), globalY.toInt())
        }
        .drawWithContent {
            val bitmapW = bitmap.width.toFloat()
            val bitmapH = bitmap.height.toFloat()
            val displayMetrics = view.resources.displayMetrics
            val screenW = when {
                rootWFromLocal > 0f -> rootWFromLocal
                screenWFromLocal > 0f -> screenWFromLocal
                displayMetrics.widthPixels > 0 -> displayMetrics.widthPixels.toFloat()
                else -> bitmapW
            }
            val screenH = when {
                rootHFromLocal > 0f -> rootHFromLocal
                screenHFromLocal > 0f -> screenHFromLocal
                displayMetrics.heightPixels > 0 -> displayMetrics.heightPixels.toFloat()
                else -> bitmapH
            }

            val srcLeft = (screenOffset.x.toFloat() / screenW * bitmapW).coerceIn(0f, bitmapW)
            val srcTop = (screenOffset.y.toFloat() / screenH * bitmapH).coerceIn(0f, bitmapH)
            val srcRight = ((screenOffset.x + size.width) / screenW * bitmapW).coerceIn(0f, bitmapW)
            val srcBottom = ((screenOffset.y + size.height) / screenH * bitmapH).coerceIn(0f, bitmapH)

            if (srcRight > srcLeft && srcBottom > srcTop) {
                drawImage(
                    image = bitmap.asImageBitmap(),
                    srcOffset = IntOffset(srcLeft.toInt(), srcTop.toInt()),
                    srcSize = IntSize(
                        (srcRight - srcLeft).toInt().coerceAtLeast(1),
                        (srcBottom - srcTop).toInt().coerceAtLeast(1)
                    ),
                    dstOffset = IntOffset.Zero,
                    dstSize = IntSize(size.width.toInt(), size.height.toInt())
                )
            } else {
                drawRect(fallbackColor)
            }

            if (tint != Color.Transparent) {
                drawRect(tint)
            }

            drawContent()
        }
        .specularGlassBorder(shape, isDark)
}

@Composable
fun Modifier.frostedBar(
    hazeState: HazeState?,
    shape: Shape = RectangleShape,
    isDark: Boolean = LocalAppTheme.current?.isDark == true
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.35f) else Color.Transparent
    return if (!hasBgImage) {
        this.background(solidSurface, shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = 0.92f))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = 0.92f), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Applies a frosted-glass effect to a FAB (or any floating circular button).
 * On Android 12+ this is a real GPU blur via Haze with specular rim border;
 * on older devices it samples the pre-blurred wallpaper crop with specular rim.
 */
@Composable
fun Modifier.frostedFab(
    hazeState: HazeState?,
    shape: Shape = androidx.compose.foundation.shape.CircleShape,
    isDark: Boolean = LocalAppTheme.current?.isDark == true
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.35f) else Color.Transparent
    return if (!hasBgImage) {
        this.clip(shape).background(solidSurface, shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = 0.90f))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = 0.90f), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Applies a frosted-glass effect to side panels, navigation drawers, and bottom sheets.
 */
@Composable
fun Modifier.frostedPanel(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.25f) else Color.Transparent
    return if (!hasBgImage) {
        this.clip(shape).background(solidSurface, shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = 0.95f))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = 0.94f), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Frosted glass for contextual dropdowns, anchored popovers, and overflow menus.
 */
@Composable
fun Modifier.frostedMenu(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(14.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.25f) else Color.Transparent
    return if (!hasBgImage) {
        this.clip(shape).background(solidSurface, shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = 0.94f))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = 0.94f), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Frosted glass dropdown menu with cross-version support (API 24+).
 * Automatically applies GPU Haze blur on API 31+, coordinate-mapped StackBlur on API < 31,
 * and solid theme surface on plain-color themes with zero transparency bugs.
 */
@Composable
fun FrostedDropdownMenu(
    expanded: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    offset: DpOffset = DpOffset(0.dp, 0.dp),
    scrollState: ScrollState = rememberScrollState(),
    properties: PopupProperties = PopupProperties(focusable = true),
    shape: Shape = RoundedCornerShape(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    val hazeState = LocalHazeState.current
    val solidSurface = LocalSolidSurface.current
    val isDark = LocalAppTheme.current?.isDark == true

    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismissRequest,
        modifier = modifier.frostedMenu(hazeState = hazeState, shape = shape, isDark = isDark),
        offset = offset,
        scrollState = scrollState,
        properties = properties,
        shape = shape,
        containerColor = Color.Transparent,
        content = {
            val contentColor = autoTextColor(solidSurface)
            CompositionLocalProvider(LocalContentColor provides contentColor) {
                content()
            }
        }
    )
}

/**
 * Frosted glass for Card composables (ElevatedCard, Card, etc).
 */
@Composable
fun Modifier.frostedCard(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(16.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    solidAlpha: Float = 0.92f,
    applyFallbackBackground: Boolean = true
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.25f) else Color.Transparent
    return if (!hasBgImage) {
        if (applyFallbackBackground) {
            this.clip(shape).background(solidSurface.copy(alpha = solidAlpha), shape = shape)
        } else {
            this
        }
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = solidAlpha))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = solidAlpha), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Frosted glass for Chips, Pill tabs, and small badges.
 */
@Composable
fun Modifier.frostedChip(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(12.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    isSelected: Boolean = false,
    selectedAlpha: Float = 0.25f,
    unselectedAlpha: Float = 0.12f,
    solidAlpha: Float = 0.90f
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val accentColor = LocalAccentColor.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val baseTint = if (isSelected) accentColor else solidSurface
    val tintAlpha = if (isSelected) selectedAlpha else unselectedAlpha
    val tintColor = if (tintEnabled) baseTint.copy(alpha = tintAlpha) else (if (isSelected) accentColor.copy(alpha = 0.18f) else Color.Transparent)

    return if (!hasBgImage) {
        val fallbackBg = if (isSelected) accentColor.copy(alpha = 0.18f) else solidSurface.copy(alpha = solidAlpha)
        this.clip(shape).background(fallbackBg, shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        val fallbackBg = if (isSelected) accentColor.copy(alpha = 0.18f) else solidSurface.copy(alpha = solidAlpha)
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, fallbackBg)
    } else {
        val fallbackBg = if (isSelected) accentColor.copy(alpha = 0.18f) else solidSurface.copy(alpha = solidAlpha)
        this
            .clip(shape)
            .background(fallbackBg, shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

/**
 * Frosted glass for Search Bars / TextFields.
 */
@Composable
fun Modifier.frostedSearchBox(
    hazeState: HazeState?,
    shape: Shape = RoundedCornerShape(12.dp),
    isDark: Boolean = LocalAppTheme.current?.isDark == true,
    solidAlpha: Float = 0.92f
): Modifier {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val barBlurBitmap = LocalBarBlurBitmap.current
    val tintEnabled = LocalFrostedTint.current
    val blurRadius = LocalFrostedBlurRadius.current
    val tintColor = if (tintEnabled) solidSurface.copy(alpha = 0.22f) else Color.Transparent

    return if (!hasBgImage) {
        this.clip(shape).background(solidSurface.copy(alpha = solidAlpha), shape = shape)
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null) {
        this
            .clip(shape)
            .hazeEffect(
                state = hazeState,
                style = HazeStyle(blurRadius = blurRadius.dp, tint = HazeTint(tintColor), noiseFactor = 0f)
            )
            .specularGlassBorder(shape, isDark)
    } else if (barBlurBitmap != null) {
        this
            .clip(shape)
            .drawWithBackdropBitmap(barBlurBitmap, tintColor, shape, isDark, solidSurface.copy(alpha = solidAlpha))
    } else {
        this
            .clip(shape)
            .background(solidSurface.copy(alpha = solidAlpha), shape = shape)
            .specularGlassBorder(shape, isDark)
    }
}

// ── Composable wrappers that combine frosted modifiers with LocalContentColor ──
//
// The frosted*() modifier functions are @Composable but return a Modifier, so they
// cannot host CompositionLocalProvider themselves. These wrapper composables apply
// both the frosted modifier AND set LocalContentColor to the correct contrasting
// colour so every Text and Icon inside inherits the right colour automatically,
// with no per-element colour arguments needed.

/**
 * Wraps [content] with LocalContentColor set to contrast against the frosted bar surface.
 *
 * When a background image is active, uses [savedBgLuminance] (precomputed at crop time)
 * to determine whether white or dark text is needed against the real visual background.
 * Falls back to [autoTextColor] against the solid surface for old themes without this field.
 * Use this around TopAppBar / NavigationBar / BottomAppBar content lambdas.
 */
@Composable
fun FrostedBarContent(content: @Composable () -> Unit) {
    val solidSurface = LocalSolidSurface.current
    val theme = LocalAppTheme.current
    val savedLum = theme?.savedBgLuminance ?: -1f
    val hasBgImage = localHasBgImage()
    val contentColor = when {
        hasBgImage && savedLum >= 0f -> if (savedLum < 0.45f) Color.White else Color(0xFF1A1A1A)
        else -> autoTextColor(solidSurface)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        content()
    }
}

/**
 * Wraps [content] with LocalContentColor set to contrast against the frosted panel surface.
 *
 * When a background image is active, uses [savedBgLuminance] (precomputed at crop time)
 * to determine whether white or dark text is needed against the real visual background.
 * Falls back to [autoTextColor] against the solid surface for old themes without this field.
 * Use this around drawer / side-panel content.
 */
@Composable
fun FrostedPanelContent(content: @Composable () -> Unit) {
    val solidSurface = LocalSolidSurface.current
    val theme = LocalAppTheme.current
    val savedLum = theme?.savedBgLuminance ?: -1f
    val hasBgImage = localHasBgImage()
    val contentColor = when {
        hasBgImage && savedLum >= 0f -> if (savedLum < 0.45f) Color.White else Color(0xFF1A1A1A)
        else -> autoTextColor(solidSurface)
    }
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        content()
    }
}

/**
 * Wraps [content] with LocalContentColor set to contrast against the frosted card surface.
 * Use this around card body content.
 */
@Composable
fun FrostedCardContent(content: @Composable () -> Unit) {
    val solidSurface = LocalSolidSurface.current
    val hasBgImage = localHasBgImage()
    val contentColor = if (hasBgImage) autoTextColor(solidSurface) else LocalContentColor.current
    CompositionLocalProvider(LocalContentColor provides contentColor) {
        content()
    }
}

/**
 * A dialog that lives in the same window as the rest of the UI, so Haze blur works correctly.
 * Standard AlertDialog creates a separate Android window which breaks hazeEffect.
 *
 * Usage: replace AlertDialog with FrostedDialog. The API mirrors AlertDialog.
 *
 * When no background image is active the dialog uses the solid surface color (guaranteed opaque).
 * When a background image IS active, the dialog surface gets the frosted blur via hazeEffect (API 31+)
 * or coordinate-mapped StackBlur (pre-API 31) with directional specular rim lighting.
 */
@Composable
fun FrostedDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: @Composable (() -> Unit)? = null,
    icon: @Composable (() -> Unit)? = null,
    title: @Composable (() -> Unit)? = null,
    text: @Composable (() -> Unit)? = null,
    shape: Shape = RoundedCornerShape(28.dp),
) {
    val hazeState = LocalHazeState.current
    val solidSurface = LocalSolidSurface.current
    val isDark = LocalAppTheme.current?.isDark == true

    BackHandler { onDismissRequest() }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.50f))
            .clickable(
                indication = null,
                interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
            ) { onDismissRequest() },
        contentAlignment = Alignment.Center
    ) {
        val dialogContentColor = autoTextColor(solidSurface)
        CompositionLocalProvider(LocalContentColor provides dialogContentColor) {
            Column(
                modifier = modifier
                    .fillMaxWidth(0.88f)
                    .clip(shape)
                    .frostedCard(
                        hazeState = hazeState,
                        shape = shape,
                        isDark = isDark,
                        solidAlpha = 0.98f,
                        applyFallbackBackground = true
                    )
                    .clickable(
                        indication = null,
                        interactionSource = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }
                    ) { /* consume so taps inside don't dismiss */ }
                    .padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                icon?.let {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        it()
                    }
                }
                title?.let {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.headlineSmall
                    ) { it() }
                }
                text?.let {
                    ProvideTextStyle(
                        value = MaterialTheme.typography.bodyMedium
                    ) { it() }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    dismissButton?.invoke()
                    confirmButton()
                }
            }
        }
    }
}

/**
 * Returns the correct containerColor for a Card or FAB when frosted glass is active.
 * When a background image is set and blur is allowed, returns [Color.Transparent] so
 * hazeEffect / drawWithBackdropBitmap shows through. Otherwise returns [fallback].
 */
@Composable
fun frostedContainerColor(fallback: Color): Color {
    val hazeState = LocalHazeState.current
    val barBlurBitmap = LocalBarBlurBitmap.current
    val hasBgImage = localHasBgImage()
    val legacyReady = Build.VERSION.SDK_INT < Build.VERSION_CODES.S && barBlurBitmap != null
    val modernReady = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && hazeState != null
    return if (hasBgImage && (modernReady || legacyReady)) Color.Transparent else fallback
}

/**
 * Backward-compatible bridge for legacy drawWithOneShotBitmap.
 * Automatically delegates to modern drawWithBackdropBitmap or frostedCard.
 */
@Composable
fun Modifier.drawWithOneShotBitmap(bitmap: Bitmap?, tint: Color): Modifier {
    val hazeState = LocalHazeState.current
    val isDark = LocalAppTheme.current?.isDark == true
    val barBlurBitmap = LocalBarBlurBitmap.current
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        this.frostedCard(hazeState = hazeState, isDark = isDark)
    } else {
        this.drawWithBackdropBitmap(bitmap ?: barBlurBitmap, tint, isDark = isDark)
    }
}

fun parseComposeColor(hex: String, fallback: Color = Color.Black): Color {
    // Guard blank/empty strings before hitting ThemeManager — avoids returning
    // the fallback colour when a theme has an uninitialised hex field, which
    // would accidentally match lightDefault or darkDefault in isDefaultText.
    if (hex.isBlank()) return fallback
    return try {
        Color(ThemeManager.parseColor(hex))
    } catch (_: Exception) {
        fallback
    }
}

@Composable
fun ScribeComposeTheme(
    appTheme: AppTheme? = null,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    // Use the app-singleton ThemeManager so its in-memory cache (seeded by ScribeApp
    // and kept up-to-date by ThemeViewModel.save/delete) is always current.
    // Creating a new ThemeManager here would give an instance whose cache is never
    // seeded, causing allThemes() to return only built-ins (no backgroundImageUri).
    val themeManager = remember { (context.applicationContext as ScribeApp).themeManager }
    val resolvedTheme = if (appTheme != null) {
        appTheme
    } else {
        val dataStore = remember { (context.applicationContext as ScribeApp).dataStore }
        val activeThemeId by dataStore.activeThemeIdFlow.collectAsState(
            initial = themeManager.activeTheme().id
        )
        val customThemesJson by dataStore.customThemesJsonFlow.collectAsState(initial = "[]")
        remember(activeThemeId, customThemesJson) {
            themeManager.allThemes().firstOrNull { it.id == activeThemeId }
                ?: DefaultThemes.all.first()
        }
    }

    val bgUri = resolvedTheme.backgroundImageUri
    val hasBgImage = !bgUri.isNullOrEmpty() && resolvedTheme.bgMode != "color"
    val view = LocalView.current
    val screenWidthPx = remember(view) { view.resources.displayMetrics.widthPixels.toFloat() }
    val screenHeightPx = remember(view) { view.resources.displayMetrics.heightPixels.toFloat() }
    var analysisBitmap by remember(bgUri, hasBgImage) { mutableStateOf<Bitmap?>(null) }

    LaunchedEffect(bgUri, hasBgImage) {
        if (!hasBgImage || bgUri.isNullOrEmpty()) {
            analysisBitmap = null
            return@LaunchedEffect
        }
        analysisBitmap = withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(bgUri)
                    .size(32, 32)
                    .allowHardware(false) // Prevents hardware bitmap; getPixel() requires software config
                    .build()
                (ImageLoader(context).execute(request).image as? BitmapImage)?.bitmap
            } catch (_: Exception) {
                null
            }
        }
    }

    val bg = parseComposeColor(resolvedTheme.colors.background, Color(0xFFFAFAF7))
    val surface = parseComposeColor(resolvedTheme.colors.surface, Color.White)
    // Fallback is Color.Black (0xFF000000), NOT Color(0xFF1A1A1A) which equals
    // darkDefault.  If parsing fails on an empty/malformed hex, a fallback that
    // equals darkDefault would set isDefaultText = true and silently enable the
    // auto-luminance override — the secondary cause of the text-revert bug.
    val configuredText = parseComposeColor(resolvedTheme.colors.text, Color.Black)
    val configuredAccent = parseComposeColor(resolvedTheme.colors.accent, Color(0xFF333333))
    val border = parseComposeColor(resolvedTheme.colors.border, Color(0xFFE0E0D8))
    val surfaceVariant = parseComposeColor(resolvedTheme.colors.surface, surface)

    val bgLum = resolvedTheme.savedBgLuminance

    // ── Text colour resolution ────────────────────────────────────────────────
    // Priority order:
    // 1. savedBgLuminance ≥ 0: use the precomputed real background luminance.
    //    Only override if the user hasn't manually set a custom text colour.
    //    We detect "custom" by checking whether configuredText differs from BOTH
    //    the light default and the dark default — if it matches neither, the user
    //    picked something intentional and we leave it alone.
    // 2. analysisBitmap available (old theme, no savedBgLuminance): live analysis.
    // 3. Neither: use the stored configuredText as-is.
    val lightDefault = Color.White
    val darkDefault = Color(0xFF1A1A1A)
    val isDefaultText = configuredText == lightDefault || configuredText == darkDefault
    val text: Color = when {
        hasBgImage && bgLum >= 0f && isDefaultText ->
            if (bgLum < 0.45f) Color.White else Color(0xFF1A1A1A)
        hasBgImage && bgLum < 0f && analysisBitmap != null -> {
            // Fallback: live analysis for old themes
            contrastingTextColor(
                bitmap = analysisBitmap,
                screenRect = Rect(0f, 0f, screenWidthPx, screenHeightPx),
                screenWidthPx = screenWidthPx,
                screenHeightPx = screenHeightPx
            )
        }
        else -> configuredText
    }

    // ── Accent colour resolution ──────────────────────────────────────────────
    // Use adaptiveAccentColor with savedBgLuminance so it contrasts the real image,
    // not the theme surface. This is now used for the full color scheme (accentIcons),
    // replacing the old hard-coded white/black override.
    val accentIcons = adaptiveAccentColor(
        accent = configuredAccent,
        solidSurface = surface,
        hasBgImage = hasBgImage,
        savedBgLuminance = bgLum
    )

    val onPrimaryColor = if (accentIcons.luminance() < 0.5f) Color.White else Color.Black

    val isLight = !resolvedTheme.isDark

    val rawColorScheme: ColorScheme = if (isLight) {
        lightColorScheme(
            primary = accentIcons,
            onPrimary = onPrimaryColor,
            primaryContainer = surface,
            onPrimaryContainer = text,
            secondary = accentIcons,
            onSecondary = onPrimaryColor,
            // KEY: secondaryContainer was unset → M3 default is purple(#E8DEF8)
            // Setting it to surfaceVariant gives a themed, warm tint instead.
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = accentIcons,
            onTertiary = onPrimaryColor,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = text,
            surfaceContainerLowest = bg,
            surfaceContainerLow = bg,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            // KEY: surfaceContainerHighest was unset → M3 default is lavender(#E6E0E9)
            // Card() in BOM 2026.06.00 uses this slot by default.
            surfaceContainerHighest = surfaceVariant,
            // Keep tonal surface tint on-theme (prevents extra purple tinting)
            surfaceTint = accentIcons,
            inverseSurface = text,
            inverseOnSurface = bg,
            inversePrimary = accentIcons,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.32f)
        )
    } else {
        darkColorScheme(
            primary = accentIcons,
            onPrimary = onPrimaryColor,
            primaryContainer = surface,
            onPrimaryContainer = text,
            secondary = accentIcons,
            onSecondary = onPrimaryColor,
            secondaryContainer = surfaceVariant,
            onSecondaryContainer = text,
            tertiary = accentIcons,
            onTertiary = onPrimaryColor,
            tertiaryContainer = surfaceVariant,
            onTertiaryContainer = text,
            background = bg,
            onBackground = text,
            surface = surface,
            onSurface = text,
            surfaceVariant = surfaceVariant,
            onSurfaceVariant = text,
            surfaceContainerLowest = bg,
            surfaceContainerLow = bg,
            surfaceContainer = surface,
            surfaceContainerHigh = surface,
            surfaceContainerHighest = surfaceVariant,
            surfaceTint = accentIcons,
            inverseSurface = text,
            inverseOnSurface = bg,
            inversePrimary = accentIcons,
            outline = border,
            outlineVariant = border,
            scrim = Color.Black.copy(alpha = 0.32f)
        )
    }

    // Use snap() on first composition and when the theme ID hasn't actually changed
    // (e.g. a color tweak inside the same theme triggers recomposition but should not
    // animate). Only animate when the user deliberately switches to a different theme.
    var prevThemeId by remember { mutableStateOf<String?>(null) }
    val isThemeChanging = prevThemeId != null && prevThemeId != resolvedTheme.id
    SideEffect { prevThemeId = resolvedTheme.id }

    val animSpec = if (isThemeChanging) tween<Color>(durationMillis = 400) else snap()

    val animPrimary by animateColorAsState(rawColorScheme.primary, animSpec, label = "primary")
    val animOnPrimary by animateColorAsState(rawColorScheme.onPrimary, animSpec, label = "onPrimary")
    val animBg by animateColorAsState(rawColorScheme.background, animSpec, label = "bg")
    val animOnBg by animateColorAsState(rawColorScheme.onBackground, animSpec, label = "onBg")
    val animSurface by animateColorAsState(rawColorScheme.surface, animSpec, label = "surface")
    val animOnSurface by animateColorAsState(rawColorScheme.onSurface, animSpec, label = "onSurface")
    val animSurfaceVariant by animateColorAsState(rawColorScheme.surfaceVariant, animSpec, label = "surfaceVariant")
    val animOnSurfaceVariant by animateColorAsState(rawColorScheme.onSurfaceVariant, animSpec, label = "onSurfaceVariant")
    val animOutline by animateColorAsState(rawColorScheme.outline, animSpec, label = "outline")

    val showWholeAppBg = resolvedTheme.themeScope == "whole_app" && hasBgImage

    // When a whole-app background image is active, surfaces must be transparent so
    // the image shows through and the Haze blur effect works. However, we must NOT
    // use Color.Transparent (= ARGB 0,0,0,0 — transparent BLACK) because any
    // downstream call like surface.copy(alpha = 0.95f) would produce a near-opaque
    // BLACK instead of the theme colour. Instead, we zero only the alpha channel
    // while keeping the RGB channels intact, so copy(alpha = X) restores the
    // correct colour at the requested opacity.
    val glassySurface        = if (showWholeAppBg) animSurface.copy(alpha = 0f)        else animSurface
    val glassySurfaceVariant = if (showWholeAppBg) animSurfaceVariant.copy(alpha = 0f) else animSurfaceVariant
    val glassyBg             = if (showWholeAppBg) animBg.copy(alpha = 0f)             else animBg

    val animatedColorScheme = rawColorScheme.copy(
        primary = animPrimary,
        onPrimary = animOnPrimary,
        primaryContainer = glassySurface,
        onPrimaryContainer = animOnSurface,
        secondary = animPrimary,
        onSecondary = animOnPrimary,
        secondaryContainer = glassySurfaceVariant,
        onSecondaryContainer = animOnSurface,
        tertiary = animPrimary,
        onTertiary = animOnPrimary,
        tertiaryContainer = glassySurfaceVariant,
        onTertiaryContainer = animOnSurface,
        background = glassyBg,
        onBackground = animOnBg,
        surface = glassySurface,
        onSurface = animOnSurface,
        surfaceVariant = glassySurfaceVariant,
        onSurfaceVariant = animOnSurfaceVariant,
        surfaceContainerLowest = glassyBg,
        surfaceContainerLow = glassyBg,
        surfaceContainer = glassySurface,
        surfaceContainerHigh = glassySurface,
        surfaceContainerHighest = glassySurfaceVariant,
        outline = animOutline,
        outlineVariant = animOutline
    )

    // enableEdgeToEdge() (called in each Activity) owns bar transparency.
    // Here we only update icon/caret appearance to match the active theme.
    val window = (LocalContext.current as? Activity)?.window
    SideEffect {
        window?.let { win ->
            WindowCompat.getInsetsController(win, win.decorView).apply {
                isAppearanceLightStatusBars = isLight
                isAppearanceLightNavigationBars = isLight
            }
        }
    }

    // Only activate Haze when there is actually a background image in blurred mode.
    // blurEnabled = false makes Haze fully dormant — no GPU overhead, no blur passes.
    // image mode (sharp wallpaper) and color mode (no image) both get blurEnabled = false.
    val hazeState = rememberHazeState(blurEnabled = hasBgImage && resolvedTheme.bgMode == "blurred")

    // ── Pre-compute blur inputs before CompositionLocalProvider ──────────────
    // These vals must live here (not inside the provider's content lambda) because
    // barBlurBitmap is referenced in the provider list itself.

    val bgOpacity = resolvedTheme.backgroundImageOpacity ?: 0.35f
    val bgMode = resolvedTheme.bgMode
    val blurIntensity = resolvedTheme.blurIntensity
    val frostedTintEnabled = resolvedTheme.frostedTintEnabled
    val frostedBlurRadius = resolvedTheme.frostedBlurRadius

    // On API < 31 we can't use RenderEffect on a live composable, so we
    // pre-blur the source bitmap once using pure Kotlin stack blur and
    // display that pre-blurred bitmap instead.
    //
    // Even for bgMode == "image" (no blur), we must load with
    // allowHardware(false) on API < 31 so that BitmapBlur.captureOnly
    // can draw the view onto a software Canvas without crashing.
    // Hardware bitmaps throw an exception when drawn onto a software Canvas,
    // which captureOnly silently catches → returns null → frosted glass falls
    // back to solid. By keeping the background image as a software bitmap
    // the capture succeeds and one-shot blur works correctly.
    val needsSoftwareBlur = bgMode == "blurred" &&
            blurIntensity > 0f &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            bgUri != null

    // For bgMode == "image" on API < 31 we still need a software bitmap
    // (no blur) so captureOnly can read the view hierarchy.
    val needsSoftwareImage = bgMode == "image" &&
            Build.VERSION.SDK_INT < Build.VERSION_CODES.S &&
            bgUri != null

    // ── Aspect-ratio-aware bitmap sizes ──────────────────────────────────────
    // The background AsyncImage now uses ContentScale.FillBounds — the saved
    // cropped JPEG is already exactly the screen's aspect ratio, so FillBounds
    // stretches it to fill without any re-cropping or distortion.
    // Blur bitmaps are still loaded at screen aspect ratio so that the blur
    // and the visible background stay in sync across API levels.
    val blurLoadW = (screenWidthPx * 0.5f).toInt().coerceAtLeast(1)
    val blurLoadH = (screenHeightPx * 0.5f).toInt().coerceAtLeast(1)

    val softwareBlurredModel by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = bgUri,
        key2 = blurIntensity,
        key3 = needsSoftwareBlur
    ) {
        if (!needsSoftwareBlur) {
            value = null
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loader = coil3.ImageLoader(context)
                val req = coil3.request.ImageRequest.Builder(context)
                    .data(bgUri)
                    .size(coil3.size.Size(blurLoadW, blurLoadH))
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                val bmp = (result as? coil3.request.SuccessResult)
                    ?.image
                    ?.let { (it as? coil3.BitmapImage)?.bitmap }
                bmp?.let {
                    val radiusPx = (blurIntensity * 0.8f).toInt().coerceIn(1, 25)
                    com.primaloptima.scribe.util.BitmapBlur.blurBitmap(it, radiusPx)
                    // applyFrostedGlassLook is now called inside blurBitmap — no chain needed here.
                }
            } catch (_: Exception) { null }
        }
    }

    // Software (non-blurred) image for bgMode == "image" on API < 31.
    // Load at full screen resolution so captureOnly has accurate pixel data.
    val softwareImageModel by produceState<android.graphics.Bitmap?>(
        initialValue = null,
        key1 = bgUri,
        key2 = needsSoftwareImage
    ) {
        if (!needsSoftwareImage) {
            value = null
            return@produceState
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            try {
                val loader = coil3.ImageLoader(context)
                val req = coil3.request.ImageRequest.Builder(context)
                    .data(bgUri)
                    .size(coil3.size.Size(screenWidthPx.toInt(), screenHeightPx.toInt()))
                    .allowHardware(false)
                    .build()
                val result = loader.execute(req)
                (result as? coil3.request.SuccessResult)
                    ?.image
                    ?.let { (it as? coil3.BitmapImage)?.bitmap }
            } catch (_: Exception) { null }
        }
    }

    // ── Bar blur bitmap (API < 31 only) ──────────────────────────────────────
    // Derived from bitmaps Coil already loaded above — no extra request.
    // Provided as LocalBarBlurBitmap for bars and FABs only.
    // Dialogs and drawers are unaffected (they use LocalOneShotBitmap).
    val barBlurBitmap by produceState<Bitmap?>(
        initialValue = null,
        keys = arrayOf(bgUri, bgMode, softwareBlurredModel, softwareImageModel, frostedBlurRadius)
    ) {
        value = null
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S || !hasBgImage) {
            return@produceState  // Haze handles it natively on API 31+
        }
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            when {
                // blurred mode: softwareBlurredModel is already blurred
                bgMode == "blurred" && softwareBlurredModel != null ->
                    softwareBlurredModel

                // image mode: blur the sharp image now
                // applyFrostedGlassLook is now called inside blurBitmap — no chain needed.
                bgMode == "image" && softwareImageModel != null ->
                    com.primaloptima.scribe.util.BitmapBlur.blurBitmap(softwareImageModel!!, radius = frostedBlurRadius.toInt().coerceIn(1, 25))

                // fallback: load fresh at screen aspect ratio (rare cold-start race)
                bgUri != null -> {
                    try {
                        val loader = coil3.ImageLoader(context)
                        val req = coil3.request.ImageRequest.Builder(context)
                            .data(bgUri)
                            .size(coil3.size.Size(blurLoadW, blurLoadH))
                            .allowHardware(false)
                            .build()
                        val bmp = (loader.execute(req) as? coil3.request.SuccessResult)
                            ?.image
                            ?.let { (it as? coil3.BitmapImage)?.bitmap }
                        // applyFrostedGlassLook is now called inside blurBitmap — no chain needed.
                        bmp?.let { com.primaloptima.scribe.util.BitmapBlur.blurBitmap(it, radius = frostedBlurRadius.toInt().coerceIn(1, 25)) }
                    } catch (_: Exception) { null }
                }

                else -> null
            }
        }
    }

    var rootDimensions by remember { mutableStateOf(Pair(screenWidthPx, screenHeightPx)) }

    MaterialTheme(
        colorScheme = animatedColorScheme,
        content = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .onGloballyPositioned { coords ->
                        val w = coords.size.width.toFloat()
                        val h = coords.size.height.toFloat()
                        if (w > 0f && h > 0f && (w != rootDimensions.first || h != rootDimensions.second)) {
                            rootDimensions = Pair(w, h)
                        }
                    }
            ) {
                CompositionLocalProvider(
                    LocalHazeState provides hazeState,
                    LocalAppTheme provides resolvedTheme,
                    LocalBgAnalysisBitmap provides analysisBitmap,
                    LocalScreenSize provides Pair(screenWidthPx, screenHeightPx),
                    LocalRootGeometry provides rootDimensions,
                    LocalFrostedGlass provides resolvedTheme.frostedGlassEnabled,
                    LocalFrostedTint provides frostedTintEnabled,
                    LocalFrostedBlurRadius provides frostedBlurRadius,
                    LocalSolidSurface provides animSurface,
                    LocalBarBlurBitmap provides barBlurBitmap,
                    // Adaptive accent resolved once here — screens read LocalAccentColor.current
                    // instead of calling parseComposeColor + adaptiveAccentColor themselves.
                    LocalAccentColor provides accentIcons,
                    // One-shot bitmap starts null; screens set it via their own
                    // CompositionLocalProvider wrapping the drawer/dialog content.
                    LocalOneShotBitmap provides null
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(if (showWholeAppBg) Color.Transparent else animBg)
                    ) {
                        if (showWholeAppBg) {
                            // Pick the right image model:
                            // • API < 31 + blurred mode → pre-blurred software bitmap
                            // • API < 31 + image mode  → software bitmap (no blur, but
                            //   allowHardware=false so captureOnly can draw the view)
                            // • API 31+ or no pre-processing needed → raw URI (Haze handles blur)
                            val imageModel = when {
                                needsSoftwareBlur && softwareBlurredModel != null -> softwareBlurredModel
                                needsSoftwareImage && softwareImageModel != null -> softwareImageModel
                                else -> bgUri
                            }
                            AsyncImage(
                                model = imageModel,
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier
                                    .fillMaxSize()
                                    // hazeSource registers this image as the layer Haze blurs from.
                                    // On API 31+, hazeEffect on child composables (bars, cards, FABs)
                                    // does all the blurring — the source must be the raw unprocessed
                                    // image. Applying renderEffect here creates an isolated offscreen
                                    // GPU render node that Haze cannot see through, which is why blur
                                    // was broken on API 31+. Pre-API-31 uses a pre-blurred software
                                    // bitmap with blurEnabled = false, so it never needs renderEffect
                                    // here either.
                                    .hazeSource(state = hazeState)
                            )
                            // Only apply the colour tint overlay in "blurred" mode.
                            // In "image" mode the user wants the image as-is — no wash.
                            if (bgMode == "blurred" && bgOpacity > 0f) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(bg.copy(alpha = bgOpacity))
                                )
                            }
                        }

                        content()
                    }
                }
            }
        }
    )
}
