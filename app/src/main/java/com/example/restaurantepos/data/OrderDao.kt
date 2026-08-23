package com.example.restaurantepos.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface OrderDao {

    @Query("SELECT * FROM order_items WHERE isPaid = 1 AND isClosed = 0")
    suspend fun getPendingPaidItems(): List<OrderItemEntity>

    @Query("UPDATE order_items SET isClosed = 1 WHERE isPaid = 1")
    suspend fun markItemsAsClosed()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDailyReport(report: DailyReportEntity)

    @Query("SELECT * FROM daily_reports ORDER BY dateTimestamp DESC")
    suspend fun getAllDailyReports(): List<DailyReportEntity>
}