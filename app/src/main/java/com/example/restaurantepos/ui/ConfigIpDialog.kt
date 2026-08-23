package com.example.restaurantepos.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight

@Composable
fun ConfigIpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var ipText by remember { mutableStateOf(ExportManager.getPcIp(context)) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configurar IP de la PC", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Ingresa la dirección IP local de la computadora donde corre el servidor receptor:")
                OutlinedTextField(
                    value = ipText,
                    onValueChange = { ipText = it },
                    label = { Text("Dirección IPv4") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    ExportManager.savePcIp(context, ipText)
                    onDismiss()
                }
            ) {
                Text("Guardar IP")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}