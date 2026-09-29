package com.hackerai.mybudget.data

import android.content.Context
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupManager(
    private val context: Context,
    private val repository: ExpenseRepository,
    private val accountRepository: AccountRepository,
    private val syncManager: SyncManager,
    private val googleDriveHelper: GoogleDriveHelper,
    private val dropboxHelper: DropboxHelper
) {

    fun generateBackupFilename(): String {
        val timestamp = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.US).format(Date())
        return "my_budget_backup_$timestamp.json"
    }

    suspend fun createLocalSyncData(): SyncData = withContext(Dispatchers.IO) {
        val expenses = repository.getAllForSync()
        val accounts = accountRepository.accounts.value
        SyncData(
            schemaVersion = 3,
            lastSyncTimestamp = System.currentTimeMillis(),
            expenses = expenses,
            accounts = accounts
        )
    }

    suspend fun backupToGoogleDrive(account: GoogleSignInAccount): String = withContext(Dispatchers.IO) {
        val syncData = createLocalSyncData()
        googleDriveHelper.uploadSyncData(account, syncData)
        "Google Drive backup saved successfully (${syncData.expenses.size} expenses)"
    }

    suspend fun restoreFromGoogleDrive(account: GoogleSignInAccount): SyncData = withContext(Dispatchers.IO) {
        val remoteData = googleDriveHelper.downloadSyncData(account)
        validateSyncData(remoteData)
        val restoredData = syncManager.fullReplaceData(remoteData)
        restoredData
    }

    suspend fun backupToDropbox(): String = withContext(Dispatchers.IO) {
        val syncData = createLocalSyncData()
        dropboxHelper.uploadSyncData(syncData)
        "Dropbox backup saved successfully (${syncData.expenses.size} expenses)"
    }

    suspend fun restoreFromDropbox(): SyncData = withContext(Dispatchers.IO) {
        val remoteData = dropboxHelper.downloadSyncData() ?: throw Exception("No Dropbox backup file found")
        validateSyncData(remoteData)
        val restoredData = syncManager.fullReplaceData(remoteData)
        restoredData
    }

    suspend fun restorePreRestoreSafetyBackup(): SyncData = withContext(Dispatchers.IO) {
        syncManager.restorePreRestoreSafetyBackup()
    }

    fun hasPreRestoreFullSafetyBackup(): Boolean {
        return accountRepository.hasPreRestoreFullSafetyBackup()
    }

    private fun validateSyncData(data: SyncData) {
        if (data.schemaVersion < 1) {
            throw Exception("Invalid backup file: unsupported schema version (${data.schemaVersion})")
        }
    }

    companion object {
        private const val TAG = "BackupManager"
    }
}
