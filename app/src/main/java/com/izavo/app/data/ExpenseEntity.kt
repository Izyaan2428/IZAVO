package com.izavo.app.data

import androidx.room3.Entity
import androidx.room3.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    val amountMinor: Long,

    val currencyCode: String = HOME_CURRENCY_CODE,

    val category: String?,

    val note: String,

    val occurredAt: Long,

    val createdAt: Long = System.currentTimeMillis()
)
