package com.hackerai.mybudget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import com.hackerai.mybudget.ui.theme.MyBudgetTheme
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import androidx.core.content.FileProvider
import android.app.Activity
import android.content.Intent
import android.util.Log
import java.io.File
import java.io.FileOutputStream

enum class Screen {
    EXPENSE_LIST, TRANSACTION_BROWSER, CATEGORY_SUMMARY, CALENDAR_VIEW, ACCOUNT_SUMMARY, SETTINGS, CATEGORY_SETTINGS, TAG_SETTINGS, SMS_IMPORT, ACCOUNT_LIST, AUDIT, BACKUP_RESTORE, CATEGORY_TRANSACTIONS
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val RC_GOOGLE_DRIVE_PERMISSION = 9001
        private const val NAV_TAG = "NAV_BACK"
        private const val SMS_TAG = "SMS_NAV"
    }

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

    private lateinit var expenseViewModel: ExpenseViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        checkSmsPermissions()

        setContent {
            MyBudgetTheme {
                val screenStack = remember { mutableStateListOf(Screen.EXPENSE_LIST) }
                val currentScreen = screenStack.last()
                
                var selectedCategoryForTransactions by remember { mutableStateOf("") }
                var selectedSummaryTypeForTransactions by remember { mutableStateOf("Category") }
                expenseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()

                fun navigateTo(screen: Screen) {
                    if (screenStack.lastOrNull() != screen) {
                        Log.d(NAV_TAG, "Navigate to: $screen. Stack: ${screenStack.joinToString(" -> ")}")
                        screenStack.add(screen)
                    }
                }

                fun navigateBack() {
                    if (screenStack.size > 1) {
                        val from = screenStack.last()
                        screenStack.removeAt(screenStack.size - 1)
                        Log.d(NAV_TAG, "Navigate back from $from to ${screenStack.last()}. Stack size: ${screenStack.size}")
                    } else {
                        Log.d(NAV_TAG, "No more screens in stack. Minimizing app.")
                        finish()
                    }
                }

                BackHandler(enabled = true) {
                    navigateBack()
                }
                
                val googleSignInLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
                    try {
                        val account = task.getResult(ApiException::class.java)
                        if (account != null) {
                            startGoogleDriveSync(account)
                        }
                    } catch (e: ApiException) {
                        android.util.Log.e("MainActivity", "Google Sign-In failed: status=${e.statusCode}", e)
                        Toast.makeText(
                            this@MainActivity,
                            "Google Sign-In failed (${e.statusCode}): ${e.message}",
                            Toast.LENGTH_LONG
                        ).show()
                    } catch (e: Exception) {
                        android.util.Log.e("MainActivity", "Google Sign-In failed", e)
                        Toast.makeText(this@MainActivity, "Google Sign-In failed: ${e.message}", Toast.LENGTH_LONG).show()
                    }
                }

                val googleRecoverableAuthLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartActivityForResult()
                ) { result ->
                    expenseViewModel.clearGoogleDriveRecoverableAuthIntent()
                    if (result.resultCode == Activity.RESULT_OK) {
                        GoogleSignIn.getLastSignedInAccount(this@MainActivity)?.let { account ->
                            startGoogleDriveSync(account)
                        }
                    } else {
                        Toast.makeText(
                            this@MainActivity,
                            "Google Drive authorization was not completed.",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }

                val googleDriveSyncState by expenseViewModel.googleDriveSyncState.collectAsState()
                
                val csvImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        expenseViewModel.importFromUri(it) { result ->
                            if (result.success) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "CSV Import complete! Parsed ${result.parsedCount} records.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "CSV Import failed: ${result.errorMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                val excelImportLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.OpenDocument()
                ) { uri ->
                    uri?.let {
                        expenseViewModel.importFromUri(it) { result ->
                            if (result.success) {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import complete! Parsed ${result.parsedCount} records.",
                                    Toast.LENGTH_SHORT
                                ).show()
                            } else {
                                Toast.makeText(
                                    this@MainActivity,
                                    "Import failed: ${result.errorMessage}",
                                    Toast.LENGTH_LONG
                                ).show()
                            }
                        }
                    }
                }

                LaunchedEffect(googleDriveSyncState) {
                    when (val state = googleDriveSyncState) {
                        is SyncState.Success -> {
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_SHORT).show()
                        }
                        is SyncState.Error -> {
                            Toast.makeText(this@MainActivity, state.message, Toast.LENGTH_LONG).show()
                        }
                        else -> Unit
                    }
                }

                val recoverableAuthIntent by expenseViewModel.googleDriveRecoverableAuthIntent.collectAsState()
                LaunchedEffect(recoverableAuthIntent) {
                    recoverableAuthIntent?.let { intent ->
                        googleRecoverableAuthLauncher.launch(intent)
                    }
                }

                LaunchedEffect(Unit) {
                    expenseViewModel.loadExpenses()
                }

                val editingExpense by expenseViewModel.editingExpense.collectAsState()
                
                LaunchedEffect(editingExpense) {
                    if (editingExpense != null) {
                        Log.d(SMS_TAG, "SMS/Expense Preview opened: ${editingExpense?.rowId}")
                    }
                }

                val accountNames by expenseViewModel.accounts.collectAsState()
                val payees by expenseViewModel.payees.collectAsState()
                val categories by expenseViewModel.categories.collectAsState()
                val subcategories by expenseViewModel.subcategories.collectAsState()
                val tags by expenseViewModel.tags.collectAsState()
                val tagMap by expenseViewModel.tagMap.collectAsState()
                val payeeMap by expenseViewModel.payeeMap.collectAsState()
                val categorySubcategoryMap by expenseViewModel.categorySubcategoryMap.collectAsState()

                if (editingExpense != null) {
                    ReviewExpenseScreen(
                        expense = editingExpense!!,
                        accounts = accountNames,
                        payees = payees,
                        categories = categories,
                        subcategories = subcategories,
                        tags = tags,
                        tagMap = tagMap,
                        payeeMap = payeeMap,
                        categorySubcategoryMap = categorySubcategoryMap,
                        onSave = { 
                            Log.d(SMS_TAG, "Save clicked for ${editingExpense?.rowId}")
                            expenseViewModel.saveReviewedExpenses(it) 
                        },
                        onCancel = { 
                            Log.d(SMS_TAG, "Cancel clicked for ${editingExpense?.rowId}")
                            expenseViewModel.cancelReview() 
                        },
                        onDelete = { expenseViewModel.deleteExpense(it) }
                    )
                } else {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = MaterialTheme.colorScheme.background,
                    ) {
                        when (currentScreen) {
                            Screen.EXPENSE_LIST -> {
                                ExpenseListScreen(
                                    viewModel = expenseViewModel,
                                    onNavigateToAccounts = { navigateTo(Screen.ACCOUNT_LIST) },
                                    onNavigateToBrowser = { navigateTo(Screen.ACCOUNT_SUMMARY) },
                                    onNavigateToSummary = { navigateTo(Screen.CATEGORY_SUMMARY) },
                                    onNavigateToCalendar = { navigateTo(Screen.CALENDAR_VIEW) },
                                    onNavigateToAccountSummary = { navigateTo(Screen.ACCOUNT_SUMMARY) },
                                    onNavigateToSettings = { navigateTo(Screen.SETTINGS) },
                                    onNavigateToSmsImport = { navigateTo(Screen.SMS_IMPORT) }
                                )
                            }
                            Screen.TRANSACTION_BROWSER -> {
                                AccountSummaryScreen(
                                    viewModel = expenseViewModel,
                                    onBack = { navigateBack() },
                                    onManageAccounts = { navigateTo(Screen.ACCOUNT_LIST) }
                                )
                            }
                            Screen.CATEGORY_SUMMARY -> {
                                CategorySummaryScreen(
                                    viewModel = expenseViewModel,
                                    onBack = { navigateBack() },
                                    onCategoryClick = { name, type ->
                                        selectedCategoryForTransactions = name
                                        selectedSummaryTypeForTransactions = type
                                        navigateTo(Screen.CATEGORY_TRANSACTIONS)
                                    }
                                )
                            }
                            Screen.CATEGORY_TRANSACTIONS -> {
                                CategoryTransactionsScreen(
                                    viewModel = expenseViewModel,
                                    filterValue = selectedCategoryForTransactions,
                                    filterType = selectedSummaryTypeForTransactions,
                                    onBack = { navigateBack() }
                                )
                            }
                            Screen.CALENDAR_VIEW -> {
                                CalendarScreen(
                                    onBack = { navigateBack() }
                                )
                            }
                            Screen.ACCOUNT_SUMMARY -> {
                                AccountSummaryScreen(
                                    viewModel = expenseViewModel,
                                    onBack = { navigateBack() },
                                    onManageAccounts = { navigateTo(Screen.ACCOUNT_LIST) }
                                )
                            }
                            Screen.SETTINGS -> {
                                SettingsScreen(
                                    onBack = { navigateBack() },
                                    onNavigateToCategorySettings = { navigateTo(Screen.CATEGORY_SETTINGS) },
                                    onNavigateToTagSettings = { navigateTo(Screen.TAG_SETTINGS) },
                                    onNavigateToAudit = { navigateTo(Screen.AUDIT) },
                                    onNavigateToAccountManagement = { navigateTo(Screen.ACCOUNT_LIST) },
                                    onNavigateToBackupRestore = { navigateTo(Screen.BACKUP_RESTORE) },
                                    onGoogleDriveSync = {
                                        val lastAccount = GoogleSignIn.getLastSignedInAccount(this@MainActivity)
                                        if (lastAccount != null) {
                                            startGoogleDriveSync(lastAccount)
                                        } else {
                                            val client = expenseViewModel.getGoogleSignInClient()
                                            googleSignInLauncher.launch(client.signInIntent)
                                        }
                                    },
                                    onDropboxSync = {
                                        expenseViewModel.startDropboxSync()
                                    },
                                    onExportCsv = {
                                        exportData(expenseViewModel, "my_budget_export.csv", "text/csv")
                                    },
                                    onExportExcel = {
                                        exportData(expenseViewModel, "my_budget_export.xls", "application/vnd.ms-excel")
                                    },
                                    onImportCsv = {
                                        csvImportLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "application/csv"))
                                    },
                                    onImportExcel = {
                                        excelImportLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                                    },
                                    expenseViewModel = expenseViewModel
                                )
                            }
                            Screen.BACKUP_RESTORE -> {
                                BackupRestoreScreen(
                                    onBack = { navigateBack() },
                                    viewModel = expenseViewModel
                                )
                            }
                            Screen.CATEGORY_SETTINGS -> {
                                CategorySettingsScreen(
                                    onBack = { navigateBack() }
                                )
                            }
                            Screen.TAG_SETTINGS -> {
                                TagSettingsScreen(
                                    onBack = { navigateBack() }
                                )
                            }
                            Screen.SMS_IMPORT -> {
                                SmsImportScreen(
                                    onBack = { navigateBack() },
                                    onReviewTransaction = { expense ->
                                        Log.d(SMS_TAG, "Opening SMS review for: ${expense.rowId}")
                                        expenseViewModel.editExpense(expense)
                                        // FIXED: Do NOT change currentScreen here. 
                                        // The ReviewExpenseScreen is an overlay that will show because editingExpense is set.
                                        // When it's finished, we'll still be on SMS_IMPORT.
                                    },
                                    accountFilter = expenseViewModel.selectedAccount.value
                                )
                            }
                            Screen.ACCOUNT_LIST -> {
                                AccountScreen(
                                    onBack = { navigateBack() },
                                    onNavigateToAccountSummary = { accountName ->
                                        expenseViewModel.filterByAccount(accountName)
                                        navigateTo(Screen.ACCOUNT_SUMMARY)
                                    }
                                )
                            }
                            Screen.AUDIT -> {
                                AuditScreen(
                                    onBack = { navigateBack() },
                                    expenseViewModel = expenseViewModel
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (::expenseViewModel.isInitialized) {
            expenseViewModel.handleDropboxAuth()
        }
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == RC_GOOGLE_DRIVE_PERMISSION) {
            if (resultCode == Activity.RESULT_OK) {
                GoogleSignIn.getLastSignedInAccount(this)?.let { account ->
                    if (::expenseViewModel.isInitialized) {
                        startGoogleDriveSync(account)
                    }
                }
            } else {
                Toast.makeText(this, "Google Drive permission denied.", Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun startGoogleDriveSync(account: com.google.android.gms.auth.api.signin.GoogleSignInAccount) {
        val driveScope = Scope(DriveScopes.DRIVE_APPDATA)
        if (!GoogleSignIn.hasPermissions(account, driveScope)) {
            GoogleSignIn.requestPermissions(
                this,
                RC_GOOGLE_DRIVE_PERMISSION,
                account,
                driveScope
            )
            return
        }
        expenseViewModel.refreshGoogleDriveConnection()
        expenseViewModel.syncWithGoogle(account)
        Toast.makeText(this, "Syncing with Google Drive...", Toast.LENGTH_SHORT).show()
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

    private fun exportData(viewModel: ExpenseViewModel, fileName: String, mimeType: String) {
        android.util.Log.d("MainActivity", "Exporting $fileName")
        viewModel.exportToCsv { data ->
            try {
                android.util.Log.d("MainActivity", "CSV data generated, size: ${data.length}")
                val cachePath = File(cacheDir, "exports")
                cachePath.mkdirs()
                val file = File(cachePath, fileName)
                val stream = FileOutputStream(file)
                stream.write(data.toByteArray())
                stream.close()

                val authority = "${applicationContext.packageName}.fileprovider"
                val contentUri = FileProvider.getUriForFile(this, authority, file)
                
                android.util.Log.d("MainActivity", "Content URI: $contentUri")

                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                    type = mimeType
                    putExtra(Intent.EXTRA_STREAM, contentUri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                startActivity(Intent.createChooser(shareIntent, "Export $fileName"))
            } catch (e: Exception) {
                android.util.Log.e("MainActivity", "Export failed", e)
                Toast.makeText(this, "Export failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
