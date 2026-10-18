package com.primaloptima.scribe.ui.components.export

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private const val CHUNK_SIZE = 50

private data class SourceChunk(
    val chunkIndex: Int,
    val startLineNumber: Int,
    val lineNumbersText: String,
    val contentText: String
)

/**
 * Virtualized monospace document source inspector with synchronized line numbers.
 * Uses chunked [LazyColumn] rendering (50 lines per chunk) to eliminate main-thread layout freezes
 * and ANRs on large 25k–100k+ word manuscripts.
 */
@Composable
fun SourcePreviewViewer(
    sourceText: String,
    modifier: Modifier = Modifier
) {
    val chunks = remember(sourceText) {
        val lines = sourceText.lines()
        val totalLines = lines.size
        val chunkList = ArrayList<SourceChunk>((totalLines / CHUNK_SIZE) + 1)
        var start = 0
        var chunkIdx = 0
        while (start < totalLines) {
            val end = (start + CHUNK_SIZE).coerceAtMost(totalLines)
            val chunkLines = lines.subList(start, end)
            val lineNumbers = (start + 1..end).joinToString("\n")
            val chunkContent = chunkLines.joinToString("\n")
            chunkList.add(
                SourceChunk(
                    chunkIndex = chunkIdx++,
                    startLineNumber = start + 1,
                    lineNumbersText = lineNumbers,
                    contentText = chunkContent
                )
            )
            start = end
        }
        chunkList
    }

    val lazyListState = rememberLazyListState()

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
    ) {
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
        ) {
            items(
                items = chunks,
                key = { it.chunkIndex }
            ) { chunk ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                ) {
                    // Line Numbers Gutter
                    Text(
                        text = chunk.lineNumbersText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        textAlign = TextAlign.End,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f),
                        modifier = Modifier
                            .width(42.dp)
                            .padding(end = 12.dp)
                    )

                    // Source Content
                    Text(
                        text = chunk.contentText,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}
