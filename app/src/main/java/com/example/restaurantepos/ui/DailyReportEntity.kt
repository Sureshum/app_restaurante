package com.example.restaurantepos.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "daily_reports")
data class DailyReportEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val dateTimestamp: Long = System.currentTimeMillis(),
    val totalSalesCount: Int,
    val totalRevenue: Double
)