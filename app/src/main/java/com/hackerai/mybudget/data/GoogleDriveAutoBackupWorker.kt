package com.hackerai.mybudget.data

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.hackerai.mybudget.MyBudgetApplication

class GoogleDriveAutoBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        Log.i(TAG, "Starting GoogleDriveAutoBackupWorker execution")
        val autoBackupPrefs = AutoBackupPreferences(applicationContext)
        if (!autoBackupPrefs.googleDriveEnabled) {
            Log.i(TAG, "Google Drive auto backup disabled; cancelling worker")
            AutoBackupPreferences.cancelGoogleDriveBackup(applicationContext)
            return Result.success()
        }

        val app = applicationContext as MyBudgetApplication
        val googleDriveHelper = GoogleDriveHelper(applicationContext)
        val googleAccount = googleDriveHelper.getLastSignedInAccount()

        if (googleAccount == null || !googleDriveHelper.hasDrivePermission(googleAccount)) {
            Log.w(TAG, "Google Drive not connected or permissions missing; auto backup skipped")
            return Result.failure()
        }

        return try {
            val syncData = app.backupManager.createLocalSyncData()
            googleDriveHelper.uploadSyncData(googleAccount, syncData)
            val now = System.currentTimeMillis()
            autoBackupPrefs.googleDriveLastBackupTimestamp = now
            Log.i(TAG, "Google Drive auto backup uploaded successfully (${syncData.expenses.size} expenses)")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Google Drive auto backup failed", e)
            Result.retry()
        }
    }

    companion object {
        private const val TAG = "GoogleDriveAutoBackupWorker"
    }
}
