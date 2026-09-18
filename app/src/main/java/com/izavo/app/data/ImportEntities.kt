package com.izavo.app.data

import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceBank: String,
    val fileName: String,
    val importedAt: Long,
    val totalRows: Int,
    val invalidRows: Int
)

@Entity(
    tableName = "imported_bank_transactions",
    indices = [Index(value = ["fingerprint"], unique = true), Index(value = ["importBatchId"])]
)
data class ImportedBankTransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceBank: String,
    val primaryReference: String?,
    val secondaryReference: String?,
    val postedAt: Long,
    val valueAt: Long?,
    val transactionAt: Long?,
    val rawType: String,
    val rawDescription: String,
    val rawDetail: String,
    val direction: String,
    val amountMinor: Long,
    val currencyCode: String,
    val runningBalanceMinor: Long?,
    val normalizedType: String,
    val reviewStatus: String,
    val fingerprint: String,
    val importBatchId: Long,
    val importedAt: Long,
    val linkedExpenseId: Long?,
    val normalizedIdentity: String?,
    val classificationConfidence: String,
    val categoryConfidence: String?
)

@Entity(
    tableName = "merchant_rules",
    indices = [
        Index(value = ["sourceBank", "normalizedIdentity", "bankRail", "direction"], unique = true),
        Index(value = ["normalizedIdentity"])
    ]
)
data class MerchantRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sourceBank: String,
    val normalizedIdentity: String,
    val bankRail: String,
    val direction: String,
    val classification: String,
    val category: String?,
    val source: String,
    val confidence: String,
    val userConfirmed: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)
