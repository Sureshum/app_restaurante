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

            // 1. Eliminar salas que ya no existan en la PC Madre
            val pcAreaIds = pcAreas.map { it.id }.toSet()
            for (localArea in currentAreasList) {
                if (!pcAreaIds.contains(localArea.id)) {
                    dao.deleteArea(localArea)
                }
            }

            // 2. Insertar o actualizar salas con su ID exacto de la PC
            for (pcArea in pcAreas) {
                dao.insertArea(AreaEntity(id = pcArea.id, name = pcArea.name, prefix = pcArea.prefix))

                val expectedCount = pcTableCounts[pcArea.id] ?: 10
                val existingTables = dao.getTablesForAreaOnce(pcArea.id)
                val existingNumbers = existingTables.map { it.number }.toSet()

                // Asegurar que existan exactamente las mesas 1..expectedCount con su ID determinista
                for (num in 1..expectedCount) {
                    if (!existingNumbers.contains(num)) {
                        dao.insertTable(
                            TableEntity(
                                id = pcArea.id * 1000 + num,
                                areaId = pcArea.id,
                                number = num,
                                isOccupied = false,
                                currentTotal = 0.0
                            )
                        )
                    }
                }

                // Eliminar mesas sobrantes (> expectedCount) que no estén ocupadas
                existingTables.filter { it.number > expectedCount && !it.isOccupied }.forEach {
                    dao.deleteTable(it)
                }
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