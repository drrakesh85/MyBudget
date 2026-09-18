package com.hackerai.mybudget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE isPendingReview = 0 AND isDiscarded = 0 AND isDeleted = 0 ORDER BY date DESC")
    fun getAllExpensesFlow(): kotlinx.coroutines.flow.Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isPendingReview = 0 AND isDiscarded = 0 AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isPendingReview = 1 AND isDiscarded = 0 AND isDeleted = 0 ORDER BY date DESC")
    suspend fun getPendingReviewExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isPendingReview = 1 AND isDiscarded = 0 AND isDeleted = 0 ORDER BY date DESC")
    fun getPendingReviewExpensesFlow(): kotlinx.coroutines.flow.Flow<List<ExpenseEntity>>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE rowId = :rowId)")
    suspend fun exists(rowId: String): Boolean

    @Query("UPDATE expenses SET isDiscarded = 1 WHERE rowId = :rowId")
    suspend fun markAsDiscarded(rowId: String)

    @Query("UPDATE expenses SET isDiscarded = 1 WHERE rowId IN (:rowIds)")
    suspend fun markAllAsDiscarded(rowIds: List<String>)

    @Query("UPDATE expenses SET isDiscarded = 1 WHERE isPendingReview = 1 AND date < :date")
    suspend fun discardOlderThan(date: String)

    @Query("UPDATE expenses SET isDiscarded = 1 WHERE isPendingReview = 1 AND date BETWEEN :startDate AND :endDate")
    suspend fun discardBetweenDates(startDate: String, endDate: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT rowId FROM expenses")
    suspend fun getAllRowIds(): List<String>

    @Query("SELECT rowId FROM expenses WHERE rowId IN (:ids)")
    suspend fun getExistingIds(ids: List<String>): List<String>

    @androidx.room.Transaction
    suspend fun fullRestore(expenses: List<ExpenseEntity>) {
        // We don't delete existing data unless specified, but for a "Full Restore" 
        // the user might expect an overwrite or a merge.
        // The instructions say: "Do NOT run an automatic cleanup of existing transactions during migration."
        // "Existing data must remain unchanged until the user explicitly performs a validated sync/restore."
        // During restore, we should probably clear and insert OR merge.
        // "Full Backup (JSON) must actually be a complete financial-data backup."
        // Given the requirement "Restore must be atomic... validation fails: database remains unchanged",
        // we'll insert them all in a transaction.
        insertAll(expenses)
    }

    @Query("SELECT COUNT(*) FROM expenses WHERE account = :accountName")
    suspend fun countByAccount(accountName: String): Int

    @Query("UPDATE expenses SET account = :newAccountName, lastModified = :timestamp WHERE account = :oldAccountName")
    suspend fun relocateTransactions(oldAccountName: String, newAccountName: String, timestamp: Long)

    @Query("UPDATE expenses SET isDeleted = 1, lastModified = :timestamp WHERE rowId = :rowId")
    suspend fun deleteById(rowId: String, timestamp: Long)

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName AND transactionType = :type")
    suspend fun renameCategory(oldName: String, newName: String, type: String)

    @Query("UPDATE expenses SET subcategory = :newName WHERE subcategory = :oldName AND category = :category AND transactionType = :type")
    suspend fun renameSubcategory(category: String, oldName: String, newName: String, type: String)

    @Query("UPDATE expenses SET tag = :newName WHERE tag = :oldName")
    suspend fun renameTag(oldName: String, newName: String)

    @Query("UPDATE expenses SET account = :newName WHERE account = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)

    @Query("SELECT * FROM expenses")
    suspend fun getAllForSync(): List<ExpenseEntity>

    @Query("DELETE FROM expenses WHERE status != 'system'")
    suspend fun deleteRealTransactions()
}
