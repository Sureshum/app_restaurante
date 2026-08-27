package com.example.restaurantepos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import com.example.restaurantepos.data.AreaEntity
import com.example.restaurantepos.data.DailyReportEntity
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.PosDao
import com.example.restaurantepos.data.ProductEntity
import com.example.restaurantepos.data.SecurityUtils
import com.example.restaurantepos.data.TableEntity
import com.example.restaurantepos.data.UserEntity
import com.example.restaurantepos.data.UserRole
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class PosViewModel(private val dao: PosDao) : ViewModel() {

    val users: StateFlow<List<UserEntity>> = dao.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas: StateFlow<List<AreaEntity>> = dao.getAllAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = dao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val pendingPaidItems: StateFlow<List<OrderItemEntity>> = dao.getAllPaidItemsFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAreaId = MutableStateFlow<Int?>(null)
    val selectedAreaId: StateFlow<Int?> = _selectedAreaId.asStateFlow()

    @OptIn(ExperimentalCoroutinesApi::class)
    val currentTables: StateFlow<List<TableEntity>> = _selectedAreaId
        .filterNotNull()
        .flatMapLatest { areaId ->
            dao.getTablesByArea(areaId).map { rawTables ->
                rawTables.distinctBy { it.number }.sortedBy { it.number }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _currentUserSession = MutableStateFlow<ActiveSession?>(null)
    val currentUserSession: StateFlow<ActiveSession?> = _currentUserSession.asStateFlow()

    // Registro de mesas modificadas localmente (para impedir que el polling
    // sobrescriba el estado local con datos viejos de la PC justo después
    // de pagar, cancelar o guardar una comanda)
    private val localTableChanges = ConcurrentHashMap<Int, Long>()

    fun markTableLocallyChanged(tableId: Int) {
        localTableChanges[tableId] = System.currentTimeMillis()
    }

    fun isTableLocallyChanged(tableId: Int, withinMs: Long = 1200): Boolean {
        val t = localTableChanges[tableId] ?: return false
        return (System.currentTimeMillis() - t) < withinMs
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val areaList = areas.first()
            if (_selectedAreaId.value == null && areaList.isNotEmpty()) {
                selectArea(areaList.first().id)
            }
        }
    }

    fun authenticate(user: UserEntity, pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        if (SecurityUtils.verifyPin(pin, user.pinHash)) {
            _currentUserSession.value = ActiveSession(user.id, user.name, user.role)
            onSuccess()
        } else {
            onError()
        }
    }

    fun logout() {
        _currentUserSession.value = null
    }

    fun createUser(name: String, pin: String, avatarUri: String, role: UserRole) {
        viewModelScope.launch(Dispatchers.IO) {
            val hashedPin = SecurityUtils.hashPin(pin)
            dao.insertUser(
                UserEntity(
                    name = name,
                    pinHash = hashedPin,
                    avatarUri = avatarUri,
                    role = role
                )
            )
        }
    }

    fun deleteUser(user: UserEntity, pin: String, onSuccess: () -> Unit, onError: () -> Unit) {
        val session = _currentUserSession.value
        if (session != null && session.isAdmin()) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.deleteUser(user)
            }
            onSuccess()
        } else if (SecurityUtils.verifyPin(pin, user.pinHash)) {
            viewModelScope.launch(Dispatchers.IO) {
                dao.deleteUser(user)
            }
            onSuccess()
        } else {
            onError()
        }
    }

    fun selectArea(areaId: Int) {
        _selectedAreaId.value = areaId
    }

    fun createProduct(category: String, name: String, price: Double, imageUri: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProduct(
                ProductEntity(
                    category = category,
                    name = name,
                    price = price,
                    imageUri = imageUri
                )
            )
        }
    }

    fun createProductFromMobile(
        category: String,
        name: String,
        price: Double,
        imageUri: String? = null,
        context: android.content.Context
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProduct(
                ProductEntity(
                    category = category,
                    name = name,
                    price = price,
                    imageUri = imageUri
                )
            )

            val currentIp = ExportManager.getPcIp(context)
            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                ExportManager.createProductOnPc(
                    pcIpAddress = currentIp,
                    category = category,
                    name = name,
                    price = price,
                    imageUri = imageUri
                )
            }
        }
    }

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProduct(product)
        }
    }

    fun updateProductFromMobile(product: ProductEntity, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProduct(product)
            val currentIp = ExportManager.getPcIp(context)
            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                ExportManager.updateProductOnPc(
                    pcIpAddress = currentIp,
                    productId = product.id,
                    category = product.category,
                    name = product.name,
                    price = product.price,
                    imageUri = product.imageUri
                )
            }
        }
    }

    fun deleteProductFromMobile(product: ProductEntity, context: android.content.Context) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteProduct(product)
            val currentIp = ExportManager.getPcIp(context)
            if (currentIp.isNotBlank() && currentIp != "192.168.x.xx") {
                ExportManager.deleteProductOnPc(
                    pcIpAddress = currentIp,
                    productId = product.id
                )
            }
        }
    }

    // --- SINCRONIZACIÓN DESDE EL SERVIDOR MADRE (PC) ---

    fun syncAreasFromPc(pcAreas: List<AreaEntity>, pcTableCounts: Map<Int, Int> = emptyMap()) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentAreasList = dao.getAllAreas().first()

            // 1. Eliminar salas que ya no existan en la PC
            val pcAreaNames = pcAreas.map { it.name.trim().lowercase() }.toSet()
            for (localArea in currentAreasList) {
                if (!pcAreaNames.contains(localArea.name.trim().lowercase())) {
                    dao.deleteArea(localArea)
                }
            }

            // 2. Sincronizar y asegurar conteo exacto de mesas por sala
            for (pcArea in pcAreas) {
                val existing = dao.getAllAreas().first().find { it.name.trim().equals(pcArea.name.trim(), ignoreCase = true) }
                val targetAreaId = if (existing == null) {
                    dao.insertArea(AreaEntity(id = pcArea.id, name = pcArea.name, prefix = pcArea.prefix)).toInt()
                } else {
                    if (existing.prefix != pcArea.prefix) {
                        dao.insertArea(existing.copy(prefix = pcArea.prefix))
                    }
                    existing.id
                }

                val existingTables = dao.getTablesForAreaOnce(targetAreaId)
                val expectedCount = pcTableCounts[pcArea.id] ?: 10

                // Deduplicar mesas
                val seenNumbers = mutableSetOf<Int>()
                val duplicates = mutableListOf<TableEntity>()
                for (t in existingTables) {
                    if (!seenNumbers.add(t.number)) {
                        duplicates.add(t)
                    }
                }
                duplicates.forEach { dao.deleteTable(it) }

                val cleanExisting = dao.getTablesForAreaOnce(targetAreaId)
                val cleanNumbers = cleanExisting.map { it.number }.toSet()

                for (i in 1..expectedCount) {
                    if (!cleanNumbers.contains(i)) {
                        dao.insertTable(TableEntity(areaId = targetAreaId, number = i))
                    }
                }

                cleanExisting.filter { it.number > expectedCount && !it.isOccupied }.forEach { dao.deleteTable(it) }
            }

            if (_selectedAreaId.value == null && pcAreas.isNotEmpty()) {
                val firstId = dao.getAllAreas().first().firstOrNull()?.id
                if (firstId != null) {
                    selectArea(firstId)
                }
            }
        }
    }

    fun syncProductsFromPc(pcProducts: List<ProductEntity>) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentProductsList = dao.getAllProducts().first()

            // 1. Eliminar productos de SQLite que fueron borrados en la PC Madre
            val pcNames = pcProducts.map { it.name.trim().lowercase() }.toSet()
            for (localProd in currentProductsList) {
                if (!pcNames.contains(localProd.name.trim().lowercase())) {
                    dao.deleteProduct(localProd)
                }
            }

            // 2. Insertar nuevos o actualizar modificados (precio, categoría, foto)
            for (pcProd in pcProducts) {
                val existing = currentProductsList.find { it.name.trim().equals(pcProd.name.trim(), ignoreCase = true) }
                if (existing == null) {
                    dao.insertProduct(pcProd)
                } else if (
                    Math.abs(existing.price - pcProd.price) > 0.01 ||
                    !existing.category.equals(pcProd.category, ignoreCase = true) ||
                    existing.imageUri != pcProd.imageUri
                ) {
                    dao.updateProduct(
                        existing.copy(
                            price = pcProd.price,
                            category = pcProd.category,
                            imageUri = pcProd.imageUri
                        )
                    )
                }
            }
        }
    }

    fun getOrderItemsForTable(tableId: Int): StateFlow<List<OrderItemEntity>> {
        return dao.getOrderItemsForTable(tableId)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())
    }

    fun saveOrderForTable(tableId: Int, items: List<Pair<ProductEntity, Int>>, total: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            markTableLocallyChanged(tableId)
            dao.clearOrderItemsForTable(tableId)

            if (items.isNotEmpty() && total > 0.0) {
                for ((product, qty) in items) {
                    repeat(qty) {
                        dao.insertOrderItem(
                            OrderItemEntity(
                                tableId = tableId,
                                productName = product.name.trim(),
                                price = product.price,
                                courseGroup = "General"
                            )
                        )
                    }
                }
                dao.updateTableStatus(tableId, total = total, isOccupied = true)
            } else {
                dao.updateTableStatus(tableId, total = 0.0, isOccupied = false)
            }
        }
    }

    fun payTableDirectly(tableId: Int, items: List<Pair<ProductEntity, Int>>, total: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            markTableLocallyChanged(tableId)
            // Eliminar cualquier item sin pagar que quedara en la mesa (para no duplicar el conteo)
            dao.clearOrderItemsForTable(tableId)
            // Registrar los items vendidos con tableId = -1 (pagados) para que
            // aparezcan en el Cierre de Día del teléfono, aunque no se haya
            // enviado la comanda previamente.
            for ((product, qty) in items) {
                repeat(qty) {
                    dao.insertOrderItem(
                        OrderItemEntity(
                            tableId = -1,
                            productName = product.name.trim(),
                            price = product.price,
                            courseGroup = "General"
                        )
                    )
                }
            }
            dao.updateTableStatus(tableId, total = 0.0, isOccupied = false)
        }
    }

    // Ventas del día provenientes de la PC (fuente unificada del cierre de día).
    // La PC acumula las ventas cobradas en la PC Y las cobradas en el teléfono
    // (que se envían vía /sale), así ambos lados muestran lo mismo.
    private val _pcDailyItems = MutableStateFlow<List<OrderItemEntity>>(emptyList())
    val pcDailyItems: StateFlow<List<OrderItemEntity>> = _pcDailyItems.asStateFlow()

    // Marca del último "finalizar día" realizado en la PC, para que el teléfono lo detecte
    // y se reinicie también (sincronización del botón finalizar día).
    private val _pcDayReset = MutableStateFlow(0.0)

    // Items combinados para el Cierre de Día: ventas de la PC + ventas locales del
    // teléfono que todavía no están reflejadas en la PC (para evitar duplicar, ya que
    // las ventas del teléfono también se envían a la PC vía /sale).
    val combinedEndDayItems: StateFlow<List<OrderItemEntity>> = combine(
        pendingPaidItems,
        _pcDailyItems
    ) { localItems, pcItems ->
        val pcQty = pcItems.groupingBy { it.productName.trim().lowercase() }.eachCount()
        val result = pcItems.toMutableList()
        val localCounts = mutableMapOf<String, Int>()
        for (item in localItems) {
            val key = item.productName.trim().lowercase()
            val localSoFar = localCounts.getOrDefault(key, 0)
            val pcCubierto = pcQty.getOrDefault(key, 0)
            if (localSoFar + 1 > pcCubierto) {
                result.add(item)
            }
            localCounts[key] = localSoFar + 1
        }
        result
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun updatePcDailySales(sales: List<OrderItemEntity>) {
        _pcDailyItems.value = sales
    }

    // Cuando la PC finaliza el día, limpiamos también nuestros registros locales.
    fun handlePcDayReset(resetTs: Double) {
        if (resetTs > _pcDayReset.value) {
            _pcDayReset.value = resetTs
            _pcDailyItems.value = emptyList()
            viewModelScope.launch(Dispatchers.IO) {
                dao.clearAllPaidOrderItems()
            }
        }
    }

    fun closeDayAndSaveReport(
        context: Context,
        totalItems: Int,
        totalRevenue: Double,
        onFinished: () -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDailyReport(
                DailyReportEntity(
                    totalSalesCount = totalItems,
                    totalRevenue = totalRevenue
                )
            )
            dao.clearAllPaidOrderItems()
            _pcDailyItems.value = emptyList()

            // Sincronizar el cierre con la PC: finalizar el día también en ella.
            val pcIp = ExportManager.getPcIp(context)
            if (pcIp.isNotBlank() && pcIp != "192.168.x.xx") {
                ExportManager.registerDayResetOnPc(pcIp)
            }

            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }
}