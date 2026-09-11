package com.hackerai.mybudget.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "expenses")
data class ExpenseEntity(
    @PrimaryKey val rowId: String,
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
    val typeId: String,
    val transactionType: String,
    val toAccount: String?,
    val isPendingReview: Boolean = false,
    val isDiscarded: Boolean = false,
    val lastModified: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false
)

fun Expense.toEntity(isPendingReview: Boolean = false, isDiscarded: Boolean = false) = ExpenseEntity(
    rowId = rowId,
    date = date,
    time = time,
    amount = amount,
    category = category,
    subcategory = subcategory,
    paymentMethod = paymentMethod,
    description = description,
    refCheckNo = refCheckNo,
    payeePayer = payeePayer,
    status = status,
    receiptPicture = receiptPicture,
    account = account,
    tag = tag,
    tax = tax,
    quantity = quantity,
    unit = unit,
    splitTotal = splitTotal,
    typeId = typeId,
    transactionType = transactionType,
    toAccount = toAccount,
    isPendingReview = isPendingReview,
    isDiscarded = isDiscarded,
    lastModified = lastModified,
    isDeleted = isDeleted
)

fun ExpenseEntity.toExpense() = Expense(
    date = date,
    time = time,
    amount = amount,
    category = category,
    subcategory = subcategory,
    paymentMethod = paymentMethod,
    description = description,
    refCheckNo = refCheckNo,
    payeePayer = payeePayer,
    status = status,
    receiptPicture = receiptPicture,
    account = account,
    tag = tag,
    tax = tax,
    quantity = quantity,
    unit = unit,
    splitTotal = splitTotal,
    rowId = rowId,
    typeId = typeId,
    transactionType = transactionType,
    toAccount = toAccount,
    lastModified = lastModified,
    isDeleted = isDeleted
)
