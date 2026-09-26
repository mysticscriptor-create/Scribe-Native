package com.primaloptima.scribe.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.ScribeTheme
import java.util.Locale
import kotlin.math.round
import kotlin.math.roundToInt

/**
 * Reusable editorial slider setting component for typography and style controls.
 *
 * Visual hierarchy:
 * 1. Label + current Value
 * 2. Precision controls: −  value  +
 * 3. Thin editorial Slider (2.5dp track, animated expanding thumb)
 * 4. RangeLabels (min and max values)
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScribeSettingSlider(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    step: Float = 1f,
    unit: String = "",
    decimalPlaces: Int = if (step < 1f) 2 else 0,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val clampedValue = value.coerceIn(valueRange.start, valueRange.endInclusive)
    val canDecrement = enabled && (clampedValue > valueRange.start + 0.0001f)
    val canIncrement = enabled && (clampedValue < valueRange.endInclusive - 0.0001f)

    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val isDragged by interactionSource.collectIsDraggedAsState()
    val isInteracting = isPressed || isDragged

    val thumbSize by animateDpAsState(
        targetValue = if (isInteracting) 19.dp else 13.dp,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "scribeSliderThumbSize"
    )

    val thumbElevation by animateDpAsState(
        targetValue = if (isInteracting) 5.dp else 1.5.dp,
        animationSpec = tween(durationMillis = 150),
        label = "scribeSliderThumbElevation"
    )

    fun roundToClean(v: Float): Float {
        val stepped = (round((v - valueRange.start) / step) * step + valueRange.start)
            .coerceIn(valueRange.start, valueRange.endInclusive)
        return if (decimalPlaces > 0) {
            val mult = if (decimalPlaces == 1) 10f else 100f
            round(stepped * mult) / mult
        } else {
            round(stepped)
        }
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        // 1. Label + current Value
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = formatTopValue(clampedValue, unit, decimalPlaces),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary
            )
        }

        // 2. Precision controls: −  value  +
        Spacer(modifier = Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Accessible Touch Target for Decrement (−)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = canDecrement,
                        role = Role.Button,
                        onClick = {
                            val next = roundToClean(clampedValue - step)
                            onValueChange(next)
                            onValueChangeFinished?.invoke()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (canDecrement) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (canDecrement) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Remove,
                            contentDescription = "Decrease $label",
                            tint = if (canDecrement) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            // Value text centered between − and +
            Text(
                text = formatCenterValue(clampedValue, decimalPlaces),
                fontSize = 14.5.sp,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 14.dp)
                    .widthIn(min = 40.dp)
            )

            // Accessible Touch Target for Increment (+)
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .clickable(
                        enabled = canIncrement,
                        role = Role.Button,
                        onClick = {
                            val next = roundToClean(clampedValue + step)
                            onValueChange(next)
                            onValueChangeFinished?.invoke()
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Surface(
                    shape = CircleShape,
                    color = if (canIncrement) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                            else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.20f),
                    border = BorderStroke(
                        width = 0.8.dp,
                        color = if (canIncrement) MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.40f)
                                else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f)
                    ),
                    modifier = Modifier.size(24.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = "Increase $label",
                            tint = if (canIncrement) MaterialTheme.colorScheme.onSurface
                                   else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.25f),
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }
        }

        // 3. Thin editorial Slider (2.5dp track, animated tactile thumb)
        Slider(
            value = clampedValue,
            onValueChange = { raw ->
                val clean = roundToClean(raw)
                if (clean != clampedValue) {
                    onValueChange(clean)
                }
            },
            onValueChangeFinished = onValueChangeFinished,
            valueRange = valueRange,
            interactionSource = interactionSource,
            enabled = enabled,
            thumb = {
                Box(
                    modifier = Modifier
                        .size(thumbSize)
                        .shadow(
                            elevation = thumbElevation,
                            shape = CircleShape,
                            spotColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                            ambientColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        )
                        .background(ScribeTheme.colors.interaction.primary, CircleShape)
                )
            },
            track = { sliderState ->
                SliderDefaults.Track(
                    sliderState = sliderState,
                    modifier = Modifier.height(2.5.dp),
                    colors = SliderDefaults.colors(
                        activeTrackColor = ScribeTheme.colors.interaction.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.30f)
                    )
                )
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(30.dp)
                .semantics {
                    this.contentDescription = contentDescription
                    this.stateDescription = formatTopValue(clampedValue, unit, decimalPlaces)
                }
        )

        // 4. RangeLabels
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = formatRangeLabel(valueRange.start, unit, decimalPlaces),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = ScribeTheme.colors.content.secondary.copy(alpha = 0.70f)
            )
            Text(
                text = formatRangeLabel(valueRange.endInclusive, unit, decimalPlaces),
                fontSize = 11.sp,
                fontWeight = FontWeight.Normal,
                color = ScribeTheme.colors.content.secondary.copy(alpha = 0.70f)
            )
        }
    }
}

/**
 * Convenience overload for Int-based settings.
 */
@Composable
fun ScribeSettingSlider(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
    valueRange: ClosedRange<Int>,
    step: Int = 1,
    unit: String = "",
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    contentDescription: String = label,
    onValueChangeFinished: (() -> Unit)? = null
) {
    ScribeSettingSlider(
        label = label,
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt()) },
        valueRange = valueRange.start.toFloat()..valueRange.endInclusive.toFloat(),
        step = step.toFloat(),
        unit = unit,
        decimalPlaces = 0,
        modifier = modifier,
        enabled = enabled,
        contentDescription = contentDescription,
        onValueChangeFinished = onValueChangeFinished
    )
}

private fun formatTopValue(value: Float, unit: String, decimalPlaces: Int): String {
    val numStr = if (decimalPlaces > 0) {
        String.format(Locale.US, "%.${decimalPlaces}f", value)
    } else {
        value.roundToInt().toString()
    }
    return if (unit.isNotBlank()) "$numStr $unit" else numStr
}

private fun formatCenterValue(value: Float, decimalPlaces: Int): String {
    return if (decimalPlaces > 0) {
        String.format(Locale.US, "%.${decimalPlaces}f", value)
    } else {
        value.roundToInt().toString()
    }
}

private fun formatRangeLabel(value: Float, unit: String, decimalPlaces: Int): String {
    val numStr = if (decimalPlaces > 0) {
        String.format(Locale.US, "%.1f", value)
    } else {
        value.roundToInt().toString()
    }
    return if (unit.isNotBlank()) "$numStr $unit" else numStr
}
