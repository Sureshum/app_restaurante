package com.example.restaurantepos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddLocation
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.RestaurantMenu
import androidx.compose.material.icons.filled.TableBar
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.restaurantepos.data.AreaEntity
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.ProductEntity
import com.example.restaurantepos.data.TableEntity
import kotlinx.coroutines.delay
import java.util.Locale

val GreenAvailable = Color(0xFF2ECC71)
val OnGreenAvailable = Color.White

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TableDashboardScreen(
    viewModel: PosViewModel,
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
    onDeleteArea: (AreaEntity) -> Unit,
    onCreateProduct: (String, String, Double) -> Unit,
    onOpenSystemMenu: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current

    var showTableConfigDialog by remember { mutableStateOf(false) }
    var showCreateProductDialog by remember { mutableStateOf(false) }
    var showCreateAreaDialog by remember { mutableStateOf(false) }
    var showDeleteAreaDialog by remember { mutableStateOf(false) }
    var showEndDayDialog by remember { mutableStateOf(false) }

    val currentArea = remember(areas, selectedAreaId) {
        areas.find { it.id == selectedAreaId }
    }

    LaunchedEffect(areas) {
        if (selectedAreaId == null && areas.isNotEmpty()) {
            onSelectArea(areas.first().id)
        }
    }

    // 🔄 Sincronización bidireccional en tiempo real (polling cada 2 segundos)
    LaunchedEffect(selectedAreaId, tables) {
        while (true) {
            val currentIp = ExportManager.getPcIp(context)
            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                ExportManager.fetchTablesFromPc(currentIp) { jsonTables ->

                    // 1. Detectar si la PC alteró la cantidad total de mesas
                    val pcTableKeys = jsonTables.keys()
                    var maxTableNumberFromPc = 0

                    while (pcTableKeys.hasNext()) {
                        val key = pcTableKeys.next()
                        val num = key.replace("Mesa ", "").replace("Mesa", "").trim().toIntOrNull() ?: 0
                        if (num > maxTableNumberFromPc) {
                            maxTableNumberFromPc = num
                        }
                    }

                    if (maxTableNumberFromPc > 0 && maxTableNumberFromPc != tables.size) {
                        onSetTableCount(maxTableNumberFromPc)
                    }

                    // 2. Sincronizar consumos y estado de ocupación por mesa
                    tables.forEach { table ->
                        val prefix = currentArea?.prefix ?: ""
                        val keyFullName = if (prefix.isNotBlank()) "$prefix${table.number}" else "Mesa ${table.number}"
                        val keyStandard = "Mesa ${table.number}"
                        val keySimple = "${table.number}"

                        val activeKey = when {
                            jsonTables.has(keyFullName) -> keyFullName
                            jsonTables.has(keyStandard) -> keyStandard
                            jsonTables.has(keySimple) -> keySimple
                            else -> null
                        }

                        if (activeKey != null) {
                            val tableData = jsonTables.getJSONObject(activeKey)
                            val pcTotal = tableData.optDouble("total", 0.0)
                            val itemsArray = tableData.optJSONArray("items")
                            val pcIsOccupied = itemsArray != null && itemsArray.length() > 0

                            // Actualizar BD local tanto si cambió a ocupada como a desocupada
                            if (table.isOccupied != pcIsOccupied || Math.abs(table.currentTotal - pcTotal) > 0.01) {
                                val updatedItems = mutableListOf<Pair<ProductEntity, Int>>()

                                if (pcIsOccupied && itemsArray != null) {
                                    for (i in 0 until itemsArray.length()) {
                                        val itemObj = itemsArray.getJSONObject(i)
                                        val nombre = itemObj.optString("nombre", "")
                                        val cantidad = itemObj.optInt("cantidad", 0)
                                        val precio = itemObj.optDouble("precio", 0.0)

                                        if (nombre.isNotBlank() && cantidad > 0) {
                                            val matchedProduct = products.find { it.name.equals(nombre, ignoreCase = true) }
                                                ?: ProductEntity(id = 0, category = "General", name = nombre, price = precio)
                                            updatedItems.add(Pair(matchedProduct, cantidad))
                                        }
                                    }
                                }

                                // Si pcIsOccupied es falso, enviará lista vacía y 0.0 para liberar la mesa
                                viewModel.saveOrderForTable(table.id, updatedItems, if (pcIsOccupied) pcTotal else 0.0)
                            }
                        }
                    }
                }
            }
            delay(2000)
        }
    }

    // --- CÓDIGO REACTIVO DE REPORTES ---
    val pendingPaidItems by viewModel.pendingPaidItems.collectAsState()

    if (showEndDayDialog) {
        EndDayDialog(
            pendingItems = pendingPaidItems,
            onDismiss = { showEndDayDialog = false },
            onConfirmEndDay = { totalItems, totalRevenue ->
                viewModel.closeDayAndSaveReport(totalItems, totalRevenue) {
                    showEndDayDialog = false
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Mesas • ${session?.userName ?: "Usuario"}", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { showEndDayDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Assessment,
                            contentDescription = "Cierre de Día"
                        )
                    }
                    IconButton(onClick = onOpenSystemMenu) {
                        Icon(Icons.Default.RestaurantMenu, contentDescription = "Menú del Sistema")
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (areas.isNotEmpty()) {
                    val selectedIndex = areas.indexOfFirst { it.id == selectedAreaId }.coerceAtLeast(0)
                    ScrollableTabRow(
                        selectedTabIndex = selectedIndex,
                        edgePadding = 0.dp,
                        modifier = Modifier.weight(1f)
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

                Row {
                    if (currentArea != null) {
                        IconButton(onClick = { showDeleteAreaDialog = true }) {
                            Icon(
                                Icons.Default.RemoveCircleOutline,
                                contentDescription = "Eliminar Sala Actual",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                    IconButton(onClick = { showCreateAreaDialog = true }) {
                        Icon(
                            Icons.Default.AddLocation,
                            contentDescription = "Agregar Sala",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
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
                    val cardBgColor = if (table.isOccupied) MaterialTheme.colorScheme.errorContainer else GreenAvailable
                    val contentColor = if (table.isOccupied) MaterialTheme.colorScheme.onErrorContainer else OnGreenAvailable

                    val prefix = currentArea?.prefix ?: ""
                    val tableName = if (prefix.isNotBlank()) "$prefix${table.number}" else "Mesa ${table.number}"

                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(105.dp)
                            .clickable { onTableClick(table.id) },
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = cardBgColor)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(8.dp),
                            verticalArrangement = Arrangement.SpaceBetween,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.TableBar,
                                contentDescription = null,
                                tint = contentColor,
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = tableName,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall,
                                color = contentColor
                            )
                            Text(
                                text = if (table.isOccupied) "Ocupado\n$$formattedTotal" else "Disponible",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = contentColor
                            )
                        }
                    }
                }
            }
        }
    }

    if (showDeleteAreaDialog && currentArea != null) {
        AlertDialog(
            onDismissRequest = { showDeleteAreaDialog = false },
            title = { Text("Eliminar Sala", fontWeight = FontWeight.Bold) },
            text = { Text("¿Estás seguro de que deseas eliminar la sala '${currentArea.name}' y sus mesas?") },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteArea(currentArea)
                        showDeleteAreaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Eliminar")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteAreaDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (showCreateAreaDialog) {
        var areaName by remember { mutableStateOf("") }
        var areaPrefix by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showCreateAreaDialog = false },
            title = { Text("Agregar Nueva Sala", fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = areaName,
                        onValueChange = { areaName = it },
                        label = { Text("Nombre de la sala (ej. Terraza)") },
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = areaPrefix,
                        onValueChange = { areaPrefix = it },
                        label = { Text("Prefijo (ej. T)") },
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    if (areaName.isNotBlank()) {
                        val prefix = if (areaPrefix.isBlank()) "M" else areaPrefix
                        onCreateArea(areaName, prefix)
                        showCreateAreaDialog = false
                    }
                }) {
                    Text("Crear")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateAreaDialog = false }) {
                    Text("Cancelar")
                }
            }
        )
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
                    countInput.toIntOrNull()?.let { newCount ->
                        if (newCount > 0) {
                            onSetTableCount(newCount)

                            val currentIp = ExportManager.getPcIp(context)
                            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                                ExportManager.updateTableCountOnPc(currentIp, newCount)
                            }
                        }
                    }
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