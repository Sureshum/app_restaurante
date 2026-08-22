package com.example.restaurantepos.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.ProductEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    tableId: Int,
    products: List<ProductEntity>,
    existingOrderItems: List<OrderItemEntity>,
    onSaveOrder: (List<Pair<ProductEntity, Int>>, Double) -> Unit,
    onPayTable: () -> Unit,
    onBack: () -> Unit
) {
    val cart = remember { mutableStateListOf<Pair<ProductEntity, Int>>() }

    LaunchedEffect(existingOrderItems) {
        if (existingOrderItems.isNotEmpty()) {
            cart.clear()
            val grouped = existingOrderItems.groupingBy { it.productName }.eachCount()
            grouped.forEach { (name, qty) ->
                val prod = products.find { it.name == name }
                    ?: ProductEntity(category = "General", name = name, price = existingOrderItems.first { it.productName == name }.price)
                cart.add(prod to qty)
            }
        }
    }

    val totalAmount by remember(cart) {
        derivedStateOf { cart.sumOf { it.first.price * it.second } }
    }

    val handleBackAction = {
        onSaveOrder(cart.toList(), totalAmount)
        onBack()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesa $tableId • Pedido", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleBackAction) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        },
        bottomBar = {
            Surface(tonalElevation = 6.dp) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Total a pagar", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                        val formattedTotal = String.format(Locale.US, "%.2f", totalAmount)
                        Text(
                            text = "$$formattedTotal",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                onPayTable()
                                onBack()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Cobrar")
                        }

                        OutlinedButton(
                            onClick = {
                                onSaveOrder(cart.toList(), totalAmount)
                                onBack()
                            },
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Guardar")
                        }
                    }
                }
            }
        }
    ) { paddingValues ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text("Menú", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(products, key = { it.id }) { product ->
                        val formattedPrice = String.format(Locale.US, "%.2f", product.price)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(text = product.name, fontWeight = FontWeight.Bold)
                                    Text(text = "$$formattedPrice", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                }
                                Button(
                                    onClick = {
                                        val existingIndex = cart.indexOfFirst { it.first.id == product.id }
                                        if (existingIndex >= 0) {
                                            val currentPair = cart[existingIndex]
                                            cart[existingIndex] = currentPair.first to (currentPair.second + 1)
                                        } else {
                                            cart.add(product to 1)
                                        }
                                    },
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                                ) {
                                    Text("Agregar")
                                }
                            }
                        }
                    }
                }
            }

            VerticalDivider(modifier = Modifier.fillMaxHeight(), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant)

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                Text("Cuenta Actual", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))

                if (cart.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("Mesa sin pedidos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        items(cart, key = { it.first.id }) { (product, qty) ->
                            val formattedSubtotal = String.format(Locale.US, "%.2f", product.price * qty)
                            Card(
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                                shape = RoundedCornerShape(12.dp),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.Bold)
                                        Text("$$formattedSubtotal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.outline)
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        FilledTonalIconButton(
                                            onClick = {
                                                val index = cart.indexOfFirst { it.first.id == product.id }
                                                if (index >= 0) {
                                                    val current = cart[index]
                                                    if (current.second > 1) {
                                                        cart[index] = current.first to (current.second - 1)
                                                    } else {
                                                        cart.removeAt(index)
                                                    }
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("-", fontWeight = FontWeight.Bold)
                                        }

                                        Text("$qty", fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))

                                        FilledTonalIconButton(
                                            onClick = {
                                                val index = cart.indexOfFirst { it.first.id == product.id }
                                                if (index >= 0) {
                                                    val current = cart[index]
                                                    cart[index] = current.first to (current.second + 1)
                                                }
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Text("+", fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}