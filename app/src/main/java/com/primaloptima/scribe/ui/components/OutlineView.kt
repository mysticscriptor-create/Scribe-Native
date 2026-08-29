package com.primaloptima.scribe.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.util.model.OutlineEntry
import io.github.rosemoe.sora.widget.CodeEditor

@Composable
fun OutlineView(
    outline      : List<OutlineEntry>,
    soraEditorRef: CodeEditor?,
    modifier     : Modifier = Modifier
) {
    if (outline.isEmpty()) {
        Box(
            modifier         = modifier.fillMaxSize().padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Icon(
                    imageVector        = Icons.Default.FormatListNumbered,
                    contentDescription = null,
                    modifier           = Modifier.size(40.dp),
                    tint               = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text      = "No headings found",
                    fontSize  = 13.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text      = "Add # Heading 1 or ## Heading 2 in your text",
                    fontSize  = 11.sp,
                    color     = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        LazyColumn(
            modifier            = modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items(outline) { entry ->
                val indent = ((entry.level - 1) * 14).dp
                Surface(
                    onClick = {
                        soraEditorRef?.let { editor ->
                            val line = entry.lineIndex.coerceIn(0, (editor.lineCount - 1).coerceAtLeast(0))
                            editor.setSelection(line, 0)
                        }
                    },
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = indent, end = 8.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(3.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                            modifier = Modifier.padding(end = 8.dp)
                        ) {
                            Text(
                                text = "H${entry.level}",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 1.dp)
                            )
                        }
                        Text(
                            text = entry.text,
                            fontSize = 13.sp,
                            fontWeight = if (entry.level == 1) FontWeight.SemiBold else FontWeight.Normal,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
