package com.example.restaurantepos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restaurantepos.data.AreaEntity
import com.example.restaurantepos.data.ProductEntity
import com.example.restaurantepos.data.TableEntity
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDashboardScreen(
    session: ActiveSession?,
    areas: List<AreaEntity>,
    selectedAreaId: Int?,
    tables: List<TableEntity>,
    products: List<ProductEntity>,
    onSelectArea: (Int) -> Unit,
    onAddTable: () -> Unit,
    onRemoveTable: () -> Unit,
    onSetTableCount: (Int) -> Unit,
    onTableClick: (Int) -> Unit,
    onCreateArea: (String, String) -> Unit,
    onCreateProduct: (String, String, Double) -> Unit,
    onOpenSystemMenu: () -> Unit,
    onLogout: () -> Unit
) {
    var showTableConfigDialog by remember { mutableStateOf(false) }
    var showCreateProductDialog by remember { mutableStateOf(false) }

    LaunchedEffect(areas) {
        if (selectedAreaId == null && areas.isNotEmpty()) {
            onSelectArea(areas.first().id)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesas • ${session?.userName ?: "Usuario"}", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = onOpenSystemMenu) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = "Menú del Sistema")
                    }
                    IconButton(onClick = { showCreateProductDialog = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Añadir Comida al Menú")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(Icons.Default.ExitToApp, contentDescription = "Cerrar sesión")
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
                    horizontalArrangement = Arrangement.Center
                ) {
                    Button(
                        onClick = { showTableConfigDialog = true },
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Configurar cantidad de mesas")
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
        ) {
            if (areas.isNotEmpty()) {
                val selectedIndex = areas.indexOfFirst { it.id == selectedAreaId }.coerceAtLeast(0)
                ScrollableTabRow(
                    selectedTabIndex = selectedIndex,
                    edgePadding = 0.dp
                ) {
                    areas.forEach { area ->
                        Tab(
                            selected = area.id == selectedAreaId,
                            onClick = { onSelectArea(area.id) },
                            text = { Text(area.name, fontWeight = FontWeight.Bold) }
                        )
                    }
                }
            } else {
                Text(
                    text = "No hay áreas disponibles",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(tables, key = { it.id }) { table ->
                    val formattedTotal = String.format(Locale.US, "%.2f", table.currentTotal)
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(95.dp)
                            .clickable { onTableClick(table.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (table.isOccupied) MaterialTheme.colorScheme.errorContainer
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Mesa ${table.number}",
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                            Text(
                                text = if (table.isOccupied) "Ocupado\n$$formattedTotal" else "Disponible",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = if (table.isOccupied) FontWeight.Bold else FontWeight.Normal,
                                color = if (table.isOccupied) MaterialTheme.colorScheme.onErrorContainer
                                else MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                }
            }
        }
    }

    if (showTableConfigDialog) {
        var countInput by remember { mutableStateOf(tables.size.toString()) }
        AlertDialog(
            onDismissRequest = { showTableConfigDialog = false },
            title = { Text("Configurar Mesas", fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = countInput,
                    onValueChange = { countInput = it },
                    label = { Text("Cantidad total de mesas") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(onClick = {
                    countInput.toIntOrNull()?.let { onSetTableCount(it) }
                    showTableConfigDialog = false
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showTableConfigDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreateProductDialog) {
        var category by remember { mutableStateOf("") }
        var name by remember { mutableStateOf("") }
        var priceInput by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateProductDialog = false },
            title = { Text("Añadir Comida al Menú", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = category,
                        onValueChange = { category = it },
                        label = { Text("Categoría (ej. General, Bebidas)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Nombre del platillo") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = priceInput,
                        onValueChange = { priceInput = it },
                        label = { Text("Precio") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    val price = priceInput.toDoubleOrNull() ?: 0.0
                    val cat = if (category.isBlank()) "General" else category
                    if (name.isNotBlank() && price > 0.0) {
                        onCreateProduct(cat, name, price)
                        showCreateProductDialog = false
                    }
                }) {
                    Text("Guardar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateProductDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}