package com.hackerai.mybudget.data

/**
 * Unified data structure for synchronization across all cloud providers and backups.
 */
data class SyncData(
    val schemaVersion: Int = 3,
    val lastSyncTimestamp: Long = System.currentTimeMillis(),
    val expenses: List<Expense> = emptyList(),
    val accounts: List<Account> = emptyList()
)
