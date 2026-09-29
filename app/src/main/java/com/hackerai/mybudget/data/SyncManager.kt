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
     * Validates cloud/remote SyncData payload before any modification to local storage.
     * Throws an exception if schema version is invalid, expenses are invalid/cannot convert,
     * or accounts are invalid.
     */
    fun validateRemoteSyncData(remoteData: SyncData): SyncData {
        if (remoteData.schemaVersion < 1) {
            throw Exception("Invalid backup file: unsupported schema version (${remoteData.schemaVersion})")
        }

        for (expense in remoteData.expenses) {
            if (!validateExpense(expense)) {
                throw Exception("Invalid expense record in remote data: rowId=${expense.rowId}")
            }
            try {
                expense.toEntity()
            } catch (e: Exception) {
                throw Exception("Expense conversion to ExpenseEntity failed for rowId ${expense.rowId}: ${e.message}", e)
            }
        }

        val sanitizedAccounts = try {
            remoteData.accounts.map { sanitizeAccount(it) }
        } catch (e: Exception) {
            throw Exception("Invalid account record in remote data: ${e.message}", e)
        }

        return remoteData.copy(
            expenses = remoteData.expenses.filter { validateExpense(it) },
            accounts = sanitizedAccounts
        )
    }

    /**
     * Performs a deterministic Full Replacement Restore of local database and account preferences.
     * Overwrites local expenses and account list with the validated remote dataset.
     */
    suspend fun fullReplaceData(remoteData: SyncData): SyncData = syncLock.withLock {
        Log.i(TAG, "Starting Full Replacement Restore. Remote transactions: ${remoteData.expenses.size}, Remote accounts: ${remoteData.accounts.size}")

        // 1. Validate remote SyncData FIRST before touching local storage
        val validatedRemoteData = validateRemoteSyncData(remoteData)
        val validExpenses = validatedRemoteData.expenses
        val sanitizedAccounts = validatedRemoteData.accounts

        // 2. Create complete local safety backup before destructive replacement
        val currentLocalExpenses = repository.getAllForSync()
        val currentLocalAccounts = accountRepository.accounts.value
        val localSafetyBackup = SyncData(
            schemaVersion = 3,
            lastSyncTimestamp = System.currentTimeMillis(),
            expenses = currentLocalExpenses,
            accounts = currentLocalAccounts
        )

        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(Account::class.java, AccountAdapter())
            .create()
        val safetyBackupJson = gson.toJson(localSafetyBackup)
        accountRepository.savePreRestoreFullSafetyBackupJson(safetyBackupJson)

        // 3. Perform full replacement with explicit rollback protection
        try {
            repository.fullReplaceSyncData(validExpenses)
            accountRepository.replaceAccountsList(sanitizedAccounts)

            Log.i(TAG, "Full Replacement Restore complete. Restored ${validExpenses.size} expenses and ${sanitizedAccounts.size} accounts.")

            return SyncData(
                schemaVersion = remoteData.schemaVersion,
                lastSyncTimestamp = System.currentTimeMillis(),
                expenses = validExpenses,
                accounts = sanitizedAccounts
            )
        } catch (e: Exception) {
            Log.e(TAG, "Full restore failed; attempting automatic rollback to local safety backup", e)
            val rollbackSuccess = try {
                repository.fullReplaceSyncData(localSafetyBackup.expenses)
                accountRepository.replaceAccountsList(localSafetyBackup.accounts)
                true
            } catch (rollbackError: Exception) {
                Log.e(TAG, "CRITICAL: Automatic rollback failed", rollbackError)
                false
            }

            if (rollbackSuccess) {
                throw Exception("Full restore failed; local database was automatically restored from safety backup: ${e.message}", e)
            } else {
                throw Exception("CRITICAL: Full restore failed AND automatic rollback failed. Local safety backup remains available for manual recovery: ${e.message}", e)
            }
        }
    }

    /**
     * Manually restores the pre-restore full safety backup snapshot.
     */
    suspend fun restorePreRestoreSafetyBackup(): SyncData = syncLock.withLock {
        val backupJson = accountRepository.getPreRestoreFullSafetyBackupJson()
            ?: throw Exception("No pre-restore safety backup found")

        val gson = com.google.gson.GsonBuilder()
            .registerTypeAdapter(Account::class.java, AccountAdapter())
            .create()

        val backupData = try {
            gson.fromJson(backupJson, SyncData::class.java)
                ?: throw Exception("Corrupted pre-restore safety backup")
        } catch (e: Exception) {
            throw Exception("Failed to deserialize pre-restore safety backup: ${e.message}", e)
        }

        val validatedBackup = validateRemoteSyncData(backupData)

        repository.fullReplaceSyncData(validatedBackup.expenses)
        accountRepository.replaceAccountsList(validatedBackup.accounts)

        Log.i(TAG, "Pre-restore safety backup restored successfully (${validatedBackup.expenses.size} expenses, ${validatedBackup.accounts.size} accounts)")

        return SyncData(
            schemaVersion = validatedBackup.schemaVersion,
            lastSyncTimestamp = System.currentTimeMillis(),
            expenses = validatedBackup.expenses,
            accounts = validatedBackup.accounts
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
