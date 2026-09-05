package com.primaloptima.scribe.ui.screens.themeeditor

import android.graphics.RenderEffect as AndroidRenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FormatBold
import androidx.compose.material.icons.filled.FormatItalic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.primaloptima.scribe.ui.theme.FontHelper
import com.primaloptima.scribe.ui.theme.parseComposeColor
import com.primaloptima.scribe.ui.theme.specularRimBorder
import com.primaloptima.scribe.util.ThemeManager
import com.primaloptima.scribe.util.model.ThemeColors
import kotlinx.coroutines.delay

/**
 * Anchored live preview stage demonstrating canonical resolved theme colors and typography in real-time.
 *
 * Consumes the authoritative resolved [ThemeColors] (incorporating ThemeSourcePalette,
 * Generated Defaults, and explicit ThemeColorOverrides) with zero legacy bypasses.
 */
@Composable
fun ThemePreviewStage(
    colors: ThemeColors,
    themeName: String,
    fontFamily: String,
    fontSize: Float,
    lineHeight: Float,
    textAlignment: String,
    sideMargins: Float,
    bgMode: String,
    bgUri: String?,
    bgOpacity: Float,
    blurIntensity: Float,
    modifier: Modifier = Modifier
) {
    val bgColor = parseComposeColor(colors.background, MaterialTheme.colorScheme.background)
    val surfaceColor = parseComposeColor(colors.surface, bgColor)
    val surfaceRaisedColor = parseComposeColor(colors.surfaceRaised, surfaceColor)
    val textColor = parseComposeColor(colors.text, MaterialTheme.colorScheme.onBackground)
    val mutedTextColor = parseComposeColor(colors.mutedText, textColor.copy(alpha = 0.7f))
    val accentColor = parseComposeColor(colors.accent, MaterialTheme.colorScheme.primary)
    val dialogueColor = parseComposeColor(colors.dialogueText, accentColor)
    val monologueColor = parseComposeColor(colors.monologueText, textColor)
    val headingColor = parseComposeColor(colors.headingText, accentColor)
    val highlightColor = if (colors.specialHighlight.isNotBlank()) parseComposeColor(colors.specialHighlight, accentColor) else accentColor
    val annotationColor = if (colors.annotation.isNotBlank()) parseComposeColor(colors.annotation, textColor) else accentColor
    val borderSubtleColor = parseComposeColor(colors.borderSubtle, MaterialTheme.colorScheme.outlineVariant)
    val accentMutedColor = parseComposeColor(colors.accentMuted, surfaceColor)
    val font = FontHelper.getFontFamily(fontFamily)

    val textAlign = when (textAlignment) {
        "justified" -> TextAlign.Justify
        "center" -> TextAlign.Center
        else -> TextAlign.Left
    }

    var cursorVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(500)
            cursorVisible = !cursorVisible
        }
    }
    val cursorAlpha by animateFloatAsState(
        targetValue = if (cursorVisible) 1f else 0f,
        animationSpec = tween(150),
        label = "cursorAlpha"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .height(230.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = bgColor),
        border = androidx.compose.foundation.BorderStroke(1.dp, borderSubtleColor.copy(alpha = 0.6f))
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            // Background Image if configured
            if ((bgMode == "image" || bgMode == "blurred") && !bgUri.isNullOrEmpty()) {
                AsyncImage(
                    model = bgUri,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        .then(
                            if (bgMode == "blurred" && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && blurIntensity > 0f) {
                                Modifier.graphicsLayer {
                                    val radiusPx = blurIntensity * density
                                    if (radiusPx > 0f) {
                                        renderEffect = AndroidRenderEffect
                                            .createBlurEffect(radiusPx, radiusPx, Shader.TileMode.CLAMP)
                                            .asComposeRenderEffect()
                                    }
                                }
                            } else Modifier
                        )
                )
                val overlayAlpha = if (bgMode == "blurred" && Build.VERSION.SDK_INT < Build.VERSION_CODES.S && blurIntensity > 0f) {
                    (bgOpacity + blurIntensity / 35f).coerceIn(0f, 0.90f)
                } else {
                    bgOpacity
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = overlayAlpha))
                )
            }

            // Preview Layout with Simulated App Bars and Content
            Column(modifier = Modifier.fillMaxSize()) {
                // Top App Bar Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceColor.copy(alpha = 0.94f))
                        .border(width = 0.5.dp, color = borderSubtleColor.copy(alpha = 0.5f))
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (themeName.isNotBlank()) themeName else "Untitled Theme",
                            color = textColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            maxLines = 1
                        )
                    }

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            color = accentMutedColor,
                            shape = RoundedCornerShape(4.dp)
                        ) {
                            Text(
                                text = "840 words",
                                color = accentColor,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 5.dp, vertical = 1.dp)
                            )
                        }
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = null,
                            tint = textColor,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }

                // Main Reading & Lexer Area
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = sideMargins.coerceIn(8f, 28f).dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.SpaceEvenly
                ) {
                    // Heading (styled via headingText token)
                    Text(
                        text = "Chapter I: The Starlit Archive",
                        color = headingColor,
                        fontFamily = font,
                        fontWeight = FontWeight.Bold,
                        fontSize = (fontSize * 0.80f).sp
                    )

                    // Prose snippet showcasing Dialogue, Narrative, Monologue & Caret
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.weight(1f, fill = false)) {
                            Text(
                                text = buildAnnotatedString {
                                    withStyle(SpanStyle(color = dialogueColor, fontWeight = FontWeight.SemiBold)) {
                                        append("\"We must chronicle every thought,\" ")
                                    }
                                    withStyle(SpanStyle(color = textColor)) {
                                        append("she murmured softly, ")
                                    }
                                    withStyle(SpanStyle(color = highlightColor, background = highlightColor.copy(alpha = 0.22f), fontWeight = FontWeight.Medium)) {
                                        append("starlit")
                                    }
                                    withStyle(SpanStyle(color = monologueColor, fontStyle = androidx.compose.ui.text.font.FontStyle.Italic)) {
                                        append(" (knowing tomorrow would rewrite it all).")
                                    }
                                },
                                fontFamily = font,
                                fontSize = (fontSize * 0.62f).sp,
                                lineHeight = (fontSize * 0.62f * lineHeight).sp,
                                textAlign = textAlign
                            )
                        }
                        Spacer(modifier = Modifier.width(2.dp))
                        Box(
                            modifier = Modifier
                                .width(2.dp)
                                .height(13.dp)
                                .graphicsLayer { alpha = cursorAlpha }
                                .background(accentColor)
                        )
                    }

                    // Floating Workbench Card Snippet (L3 SurfaceRaised)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(surfaceRaisedColor.copy(alpha = 0.95f))
                            .specularRimBorder(
                                shape = RoundedCornerShape(8.dp),
                                isDark = ThemeManager.isDarkColor(colors.background),
                                strokeWidth = 1.dp
                            )
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Character Arc & Scene Tone",
                            color = textColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(annotationColor.copy(alpha = 0.15f))
                                    .padding(horizontal = 5.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Note",
                                    color = annotationColor,
                                    fontSize = 8.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(accentMutedColor)
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = "Active",
                                    color = accentColor,
                                    fontSize = 9.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }

                // Bottom Toolbar Strip
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(surfaceColor.copy(alpha = 0.94f))
                        .border(width = 0.5.dp, color = borderSubtleColor.copy(alpha = 0.5f))
                        .padding(horizontal = 16.dp, vertical = 5.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.FormatBold, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                    Icon(Icons.Default.FormatItalic, contentDescription = null, tint = textColor, modifier = Modifier.size(13.dp))
                    Icon(Icons.AutoMirrored.Filled.FormatListBulleted, contentDescription = null, tint = textColor, modifier = Modifier.size(13.dp))
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = accentColor, modifier = Modifier.size(13.dp))
                }
            }
        }
    }
}

