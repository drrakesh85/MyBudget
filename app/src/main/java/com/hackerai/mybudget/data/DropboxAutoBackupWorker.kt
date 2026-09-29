package com.hackerai.mybudget.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackerai.mybudget.MyBudgetApplication

class DropboxAutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting DropboxAutoBackupWorker execution")
        val autoBackupPrefs = AutoBackupPreferences(applicationContext)
        if (!autoBackupPrefs.dropboxEnabled) {
            Log.i(TAG, "Dropbox auto backup disabled; cancelling worker")
            AutoBackupPreferences.cancelDropboxBackup(applicationContext)
            return Result.success()
        }

        val app = applicationContext as MyBudgetApplication
        val dropboxHelper = DropboxHelper(applicationContext)

        if (!dropboxHelper.isConnected()) {
            Log.w(TAG, "Dropbox not connected; auto backup skipped")
            return Result.failure()
        }

        return try {
            val syncData = app.backupManager.createLocalSyncData()
            dropboxHelper.uploadSyncData(syncData)
            val now = System.currentTimeMillis()
            autoBackupPrefs.dropboxLastBackupTimestamp = now
            Log.i(TAG, "Dropbox auto backup uploaded successfully (${syncData.expenses.size} expenses)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Dropbox auto backup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "DropboxAutoBackupWorker"
    }
}
