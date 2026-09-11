package com.primaloptima.scribe.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.BatteryFull
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.StickyNote2
import androidx.compose.material.icons.filled.ViewColumn
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.screens.DashboardContent
import com.primaloptima.scribe.ui.screens.DashboardPreviewData
import com.primaloptima.scribe.ui.screens.EditorContent
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeComposeTheme
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.util.model.AppTheme
import com.primaloptima.scribe.util.model.ThemeColors
import dev.chrisbanes.haze.HazeState

/**
 * Preview pane presentation mode selector.
 */
enum class PreviewPaneMode(val label: String) {
    SPLIT("Side-by-Side"),
    DASHBOARD("Dashboard"),
    EDITOR("Editor")
}

/**
 * ScribeThemeLivePreview
 *
 * Canonical live, real-time theme preview system that renders Scribe's actual production
 * UI composables (Dashboard + Editor) within simulated device frames.
 *
 * Any future UI evolutions made in [DashboardScreen] or [MainEditorScreen] automatically
 * propagate directly to this preview without duplicated or fake mock layouts.
 */
@Composable
fun ScribeThemeLivePreview(
    theme: AppTheme? = null,
    colors: ThemeColors? = null,
    themeName: String = "Preview Theme",
    fontFamily: String = "sans",
    fontSize: Float = 17f,
    lineHeight: Float = 1.7f,
    textAlignment: String = "left",
    sideMargins: Float = 24f,
    bgMode: String = "color",
    bgUri: String? = null,
    bgOpacity: Float = 0.35f,
    blurIntensity: Float = 15f,
    frostedGlassEnabled: Boolean = true,
    frostedTintEnabled: Boolean = true,
    frostedBlurRadius: Float = 15f,
    isDark: Boolean = false,
    selectedOrnamentId: String = "classic_flourish",
    modifier: Modifier = Modifier
) {
    val effectiveTheme = remember(
        theme, colors, themeName, fontFamily, fontSize, lineHeight,
        textAlignment, sideMargins, bgMode, bgUri, bgOpacity, blurIntensity,
        frostedGlassEnabled, frostedTintEnabled, frostedBlurRadius, isDark
    ) {
        if (theme != null) {
            theme
        } else {
            val resolvedColors = colors ?: ThemeColors()
            AppTheme(
                id = "live_preview_theme",
                name = themeName,
                isDark = isDark,
                colors = resolvedColors,
                fontFamily = fontFamily,
                fontSize = fontSize.toInt(),
                lineHeight = lineHeight,
                textAlignment = textAlignment,
                paddingHorizontal = sideMargins.toInt(),
                bgMode = bgMode,
                backgroundImageUri = bgUri,
                backgroundImageOpacity = bgOpacity,
                blurIntensity = blurIntensity,
                frostedGlassEnabled = frostedGlassEnabled,
                frostedTintEnabled = frostedTintEnabled,
                frostedBlurRadius = frostedBlurRadius
            )
        }
    }

    var selectedPaneMode by remember { mutableStateOf(PreviewPaneMode.SPLIT) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(10.dp)
    ) {
        // ── Header Bar: Mode Switcher ─────────────────────────────────────────
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
                Text(
                    text = "LIVE THEME PREVIEW",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 1.5.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )
            }

            // Mode Selector Pills
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .border(
                        width = 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f),
                        shape = CircleShape
                    )
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                PreviewPaneMode.values().forEach { mode ->
                    val isSelected = selectedPaneMode == mode
                    val pillBg by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        label = "pill_bg"
                    )
                    val pillTextColor by animateColorAsState(
                        targetValue = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        label = "pill_text"
                    )

                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(pillBg)
                            .clickable { selectedPaneMode = mode }
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = mode.label,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = pillTextColor
                        )
                    }
                }
            }
        }

        // ── Preview Body Wrapped in ScribeComposeTheme ────────────────────────
        ScribeComposeTheme(appTheme = effectiveTheme) {
            val previewHazeState = remember { HazeState() }
            CompositionLocalProvider(LocalHazeState provides previewHazeState) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = true)
                ) {
                    AnimatedContent(
                        targetState = selectedPaneMode,
                        transitionSpec = { fadeIn() togetherWith fadeOut() },
                        label = "pane_mode_content"
                    ) { mode ->
                        when (mode) {
                            PreviewPaneMode.SPLIT -> {
                                Row(
                                    modifier = Modifier.fillMaxSize(),
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        DeviceMockupFrame(
                                            title = "Dashboard",
                                            hazeState = previewHazeState
                                        ) {
                                            DashboardPreviewScreen()
                                        }
                                    }

                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .fillMaxHeight()
                                    ) {
                                        DeviceMockupFrame(
                                            title = "Editor",
                                            hazeState = previewHazeState
                                        ) {
                                            EditorPreviewScreen(selectedOrnamentId = selectedOrnamentId)
                                        }
                                    }
                                }
                            }

                            PreviewPaneMode.DASHBOARD -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .widthIn(max = 440.dp)
                                            .fillMaxWidth()
                                    ) {
                                        DeviceMockupFrame(
                                            title = "Dashboard",
                                            hazeState = previewHazeState
                                        ) {
                                            DashboardPreviewScreen()
                                        }
                                    }
                                }
                            }

                            PreviewPaneMode.EDITOR -> {
                                Box(
                                    modifier = Modifier.fillMaxSize(),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxHeight()
                                            .widthIn(max = 440.dp)
                                            .fillMaxWidth()
                                    ) {
                                        DeviceMockupFrame(
                                            title = "Editor",
                                            hazeState = previewHazeState
                                        ) {
                                            EditorPreviewScreen(selectedOrnamentId = selectedOrnamentId)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Simulated Android phone frame with status bar, screen content, and theme border.
 */
@Composable
private fun DeviceMockupFrame(
    title: String,
    hazeState: HazeState,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val frameShape = RoundedCornerShape(14.dp)

    Column(
        modifier = modifier
            .fillMaxSize()
            .clip(frameShape)
            .background(ScribeTheme.colors.surfaces.surfaceLowest)
            .border(
                width = 1.dp,
                color = ScribeTheme.colors.borders.subtle,
                shape = frameShape
            )
    ) {
        // Status Bar Simulator
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(18.dp)
                .background(ScribeTheme.colors.surfaces.surfaceElevated.copy(alpha = 0.5f))
                .padding(horizontal = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "9:41",
                fontSize = 9.sp,
                fontWeight = FontWeight.SemiBold,
                color = ScribeTheme.colors.content.tertiary
            )

            // Center notch indicator
            Box(
                modifier = Modifier
                    .width(36.dp)
                    .height(3.dp)
                    .clip(CircleShape)
                    .background(ScribeTheme.colors.borders.subtle)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Wifi,
                    contentDescription = null,
                    tint = ScribeTheme.colors.content.tertiary,
                    modifier = Modifier.size(10.dp)
                )
                Icon(
                    imageVector = Icons.Default.BatteryFull,
                    contentDescription = null,
                    tint = ScribeTheme.colors.content.tertiary,
                    modifier = Modifier.size(10.dp)
                )
            }
        }

        // Screen Body
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            content()
        }
    }
}

