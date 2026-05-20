package com.iptv.player.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.iptv.player.data.SourceAggregator

@Composable
fun AddSourceDialog(
    onDismiss: () -> Unit,
    onAddUrl: (String) -> Unit,
    onAddPreset: (String) -> Unit
) {
    var urlText by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = MaterialTheme.shapes.large,
            tonalElevation = 6.dp
        ) {
            Column(modifier = Modifier.padding(20.dp).width(480.dp)) {
                Text("添加 M3U 源", style = MaterialTheme.typography.titleLarge)

                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = urlText,
                    onValueChange = { urlText = it },
                    label = { Text("https://example.com/playlist.m3u") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = {
                        if (urlText.isNotBlank()) {
                            onAddUrl(urlText.trim())
                            onDismiss()
                        }
                    })
                )

                Spacer(Modifier.height(12.dp))
                Text("或选择预设源组:", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f))
                Spacer(Modifier.height(8.dp))

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SourceAggregator.OPTIONAL_SOURCES.keys.sorted().forEach { key ->
                        OutlinedButton(onClick = {
                            onAddPreset(key)
                            onDismiss()
                        }) {
                            Text(key)
                        }
                    }
                }

                Spacer(Modifier.height(16.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    content = {
                        TextButton(onClick = onDismiss) { Text("取消") }
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = {
                                if (urlText.isNotBlank()) {
                                    onAddUrl(urlText.trim())
                                    onDismiss()
                                }
                            },
                            enabled = urlText.isNotBlank()
                        ) { Text("添加") }
                    }
                )
            }
        }
    }
}
