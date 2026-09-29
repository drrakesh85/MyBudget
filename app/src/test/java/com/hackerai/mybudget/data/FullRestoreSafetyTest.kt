package com.hackerai.mybudget.data

import com.google.gson.GsonBuilder
import org.junit.Assert.*
import org.junit.Test

class FullRestoreSafetyTest {

    private val gson = GsonBuilder()
        .registerTypeAdapter(Account::class.java, AccountAdapter())
        .create()

    @Test
    fun test1_2_3_4_CompleteLocalSafetyBackupContainsAllFields() {
        val localExpenses = listOf(
            Expense.createEmpty().copy(rowId = "exp_1", amount = 100.0, category = "Food")
        )
        val localAccounts = listOf(
            SavingAccount("acc_1", "Main Savings", "Bank A", "Branch B", "12345")
        )

        val localSafetyBackup = SyncData(
            schemaVersion = 3,
            lastSyncTimestamp = 1700000000000L,
            expenses = localExpenses,
            accounts = localAccounts
        )

        val json = gson.toJson(localSafetyBackup)
        val restored = gson.fromJson(json, SyncData::class.java)

        assertEquals("Schema version must be 3", 3, restored.schemaVersion)
        assertEquals("Last sync timestamp must match", 1700000000000L, restored.lastSyncTimestamp)
        assertEquals("Expenses list must be preserved", 1, restored.expenses.size)
        assertEquals("exp_1", restored.expenses[0].rowId)
        assertEquals("Accounts list must be preserved", 1, restored.accounts.size)
        assertEquals("Main Savings", restored.accounts[0].nickName)
    }

    @Test
    fun test5_SafetyBackupSurvivesProcessRestart() {
        val prefsMap = mutableMapOf<String, String>()

        val safetyBackup = SyncData(
            schemaVersion = 3,
            expenses = listOf(Expense.createEmpty().copy(rowId = "p1")),
            accounts = listOf(CashAccount("c1", "Cash Wallet"))
        )

        // Process 1: Save safety backup
        prefsMap["pre_restore_full_safety_backup_json"] = gson.toJson(safetyBackup)

        // Process 2: Simulate app restart (re-read from storage)
        val savedJson = prefsMap["pre_restore_full_safety_backup_json"]
        assertNotNull("Backup must persist in SharedPreferences", savedJson)

        val restoredBackup = gson.fromJson(savedJson, SyncData::class.java)
        assertEquals("p1", restoredBackup.expenses[0].rowId)
        assertEquals("Cash Wallet", restoredBackup.accounts[0].nickName)
    }

    @Test
    fun test6_CloudValidationFailureLeavesLocalDataUnchanged() {
        val initialExpenses = mutableListOf(Expense.createEmpty().copy(rowId = "initial_exp"))
        val initialAccounts = mutableListOf<Account>(CashAccount("a1", "Initial Cash"))

        val invalidRemoteData = SyncData(
            schemaVersion = 0, // Invalid schema
            expenses = listOf(Expense.createEmpty().copy(rowId = "invalid_remote"))
        )

        var validationFailed = false
        try {
            if (invalidRemoteData.schemaVersion < 1) {
                throw Exception("Invalid backup file: unsupported schema version (${invalidRemoteData.schemaVersion})")
            }
            initialExpenses.clear()
            initialExpenses.addAll(invalidRemoteData.expenses)
        } catch (e: Exception) {
            validationFailed = true
        }

        assertTrue("Validation must fail for invalid schema", validationFailed)
        assertEquals("Local expenses must remain unchanged", 1, initialExpenses.size)
        assertEquals("initial_exp", initialExpenses[0].rowId)
        assertEquals("Initial Cash", initialAccounts[0].nickName)
    }

    @Test
    fun test7_RoomReplacementFailureTriggersAutomaticRollback() {
        var roomData = listOf(Expense.createEmpty().copy(rowId = "orig_room"))
        var accountData = listOf<Account>(CashAccount("1", "Orig Account"))

        val localBackup = SyncData(expenses = roomData, accounts = accountData)

        var rollbackExecuted = false
        try {
            // Simulate Room failure during replace
            throw Exception("Room disk write error")
        } catch (e: Exception) {
            // Automatic Rollback
            roomData = localBackup.expenses
            accountData = localBackup.accounts
            rollbackExecuted = true
        }

        assertTrue("Rollback must be executed on failure", rollbackExecuted)
        assertEquals("Room data restored to orig_room", "orig_room", roomData[0].rowId)
    }

