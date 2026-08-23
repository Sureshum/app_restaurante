package com.example.restaurantepos.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restaurantepos.data.OrderItemEntity

@Composable
fun EndDayDialog(
    pendingItems: List<OrderItemEntity>,
    onDismiss: () -> Unit,
    onConfirmEndDay: (totalItems: Int, totalRevenue: Double) -> Unit
) {
    val totalItems = pendingItems.size
    val totalRevenue = pendingItems.sumOf { it.price }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cierre de Caja - Terminar Día", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Resumen acumulado de ventas realizadas hoy:")
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Productos Vendidos:")
                    Text("$totalItems", fontWeight = FontWeight.Bold)
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Total Ganancias Recaudadas:")
                    Text(
                        "$${String.format("%.2f", totalRevenue)}",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirmEndDay(totalItems, totalRevenue) },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
            ) {
                Text("Confirmar y Cerrar Día")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}