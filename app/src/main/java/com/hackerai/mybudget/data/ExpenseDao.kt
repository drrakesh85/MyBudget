package com.hackerai.mybudget.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface ExpenseDao {

    @Query("SELECT * FROM expenses WHERE isPendingReview = 0 AND isDiscarded = 0 ORDER BY date DESC")
    fun getAllExpensesFlow(): kotlinx.coroutines.flow.Flow<List<ExpenseEntity>>

    @Query("SELECT * FROM expenses WHERE isPendingReview = 0 AND isDiscarded = 0 ORDER BY date DESC")
    suspend fun getAllExpenses(): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE isPendingReview = 1 AND isDiscarded = 0 ORDER BY date DESC")
    suspend fun getPendingReviewExpenses(): List<ExpenseEntity>

    @Query("SELECT COUNT(*) FROM expenses")
    suspend fun count(): Int

    @Query("SELECT EXISTS(SELECT 1 FROM expenses WHERE rowId = :rowId)")
    suspend fun exists(rowId: String): Boolean

    @Query("UPDATE expenses SET isDiscarded = 1 WHERE rowId = :rowId")
    suspend fun markAsDiscarded(rowId: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(expense: ExpenseEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(expenses: List<ExpenseEntity>)

    @Query("SELECT COUNT(*) FROM expenses WHERE account = :accountName")
    suspend fun countByAccount(accountName: String): Int

    @Query("UPDATE expenses SET account = :newAccountName WHERE account = :oldAccountName")
    suspend fun relocateTransactions(oldAccountName: String, newAccountName: String)

    @Query("DELETE FROM expenses WHERE rowId = :rowId")
    suspend fun deleteById(rowId: String)

    @Query("UPDATE expenses SET category = :newName WHERE category = :oldName AND transactionType = :type")
    suspend fun renameCategory(oldName: String, newName: String, type: String)

    @Query("UPDATE expenses SET subcategory = :newName WHERE subcategory = :oldName AND category = :category AND transactionType = :type")
    suspend fun renameSubcategory(category: String, oldName: String, newName: String, type: String)

    @Query("UPDATE expenses SET tag = :newName WHERE tag = :oldName")
    suspend fun renameTag(oldName: String, newName: String)

    @Query("UPDATE expenses SET account = :newName WHERE account = :oldName")
    suspend fun renameAccount(oldName: String, newName: String)
}
