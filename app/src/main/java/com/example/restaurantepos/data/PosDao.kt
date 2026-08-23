package com.example.restaurantepos.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Query("SELECT * FROM areas")
    fun getAllAreas(): Flow<List<AreaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: AreaEntity): Long

    @Query("SELECT * FROM tables WHERE areaId = :areaId ORDER BY number ASC")
    fun getTablesByArea(areaId: Int): Flow<List<TableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableEntity)

    @Delete
    suspend fun deleteTable(table: TableEntity)

    @Query("DELETE FROM tables WHERE id = (SELECT id FROM tables WHERE areaId = :areaId ORDER BY number DESC LIMIT 1)")
    suspend fun deleteLastTableFromArea(areaId: Int)

    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Query("SELECT * FROM order_items WHERE tableId = :tableId")
    fun getOrderItemsForTable(tableId: Int): Flow<List<OrderItemEntity>>

    @Insert
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)

    @Query("DELETE FROM order_items WHERE tableId = :tableId")
    suspend fun clearOrderItemsForTable(tableId: Int)

    @Delete
    suspend fun deleteArea(area: AreaEntity)

    @Query("UPDATE tables SET currentTotal = :total, isOccupied = :isOccupied WHERE id = :tableId")
    suspend fun updateTableStatus(tableId: Int, total: Double, isOccupied: Boolean)
}