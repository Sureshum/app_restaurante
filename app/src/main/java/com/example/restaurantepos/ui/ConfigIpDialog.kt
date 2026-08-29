package com.example.restaurantepos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
fun ConfigIpDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var ipText by remember { mutableStateOf(ExportManager.getPcIp(context)) }
    var testStatusMessage by remember { mutableStateOf<String?>(null) }
    var isTesting by remember { mutableStateOf(false) }
    var testSuccess by remember { mutableStateOf<Boolean?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Configuración de Servidor Madre", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Ingresa la dirección IP de la computadora donde corre la Caja:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = ipText,
                    onValueChange = {
                        ipText = it
                        testStatusMessage = null
                        testSuccess = null
                    },
                    label = { Text("Dirección IP (ej. 192.168.1.50)") },
                    singleLine = true,
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedButton(
                    onClick = {
                        isTesting = true
                        testStatusMessage = null
                        testSuccess = null
                        ExportManager.testConnection(ipText) { success, msg ->
                            isTesting = false
                            testSuccess = success
                            testStatusMessage = msg
                        }
                    },
                    enabled = !isTesting && ipText.isNotBlank(),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isTesting) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text("Probando conexión...")
                    } else {
                        Text("🔍 Probar Conexión con PC")
                    }
                }

                if (testStatusMessage != null) {
                    Text(
                        text = testStatusMessage!!,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (testSuccess == true) Color(0xFF16A34A) else MaterialTheme.colorScheme.error,
                        modifier = Modifier.padding(horizontal = 4.dp)
                    )
                }
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
                Text("Cerrar")
            }
        }
    )
}