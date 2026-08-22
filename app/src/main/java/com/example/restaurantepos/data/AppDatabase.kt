package com.example.restaurantepos.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@Database(
    entities = [UserEntity::class, AreaEntity::class, TableEntity::class, ProductEntity::class, OrderItemEntity::class],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun posDao(): PosDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "restaurante_pos_db"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(object : RoomDatabase.Callback() {
                        override fun onCreate(db: SupportSQLiteDatabase) {
                            super.onCreate(db)
                            INSTANCE?.let { database ->
                                CoroutineScope(Dispatchers.IO).launch {
                                    val dao = database.posDao()

                                    // Crear usuario Admin por defecto (PIN: 1234)
                                    val adminPinHash = SecurityUtils.hashPin("1234")
                                    dao.insertUser(
                                        UserEntity(
                                            name = "Admin",
                                            pinHash = adminPinHash,
                                            avatarUri = "",
                                            role = UserRole.ADMIN
                                        )
                                    )

                                    // Crear Area por defecto
                                    val areaId = dao.insertArea(
                                        AreaEntity(name = "Salon Principal", prefix = "M")
                                    ).toInt()

                                    // Crear Mesas por defecto
                                    for (i in 1..8) {
                                        dao.insertTable(
                                            TableEntity(areaId = areaId, number = i)
                                        )
                                    }
                                }
                            }
                        }
                    })
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}