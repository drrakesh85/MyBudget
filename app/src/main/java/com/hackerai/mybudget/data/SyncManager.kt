package com.hackerai.mybudget.data

import android.util.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class SyncManager(
    private val repository: ExpenseRepository,
    private val accountRepository: AccountRepository
) {

    private val syncLock = Mutex()

    /**
     * Merges a remote sync dataset with the local database safely.
     * Handles both transactions and account metadata.
     */
    suspend fun mergeSyncData(remoteData: SyncData): SyncData = syncLock.withLock {
        Log.i(TAG, "Starting sync merge. Remote transactions: ${remoteData.expenses.size}, Remote accounts: ${remoteData.accounts.size}")
        
        // 1. Validate Transactions
        val validRemoteExpenses = remoteData.expenses.filter { validateExpense(it) }
        
        // 2. Load Local Data
        val localExpenses = repository.getAllForSync()
        val localAccounts = accountRepository.accounts.value
        
        // 3. Merge Expenses
        val localById = localExpenses.associateBy { it.rowId }
        val localByFingerprint = localExpenses.filter { !it.isDeleted }.associateBy { it.calculateFingerprint() }
        
        val mergedExpensesMap = localById.toMutableMap()
        
        var duplicatesResolved = 0
        var conflictsUpdated = 0
        var newRemoteRecords = 0

        for (remote in validRemoteExpenses) {
            val existingById = localById[remote.rowId]
            
            // Logical match fallback for imported metadata or high-confidence SMS
            val remoteFingerprint = remote.calculateFingerprint()
            val existingByFingerprint = if (existingById == null) localByFingerprint[remoteFingerprint] else null
            
            if (existingById != null) {
                // Exact rowId match.
                if (remoteFingerprint == existingById.calculateFingerprint()) {
                    // Unambiguous same transaction data: Last Write Wins for status/category updates
                    if (remote.lastModified > existingById.lastModified) {
                        conflictsUpdated++
                        mergedExpensesMap[remote.rowId] = remote
                    }
                } else {
                    // COLLISION: Same rowId but materially different data.
                    // Preserve both records as per safety requirement.
                    val safeRowId = "${remote.rowId}_sync_conflict_${System.currentTimeMillis()}"
                    mergedExpensesMap[safeRowId] = remote.copy(rowId = safeRowId)
                    newRemoteRecords++
                }
            } else if (existingByFingerprint != null && shouldMergeLogicalDuplicate(remote, existingByFingerprint)) {
                // Logical match found with different rowId
                duplicatesResolved++
                // Preserve the local rowId but take remote data if it's newer
                if (remote.lastModified > existingByFingerprint.lastModified) {
                    conflictsUpdated++
                    mergedExpensesMap[existingByFingerprint.rowId] = remote.copy(rowId = existingByFingerprint.rowId)
                }
            } else {
                // New record
                newRemoteRecords++
                mergedExpensesMap[remote.rowId] = remote
            }
        }

        val finalExpenses = mergedExpensesMap.values.toList()

        // 4. Merge Accounts
        val mergedAccountsMap = localAccounts.associateBy { it.nickName }.toMutableMap()
        for (remoteAcc in remoteData.accounts) {
            val existing = mergedAccountsMap[remoteAcc.nickName]
            if (existing == null) {
                mergedAccountsMap[remoteAcc.nickName] = sanitizeAccount(remoteAcc)
            }
            // If local exists, we keep it to preserve local secrets/config
        }
        val finalAccounts = mergedAccountsMap.values.toList()

        // 5. Diagnostics
        Log.i(TAG, "Sync Merge Report:")
        Log.i(TAG, "  Expenses - Local: ${localExpenses.size}, Remote: ${validRemoteExpenses.size}, Final: ${finalExpenses.size}")
        Log.i(TAG, "  Duplicates Resolved: $duplicatesResolved, Conflicts Updated: $conflictsUpdated, New: $newRemoteRecords")
        Log.i(TAG, "  Accounts - Local: ${localAccounts.size}, Remote: ${remoteData.accounts.size}, Final: ${finalAccounts.size}")

        // 6. Atomic Commits
        repository.insertSyncData(finalExpenses)
        finalAccounts.forEach { accountRepository.addAccount(it) }

        return SyncData(
            lastSyncTimestamp = System.currentTimeMillis(),
            expenses = finalExpenses,
            accounts = finalAccounts
        )
    }

    /**
     * Identifies whether two records with different rowIds but identical fingerprints 
     * should be collapsed into one.
     */
    private fun shouldMergeLogicalDuplicate(remote: Expense, local: Expense): Boolean {
        // 1. Metadata/Placeholder entries (system status) should always be merged
        if (remote.status == "system" && local.status == "system") return true
        
        // 2. SMS transactions from the same bank with same amount/time.
        // We only merge if we have a stable millisecond timestamp (provenance) in typeId.
        if (remote.tag == "SMS" && local.tag == "SMS") {
            return remote.typeId.isNotBlank() && remote.typeId == local.typeId
        }
        
        // 3. Manual entries or ambiguous cases (blank typeId) are preserved as separate records.
        // This prevents collapsing legitimate identical transactions.
        return false
    }

    private fun validateExpense(exp: Expense): Boolean {
        if (exp.rowId.isBlank()) return false
        if (exp.date.isBlank()) return false
        if (exp.amount.isNaN() || exp.amount.isInfinite()) return false
        // Strict threshold: No single transaction can be > 1 Billion INR.
        // This prevents the "Account Number/Utility ID interpreted as Amount" corruption.
        if (kotlin.math.abs(exp.amount) > 1_000_000_000.0) return false
        return true
    }

    private fun sanitizeAccount(account: Account): Account {
        return when (account) {
            is CreditCardAccount -> account.copy(cvv = "")
            else -> account
        }
    }

    companion object {
        private const val TAG = "SyncManager"
    }
}
