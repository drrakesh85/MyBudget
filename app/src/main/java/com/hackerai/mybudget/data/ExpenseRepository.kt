package com.hackerai.mybudget.data

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class ExpenseRepository(
    private val context: Context,
    private val expenseDao: ExpenseDao
) {

    suspend fun loadExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.getAllExpenses().map { it.toExpense() }
    }

    fun allExpensesFlow(): kotlinx.coroutines.flow.Flow<List<Expense>> =
        expenseDao.getAllExpensesFlow().map { list -> list.map { it.toExpense() } }

    suspend fun loadPendingReviewExpenses(): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.getPendingReviewExpenses().map { it.toExpense() }
    }

    suspend fun getTransactionCountForAccount(accountName: String): Int = withContext(Dispatchers.IO) {
        expenseDao.countByAccount(accountName)
    }

    suspend fun relocateTransactions(oldAccount: String, newAccount: String) = withContext(Dispatchers.IO) {
        expenseDao.relocateTransactions(oldAccount, newAccount, System.currentTimeMillis())
    }

    suspend fun saveExpense(expense: Expense, isPendingReview: Boolean = false, isDiscarded: Boolean = false) = withContext(Dispatchers.IO) {
        val updated = expense.copy(lastModified = System.currentTimeMillis())
        expenseDao.insert(updated.toEntity(isPendingReview, isDiscarded))
    }

    suspend fun approveExpense(expense: Expense) = withContext(Dispatchers.IO) {
        val updated = expense.copy(lastModified = System.currentTimeMillis())
        expenseDao.insert(updated.toEntity(isPendingReview = false, isDiscarded = false))
    }

    suspend fun exists(rowId: String): Boolean = withContext(Dispatchers.IO) {
        expenseDao.exists(rowId)
    }

    suspend fun deleteById(rowId: String) = withContext(Dispatchers.IO) {
        expenseDao.deleteById(rowId, System.currentTimeMillis())
    }

    suspend fun markAsDiscarded(rowId: String) = withContext(Dispatchers.IO) {
        expenseDao.markAsDiscarded(rowId)
    }

    suspend fun renameCategory(oldName: String, newName: String, type: String) = withContext(Dispatchers.IO) {
        expenseDao.renameCategory(oldName, newName, type)
    }

    suspend fun renameSubcategory(category: String, oldName: String, newName: String, type: String) = withContext(Dispatchers.IO) {
        expenseDao.renameSubcategory(category, oldName, newName, type)
    }

    suspend fun renameTag(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        expenseDao.renameTag(oldName, newName)
    }

    suspend fun renameAccount(oldName: String, newName: String) = withContext(Dispatchers.IO) {
        expenseDao.renameAccount(oldName, newName)
    }

    suspend fun addDummyTransaction(category: String, subcategory: String, type: String) = withContext(Dispatchers.IO) {
        val dummy = Expense.createEmpty().copy(
            category = category,
            subcategory = subcategory,
            transactionType = type,
            amount = 0.0,
            status = "system"
        )
        expenseDao.insert(dummy.toEntity(isPendingReview = false))
    }

    suspend fun addDummyTag(tag: String) = withContext(Dispatchers.IO) {
        val dummy = Expense.createEmpty().copy(
            tag = tag,
            amount = 0.0,
            status = "system"
        )
        expenseDao.insert(dummy.toEntity(isPendingReview = false, isDiscarded = false))
    }

    suspend fun insertPendingIfNew(expense: Expense): Boolean = withContext(Dispatchers.IO) {
        if (expenseDao.exists(expense.rowId)) return@withContext false
        expenseDao.insert(expense.toEntity(isPendingReview = true))
        true
    }

    suspend fun scanAndStoreSmsExpenses(messages: List<SmsMessage>): List<Expense> = withContext(Dispatchers.IO) {
        val detected = messages.mapNotNull { TransactionParser.parse(it) }
        val newExpenses = detected.filter { expense ->
            !expenseDao.exists(expense.rowId)
        }
        if (newExpenses.isNotEmpty()) {
            expenseDao.insertAll(newExpenses.map { it.toEntity(isPendingReview = true, isDiscarded = false) })
        }
        expenseDao.getPendingReviewExpenses().map { it.toExpense() }
    }

    suspend fun importFromCsv() = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open("expensemanager.csv")
            val csvExpenses = CsvParser.parse(inputStream)
            if (csvExpenses.isNotEmpty()) {
                val now = System.currentTimeMillis()
                expenseDao.insertAll(csvExpenses.map { it.copy(lastModified = now).toEntity(isPendingReview = false) })
            }
        } catch (e: Exception) {
            Log.w("ExpenseRepository", "CSV import failed", e)
        }
    }

    suspend fun getAllForSync(): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.getAllForSync().map { it.toExpense() }
    }

    suspend fun insertSyncData(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        expenseDao.insertAll(expenses.map { it.toEntity() })
    }

    suspend fun generateCsvData(): String = withContext(Dispatchers.IO) {
        val expenses = expenseDao.getAllExpenses().map { it.toExpense() }
        val sb = StringBuilder()
        
        // Header matching your source CSV format
        sb.append("Date,Amount,Category,Subcategory,Payment Mode,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row ID,Type ID,Transaction Type,To Account\n")
        
        expenses.forEach { exp ->
            val fields = listOf(
                exp.date,
                exp.amount.toString(),
                exp.category,
                exp.subcategory,
                exp.paymentMethod,
                exp.description,
                exp.refCheckNo,
                exp.payeePayer,
                exp.status,
                exp.receiptPicture,
                exp.account,
                exp.tag,
                exp.tax,
                exp.quantity.toString(),
                exp.unit,
                exp.splitTotal,
                exp.rowId,
                exp.typeId,
                exp.transactionType,
                exp.toAccount ?: ""
            ).map { field ->
                "\"${field.replace("\"", "\"\"")}\""
            }
            sb.append(fields.joinToString(",")).append("\n")
        }
        sb.toString()
    }
}
