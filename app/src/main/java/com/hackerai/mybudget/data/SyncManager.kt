package com.hackerai.mybudget.data

import android.util.Log

class SyncManager(private val repository: ExpenseRepository) {

    /**
     * Merges a list of remote expenses with the local database.
     * Implements "Last Write Wins" based on lastModified timestamp.
     * Handles deletion tombstones (isDeleted=true).
     */
    suspend fun mergeExpenses(remoteExpenses: List<Expense>): List<Expense> {
        val localExpenses = repository.getAllForSync()
        val localMap = localExpenses.associateBy { it.rowId }
        val remoteMap = remoteExpenses.associateBy { it.rowId }
        
        val mergedList = mutableListOf<Expense>()
        val allIds = (localMap.keys + remoteMap.keys).toSet()

        for (id in allIds) {
            val local = localMap[id]
            val remote = remoteMap[id]

            val merged = when {
                local != null && remote != null -> {
                    // Both exist, take the one with the later timestamp
                    if (remote.lastModified > local.lastModified) remote else local
                }
                local != null -> {
                    // Local only
                    local
                }
                remote != null -> {
                    // Remote only
                    remote
                }
                else -> null
            }

            merged?.let { mergedList.add(it) }
        }

        // Save the merged list back to the local database
        // We use a specialized sync insert that preserves the incoming fields exactly.
        repository.insertSyncData(mergedList)

        return mergedList
    }
}