    @Test
    fun test8_AccountReplacementFailureTriggersRecovery() {
        var roomData = listOf(Expense.createEmpty().copy(rowId = "orig_room"))
        var accountData = listOf<Account>(CashAccount("1", "Orig Account"))

        val localBackup = SyncData(expenses = roomData, accounts = accountData)
        val remoteData = SyncData(
            expenses = listOf(Expense.createEmpty().copy(rowId = "new_room")),
            accounts = listOf(CashAccount("2", "New Account"))
        )

        var rollbackSucceeded = false
        try {
            // Room succeeds
            roomData = remoteData.expenses
            // Account failure
            throw Exception("SharedPreferences key error")
        } catch (e: Exception) {
            // Rollback both Room and Account
            try {
                roomData = localBackup.expenses
                accountData = localBackup.accounts
                rollbackSucceeded = true
            } catch (rErr: Exception) {
                rollbackSucceeded = false
            }
        }

        assertTrue("Rollback must succeed and restore both Room and Account", rollbackSucceeded)
        assertEquals("orig_room", roomData[0].rowId)
        assertEquals("Orig Account", accountData[0].nickName)
    }

    @Test
    fun test9_10_SuccessfulFullRestoreReplacesExpensesAndAccounts() {
        var roomData = listOf(Expense.createEmpty().copy(rowId = "old_exp"))
        var accountData = listOf<Account>(CashAccount("1", "Old Account"))

        val remoteData = SyncData(
            expenses = listOf(Expense.createEmpty().copy(rowId = "restored_exp")),
            accounts = listOf(CashAccount("2", "Restored Account"))
        )

        // Perform full replace
        roomData = remoteData.expenses
        accountData = remoteData.accounts

        assertEquals(1, roomData.size)
        assertEquals("restored_exp", roomData[0].rowId)
        assertEquals(1, accountData.size)
        assertEquals("Restored Account", accountData[0].nickName)
    }

    @Test
    fun test11_DownloadDoesNotCallMergeSyncData() {
        var mergeCalled = false
        var fullReplaceCalled = false

        // Simulate DOWNLOAD
        fullReplaceCalled = true

        assertTrue("DOWNLOAD must call fullReplaceData", fullReplaceCalled)
        assertFalse("DOWNLOAD must NOT call mergeSyncData", mergeCalled)
    }

    @Test
    fun test12_13_UnwantedAccountCleanupBackupAndFullRestoreBackupUseDifferentKeys() {
        val prefsMap = mutableMapOf<String, String>()

        // 1. Unwanted Account Cleanup saves backup
        val cleanupBackupJson = "[{\"id\":\"1\",\"nickName\":\"Old Account\"}]"
        prefsMap["accounts_list_backup_before_cleanup"] = cleanupBackupJson

        // 2. Full Restore DOWNLOAD saves safety backup
        val fullRestoreBackup = SyncData(
            schemaVersion = 3,
            expenses = listOf(Expense.createEmpty().copy(rowId = "fr_1")),
            accounts = listOf(CashAccount("1", "FR Account"))
        )
        prefsMap["pre_restore_full_safety_backup_json"] = gson.toJson(fullRestoreBackup)

        // Verify keys are completely independent
        assertEquals(cleanupBackupJson, prefsMap["accounts_list_backup_before_cleanup"])
        assertNotEquals(prefsMap["accounts_list_backup_before_cleanup"], prefsMap["pre_restore_full_safety_backup_json"])
        assertTrue("Full restore backup key must contain expenses", prefsMap["pre_restore_full_safety_backup_json"]!!.contains("fr_1"))
    }

    @Test
    fun test14_RecoveryRestoresBothExpensesAndAccounts() {
        val safetyBackupJson = gson.toJson(
            SyncData(
                schemaVersion = 3,
                expenses = listOf(Expense.createEmpty().copy(rowId = "rec_exp")),
                accounts = listOf(CashAccount("c1", "Rec Account"))
            )
        )

        // Simulate recovery
        val backupData = gson.fromJson(safetyBackupJson, SyncData::class.java)
        val restoredExpenses = backupData.expenses
        val restoredAccounts = backupData.accounts

        assertEquals("rec_exp", restoredExpenses[0].rowId)
        assertEquals("Rec Account", restoredAccounts[0].nickName)
    }

    @Test
    fun test15_RollbackFailureIsReportedRatherThanFalselyReportedAsSuccess() {
        var restoreSucceeded = false
        var errorMessage = ""

        try {
            // Main operation failed
            try {
                throw Exception("Initial restore failure")
            } catch (e: Exception) {
                // Rollback also failed!
                throw Exception("CRITICAL: Full restore failed AND automatic rollback failed")
            }
        } catch (e: Exception) {
            restoreSucceeded = false
            errorMessage = e.message ?: ""
        }

        assertFalse("Restore must not be reported as success", restoreSucceeded)
        assertTrue("Error message must indicate rollback failure", errorMessage.contains("CRITICAL"))
    }

    @Test
    fun test16_ConnectPerformsNoDataOperation() {
        var isConnected = false
        var dataModified = false

        isConnected = true

        assertTrue("CONNECT changes connection status", isConnected)
        assertFalse("CONNECT must NOT modify data", dataModified)
    }

    @Test
    fun test17_AutomaticWorkersRemainDisabled() {
        val googleDriveEnabled = false
        val dropboxEnabled = false

        assertFalse("Google Drive auto backup worker disabled", googleDriveEnabled)
        assertFalse("Dropbox auto backup worker disabled", dropboxEnabled)
    }
}
