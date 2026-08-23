package com.example.restaurantepos.ui

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
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Receipt
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.ProductEntity
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderScreen(
    tableId: Int,
    products: List<ProductEntity>,
    existingOrderItems: List<OrderItemEntity>,
    onSaveOrder: (List<Pair<ProductEntity, Int>>, Double) -> Unit,
    onPayTable: (List<Pair<ProductEntity, Int>>, Double) -> Unit,
    onBack: () -> Unit
){
    val categorySheetState = rememberModalBottomSheetState()
    val receiptSheetState = rememberModalBottomSheetState()

    var showCategorySheet by remember { mutableStateOf(false) }
    var showReceiptSheet by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val categories = remember(products) {
        listOf("Todas") + products.map { it.category.trim() }.distinctBy { it.uppercase() }
    }
    var selectedCategory by remember { mutableStateOf("Todas") }
    val cart = remember { mutableStateMapOf<ProductEntity, Int>() }

    // Bandera para asegurar que la carga inicial desde la BD solo ocurra una sola vez
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

    val handleSave = {
        if (!isNavigatingBack) {
            isNavigatingBack = true
            onSaveOrder(cart.map { Pair(it.key, it.value) }, totalAmount)
            onBack()
        }
    }

    val handlePay = {
        if (!isNavigatingBack) {
            isNavigatingBack = true
            onPayTable(cart.map { Pair(it.key, it.value) }, totalAmount)
            onBack()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Comanda • Mesa $tableId", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = handleSave) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Regresar")
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
                    Text(
                        "Factura • Mesa $tableId",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
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
                            Icon(Icons.Default.Save, contentDescription = null)
                            Spacer(Modifier.width(6.dp))
                            Text("Guardar")
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