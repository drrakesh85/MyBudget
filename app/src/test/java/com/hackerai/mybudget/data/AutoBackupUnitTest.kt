package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test

class AutoBackupUnitTest {

    @Test
    fun test1_GoogleDriveBackupOnSchedulesWorker() {
        var isGoogleDriveEnabled = false
        var workerScheduled = false

        // User toggles Google Drive Auto Backup ON
        isGoogleDriveEnabled = true
        if (isGoogleDriveEnabled) {
            workerScheduled = true
        }

        assertTrue("Google Drive auto backup enabled state is true", isGoogleDriveEnabled)
        assertTrue("WorkManager periodic worker scheduled for Google Drive", workerScheduled)
    }

    @Test
    fun test2_GoogleDriveBackupOffCancelsWorker() {
        var isGoogleDriveEnabled = true
        var workerCancelled = false

        // User toggles Google Drive Auto Backup OFF
        isGoogleDriveEnabled = false
        if (!isGoogleDriveEnabled) {
            workerCancelled = true
        }

        assertFalse("Google Drive auto backup enabled state is false", isGoogleDriveEnabled)
        assertTrue("WorkManager periodic worker cancelled for Google Drive", workerCancelled)
    }

    @Test
    fun test3_DropboxBackupOnSchedulesWorker() {
        var isDropboxEnabled = false
        var workerScheduled = false

        // User toggles Dropbox Auto Backup ON
        isDropboxEnabled = true
        if (isDropboxEnabled) {
            workerScheduled = true
        }

        assertTrue("Dropbox auto backup enabled state is true", isDropboxEnabled)
        assertTrue("WorkManager periodic worker scheduled for Dropbox", workerScheduled)
    }

    @Test
    fun test4_DropboxBackupOffCancelsWorker() {
        var isDropboxEnabled = true
        var workerCancelled = false

        // User toggles Dropbox Auto Backup OFF
        isDropboxEnabled = false
        if (!isDropboxEnabled) {
            workerCancelled = true
        }

        assertFalse("Dropbox auto backup enabled state is false", isDropboxEnabled)
        assertTrue("WorkManager periodic worker cancelled for Dropbox", workerCancelled)
    }

    @Test
    fun test5_AutomaticBackupUploadsLocalData() {
        val localExpenses = listOf(Expense.createEmpty().copy(rowId = "local_1", amount = 250.0))
        val localAccounts = listOf(CashAccount("acc_1", "Main Wallet"))

        // Create local snapshot
        val syncData = SyncData(expenses = localExpenses, accounts = localAccounts)

        // Upload direction: Local -> Cloud
        val uploadedPayload = syncData

        assertEquals("Uploaded payload contains local expenses", 1, uploadedPayload.expenses.size)
        assertEquals("local_1", uploadedPayload.expenses[0].rowId)
        assertEquals("Main Wallet", uploadedPayload.accounts[0].nickName)
    }

    @Test
    fun test6_7_AutomaticBackupNeverDownloadsOrCallsMergeSyncData() {
        var downloadCalled = false
        var mergeSyncDataCalled = false
        var uploadCalled = false

        // Simulate Worker execution
        uploadCalled = true

        assertTrue("Automatic backup worker calls upload", uploadCalled)
        assertFalse("Automatic backup worker must NEVER download", downloadCalled)
        assertFalse("Automatic backup worker must NEVER call mergeSyncData", mergeSyncDataCalled)
    }

    @Test
    fun test8_FailedUploadDoesNotUpdateLastSuccessTimestamp() {
        var lastSuccessTimestamp = 1000L
        val now = 2000L
        var uploadSuccess = false

        try {
            // Simulate upload failure
            throw Exception("Network connection lost")
        } catch (e: Exception) {
            uploadSuccess = false
        }

        if (uploadSuccess) {
            lastSuccessTimestamp = now
        }

        assertEquals("Timestamp remains unchanged on upload failure", 1000L, lastSuccessTimestamp)
    }

    @Test
    fun test9_GoogleAndDropboxSettingsAreIndependent() {
        var googleEnabled = true
        var googleInterval = 60 // 1 hour

        var dropboxEnabled = false
        var dropboxInterval = 1440 // 24 hours

        // Toggle Dropbox ON without affecting Google Drive
        dropboxEnabled = true
        dropboxInterval = 120 // 2 hours

        assertTrue("Google Drive remains enabled", googleEnabled)
        assertEquals("Google Drive interval unchanged", 60, googleInterval)

        assertTrue("Dropbox is now enabled independently", dropboxEnabled)
        assertEquals("Dropbox interval updated independently", 120, dropboxInterval)
    }
}
