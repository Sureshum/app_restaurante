package com.example.restaurantepos.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.ExitToApp
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
    onTableClick: (Int) -> Unit,
    onOpenSystemMenu: () -> Unit,
    onLogout: () -> Unit
) {
    val context = LocalContext.current
    var showEndDayDialog by remember { mutableStateOf(false) }

    val currentArea = remember(areas, selectedAreaId) {
        areas.find { it.id == selectedAreaId }
    }

    LaunchedEffect(areas) {
        if (selectedAreaId == null && areas.isNotEmpty()) {
            onSelectArea(areas.first().id)
        }
    }

    // 🔄 Sincronización continua con el Servidor Madre (PC) cada 2 segundos
    LaunchedEffect(selectedAreaId, tables) {
        while (true) {
            val currentIp = ExportManager.getPcIp(context)
            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {

                // 1. Sincronizar Salas desde la PC Madre
                ExportManager.fetchAreasFromPc(currentIp) { jsonAreas ->
                    val pcAreasList = mutableListOf<AreaEntity>()
                    val pcCountsMap = mutableMapOf<Int, Int>()
                    for (i in 0 until jsonAreas.length()) {
                        val obj = jsonAreas.getJSONObject(i)
                        val id = obj.optInt("id", 0)
                        val name = obj.optString("name", "")
                        val prefix = obj.optString("prefix", "M")
                        val count = obj.optInt("count", 10)
                        if (name.isNotBlank()) {
                            pcAreasList.add(AreaEntity(id = id, name = name, prefix = prefix))
                            pcCountsMap[id] = count
                        }
                    }
                    if (pcAreasList.isNotEmpty()) {
                        viewModel.syncAreasFromPc(pcAreasList, pcCountsMap)
                    }
                }

                // 2. Sincronizar Menú / Productos desde la PC Madre
                ExportManager.fetchProductsFromPc(currentIp) { jsonProducts ->
                    val pcProductsList = mutableListOf<ProductEntity>()
                    for (i in 0 until jsonProducts.length()) {
                        val obj = jsonProducts.getJSONObject(i)
                        val id = obj.optInt("id", 0)
                        val category = obj.optString("category", "General")
                        val name = obj.optString("name", "")
                        val price = obj.optDouble("price", 0.0)
                        if (name.isNotBlank()) {
                            pcProductsList.add(ProductEntity(id = id, category = category, name = name, price = price))
                        }
                    }
                    if (pcProductsList.isNotEmpty()) {
                        viewModel.syncProductsFromPc(pcProductsList)
                    }
                }

                // 3. Sincronizar Estado de Mesas de la Sala Seleccionada
                val activeAreaId = selectedAreaId ?: currentArea?.id
                ExportManager.fetchTablesFromPc(currentIp, areaId = activeAreaId) { jsonTables ->
                    tables.forEach { table ->
                        val prefix = currentArea?.prefix?.trim() ?: ""
                        val areaName = currentArea?.name?.trim() ?: ""
                        val keyFullName = if (prefix.isNotBlank() && prefix.uppercase() != "M") "$prefix${table.number}" else "Mesa ${table.number}"
                        val keyRoomName = "$areaName ${table.number}"
                        val keyStandard = "Mesa ${table.number}"
                        val keySimple = "${table.number}"

                        val activeKey = when {
                            jsonTables.has(keyFullName) -> keyFullName
                            jsonTables.has(keyRoomName) -> keyRoomName
                            jsonTables.has(keyStandard) -> keyStandard
                            jsonTables.has(keySimple) -> keySimple
                            else -> null
                        }

                        if (activeKey != null) {
                            val tableData = jsonTables.getJSONObject(activeKey)
                            val pcTotal = tableData.optDouble("total", 0.0)
                            val itemsArray = tableData.optJSONArray("items")
                            val pcIsOccupied = itemsArray != null && itemsArray.length() > 0

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

                                viewModel.saveOrderForTable(table.id, updatedItems, if (pcIsOccupied) pcTotal else 0.0)
                            }
                        }
                    }
                }
            }
            delay(2000)
        }
    }

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
                title = { Text("Mesas (v1.7.0) • ${session?.userName ?: "Mesero"}", fontWeight = FontWeight.Bold) },
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
                    edgePadding = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    areas.forEach { area ->
                        Tab(
                            selected = area.id == selectedAreaId,
                            onClick = { onSelectArea(area.id) },
                            text = {
                                val tabLabel = if (area.prefix.isNotBlank() && area.prefix.uppercase() != "M") {
                                    "${area.name} (${area.prefix})"
                                } else {
                                    area.name
                                }
                                Text(tabLabel, fontWeight = FontWeight.Bold)
                            }
                        )
                    }
                }
            } else {
                Text(
                    text = "Conectando con el Servidor Madre...",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.outline,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            Spacer(Modifier.height(16.dp))

            val distinctTables = remember(tables) { tables.distinctBy { it.number }.sortedBy { it.number } }

            LazyVerticalGrid(
                columns = GridCells.Fixed(4),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(distinctTables, key = { it.id }) { table ->
                    val formattedTotal = String.format(Locale.US, "%.2f", table.currentTotal)
                    val cardBgColor = if (table.isOccupied) MaterialTheme.colorScheme.errorContainer else GreenAvailable
                    val contentColor = if (table.isOccupied) MaterialTheme.colorScheme.onErrorContainer else OnGreenAvailable

                    val prefix = currentArea?.prefix?.trim() ?: ""
                    val tableName = if (prefix.isNotBlank() && prefix.uppercase() != "M") "$prefix${table.number}" else "Mesa ${table.number}"

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
}