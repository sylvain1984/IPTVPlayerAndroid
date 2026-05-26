package com.iptv.player.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.iptv.player.data.LiveChannel

@Composable
fun PinEntryDialog(
    channelName: String,
    onVerify: (String) -> Boolean,
    onSuccess: () -> Unit,
    onDismiss: () -> Unit
) {
    var pin by remember { mutableStateOf("") }
    var error by remember { mutableStateOf(false) }

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 8.dp
        ) {
            Column(
                modifier = Modifier.padding(28.dp).width(320.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text("专属频道", style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.height(4.dp))
                Text(channelName, style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold, maxLines = 1)
                Spacer(Modifier.height(20.dp))

                // 4-dot PIN indicator
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    repeat(4) { i ->
                        Box(
                            modifier = Modifier.size(14.dp)
                                .background(
                                    if (i < pin.length) MaterialTheme.colorScheme.primary
                                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.2f),
                                    shape = androidx.compose.foundation.shape.CircleShape
                                )
                        )
                    }
                }

                if (error) {
                    Spacer(Modifier.height(8.dp))
                    Text("密码错误", color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall)
                }

                Spacer(Modifier.height(20.dp))

                // Numpad
                val keys = listOf("1","2","3","4","5","6","7","8","9","","0","⌫")
                for (row in keys.chunked(3)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        row.forEach { key ->
                            if (key.isEmpty()) {
                                Spacer(Modifier.size(80.dp, 52.dp))
                            } else {
                                OutlinedButton(
                                    onClick = {
                                        error = false
                                        when (key) {
                                            "⌫" -> if (pin.isNotEmpty()) pin = pin.dropLast(1)
                                            else -> if (pin.length < 4) {
                                                pin += key
                                                if (pin.length == 4) {
                                                    if (onVerify(pin)) onSuccess()
                                                    else { error = true; pin = "" }
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(80.dp, 52.dp),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(key, fontSize = 20.sp, fontWeight = FontWeight.Medium,
                                        textAlign = TextAlign.Center)
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Spacer(Modifier.height(4.dp))
                TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                    Text("取消")
                }
            }
        }
    }
}
