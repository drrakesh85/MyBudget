package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.*
import android.content.Intent
import android.util.Log
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface BudgetUiState {
    object Loading : BudgetUiState
    data class Success(val expenses: List<Expense>, val pendingSms: List<Expense> = emptyList()) : BudgetUiState
    data class Error(val message: String) : BudgetUiState
}

sealed interface SyncState {
    object Idle : SyncState
    object Loading : SyncState
    data class Success(val message: String) : SyncState
    data class Error(val message: String) : SyncState
}

class ExpenseViewModel(application: Application) : AndroidViewModel(application) {
    private val repository: ExpenseRepository = (application as MyBudgetApplication).expenseRepository
    private val accountRepository = (application as MyBudgetApplication).accountRepository
    private val smsRepository = SmsRepository(application)
    private val syncManager = SyncManager(repository, accountRepository)
    private val googleDriveHelper = GoogleDriveHelper(application)
    private val dropboxHelper = DropboxHelper(application)
    private val gson = com.google.gson.Gson()

    private val _uiState = MutableStateFlow<BudgetUiState>(BudgetUiState.Loading)
    val uiState: StateFlow<BudgetUiState> = _uiState.asStateFlow()

    private val _dropboxSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val dropboxSyncState: StateFlow<SyncState> = _dropboxSyncState.asStateFlow()

    private val _googleDriveSyncState = MutableStateFlow<SyncState>(SyncState.Idle)
    val googleDriveSyncState: StateFlow<SyncState> = _googleDriveSyncState.asStateFlow()

    private val _googleDriveRecoverableAuthIntent = MutableStateFlow<Intent?>(null)
    val googleDriveRecoverableAuthIntent: StateFlow<Intent?> = _googleDriveRecoverableAuthIntent.asStateFlow()

    private val _isDropboxConnected = MutableStateFlow(dropboxHelper.isConnected())
    val isDropboxConnected: StateFlow<Boolean> = _isDropboxConnected.asStateFlow()

    private val _isGoogleDriveConnected = MutableStateFlow(googleDriveHelper.isDriveConnected())
    val isGoogleDriveConnected: StateFlow<Boolean> = _isGoogleDriveConnected.asStateFlow()

    private val _googleDriveLastSuccessfulSyncMillis = MutableStateFlow(
        googleDriveHelper.getLastSuccessfulSyncMillis().takeIf { it > 0L }
    )
    val googleDriveLastSuccessfulSyncMillis: StateFlow<Long?> =
        _googleDriveLastSuccessfulSyncMillis.asStateFlow()

    private val _currentBalance = MutableStateFlow(0.0)
    val currentBalance: StateFlow<Double> = _currentBalance.asStateFlow()

    private val _summaryData = MutableStateFlow<Map<String, Triple<Double, Double, Double>>>(emptyMap())
    val summaryData: StateFlow<Map<String, Triple<Double, Double, Double>>> = _summaryData.asStateFlow()

    private val _selectedAccount = MutableStateFlow<String?>(null)
    val selectedAccount: StateFlow<String?> = _selectedAccount.asStateFlow()

    private val _selectedCategory = MutableStateFlow<String?>(null)
    val selectedCategory: StateFlow<String?> = _selectedCategory.asStateFlow()

    private val _selectedType = MutableStateFlow<String?>(null)
    val selectedType: StateFlow<String?> = _selectedType.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val dateRange: StateFlow<Pair<Long?, Long?>> = _dateRange.asStateFlow()

    private val _accounts = MutableStateFlow<List<String>>(emptyList())
    val accounts: StateFlow<List<String>> = _accounts.asStateFlow()

    private val _payees = MutableStateFlow<List<String>>(emptyList())
    val payees: StateFlow<List<String>> = _payees.asStateFlow()

    private val _categories = MutableStateFlow<List<String>>(emptyList())
    val categories: StateFlow<List<String>> = _categories.asStateFlow()

