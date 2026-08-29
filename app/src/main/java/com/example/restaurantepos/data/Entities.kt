package com.example.restaurantepos.data

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

enum class UserRole { ADMIN, WAITER }

@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val pinHash: String,
    val avatarUri: String,
    val role: UserRole = UserRole.WAITER
)

@Entity(tableName = "areas")
data class AreaEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val prefix: String
)

@Entity(
    tableName = "tables",
    foreignKeys = [
        ForeignKey(
            entity = AreaEntity::class,
            parentColumns = ["id"],
            childColumns = ["areaId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class TableEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val areaId: Int,
    val number: Int,
    val isOccupied: Boolean = false,
    val currentTotal: Double = 0.0
)

@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val category: String,
    val name: String,
    val price: Double,
    val imageUri: String? = null
)

@Entity(tableName = "order_items")
data class OrderItemEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Int = 0,
    val tableId: Int,
    val productName: String,
    val price: Double,
    val courseGroup: String = "General"
)