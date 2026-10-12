package com.nexivo.payo.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface InstallmentDao {
    @Query("SELECT * FROM installments WHERE deviceId = :deviceId ORDER BY installmentNumber ASC")
    suspend fun getInstallments(deviceId: String): List<InstallmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertInstallments(installments: List<InstallmentEntity>)

    @Query("DELETE FROM installments WHERE deviceId = :deviceId")
    suspend fun clearInstallments(deviceId: String)
}