    private val _tags = MutableStateFlow<List<String>>(emptyList())
    val tags: StateFlow<List<String>> = _tags.asStateFlow()

    private val _subcategories = MutableStateFlow<List<String>>(emptyList())
    val subcategories: StateFlow<List<String>> = _subcategories.asStateFlow()

    val fullAccounts = accountRepository.accounts

    private val _categorySubcategoryMap = MutableStateFlow<Map<String, List<String>>>(emptyMap())
    val categorySubcategoryMap: StateFlow<Map<String, List<String>>> = _categorySubcategoryMap.asStateFlow()

    private val _tagMap = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val tagMap: StateFlow<Map<String, Pair<String, String>>> = _tagMap.asStateFlow()

    private val _payeeMap = MutableStateFlow<Map<String, Pair<String, String>>>(emptyMap())
    val payeeMap: StateFlow<Map<String, Pair<String, String>>> = _payeeMap.asStateFlow()

    private val _editingExpense = MutableStateFlow<Expense?>(null)
    val editingExpense: StateFlow<Expense?> = _editingExpense.asStateFlow()

    init {
        // Collect accounts from accountRepository to keep them in global order
        // and instantly update tabs when accounts are added/hidden
        viewModelScope.launch {
            accountRepository.accounts.collect { accountList ->
                _accounts.value = accountList.filter { !it.isHidden }.map { it.nickName }
            }
        }
        
        // Auto-Discovery: Ensure all accounts in transactions are registered
        viewModelScope.launch {
            val expenses = repository.loadExpenses()
            val uniqueAccountNames = expenses.map { it.account }.filter { it.isNotBlank() }.distinct()
            val currentNicknames = accountRepository.getUniqueNickNames()
            
            uniqueAccountNames.forEach { name ->
                if (!currentNicknames.contains(name)) {
                    if (name.contains("Cash", ignoreCase = true) || name.contains("PayTM", ignoreCase = true) || name.contains("Wallet", ignoreCase = true)) {
                        accountRepository.addAccount(com.hackerai.mybudget.data.CashAccount(UUID.randomUUID().toString(), name))
                    } else if (name.contains("Card", ignoreCase = true)) {
                        accountRepository.addAccount(com.hackerai.mybudget.data.CreditCardAccount(UUID.randomUUID().toString(), name, name, "0000", "01/99", "000", 1, 1))
                    } else {
                        accountRepository.addAccount(com.hackerai.mybudget.data.SavingAccount(UUID.randomUUID().toString(), name, name, "Auto-Imported", "00000000"))
                    }
                }
            }
        }
        loadExpenses()
    }

