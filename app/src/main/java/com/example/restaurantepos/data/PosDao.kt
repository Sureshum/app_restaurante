package com.example.restaurantepos.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PosDao {
    // --- Usuarios ---
    @Query("SELECT * FROM users")
    fun getAllUsers(): Flow<List<UserEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertUser(user: UserEntity)

    @Delete
    suspend fun deleteUser(user: UserEntity)


    // --- Áreas ---
    @Query("SELECT * FROM areas")
    fun getAllAreas(): Flow<List<AreaEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertArea(area: AreaEntity): Long

    @Delete
    suspend fun deleteArea(area: AreaEntity)


    // --- Mesas ---
    @Query("SELECT * FROM tables WHERE areaId = :areaId ORDER BY number ASC")
    fun getTablesByArea(areaId: Int): Flow<List<TableEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTable(table: TableEntity)

    @Delete
    suspend fun deleteTable(table: TableEntity)

    @Query("UPDATE tables SET currentTotal = :total, isOccupied = :isOccupied WHERE id = :tableId")
    suspend fun updateTableStatus(tableId: Int, total: Double, isOccupied: Boolean)


    // --- Productos ---
    @Query("SELECT * FROM products")
    fun getAllProducts(): Flow<List<ProductEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: ProductEntity)

    @Update
    suspend fun updateProduct(product: ProductEntity)


    // --- Comandas y Reportes ---
    @Query("SELECT * FROM order_items WHERE tableId = :tableId")
    fun getOrderItemsForTable(tableId: Int): Flow<List<OrderItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(item: OrderItemEntity)

    @Query("DELETE FROM order_items WHERE tableId = :tableId")
    suspend fun clearOrderItemsForTable(tableId: Int)

    @Query("DELETE FROM order_items")
    suspend fun clearAllOrderItems()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReport(report: DailyReportEntity): Long

    @Query("SELECT * FROM tables WHERE areaId = :areaId ORDER BY number ASC")
    suspend fun getTablesForAreaOnce(areaId: Int): List<TableEntity>

    @Query("SELECT * FROM order_items WHERE tableId = -1")
    suspend fun getAllPaidItems(): List<OrderItemEntity>

    @Query("SELECT * FROM order_items WHERE tableId = -1")
    fun getAllPaidItemsFlow(): Flow<List<OrderItemEntity>>

    @Query("UPDATE order_items SET tableId = -1 WHERE tableId = :tableId")
    suspend fun markOrderItemsAsPaid(tableId: Int)

    @Query("DELETE FROM order_items WHERE tableId = -1")
    suspend fun clearAllPaidOrderItems()
}