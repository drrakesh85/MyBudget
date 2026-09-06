package com.hackerai.mybudget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.hackerai.mybudget.ui.theme.MyBudgetTheme

enum class Screen {
    EXPENSE_LIST, ACCOUNT_SETUP, TRANSACTION_BROWSER, CATEGORY_SUMMARY, CALENDAR_VIEW, ACCOUNT_SUMMARY, SETTINGS, CATEGORY_SETTINGS, TAG_SETTINGS, SMS_IMPORT, ACCOUNT_LIST, AUDIT
}

class MainActivity : ComponentActivity() {

    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) {
            Toast.makeText(this, "SMS permissions granted. Tap the SMS icon to scan.", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "SMS permissions denied. SMS import will not work.", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkSmsPermissions()

        setContent {
            MyBudgetTheme {
                var currentScreen by remember { mutableStateOf(Screen.EXPENSE_LIST) }
                val expenseViewModel: ExpenseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
                LaunchedEffect(Unit) {
                    // Manual trigger of initial load without SMS parsing
                    expenseViewModel.loadExpenses()
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    when (currentScreen) {
                        Screen.EXPENSE_LIST -> {
                            ExpenseListScreen(
                                viewModel = expenseViewModel,
                                onNavigateToAccounts = { currentScreen = Screen.ACCOUNT_LIST },
                                onNavigateToBrowser = { currentScreen = Screen.TRANSACTION_BROWSER },
                                onNavigateToSummary = { currentScreen = Screen.CATEGORY_SUMMARY },
                                onNavigateToCalendar = { currentScreen = Screen.CALENDAR_VIEW },
                                onNavigateToAccountSummary = { currentScreen = Screen.ACCOUNT_SUMMARY },
                                onNavigateToSettings = { currentScreen = Screen.SETTINGS },
                                onNavigateToSmsImport = { currentScreen = Screen.SMS_IMPORT }
                            )
                        }
                        Screen.ACCOUNT_SETUP -> {
                            AccountSetupScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST }
                            )
                        }
                        Screen.TRANSACTION_BROWSER -> {
                            TransactionBrowserScreen(
                                viewModel = expenseViewModel,
                                onBack = { currentScreen = Screen.EXPENSE_LIST }
                            )
                        }
                        Screen.CATEGORY_SUMMARY -> {
                            CategorySummaryScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST }
                            )
                        }
                        Screen.CALENDAR_VIEW -> {
                            CalendarScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST }
                            )
                        }
                        Screen.ACCOUNT_SUMMARY -> {
                            AccountSummaryScreen(
                                viewModel = expenseViewModel,
                                onBack = { currentScreen = Screen.ACCOUNT_LIST },
                                onManageAccounts = { currentScreen = Screen.ACCOUNT_SETUP }
                            )
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST },
                                onNavigateToCategorySettings = { currentScreen = Screen.CATEGORY_SETTINGS },
                                onNavigateToTagSettings = { currentScreen = Screen.TAG_SETTINGS },
                                onNavigateToAudit = { currentScreen = Screen.AUDIT },
                                onNavigateToAccountManagement = { currentScreen = Screen.ACCOUNT_LIST }
                            )
                        }
                        Screen.CATEGORY_SETTINGS -> {
                            CategorySettingsScreen(
                                onBack = { currentScreen = Screen.SETTINGS }
                            )
                        }
                        Screen.TAG_SETTINGS -> {
                            TagSettingsScreen(
                                onBack = { currentScreen = Screen.SETTINGS }
                            )
                        }
                        Screen.SMS_IMPORT -> {
                            SmsImportScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST },
                                onReviewTransaction = { expense ->
                                    expenseViewModel.editExpense(expense)
                                    currentScreen = Screen.EXPENSE_LIST
                                },
                                accountFilter = expenseViewModel.selectedAccount.value
                            )
                        }
                        Screen.ACCOUNT_LIST -> {
                            AccountScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST },
                                onManageAccounts = { currentScreen = Screen.ACCOUNT_SETUP },
                                onNavigateToAccountSummary = { accountName ->
                                    expenseViewModel.filterByAccount(accountName)
                                    currentScreen = Screen.ACCOUNT_SUMMARY
                                }
                            )
                        }
                        Screen.AUDIT -> {
                            AuditScreen(
                                onBack = { currentScreen = Screen.SETTINGS }
                            )
                        }
                    }
                }
            }
        }
    }

    private fun checkSmsPermissions() {
        val permissions = arrayOf(
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS
        )
        val missingPermissions = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missingPermissions.isNotEmpty()) {
            requestPermissionLauncher.launch(missingPermissions.toTypedArray())
        }
    }
}
