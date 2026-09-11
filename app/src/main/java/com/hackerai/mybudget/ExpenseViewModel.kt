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
    private val syncManager = SyncManager(repository)
    private val googleDriveHelper = GoogleDriveHelper(application)
    private val dropboxHelper = DropboxHelper(application)

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
                        accountRepository.addAccount(com.hackerai.mybudget.data.CreditCardAccount(UUID.randomUUID().toString(), name, name, "0000", "01/99", 1, 1))
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

    fun loadExpenses() {
        viewModelScope.launch {
            _uiState.value = BudgetUiState.Loading
            try {
                val expenses = repository.loadExpenses()
                val pending = repository.loadPendingReviewExpenses()
                updateAutocompleteLists(expenses)
                _uiState.value = BudgetUiState.Success(expenses, pending)
                refreshSummaries(expenses)
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
        val filtered = ExpenseSummaryCalculator.filterByAccount(expenses, _selectedAccount.value)
        _currentBalance.value = ExpenseSummaryCalculator.currentBalance(filtered)
        _summaryData.value = ExpenseSummaryCalculator.calculateSummaries(filtered)
    }

    fun scanSms() {
        viewModelScope.launch {
            try {
                _uiState.value = BudgetUiState.Loading
                val messages = smsRepository.fetchSmsMessages()
                val pending = repository.scanAndStoreSmsExpenses(messages)
                val expenses = repository.loadExpenses()
                updateAutocompleteLists(expenses)
                _uiState.value = BudgetUiState.Success(expenses, pending)
                refreshSummaries(expenses)
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

    fun addNewExpense() {
        _editingExpense.value = Expense.createEmpty()
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

    fun saveReviewedExpense(expense: Expense) {
        viewModelScope.launch {
            val currentState = _uiState.value
            if (currentState !is BudgetUiState.Success) return@launch
            try {
                val isExisting = currentState.expenses.any { it.rowId == expense.rowId }
                val wasPending = currentState.pendingSms.any { it.rowId == expense.rowId }

                repository.approveExpense(expense)

                val updatedExpenses = if (isExisting || wasPending) {
                    if (isExisting) {
                        currentState.expenses.map { if (it.rowId == expense.rowId) expense else it }
                    } else {
                        currentState.expenses + expense
                    }
                } else {
                    currentState.expenses + expense
                }

                updateAutocompleteLists(updatedExpenses)
                val updatedPending = currentState.pendingSms.filter { it.rowId != expense.rowId }
                _uiState.value = currentState.copy(expenses = updatedExpenses, pendingSms = updatedPending)
                _editingExpense.value = null
                refreshSummaries(updatedExpenses)
            } catch (e: Exception) {
                _uiState.value = BudgetUiState.Error(e.message ?: "Failed to save expense")
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
                val remoteExpenses = googleDriveHelper.downloadSyncData(account)
                val merged = syncManager.mergeExpenses(remoteExpenses)
                googleDriveHelper.uploadSyncData(account, merged)
                loadExpenses()
                _googleDriveSyncState.value = SyncState.Success("Google Drive sync complete")
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

    fun clearGoogleDriveRecoverableAuthIntent() {
        _googleDriveRecoverableAuthIntent.value = null
    }

    fun getGoogleSignInClient() = googleDriveHelper.getGoogleSignInClient()

    fun hasGoogleDrivePermission(account: GoogleSignInAccount) = googleDriveHelper.hasDrivePermission(account)

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
                val localExpenses = repository.getAllForSync()
                
                val mergedExpenses = if (remoteSyncData != null) {
                    syncManager.mergeExpenses(remoteSyncData.expenses)
                } else {
                    localExpenses
                }

                dropboxHelper.uploadSyncData(
                    DropboxSyncData(
                        lastSyncTimestamp = System.currentTimeMillis(),
                        expenses = mergedExpenses
                    )
                )
                
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

    companion object {
        private const val TAG = "ExpenseViewModel"
    }
}
