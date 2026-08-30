package com.primaloptima.scribe.ui.screens

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.primaloptima.scribe.ScribeApp
import com.primaloptima.scribe.ui.components.ScribeCard
import com.primaloptima.scribe.ui.components.ScribeCardTokens
import com.primaloptima.scribe.ui.components.ScribeContentCard
import com.primaloptima.scribe.ui.components.ScribeSectionLabel
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.FrostedDialog
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeTheme
import com.primaloptima.scribe.ui.theme.frostedChip
import com.primaloptima.scribe.viewmodel.SettingsViewModel
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onOpenThemes: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as ScribeApp
    val themeManager = remember { app.themeManager }
    val vm: SettingsViewModel = viewModel()
    val hazeState = LocalHazeState.current
    val accentColor = ScribeTheme.colors.interaction.primary
    val subtleText = ScribeTheme.colors.content.secondary

    val showWordCount by vm.showWordCount.collectAsStateWithLifecycle()
    val typewriterMode by vm.typewriterMode.collectAsStateWithLifecycle()
    val lineSpacing by vm.lineSpacing.collectAsStateWithLifecycle()
    val fontSize by vm.editorFontSize.collectAsStateWithLifecycle()
    val dailyGoal by vm.dailyGoal.collectAsStateWithLifecycle()
    val homeStartPage by vm.homeStartPage.collectAsStateWithLifecycle()
    val todayWords by vm.todayWords.collectAsStateWithLifecycle()
    val currentStreak by vm.currentStreak.collectAsStateWithLifecycle()
    val longestStreak by vm.longestStreak.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { vm.loadStats() }

    // History settings
    val autoHistoryEnabled by vm.autoHistoryEnabled.collectAsStateWithLifecycle()
    val manualCheckpointsEnabled by vm.manualCheckpointsEnabled.collectAsStateWithLifecycle()
    val autoHistorySlots by vm.autoHistorySlots.collectAsStateWithLifecycle()
    val manualCheckpointSlots by vm.manualCheckpointSlots.collectAsStateWithLifecycle()
    val autoHistoryMinWords by vm.autoHistoryMinWords.collectAsStateWithLifecycle()

    var showGoalDialog by remember { mutableStateOf(false) }

    val subtleColor = if (subtleText != Color.Unspecified) subtleText else MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = "Settings",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Home Section
            ScribeSectionLabel(text = "Home")
            ScribeContentCard(
                title = "Startup Destination",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Start on", fontWeight = FontWeight.Medium)
                    Text(
                        "Which screen opens when you launch Scribe.",
                        fontSize = 12.sp,
                        color = subtleColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = homeStartPage == "books",
                            onClick = { vm.setHomeStartPage("books") },
                            label = { Text("Books") },
                            leadingIcon = if (homeStartPage == "books") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = homeStartPage == "books")
                        )
                        FilterChip(
                            selected = homeStartPage == "dashboard",
                            onClick = { vm.setHomeStartPage("dashboard") },
                            label = { Text("Dashboard") },
                            leadingIcon = if (homeStartPage == "dashboard") {
                                { Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            } else null,
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.frostedChip(hazeState, shape = RoundedCornerShape(12.dp), isSelected = homeStartPage == "dashboard")
                        )
                    }
                }
            }

            // Appearance Section
            ScribeSectionLabel(text = "Appearance")
            ScribeCard(
                modifier = Modifier
                    .fillMaxWidth(),
                cornerRadius = ScribeCardTokens.RadiusLarge,
                onClick = { onOpenThemes() }
            ) {
                Row(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = accentColor)
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Active Theme", fontWeight = FontWeight.Bold)
                        Text(themeManager.activeTheme().name, fontSize = 12.sp, color = subtleColor)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = subtleColor)
                }
            }

            // Editor Section
            ScribeSectionLabel(text = "Editor")
            ScribeContentCard(
                title = "Editor Experience",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Show Word Count FAB", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = showWordCount,
                            onCheckedChange = { vm.setShowWordCount(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Typewriter Mode (Center Active Line)", fontWeight = FontWeight.Medium)
                        Switch(
                            checked = typewriterMode,
                            onCheckedChange = { vm.setTypewriterMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                    Column {
                        Text("Editor Font Size: $fontSize sp", fontWeight = FontWeight.Medium)
                        Slider(
                            value = fontSize.toFloat(),
                            onValueChange = { vm.setEditorFontSize(it.toInt()) },
                            valueRange = 12f..28f,
                            colors = SliderDefaults.colors(
                                thumbColor = accentColor,
                                activeTrackColor = accentColor
                            )
                        )
                    }
                }
            }

            // Version History Section
            ScribeSectionLabel(text = "Version History")
            ScribeContentCard(
                title = "Snapshots & Checkpoints",
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-save History", fontWeight = FontWeight.Medium)
                            Text("Saves a snapshot when you leave a note", fontSize = 12.sp, color = subtleColor)
                        }
                        Switch(
                            checked = autoHistoryEnabled,
                            onCheckedChange = { vm.setAutoHistoryEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                    if (autoHistoryEnabled) {
                        Column {
                            Text("Auto-save slots: $autoHistorySlots", fontWeight = FontWeight.Medium)
                            Text("How many auto-saves to keep per note", fontSize = 12.sp, color = subtleColor)
                            Slider(
                                value = autoHistorySlots.toFloat(),
                                onValueChange = { vm.setAutoHistorySlots(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                        Column {
                            Text("Min words to trigger: $autoHistoryMinWords words", fontWeight = FontWeight.Medium)
                            Text("Net word change needed to auto-save", fontSize = 12.sp, color = subtleColor)
                            Slider(
                                value = autoHistoryMinWords.toFloat(),
                                onValueChange = { vm.setAutoHistoryMinWords(it.toInt()) },
                                valueRange = 1f..100f,
                                steps = 98,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                    }
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.10f))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Manual Checkpoints", fontWeight = FontWeight.Medium)
                            Text("Saved when you tap the bookmark button", fontSize = 12.sp, color = subtleColor)
                        }
                        Switch(
                            checked = manualCheckpointsEnabled,
                            onCheckedChange = { vm.setManualCheckpointsEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = accentColor
                            )
                        )
                    }
                    if (manualCheckpointsEnabled) {
                        Column {
                            Text("Checkpoint slots: $manualCheckpointSlots", fontWeight = FontWeight.Medium)
                            Text("How many checkpoints to keep per note", fontSize = 12.sp, color = subtleColor)
                            Slider(
                                value = manualCheckpointSlots.toFloat(),
                                onValueChange = { vm.setManualCheckpointSlots(it.toInt()) },
                                valueRange = 1f..30f,
                                steps = 28,
                                colors = SliderDefaults.colors(
                                    thumbColor = accentColor,
                                    activeTrackColor = accentColor
                                )
                            )
                        }
                    }
                }
            }

            // Daily Goals Section
            ScribeSectionLabel(text = "Goals & Progress")
            ScribeContentCard(
                title = "Daily Target",
                modifier = Modifier
                    .fillMaxWidth(),
                headerTrailing = {
                    Text(
                        "$dailyGoal words",
                        fontWeight = FontWeight.Bold,
                        color = accentColor,
                        fontSize = 13.sp,
                        modifier = Modifier.clickable { showGoalDialog = true }
                    )
                },
                onClick = { showGoalDialog = true }
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Words today: $todayWords", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Current streak: $currentStreak days", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurface)
                    Text("Longest streak: $longestStreak days", fontSize = 13.sp, color = subtleColor)
                }
            }
        }
    }

    if (showGoalDialog) {
        var goalInput by remember { mutableStateOf("$dailyGoal") }
        FrostedDialog(
            onDismissRequest = { showGoalDialog = false },
            title = { Text("Set Daily Word Goal") },
            text = {
                OutlinedTextField(
                    value = goalInput,
                    onValueChange = { goalInput = it },
                    label = { Text("Target Words") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val num = goalInput.toIntOrNull()
                        if (num != null && num >= 50) {
                            vm.setDailyGoal(num)
                            showGoalDialog = false
                            Toast.makeText(context, "Daily goal updated", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) { Text("Save", color = accentColor) }
            },
            dismissButton = {
                TextButton(onClick = { showGoalDialog = false }) { Text("Cancel") }
            }
        )
    }
}
