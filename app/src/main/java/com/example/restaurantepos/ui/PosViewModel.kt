package com.example.restaurantepos.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.restaurantepos.data.AreaEntity
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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class PosViewModel(private val dao: PosDao) : ViewModel() {

    val users: StateFlow<List<UserEntity>> = dao.getAllUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val areas: StateFlow<List<AreaEntity>> = dao.getAllAreas()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val products: StateFlow<List<ProductEntity>> = dao.getAllProducts()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _selectedAreaId = MutableStateFlow<Int?>(null)
    val selectedAreaId: StateFlow<Int?> = _selectedAreaId.asStateFlow()

    private val _currentTables = MutableStateFlow<List<TableEntity>>(emptyList())
    val currentTables: StateFlow<List<TableEntity>> = _currentTables.asStateFlow()

    private val _currentUserSession = MutableStateFlow<ActiveSession?>(null)
    val currentUserSession: StateFlow<ActiveSession?> = _currentUserSession.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            areas.collect { areaList ->
                if (_selectedAreaId.value == null && areaList.isNotEmpty()) {
                    selectArea(areaList.first().id)
                }
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
            dao.insertUser(UserEntity(name = name, pinHash = hashedPin, avatarUri = avatarUri, role = role))
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
            dao.getTablesByArea(areaId).collect {
                _currentTables.value = it
            }
        }
    }

    fun addTable() {
        val areaId = _selectedAreaId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val count = _currentTables.value.size
            dao.insertTable(TableEntity(areaId = areaId, number = count + 1, currentTotal = 0.0, isOccupied = false))
        }
    }

    fun removeTable() {
        val areaId = _selectedAreaId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val lastTable = _currentTables.value.lastOrNull()
            if (lastTable != null && !lastTable.isOccupied) {
                dao.deleteTable(lastTable)
            }
        }
    }

    fun setTableCount(count: Int) {
        val areaId = _selectedAreaId.value ?: return
        viewModelScope.launch(Dispatchers.IO) {
            val currentCount = _currentTables.value.size
            if (count > currentCount) {
                for (i in (currentCount + 1)..count) {
                    dao.insertTable(TableEntity(areaId = areaId, number = i, currentTotal = 0.0, isOccupied = false))
                }
            } else if (count < currentCount) {
                val tablesToRemove = _currentTables.value.takeLast(currentCount - count)
                tablesToRemove.filter { !it.isOccupied }.forEach { dao.deleteTable(it) }
            }
        }
    }

    fun createArea(name: String, prefix: String) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertArea(AreaEntity(name = name, prefix = prefix))
        }
    }

    fun createProduct(category: String, name: String, price: Double) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProduct(ProductEntity(category = category, name = name, price = price))
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
                                productName = product.name,
                                price = product.price,
                                courseGroup = "General"
                            )
                        )
                    }
                }
                dao.updateTableStatus(tableId, total, isOccupied = true)
            } else {
                dao.updateTableStatus(tableId, 0.0, isOccupied = false)
            }
        }
    }

    // Agrega esta función para actualizar productos existentes
    fun updateProduct(product: ProductEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.updateProduct(product)
        }
    }

    // Actualiza createProduct para aceptar imageUri
    fun createProduct(category: String, name: String, price: Double, imageUri: String? = null) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.insertProduct(ProductEntity(category = category, name = name, price = price, imageUri = imageUri))
        }
    }

    fun deleteArea(area: AreaEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.deleteArea(area)
            if (_selectedAreaId.value == area.id) {
                _selectedAreaId.value = areas.value.firstOrNull { it.id != area.id }?.id
            }
        }
    }

    fun payTable(tableId: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            dao.clearOrderItemsForTable(tableId)
            dao.updateTableStatus(tableId, 0.0, isOccupied = false)
        }
    }
}

class PosViewModelFactory(private val dao: PosDao) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PosViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return PosViewModel(dao) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}