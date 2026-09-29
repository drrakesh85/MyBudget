package com.hackerai.mybudget

import android.app.Application
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob

class MyBudgetApplication : Application() {

    val applicationScope = CoroutineScope(SupervisorJob())

    val database: AppDatabase by lazy { AppDatabase.getInstance(this) }

    val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(this, database.expenseDao())
    }

    val accountRepository: AccountRepository by lazy {
        AccountRepository(this)
    }

    val syncManager: SyncManager by lazy {
        SyncManager(expenseRepository, accountRepository)
    }

    val googleDriveHelper: GoogleDriveHelper by lazy {
        GoogleDriveHelper(this)
    }

    val dropboxHelper: DropboxHelper by lazy {
        DropboxHelper(this)
    }

    val backupManager: BackupManager by lazy {
        BackupManager(this, expenseRepository, accountRepository, syncManager, googleDriveHelper, dropboxHelper)
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        AutoBackupPreferences.cancelAllAutoBackups(this)
    }

    companion object {
        lateinit var instance: MyBudgetApplication
            private set
    }
}
