package com.nexivo.payo.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "installments")
data class InstallmentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val deviceId: String,
    val installmentNumber: Int,
    val dueDate: String?,
    val amount: String?,
    val status: String?,
    val cachedAtEpochMs: Long = System.currentTimeMillis()
)
