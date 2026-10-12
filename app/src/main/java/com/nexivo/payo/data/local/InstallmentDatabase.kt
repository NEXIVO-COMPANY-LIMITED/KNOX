package com.nexivo.payo.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [InstallmentEntity::class], version = 1, exportSchema = false)
abstract class InstallmentDatabase : RoomDatabase() {
    abstract fun installmentDao(): InstallmentDao

    companion object {
        @Volatile
        private var INSTANCE: InstallmentDatabase? = null

        fun getDatabase(context: Context): InstallmentDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    InstallmentDatabase::class.java,
                    "payo_installments.db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