    fun importCsv() {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            try {
                repository.importFromCsv()
                loadExpenses()
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to import CSV")
            }
        }
    }

    fun importFromStream(inputStream: java.io.InputStream, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            try {
                repository.importFromStream(inputStream)
                loadExpenses()
                onComplete()
            } catch (e: Exception) {
                Log.e("ExpenseViewModel", "Import from stream failed", e)
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to import from stream")
            }
        }
    }

    fun importFromUri(uri: android.net.Uri, onResult: (ImportResult) -> Unit) {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            val result = repository.importFromUri(uri)
            if (result.success) {
                loadExpenses()
                _uiState.value = BudgetUiState.Success(
                    (uiState.value as? BudgetUiState.Success)?.expenses ?: emptyList(),
                    (uiState.value as? BudgetUiState.Success)?.pendingSms ?: emptyList()
                )
                onResult(result)
            } else {
                _uiState.value = BudgetUiState.Error(result.errorMessage ?: "Failed to import CSV")
            }
        }
    }

    fun loadExpenses() {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            try {
                val allExpenses = repository.loadExpenses()
                val pending = repository.loadPendingReviewExpenses()
                
                // Keep system entries for autocomplete dropdowns
                updateAutocompleteLists(allExpenses)
                
                // Filter out system placeholders for the UI display
                val displayExpenses = allExpenses.filter { it.status != "system" }
                
                _uiState.value = BudgetUiState.Success(displayExpenses, pending)
                refreshSummaries(displayExpenses)
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to load expenses")
            }
        }
    }

    private fun updateAutocompleteLists(expenses: List<Expense>) {
        _payees.value = expenses.map { it.payeePayer }.filter { it.isNotBlank() }.distinct().sorted()
        _categories.value = expenses.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()
        _subcategories.value = expenses.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted()
        _tags.value = expenses.map { it.tag }.filter { it.isNotBlank() }.distinct().sorted()
        _tagMap.value = expenses.filter { it.tag.isNotBlank() && it.category.isNotBlank() }
            .groupBy { it.tag }
            .mapValues { (_, list) -> 
                val mostFrequent = list.groupBy { it.category to it.subcategory }
                    .maxByOrNull { it.value.size }?.key ?: ("" to "")
                mostFrequent
            }
        _payeeMap.value = expenses.filter { 
            it.payeePayer.isNotBlank() && it.category.isNotBlank() && it.category != "Imported" 
        }
            .groupBy { it.payeePayer }
            .mapValues { (_, list) ->
                val mostFrequent = list.groupBy { it.category to it.subcategory }
                    .maxByOrNull { it.value.size }?.key ?: ("" to "")
                mostFrequent
            }
        _categorySubcategoryMap.value = expenses.groupBy { it.category }
            .mapValues { (_, list) -> list.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted() }
    }

    private fun refreshSummaries(expenses: List<Expense>) {
        val account = _selectedAccount.value
        val filtered = ExpenseSummaryCalculator.filterByAccount(expenses, account)
        _currentBalance.value = ExpenseSummaryCalculator.currentBalance(filtered, account)
        _summaryData.value = ExpenseSummaryCalculator.calculateSummaries(filtered, account)
    }

    fun scanSms() {
        viewModelScope.launch {
            try {
                _uiState.value = BudgetUiState.Loading
                val messages = smsRepository.fetchSmsMessages()
                val pending = repository.scanAndStoreSmsExpenses(messages)
                val allExpenses = repository.loadExpenses()
                
                updateAutocompleteLists(allExpenses)
                val displayExpenses = allExpenses.filter { it.status != "system" }
                
                _uiState.value = BudgetUiState.Success(displayExpenses, pending)
                refreshSummaries(displayExpenses)
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to scan SMS")
            }
        }
    }

    fun importSmsExpense(expense: Expense) {
        _editingExpense.value = expense
    }

    fun editExpense(expense: Expense) {
        _editingExpense.value = expense
    }

    fun addNewExpense(defaultAccount: String? = null) {
        _editingExpense.value = Expense.createEmpty().copy(account = defaultAccount ?: "")
    }

    fun filterByAccount(accountName: String?) {
        _selectedAccount.value = accountName
        val currentState = _uiState.value
        if (currentState is BudgetUiState.Success) {
            refreshSummaries(currentState.expenses)
        }
    }

    fun filterByCategory(category: String?) {
        _selectedCategory.value = category
    }

    fun filterByType(type: String?) {
        _selectedType.value = type
    }

    fun setDateRange(start: Long?, end: Long?) {
        _dateRange.value = start to end
    }

    fun cancelReview() {
        _editingExpense.value = null
    }

    fun markAsReviewed(expense: Expense) {
        viewModelScope.launch {
            repository.approveExpense(expense)
            loadExpenses()
        }
    }

    fun markAsDiscarded(expense: Expense) {
        viewModelScope.launch {
            repository.markAsDiscarded(expense.rowId)
            loadExpenses()
        }
    }

    fun discardMultiple(expenses: List<Expense>) {
        viewModelScope.launch {
            repository.discardMultiple(expenses.map { it.rowId })
            loadExpenses()
        }
    }

    fun discardOlderThan(days: Int) {
        viewModelScope.launch {
            val date = java.time.LocalDate.now().minusDays(days.toLong())
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            repository.discardOlderThan(dateStr)
            loadExpenses()
        }
    }

    fun discardBetween(start: Long, end: Long) {
        viewModelScope.launch {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val startStr = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            val endStr = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            repository.discardBetween(startStr, endStr)
            loadExpenses()
        }
    }

    fun deleteExpense(expense: Expense) {
        viewModelScope.launch {
            repository.deleteById(expense.rowId)
            loadExpenses()
            _editingExpense.value = null
        }
    }

    fun saveReviewedExpense(expense: Expense) {
        saveReviewedExpenses(listOf(expense))
    }

    fun saveReviewedExpenses(expenses: List<Expense>) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is BudgetUiState.Success) return@launch
            try {
                // Optimized bulk save
                repository.saveExpenses(expenses, isPendingReview = false, isDiscarded = false)

                var currentExpenses = currentState.expenses
                var currentPending = currentState.pendingSms

                expenses.forEach { expense ->
                    val isExisting = currentExpenses.any { it.rowId == expense.rowId }
                    currentExpenses = if (isExisting) {
                        currentExpenses.map { if (it.rowId == expense.rowId) expense else it }
                    } else {
                        currentExpenses + expense
                    }
                    currentPending = currentPending.filter { it.rowId != expense.rowId }
                }

                updateAutocompleteLists(currentExpenses)
                _uiState.value = currentState.copy(expenses = currentExpenses, pendingSms = currentPending)
                _editingExpense.value = null
                refreshSummaries(currentExpenses)
                Log.d("SMS_NAV", "Room bulk save SUCCESS. Pending SMS count: ${currentPending.size}")
            } catch (e: Exception) {
                Log.e("SMS_NAV", "Room bulk save FAILED", e)
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to save expenses")
            }
        }
    }

    fun syncWithGoogle(account: GoogleSignInAccount) {
        viewModelScope.launch {
            _googleDriveSyncState.value = SyncState.Loading
            try {
                if (!googleDriveHelper.hasDrivePermission(account)) {
                    _googleDriveSyncState.value = SyncState.Error("Google Drive permission was not granted.")
                    return@launch
                }
                val remoteData = googleDriveHelper.downloadSyncData(account)
                val merged = syncManager.mergeSyncData(remoteData)
                googleDriveHelper.uploadSyncData(account, merged)
                _googleDriveSyncState.value = SyncState.Success("Sync complete")
            } catch (e: GoogleDriveAuthException) {
                Log.e(TAG, "Google Drive authorization failed", e)
                if (e.recoverableIntent != null) {
                    _googleDriveRecoverableAuthIntent.value = e.recoverableIntent
                }
                _googleDriveSyncState.value = SyncState.Error(e.message ?: "Google Drive authorization failed")
            } catch (e: Exception) {
                Log.e(TAG, "Google Drive sync failed", e)
                _googleDriveSyncState.value = SyncState.Error(e.message ?: "Google Drive sync failed")
            }
        }
    }

    fun forceReplaceGoogleDrive(account: GoogleSignInAccount, onComplete: (String) -> Unit) {
        viewModelScope.launch {
            _googleDriveSyncState.value = SyncState.Loading
            try {
                val localExpenses = repository.getAllForSync()
                val localAccounts = accountRepository.accounts.value
                val localSyncData = SyncData(
                    schemaVersion = 3,
                    lastSyncTimestamp = System.currentTimeMillis(),
                    expenses = localExpenses,
                    accounts = localAccounts
                )
                
                googleDriveHelper.forceReplaceSyncData(account, localSyncData)
                
                val syncedAt = System.currentTimeMillis()
                googleDriveHelper.saveLastSuccessfulSyncMillis(syncedAt)
                _googleDriveLastSuccessfulSyncMillis.value = syncedAt
                _isGoogleDriveConnected.value = true
                _googleDriveSyncState.value = SyncState.Success("Google Drive replaced successfully")
                onComplete("SUCCESS")
            } catch (e: Exception) {
                Log.e("FORCE_CLOUD_RESTORE", "Force replace failed in ViewModel: ${e.message}", e)
                _googleDriveSyncState.value = SyncState.Error("Force replace failed: ${e.message}")
                onComplete("FAILED: ${e.message}")
            }
        }
    }

    fun forceUploadToDropbox() {
        viewModelScope.launch {
            _dropboxSyncState.value = SyncState.Loading
            try {
                val localExpenses = repository.getAllForSync()
                val localAccounts = accountRepository.accounts.value
                val dataToUpload = SyncData(
                    lastSyncTimestamp = System.currentTimeMillis(),
                    expenses = localExpenses,
                    accounts = localAccounts
                )
                
                dropboxHelper.uploadSyncData(dataToUpload)
                _dropboxSyncState.value = SyncState.Success("Cloud data overwritten with local data.")
            } catch (e: Exception) {
                Log.e(TAG, "Dropbox force upload failed", e)
                _dropboxSyncState.value = SyncState.Error("Force upload failed: ${e.message}")
            }
        }
    }

    fun clearGoogleDriveRecoverableAuthIntent() {
        _googleDriveRecoverableAuthIntent.value = null
    }

    fun getGoogleSignInClient() = googleDriveHelper.getGoogleSignInClient()

    fun getLastSignedInGoogleAccount() = googleDriveHelper.getLastSignedInAccount()

    fun hasGoogleDrivePermission(account: GoogleSignInAccount) = googleDriveHelper.hasDrivePermission(account)

    fun refreshGoogleDriveConnection() {
        _isGoogleDriveConnected.value = googleDriveHelper.isDriveConnected()
    }

    fun startDropboxSync() {
        dropboxHelper.startAuth()
    }

    fun handleDropboxAuth() {
        if (dropboxHelper.handleAuthResponse()) {
            _isDropboxConnected.value = true
            syncWithDropbox()
        }
    }

    fun syncWithDropbox() {
        viewModelScope.launch {
            _dropboxSyncState.value = SyncState.Loading
            try {
                val remoteSyncData = dropboxHelper.downloadSyncData()
                val merged = syncManager.mergeSyncData(remoteSyncData ?: SyncData())

                dropboxHelper.uploadSyncData(merged)
                
                _dropboxSyncState.value = SyncState.Success("Sync complete")
                loadExpenses()
            } catch (e: Exception) {
                _dropboxSyncState.value = SyncState.Error(e.message ?: "Sync failed")
            }
        }
    }

    fun disconnectDropbox() {
        dropboxHelper.disconnect()
        _isDropboxConnected.value = false
        _dropboxSyncState.value = SyncState.Idle
    }

    fun exportToCsv(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val csv = repository.generateCsvData()
            onResult(csv)
        }
    }

    fun exportFullBackup(onResult: (String) -> Unit) {
        viewModelScope.launch {
            val expenses = repository.getAllForSync()
            val accounts = accountRepository.accounts.value
            val syncData = SyncData(expenses = expenses, accounts = accounts)
            onResult(gson.toJson(syncData))
        }
    }

    fun importFullBackup(json: String, onComplete: () -> Unit) {
        viewModelScope.launch {
            try {
                // Try parsing as SyncData first
                val syncData = try {
                    val data = gson.fromJson(json, SyncData::class.java)
                    // If expenses is null, it might be a legacy List<Expense> format
                    if (data?.expenses == null) {
                        val listType = object : com.google.gson.reflect.TypeToken<List<Expense>>() {}.type
                        val list: List<Expense> = gson.fromJson(json, listType)
                        SyncData(expenses = list)
                    } else {
                        data
                    }
                } catch (e: Exception) {
                    // Fallback to legacy List<Expense> if it's just a JSON array
                    val listType = object : com.google.gson.reflect.TypeToken<List<Expense>>() {}.type
                    val list: List<Expense> = gson.fromJson(json, listType)
                    SyncData(expenses = list)
                }

                if (syncData.expenses.isNotEmpty() || syncData.accounts.isNotEmpty()) {
                    syncManager.mergeSyncData(syncData)
                    onComplete()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Full backup import failed", e)
            }
        }
    }

    fun exportAppData(categories: Boolean, tags: Boolean, payers: Boolean, payees: Boolean, onResult: (String) -> Unit) {
        viewModelScope.launch(kotlinx.coroutines.Dispatchers.Default) {
            val sb = StringBuilder()
            sb.append("Type,Value1,Value2,Value3\n")
            
            if (categories) {
                _categorySubcategoryMap.value.forEach { (cat, subs) ->
                    if (subs.isEmpty()) {
                        sb.append("CATEGORY,\"$cat\",\"\",\"Expense\"\n")
                    } else {
                        subs.forEach { sub ->
                            sb.append("CATEGORY,\"$cat\",\"$sub\",\"Expense\"\n")
                        }
                    }
                }
            }
            if (tags) {
                _tags.value.forEach { sb.append("TAG,\"$it\",\"\",\"\"\n") }
            }
            if (payees) {
                _payees.value.forEach { sb.append("PAYEE,\"$it\",\"\",\"\"\n") }
            }
            if (payers) {
                _payees.value.forEach { sb.append("PAYER,\"$it\",\"\",\"\"\n") }
            }
            val result = sb.toString()
            android.util.Log.d("Backup", "Generated CSV with ${result.length} characters")
            kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.Main) {
                onResult(result)
            }
        }
    }

    fun importAppData(csvContent: String, categories: Boolean, tags: Boolean, payers: Boolean, payees: Boolean, onComplete: () -> Unit) {
        viewModelScope.launch {
            val lines = csvContent.lines()
            android.util.Log.d("Restore", "Importing CSV with ${lines.size} lines")
            if (lines.size <= 1) {
                onComplete()
                return@launch
            }
            
            val dummyTransactions = mutableListOf<Expense>()
            
            lines.drop(1).forEach { line ->
                if (line.isBlank()) return@forEach
                val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                    .map { it.trim().removeSurrounding("\"") }
                if (tokens.size >= 2) {
                    val type = tokens[0]
                    when (type) {
                        "CATEGORY" -> if (categories) {
                            dummyTransactions.add(Expense.createEmpty().copy(
                                category = tokens[1],
                                subcategory = tokens.getOrNull(2) ?: "",
                                transactionType = tokens.getOrNull(3) ?: "Expense",
                                amount = 0.0,
                                status = "system",
                                rowId = "system_cat_${tokens[1]}_${tokens.getOrNull(2) ?: ""}"
                            ))
                        }
                        "TAG" -> if (tags) {
                            dummyTransactions.add(Expense.createEmpty().copy(
                                tag = tokens[1],
                                amount = 0.0,
                                status = "system",
                                rowId = "system_tag_${tokens[1]}"
                            ))
                        }
                        "PAYEE", "PAYER" -> if ((type == "PAYEE" && payees) || (type == "PAYER" && payers)) {
                            dummyTransactions.add(Expense.createEmpty().copy(
                                payeePayer = tokens[1],
                                amount = 0.0,
                                status = "system",
                                rowId = "system_payee_${tokens[1]}"
                            ))
                        }
                    }
                }
            }
            
            if (dummyTransactions.isNotEmpty()) {
                repository.saveExpenses(dummyTransactions, isPendingReview = false, isDiscarded = false)
            }

            loadExpenses()
            onComplete()
        }
    }

    fun clearAllTransactions(onComplete: () -> Unit) {
        viewModelScope.launch {
            repository.clearAllTransactionsPreservingMetadata()
            loadExpenses()
            onComplete()
        }
    }

    companion object {
        private const val TAG = "ExpenseViewModel"
    }
}
