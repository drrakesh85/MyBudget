package com.hackerai.mybudget.data

import android.content.Context
import android.util.Log
import androidx.work.*
import java.util.concurrent.TimeUnit

class AutoBackupPreferences(context: Context) {
    private val prefs = context.getSharedPreferences("auto_backup_prefs", Context.MODE_PRIVATE)

    var googleDriveEnabled: Boolean
        get() = prefs.getBoolean(KEY_GOOGLE_DRIVE_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_GOOGLE_DRIVE_ENABLED, value).apply()

    var googleDriveIntervalMinutes: Int
        get() = prefs.getInt(KEY_GOOGLE_DRIVE_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_GOOGLE_DRIVE_INTERVAL, value).apply()

    var googleDriveLastBackupTimestamp: Long
        get() = prefs.getLong(KEY_GOOGLE_DRIVE_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_GOOGLE_DRIVE_LAST_BACKUP, value).apply()

    var dropboxEnabled: Boolean
        get() = prefs.getBoolean(KEY_DROPBOX_ENABLED, false)
        set(value) = prefs.edit().putBoolean(KEY_DROPBOX_ENABLED, value).apply()

    var dropboxIntervalMinutes: Int
        get() = prefs.getInt(KEY_DROPBOX_INTERVAL, 60)
        set(value) = prefs.edit().putInt(KEY_DROPBOX_INTERVAL, value).apply()

    var dropboxLastBackupTimestamp: Long
        get() = prefs.getLong(KEY_DROPBOX_LAST_BACKUP, 0L)
        set(value) = prefs.edit().putLong(KEY_DROPBOX_LAST_BACKUP, value).apply()

    companion object {
        private const val TAG = "AutoBackupPreferences"
        private const val KEY_GOOGLE_DRIVE_ENABLED = "google_drive_auto_backup_enabled"
        private const val KEY_GOOGLE_DRIVE_INTERVAL = "google_drive_backup_interval_minutes"
        private const val KEY_GOOGLE_DRIVE_LAST_BACKUP = "google_drive_last_backup_timestamp"

        private const val KEY_DROPBOX_ENABLED = "dropbox_auto_backup_enabled"
        private const val KEY_DROPBOX_INTERVAL = "dropbox_backup_interval_minutes"
        private const val KEY_DROPBOX_LAST_BACKUP = "dropbox_last_backup_timestamp"

        const val WORK_NAME_GOOGLE_DRIVE = "google_drive_auto_backup_worker"
        const val WORK_NAME_DROPBOX = "dropbox_auto_backup_worker"

        fun scheduleGoogleDriveBackup(context: Context) {
            val autoBackupPrefs = AutoBackupPreferences(context)
            if (!autoBackupPrefs.googleDriveEnabled) {
                cancelGoogleDriveBackup(context)
                return
            }

            val interval = autoBackupPrefs.googleDriveIntervalMinutes.coerceAtLeast(15)
            Log.i(TAG, "Scheduling Google Drive Auto Backup every $interval minutes")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<GoogleDriveAutoBackupWorker>(
                interval.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_GOOGLE_DRIVE,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }

        fun cancelGoogleDriveBackup(context: Context) {
            Log.i(TAG, "Cancelling Google Drive Auto Backup")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_GOOGLE_DRIVE)
        }

        fun scheduleDropboxBackup(context: Context) {
            val autoBackupPrefs = AutoBackupPreferences(context)
            if (!autoBackupPrefs.dropboxEnabled) {
                cancelDropboxBackup(context)
                return
            }

            val interval = autoBackupPrefs.dropboxIntervalMinutes.coerceAtLeast(15)
            Log.i(TAG, "Scheduling Dropbox Auto Backup every $interval minutes")

            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val request = PeriodicWorkRequestBuilder<DropboxAutoBackupWorker>(
                interval.toLong(), TimeUnit.MINUTES
            )
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_DROPBOX,
                ExistingPeriodicWorkPolicy.CANCEL_AND_REENQUEUE,
                request
            )
        }

        fun cancelDropboxBackup(context: Context) {
            Log.i(TAG, "Cancelling Dropbox Auto Backup")
            WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME_DROPBOX)
        }

        fun cancelAllAutoBackups(context: Context) {
            Log.i(TAG, "Cancelling all automatic cloud backup workers")
            try {
                val wm = WorkManager.getInstance(context)
                wm.cancelUniqueWork(WORK_NAME_GOOGLE_DRIVE)
                wm.cancelUniqueWork(WORK_NAME_DROPBOX)
            } catch (e: Exception) {
                Log.w(TAG, "Failed to cancel automatic workers: ${e.message}")
            }
        }
    }
}
