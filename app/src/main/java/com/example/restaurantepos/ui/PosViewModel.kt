package com.example.restaurantepos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.restaurantepos.data.AreaEntity
import com.example.restaurantepos.data.DailyReportEntity
import com.example.restaurantepos.data.OrderItemEntity
import com.example.restaurantepos.data.PosDao
import com.example.restaurantepos.data.ProductEntity
import com.example.restaurantepos.data.SecurityUtils
import com.example.restaurantepos.data.TableEntity
import com.example.restaurantepos.data.UserEntity
import com.example.restaurantepos.data.UserRole
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
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

    private val _currentTables = MutableStateFlow<List<TableEntity>>(emptyList())
    val currentTables: StateFlow<List<TableEntity>> = _currentTables.asStateFlow()

    private val _currentUserSession = MutableStateFlow<ActiveSession?>(null)
    val currentUserSession: StateFlow<ActiveSession?> = _currentUserSession.asStateFlow()

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
        viewModelScope.launch(Dispatchers.IO) {
            dao.getTablesByArea(areaId).collect { rawTables ->
                val distinctTables = rawTables.distinctBy { it.number }.sortedBy { it.number }
                _currentTables.value = distinctTables
            }
        }
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

    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProduct(product)
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
            for (pcProd in pcProducts) {
                val existing = currentProductsList.find { it.name.trim().equals(pcProd.name.trim(), ignoreCase = true) }
                if (existing == null) {
                    dao.insertProduct(pcProd)
                } else if (Math.abs(existing.price - pcProd.price) > 0.01 || !existing.category.equals(pcProd.category, ignoreCase = true)) {
                    dao.updateProduct(existing.copy(price = pcProd.price, category = pcProd.category))
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
            dao.markOrderItemsAsPaid(tableId)
            dao.clearOrderItemsForTable(tableId)
            dao.updateTableStatus(tableId, total = 0.0, isOccupied = false)
        }
    }

    fun closeDayAndSaveReport(totalItems: Int, totalRevenue: Double, onFinished: () -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertDailyReport(
                DailyReportEntity(
                    totalSalesCount = totalItems,
                    totalRevenue = totalRevenue
                )
            )
            dao.clearAllPaidOrderItems()
            withContext(Dispatchers.Main) {
                onFinished()
            }
        }
    }
}