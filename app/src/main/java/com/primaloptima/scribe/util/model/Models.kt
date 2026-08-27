package com.primaloptima.scribe.util.model

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import androidx.compose.ui.graphics.Color
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

// ── Shortcut ─────────────────────────────────────────────────────────────────

@Immutable
@Serializable
data class ShortcutAction(
    val id: String,
    val label: String,
    /** "insert" | "wrap" | "pair" */
    val kind: String,
    val payload: String,
    /** Non-null for wrap/pair */
    val closing: String? = null
)

// ── Pinned item ───────────────────────────────────────────────────────────────

@Immutable
@Serializable
data class PinnedItem(
    /** "top" | "bottom" */
    val slot: String,
    val noteId: String
)

// ── Floating window ───────────────────────────────────────────────────────────

// @Stable (not @Immutable): FloatingWindow has var fields (x, y, width, height,
// zOrder, collapsed) mutated during drag/resize. @Stable is the correct promise:
// "mutations will be reflected through Compose state channels."
@Stable
@Serializable
data class FloatingWindow(
    val id: String,
    val noteId: String,
    var x: Float,
    var y: Float,
    var width: Int,
    var height: Int,
    var zOrder: Int,
    var collapsed: Boolean = false
)

// ── External root ─────────────────────────────────────────────────────────────

@Immutable
@Serializable
data class ExternalRoot(
    val uri: String,
    val name: String
)

// ── Outline entry ─────────────────────────────────────────────────────────────

@Immutable
data class OutlineEntry(
    val level: Int,   // 1–4
    val text: String,
    val lineIndex: Int,
    val preview: String? = null
)

// ── App theme ─────────────────────────────────────────────────────────────────

@Immutable
@Serializable
data class ThemeColors(
    val background: String,
    val surface: String,
    val text: String,
    val mutedText: String,
    val accent: String,
    val border: String,
    val selection: String,
    val toolbar: String,
    val toolbarText: String
)

// @Stable (not @Immutable): AppTheme instances are replaced wholesale via copy()
// in ThemeEditScreen — no field is mutated in-place, so the contract holds.
// We use @Stable rather than @Immutable because AppTheme instances are reconstructed
// frequently during editing; @Stable lets Compose diff by equals() and skip
// composables that receive an unchanged theme reference.
@Stable
@Serializable
data class AppTheme(
    val id: String,
    val name: String,
    val isDark: Boolean,
    val builtIn: Boolean,
    val colors: ThemeColors,
    /** Font family key matching Google Fonts or system fonts */
    val fontFamily: String,
    val fontSize: Int,
    val lineHeight: Float,
    val letterSpacing: Float,
    val paragraphSpacing: Int,
    val paddingHorizontal: Int,
    val paddingVertical: Int,
    val maxWidth: Int,
    val backgroundImageUri: String? = null,
    /** The original full-resolution image the user picked, before cropping.
     *  Preserved so the user can re-crop later without quality loss, and so
     *  the crop can be re-run at a different screen ratio if needed. */
    val backgroundImageOriginalUri: String? = null,
    val backgroundImageOpacity: Float? = 0.35f,
    val bgMode: String = "color", // "color" | "image" | "blurred"
    val blurIntensity: Float = 15f,
    val frostedGlassEnabled: Boolean = true,
    /** When false, frosted glass shows pure blur with no surface tint overlay. */
    val frostedTintEnabled: Boolean = true,
    /** Blur radius (dp) for bars, drawers, dialogs in frosted glass mode.
     *  API 31+: live via Haze. Pre-API-31: applied at theme-load time (one-shot bitmap). */
    val frostedBlurRadius: Float = 15f,
    val textAlignment: String = "left", // "left" | "justified" | "center"
    val themeScope: String = "whole_app", // "editor_only" | "whole_app"
    val emoji: String? = null,
    /**
     * Average linear luminance of the background image, computed once at crop-confirm
     * time and stored with the theme. Range [0.0, 1.0]: 0 = black, 1 = white.
     * -1f means not yet computed (no image set, or pre-existing theme without this field).
     * Gson will deserialize old JSON without this field and default it to -1f cleanly.
     *
     * This single float drives text colour, frosted-panel content colour, and
     * accent-colour adaptation at runtime with zero bitmap processing on the device.
     */
    val savedBgLuminance: Float = -1f
)

// ── SAF scan result ───────────────────────────────────────────────────────────

@Immutable
data class SafFile(
    val uri: String,
    val name: String,
    val ext: String,
    val folderPath: String
)

@Immutable
data class SafFolder(
    val uri: String,
    val relativePath: String
)

