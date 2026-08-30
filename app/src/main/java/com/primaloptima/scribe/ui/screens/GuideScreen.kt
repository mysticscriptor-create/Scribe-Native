package com.primaloptima.scribe.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.components.ScribeContentCard
import com.primaloptima.scribe.ui.components.ScribeTopBar
import com.primaloptima.scribe.ui.theme.LocalHazeState
import com.primaloptima.scribe.ui.theme.ScribeTheme
import dev.chrisbanes.haze.hazeSource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideScreen(
    onBack: () -> Unit
) {
    val hazeState = LocalHazeState.current

    Scaffold(
        containerColor = Color.Transparent,
        contentWindowInsets = WindowInsets.systemBars,
        topBar = {
            ScribeTopBar(
                title             = "User Guide",
                navigationIcon    = Icons.AutoMirrored.Filled.ArrowBack,
                onNavigationClick = onBack
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .then(if (hazeState != null) Modifier.hazeSource(hazeState) else Modifier)
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            GuideSection("Smart Pairs", "Type \", (, [, {, or ' and Scribe inserts the matching close character. The cursor lands between them. Press Enter or Space to move smoothly.")
            GuideSection("Shortcut Bar", "The formatting toolbar above the keyboard allows one-tap Markdown formatting: headers, bold, italics, quotes, lists, undo, and redo.")
            GuideSection("File Vault & Folders", "Organize notes into custom books and sub-folders. Connect an external phone folder using Storage Access Framework to edit local markdown files.")
            GuideSection("Outline Navigation", "The right drawer extracts all #, ##, ### headers from your active note so you can jump to any chapter or section instantly.")
            GuideSection("Themes & Customization", "Switch between built-in themes (Paper, Midnight, Sepia, Typewriter, Focus) or customize your own colors, fonts, line height, and editor padding.")
            GuideSection("Zen Mode", "Double-tap the editor or tap the Zen button to hide all chrome and write distraction-free.")
            GuideSection("Version History", "Scribe automatically saves periodic snapshots of your work so you can preview and restore previous versions at any time.")
            GuideSection("World Building Sheets", "Track characters, locations, and lore elements side-by-side with your writing.")
        }
    }
}

@Composable
private fun GuideSection(title: String, description: String) {
    val subtle = ScribeTheme.colors.content.tertiary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val textColor = if (subtle != Color.Unspecified) subtle else onSurface.copy(alpha = 0.85f)

    ScribeContentCard(
        title = title,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = description,
                fontSize = 14.sp,
                lineHeight = 21.sp,
                color = textColor
            )
        }
    }
}
