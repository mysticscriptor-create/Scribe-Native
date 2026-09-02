package com.primaloptima.scribe.ui.components

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.primaloptima.scribe.ui.theme.FrostedDialog

@Composable
fun DualTitleNoteDialog(
    dialogTitle: String = "New Note",
    confirmButtonText: String = "Create",
    initialPrimary: String = "",
    initialSecondary: String = "",
    onDismiss: () -> Unit,
    onConfirm: (fullName: String) -> Unit
) {
    var primaryTitle by remember { mutableStateOf(initialPrimary) }
    var secondaryTitle by remember { mutableStateOf(initialSecondary) }
    var showSecondary by remember { mutableStateOf(initialSecondary.isNotEmpty()) }

    val primaryFocusRequester = remember { FocusRequester() }
    val secondaryFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        primaryFocusRequester.requestFocus()
    }

    fun submit() {
        val p = primaryTitle.trim()
        val s = secondaryTitle.trim()
        val combined = if (showSecondary && s.isNotEmpty()) {
            if (p.isNotEmpty()) "$p\n$s" else s
        } else {
            p
        }
        if (combined.isNotEmpty()) {
            onConfirm(combined)
        }
    }

    FrostedDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = dialogTitle,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = primaryTitle,
                    onValueChange = { primaryTitle = it },
                    label = { Text("Primary Title / Kicker (e.g. Chapter 1)") },
                    placeholder = { Text("e.g. Chapter 1, Prologue") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        imeAction = if (showSecondary) ImeAction.Next else ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = {
                            showSecondary = true
                            secondaryFocusRequester.requestFocus()
                        }
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(primaryFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                showSecondary = true
                                secondaryFocusRequester.requestFocus()
                                true
                            } else false
                        }
                )

                AnimatedVisibility(
                    visible = showSecondary,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    OutlinedTextField(
                        value = secondaryTitle,
                        onValueChange = { secondaryTitle = it },
                        label = { Text("Main Title (e.g. The Starlit Archive)") },
                        placeholder = { Text("e.g. The Starlit Archive") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(
                            onDone = { submit() }
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(secondaryFocusRequester)
                            .onPreviewKeyEvent { event ->
                                if (event.key == Key.Backspace && secondaryTitle.isEmpty() && event.type == KeyEventType.KeyUp) {
                                    showSecondary = false
                                    primaryFocusRequester.requestFocus()
                                    true
                                } else if (event.key == Key.Enter && event.type == KeyEventType.KeyUp) {
                                    submit()
                                    true
                                } else false
                            }
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { submit() },
                enabled = primaryTitle.isNotBlank() || (showSecondary && secondaryTitle.isNotBlank())
            ) {
                Text(confirmButtonText)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
