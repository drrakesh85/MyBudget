package com.hackerai.mybudget

import android.app.Application
import com.hackerai.mybudget.data.AccountRepository
import com.hackerai.mybudget.data.AppDatabase
import com.hackerai.mybudget.data.ExpenseRepository
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

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    companion object {
        lateinit var instance: MyBudgetApplication
            private set
    }
}
