package com.example.restaurantepos.ui

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.restaurantepos.data.AreaEntity
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.ProductEntity
import com.example.restaurantepos.data.TableEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    table: TableEntity,
    area: AreaEntity? = null,
    products: List<ProductEntity>,
    existingOrderItems: List<OrderItemEntity>,
    waiterName: String = "Camarero",
    onSaveOrder: (List<Pair<ProductEntity, Int>>, Double) -> Unit,
    onPayTable: (List<Pair<ProductEntity, Int>>, Double) -> Unit,
    onBack: () -> Unit
) {
    val categorySheetState = rememberModalBottomSheetState()
    val receiptSheetState = rememberModalBottomSheetState()

    var showCategorySheet by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    var showCancelDialog by remember { mutableStateOf(false) }

    val scope = rememberCoroutineScope()

    val categories = remember(products) {
        listOf("Todas") + products.map { it.category.trim() }.distinctBy { it.uppercase() }
    }
    var selectedCategory by remember { mutableStateOf("Todas") }
    val cart = remember { mutableStateMapOf<ProductEntity, Int>() }

    var isCartInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(existingOrderItems, products) {
        if (!isCartInitialized && existingOrderItems.isNotEmpty() && products.isNotEmpty()) {
            cart.clear()
            val counts = existingOrderItems.groupingBy { it.productName.trim() }.eachCount()

            counts.forEach { (prodName, count) ->
                products.find { it.name.trim().equals(prodName, ignoreCase = true) }?.let { prod ->
                    cart[prod] = count
                }
            }
            isCartInitialized = true
        }
    }

    val filteredProducts = remember(selectedCategory, products) {
        if (selectedCategory == "Todas") products
        else products.filter { it.category.trim().equals(selectedCategory, ignoreCase = true) }
    }

    val totalAmount = cart.entries.sumOf { it.key.price * it.value }
    val totalItemsCount = cart.values.sum()
    var isNavigatingBack by remember { mutableStateOf(false) }
    val context = LocalContext.current

    val areaPrefix = area?.prefix?.trim() ?: ""
    val tableNameToSend = if (areaPrefix.isNotBlank()) "$areaPrefix${table.number}" else "Mesa ${table.number}"
    val tableDisplayName = if (areaPrefix.isNotBlank()) "$areaPrefix${table.number} • ${area?.name ?: ""}" else "Mesa ${table.number}"

    // Guardar comanda y enviar a PC
    val handleSave = {
        if (!isNavigatingBack) {
            isNavigatingBack = true

            scope.launch(Dispatchers.IO) {
                val currentIp = ExportManager.getPcIp(context)
                if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                    val jsonItems = cart.map { (product, qty) ->
                        ExportManager.ItemOrderRequest(
                            nombre = product.name,
                            cantidad = qty,
                            precio = product.price
                        )
                    }

                    ExportManager.sendOrderJsonToPc(
                        pcIpAddress = currentIp,
                        tableName = tableNameToSend,
                        waiterName = waiterName,
                        itemsList = jsonItems,
                        areaId = table.areaId
                    )
                }

                withContext(Dispatchers.Main) {
                    onSaveOrder(cart.map { Pair(it.key, it.value) }, totalAmount)
                    onBack()
                }
            }
        }
    }

    // Cancelar comanda por completo y liberar mesa
    val handleCancelOrder = {
        if (!isNavigatingBack) {
            isNavigatingBack = true
            cart.clear()

            scope.launch(Dispatchers.IO) {
                val currentIp = ExportManager.getPcIp(context)
                if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                    ExportManager.sendOrderJsonToPc(
                        pcIpAddress = currentIp,
                        tableName = tableNameToSend,
                        waiterName = "",
                        itemsList = emptyList(),
                        areaId = table.areaId
                    )
                }

                withContext(Dispatchers.Main) {
                    onSaveOrder(emptyList(), 0.0)
                    onBack()
                }
            }
        }
    }

    // Cobrar mesa y emitir ticket
    val handlePay = {
        if (!isNavigatingBack) {
            isNavigatingBack = true
            val itemsList = cart.map { Pair(it.key, it.value) }

            scope.launch(Dispatchers.IO) {
                val currentIp = ExportManager.getPcIp(context)
                if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                    val pdfFile = ExportManager.generatePdfReceipt(
                        context = context,
                        tableId = table.number,
                        tableNameDisplay = tableDisplayName,
                        items = itemsList,
                        totalAmount = totalAmount,
                        waiterName = waiterName
                    )

                    if (pdfFile != null && pdfFile.exists()) {
                        ExportManager.sendPdfToPc(
                            pdfFile = pdfFile,
                            pcIpAddress = currentIp,
                            tableId = table.number,
                            tableName = tableNameToSend,
                            areaId = table.areaId
                        )
                    }
                }

                withContext(Dispatchers.Main) {
                    onPayTable(itemsList, totalAmount)
                    onBack()
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comanda • $tableDisplayName", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { handleSave() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Guardar y Regresar")
                    }
                },
                actions = {
                    if (cart.isNotEmpty() || totalAmount > 0.0 || table.isOccupied) {
                        IconButton(onClick = { showCancelDialog = true }) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = "Cancelar Pedido",
                                tint = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            )
        },
        bottomBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant,
                tonalElevation = 8.dp,
                shadowElevation = 8.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = { showCategorySheet = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.Category, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text("Categoría: $selectedCategory", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = { showReceiptSheet = true },
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        BadgedBox(
                            badge = { if (totalItemsCount > 0) Badge { Text("$totalItemsCount") } }
                        ) {
                            Icon(Icons.Default.Receipt, contentDescription = "Factura")
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "$$${String.format(Locale.US, "%.2f", totalAmount)}",
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(8.dp)
        ) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredProducts) { product ->
                    val currentQty = cart[product] ?: 0
                    Card(
                        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.clickable { cart[product] = currentQty + 1 }
                    ) {
                        Column(modifier = Modifier.padding(6.dp)) {
                            if (!product.imageUri.isNullOrEmpty()) {
                                AsyncImage(
                                    model = File(product.imageUri),
                                    contentDescription = product.name,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(90.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    contentScale = ContentScale.Crop
                                )
                                Spacer(Modifier.height(6.dp))
                            } else {
                                Spacer(Modifier.height(16.dp))
                            }
                            Text(
                                product.name,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.bodyMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(Modifier.height(2.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "$$${String.format(Locale.US, "%.2f", product.price)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.secondary,
                                    fontWeight = FontWeight.SemiBold
                                )
                                if (currentQty > 0) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.primaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            "x$currentQty",
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Diálogo de Confirmación de Cancelación de Pedido
        if (showCancelDialog) {
            AlertDialog(
                onDismissRequest = { showCancelDialog = false },
                title = { Text("Cancelar Pedido", fontWeight = FontWeight.Bold) },
                text = { Text("¿Estás seguro de que deseas cancelar todos los pedidos de $tableDisplayName y liberar la mesa?") },
                confirmButton = {
                    Button(
                        onClick = {
                            showCancelDialog = false
                            handleCancelOrder()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Sí, Cancelar Pedido")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showCancelDialog = false }) {
                        Text("No, Conservar")
                    }
                }
            )
        }

        if (showCategorySheet) {
            ModalBottomSheet(
                onDismissRequest = { showCategorySheet = false },
                sheetState = categorySheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Text(
                        "Seleccionar Categoría",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        items(categories) { category ->
                            NavigationDrawerItem(
                                icon = { Icon(Icons.Default.Category, contentDescription = null) },
                                label = { Text(category, fontWeight = FontWeight.SemiBold) },
                                selected = category == selectedCategory,
                                onClick = {
                                    selectedCategory = category
                                    scope.launch { categorySheetState.hide() }.invokeOnCompletion {
                                        showCategorySheet = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }

        if (showReceiptSheet) {
            ModalBottomSheet(
                onDismissRequest = { showReceiptSheet = false },
                sheetState = receiptSheetState
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "Factura • $tableDisplayName",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        if (cart.isNotEmpty() || table.isOccupied) {
                            TextButton(
                                onClick = {
                                    scope.launch { receiptSheetState.hide() }.invokeOnCompletion {
                                        showReceiptSheet = false
                                        showCancelDialog = true
                                    }
                                },
                                colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error)
                            ) {
                                Icon(Icons.Default.DeleteOutline, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Cancelar Pedido")
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                    if (cart.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No hay consumos en esta mesa", color = MaterialTheme.colorScheme.outline)
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 240.dp)
                        ) {
                            items(
                                items = cart.entries.toList(),
                                key = { it.key.id }
                            ) { (prod, qty) ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            prod.name,
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium
                                        )
                                        Text(
                                            "$$${String.format(Locale.US, "%.2f", prod.price)} c/u",
                                            style = MaterialTheme.typography.bodySmall
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(onClick = {
                                            if (qty > 1) {
                                                cart[prod] = qty - 1
                                            } else {
                                                cart.remove(prod)
                                            }
                                        }) {
                                            Icon(Icons.Default.Remove, contentDescription = "Restar")
                                        }
                                        Text(
                                            "$qty",
                                            fontWeight = FontWeight.Bold,
                                            modifier = Modifier.padding(horizontal = 4.dp)
                                        )
                                        IconButton(onClick = {
                                            cart[prod] = qty + 1
                                        }) {
                                            Icon(Icons.Default.Add, contentDescription = "Sumar")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Total:", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "$$${String.format(Locale.US, "%.2f", totalAmount)}",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Spacer(Modifier.height(16.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = {
                                scope.launch { receiptSheetState.hide() }.invokeOnCompletion {
                                    showReceiptSheet = false
                                    handleSave()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Send, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Enviar Comanda")
                        }
                        Button(
                            onClick = {
                                scope.launch { receiptSheetState.hide() }.invokeOnCompletion {
                                    showReceiptSheet = false
                                    handlePay()
                                }
                            },
                            modifier = Modifier.weight(1f),
                            enabled = totalAmount > 0,
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Icon(Icons.Default.Payment, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Pagar Mesa")
                        }
                    }
                }
            }
        }
    }
}