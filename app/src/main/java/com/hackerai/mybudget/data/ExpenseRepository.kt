package com.hackerai.mybudget.data

import android.content.Context
import android.net.Uri
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

    fun getPendingReviewExpensesFlow(): kotlinx.coroutines.flow.Flow<List<Expense>> =
        expenseDao.getPendingReviewExpensesFlow().map { list -> list.map { it.toExpense() } }

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

    suspend fun saveExpenses(expenses: List<Expense>, isPendingReview: Boolean = false, isDiscarded: Boolean = false) = withContext(Dispatchers.IO) {
        if (expenses.isEmpty()) return@withContext
        val now = System.currentTimeMillis()
        val entities = expenses.map { it.copy(lastModified = now).toEntity(isPendingReview, isDiscarded) }
        expenseDao.insertAll(entities)
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

    suspend fun discardMultiple(rowIds: List<String>) = withContext(Dispatchers.IO) {
        expenseDao.markAllAsDiscarded(rowIds)
    }

    suspend fun discardOlderThan(dateStr: String) = withContext(Dispatchers.IO) {
        expenseDao.discardOlderThan(dateStr)
    }

    suspend fun discardBetween(start: String, end: String) = withContext(Dispatchers.IO) {
        expenseDao.discardBetweenDates(start, end)
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
        if (messages.isEmpty()) return@withContext emptyList()
        
        val detected = messages.mapNotNull { TransactionParser.parse(it) }
        if (detected.isEmpty()) return@withContext emptyList()

        val candidateIds = detected.map { it.rowId }
        val existingIds = expenseDao.getExistingIds(candidateIds).toSet()

        val newExpenses = detected.filter { !existingIds.contains(it.rowId) }

        if (newExpenses.isNotEmpty()) {
            expenseDao.insertAll(newExpenses.map { it.toEntity(isPendingReview = true, isDiscarded = false) })
        }
        expenseDao.getPendingReviewExpenses().map { it.toExpense() }
    }

    suspend fun importFromCsv() = withContext(Dispatchers.IO) {
        try {
            val inputStream = context.assets.open("expensemanager.csv")
            importFromStream(inputStream)
        } catch (e: Exception) {
            Log.w("ExpenseRepository", "Default CSV import failed", e)
        }
    }

    suspend fun importFromStream(inputStream: java.io.InputStream) = withContext(Dispatchers.IO) {
        try {
            val csvExpenses = CsvParser.parse(inputStream)
            if (csvExpenses.isNotEmpty()) {
                val now = System.currentTimeMillis()
                expenseDao.insertAll(csvExpenses.map { it.copy(lastModified = now).toEntity(isPendingReview = false) })
            }
        } catch (e: Exception) {
            Log.e("ExpenseRepository", "Stream import failed", e)
        }
    }

    suspend fun importFromUri(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        val beforeCount = expenseDao.count()
        Log.d("CSV_IMPORT", "Starting import from URI: $uri. Database count before: $beforeCount")
        
        try {
            context.contentResolver.openInputStream(uri).use { stream ->
                if (stream == null) {
                    return@withContext ImportResult(false, 0, 0, 0, "Failed to open input stream")
                }
                
                val csvExpenses = CsvParser.parse(stream)
                val parsedCount = csvExpenses.size
                Log.d("CSV_IMPORT", "Parsed $parsedCount records from CSV")
                
                if (csvExpenses.isEmpty()) {
                    return@withContext ImportResult(true, 0, 0, 0)
                }
                
                val now = System.currentTimeMillis()
                val entities = csvExpenses.map { it.copy(lastModified = now).toEntity(isPendingReview = false) }
                
                expenseDao.insertAll(entities)
                
                val afterCount = expenseDao.count()
                val insertedCount = afterCount - beforeCount
                // Note: since it's REPLACE, insertedCount might be 0 if all were overwrites, 
                // but usually we want to know how many were attempted.
                
                Log.d("CSV_IMPORT", "Import complete. Attempted: $parsedCount, Database count after: $afterCount")
                
                ImportResult(
                    success = true,
                    parsedCount = parsedCount,
                    insertedCount = parsedCount, // Attempted inserts
                    skippedCount = 0
                )
            }
        } catch (e: Exception) {
            Log.e("CSV_IMPORT", "Import failed for URI: $uri", e)
            ImportResult(false, 0, 0, 0, e.message ?: "Unknown error during import")
        }
    }

    suspend fun getAllForSync(): List<Expense> = withContext(Dispatchers.IO) {
        expenseDao.getAllForSync().map { it.toExpense() }
    }

    suspend fun getAllRowIds(): Set<String> = withContext(Dispatchers.IO) {
        expenseDao.getAllRowIds().toSet()
    }

    suspend fun insertSyncData(expenses: List<Expense>) = withContext(Dispatchers.IO) {
        expenseDao.fullRestore(expenses.map { it.toEntity() })
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

    suspend fun clearAllTransactionsPreservingMetadata() = withContext(Dispatchers.IO) {
        expenseDao.deleteRealTransactions()
    }
}

data class ImportResult(
    val success: Boolean,
    val parsedCount: Int,
    val insertedCount: Int,
    val skippedCount: Int,
    val errorMessage: String? = null
)