@Immutable
data class SafCover(
    val uri: String,
    val folderPath: String,
    val ext: String
)

// Note: SafScanResult contains List<T> fields. @Immutable is correct here because
// these lists are populated once during a SAF scan and never mutated afterward.
// The List<T> stability issue for composable parameters is handled by stability_config.conf.
@Immutable
data class SafScanResult(
    val files: List<SafFile> = emptyList(),
    val folders: List<SafFolder> = emptyList(),
    val covers: List<SafCover> = emptyList()
)

// ── History snapshot (SharedPreferences-backed legacy history) ────────────────

@Immutable
data class HistorySnapshot(
    val content: String,
    val savedAt: Long
)

// ── Workbench / Right Panel ───────────────────────────────────────────────────

@Immutable
@Serializable
sealed class PaneScope {
    @SerialName("global")
    @Serializable data object Global : PaneScope()

    @SerialName("book")
    @Serializable data class Book(val id: String, val title: String) : PaneScope()

    @SerialName("folder")
    @Serializable data class Folder(val id: String, val title: String) : PaneScope()

    @SerialName("file")
    @Serializable data class File(val id: String, val title: String) : PaneScope()
}

val PaneScope.specificity: Int get() = when (this) {
    is PaneScope.Global -> 0
    is PaneScope.Book   -> 1
    is PaneScope.Folder -> 2
    is PaneScope.File   -> 3
}

@Serializable
enum class PaneAccentColor { NONE, SLATE, BLUE, INDIGO, TEAL, GREEN, AMBER, ROSE, PLUM }

fun PaneAccentColor.toComposeColor(isDark: Boolean): androidx.compose.ui.graphics.Color = when (this) {
    PaneAccentColor.NONE  -> androidx.compose.ui.graphics.Color.Unspecified
    PaneAccentColor.SLATE -> if (isDark) androidx.compose.ui.graphics.Color(0xFF94A3B8) else androidx.compose.ui.graphics.Color(0xFF64748B)
    PaneAccentColor.BLUE  -> if (isDark) androidx.compose.ui.graphics.Color(0xFF60A5FA) else androidx.compose.ui.graphics.Color(0xFF3B82F6)
    PaneAccentColor.INDIGO-> if (isDark) androidx.compose.ui.graphics.Color(0xFF818CF8) else androidx.compose.ui.graphics.Color(0xFF6366F1)
    PaneAccentColor.TEAL  -> if (isDark) androidx.compose.ui.graphics.Color(0xFF2DD4BF) else androidx.compose.ui.graphics.Color(0xFF14B8A6)
    PaneAccentColor.GREEN -> if (isDark) androidx.compose.ui.graphics.Color(0xFF4ADE80) else androidx.compose.ui.graphics.Color(0xFF22C55E)
    PaneAccentColor.AMBER -> if (isDark) androidx.compose.ui.graphics.Color(0xFFFBBF24) else androidx.compose.ui.graphics.Color(0xFFF59E0B)
    PaneAccentColor.ROSE  -> if (isDark) androidx.compose.ui.graphics.Color(0xFFFB7185) else androidx.compose.ui.graphics.Color(0xFFF43F5E)
    PaneAccentColor.PLUM  -> if (isDark) androidx.compose.ui.graphics.Color(0xFFC084FC) else androidx.compose.ui.graphics.Color(0xFFA855F7)
}

@Serializable
enum class MinimizedBy { USER, SYSTEM }

@Serializable
enum class OutOfScopeDefault { SESSION_ONLY, ALWAYS_ADD, ALWAYS_ASK }

@Immutable
@Serializable
data class PaneConfig(
    val id              : String,
    val label           : String = "Section",
    val accentColor     : PaneAccentColor = PaneAccentColor.NONE,
    val primaryScope    : PaneScope = PaneScope.Global,
    val secondaryScopes : List<PaneScope> = emptyList(),
    val pinnedNoteIds   : List<String> = emptyList(),
    val currentIndex    : Int = 0,
    val isMinimized     : Boolean = false,
    val minimizedBy     : MinimizedBy? = null,
    val showFooterPills : Boolean = true,
    val showLabel       : Boolean = true,
    val splitFraction   : Float = 0.5f,
    val systemMinimizedNoticeShown : Boolean = false
)

@Immutable
@Serializable
data class WorkbenchState(
    val panes    : List<PaneConfig> = listOf(PaneConfig(id = "pane_default")),
    val maxSlots : Int = 2,
    val outOfScopeDefault : OutOfScopeDefault = OutOfScopeDefault.ALWAYS_ASK,
    val splitHorizontal   : Boolean = false,
    val tabBarAtBottom    : Boolean = false
)