/**
 * Real Scribe Dashboard preview composition.
 * Reuses ScribeTopBar, DashboardContent, and ScribeNavBar.
 */
@Composable
private fun DashboardPreviewScreen() {
    var selectedNavTab by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize()) {
        ScribeTopBar(
            title = "Scribe",
            navigationIcon = Icons.Default.Menu,
            onNavigationClick = {},
            actions = listOf(
                com.primaloptima.scribe.ui.components.ScribeBarAction(Icons.Default.Search, "Search") {}
            )
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            DashboardContent(
                ongoingBook = DashboardPreviewData.sampleBook,
                chapters = DashboardPreviewData.sampleChapters,
                totalProjectWords = 34_500,
                totalTarget = 80_000,
                currentStreak = 7,
                todayWords = 1_240,
                dailyGoal = 1_000,
                weekData = DashboardPreviewData.sampleWeekData,
                accentColor = ScribeTheme.colors.interaction.primary
            )
        }

        ScribeNavBar(
            items = listOf(
                ScribeNavItem(Icons.Default.Dashboard, "Dashboard"),
                ScribeNavItem(Icons.Default.Book, "Books"),
                ScribeNavItem(Icons.Default.StickyNote2, "Notes"),
                ScribeNavItem(Icons.Default.BarChart, "Stats")
            ),
            selectedIndex = selectedNavTab,
            onTabSelected = { selectedNavTab = it }
        )
    }
}

/**
 * Real Scribe Editor preview composition.
 * Reuses EditorContent (ScribeEditorTopBar, ManuscriptHeader, EditorProsePreviewBody,
 * WordCountPill, and EditorShortcutBar).
 */
@Composable
private fun EditorPreviewScreen(
    selectedOrnamentId: String
) {
    EditorContent(
        primaryTitle = "CHAPTER VII",
        secondaryTitle = "The Obsidian Gate",
        selectedOrnamentId = selectedOrnamentId,
        wordCount = 1420,
        charCount = 7850,
        deltaText = "+340 today",
        isPositiveDelta = true,
        showTopBar = true,
        showWordCountPill = true,
        showShortcutBar = true
    )
}
