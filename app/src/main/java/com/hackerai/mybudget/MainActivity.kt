package com.hackerai.mybudget

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
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
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.api.services.drive.DriveScopes
import androidx.core.content.FileProvider
import android.app.Activity
import android.content.Intent
import java.io.File
import java.io.FileOutputStream

enum class Screen {
    EXPENSE_LIST, TRANSACTION_BROWSER, CATEGORY_SUMMARY, CALENDAR_VIEW, ACCOUNT_SUMMARY, SETTINGS, CATEGORY_SETTINGS, TAG_SETTINGS, SMS_IMPORT, ACCOUNT_LIST, AUDIT
}

class MainActivity : ComponentActivity() {

    companion object {
        private const val RC_GOOGLE_DRIVE_PERMISSION = 9001
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
                var currentScreen by remember { mutableStateOf(Screen.EXPENSE_LIST) }
                expenseViewModel = androidx.lifecycle.viewmodel.compose.viewModel()
                
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
                                onManageAccounts = { currentScreen = Screen.ACCOUNT_LIST },
                                onNavigateToBrowser = { currentScreen = Screen.TRANSACTION_BROWSER }
                            )
                        }
                        Screen.SETTINGS -> {
                            SettingsScreen(
                                onBack = { currentScreen = Screen.EXPENSE_LIST },
                                onNavigateToCategorySettings = { currentScreen = Screen.CATEGORY_SETTINGS },
                                onNavigateToTagSettings = { currentScreen = Screen.TAG_SETTINGS },
                                onNavigateToAudit = { currentScreen = Screen.AUDIT },
                                onNavigateToAccountManagement = { currentScreen = Screen.ACCOUNT_LIST },
                                onRestoreBackup = {
                                    expenseViewModel.importCsv()
                                    Toast.makeText(this@MainActivity, "Restoring transactions...", Toast.LENGTH_SHORT).show()
                                    currentScreen = Screen.EXPENSE_LIST
                                },
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
                                expenseViewModel = expenseViewModel
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
