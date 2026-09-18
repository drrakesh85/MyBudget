package com.hackerai.mybudget.data

import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class Expense(
    val date: String,
    val time: String = "",
    val amount: Double,
    val category: String,
    val subcategory: String,
    val paymentMethod: String,
    val description: String,
    val refCheckNo: String,
    val payeePayer: String,
    val status: String,
    val receiptPicture: String,
    val account: String,
    val tag: String,
    val tax: String,
    val quantity: Double,
    val unit: String,
    val splitTotal: String,
    val rowId: String,
    val typeId: String,
    val transactionType: String = "Expense",
    val toAccount: String? = null,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val dateMillis: Long = 0L // Pre-calculated for performance
) {
    /**
     * Generates a deterministic identity for a transaction based on its core properties.
     * Forced to Locale.US to ensure decimal consistency across different device locales.
     */
    fun calculateFingerprint(): String {
        return listOf(
            date,
            time,
            String.format(java.util.Locale.US, "%.2f", amount),
            account,
            payeePayer,
            description,
            transactionType,
            toAccount ?: "",
            typeId
        ).joinToString("|")
    }

    companion object {
        private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault())
        private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
        private val zoneId = ZoneId.systemDefault()
        
        fun createEmpty() = Expense(
            date = LocalDate.now(zoneId).format(dateFormatter),
            time = java.time.LocalTime.now(zoneId).format(timeFormatter),
            amount = 0.0,
            category = "",
            subcategory = "",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "",
            status = "",
            receiptPicture = "",
            account = "",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "new_${java.util.UUID.randomUUID()}",
            typeId = "",
            transactionType = "Expense",
            lastModified = System.currentTimeMillis(),
            isDeleted = false
        )
    }
}
