package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface SmsImportUiState {
    object Idle : SmsImportUiState
    object Loading : SmsImportUiState
    data class Success(
        val toImport: List<Expense>,
        val pendingReview: List<Expense>
    ) : SmsImportUiState
    data class Error(val message: String) : SmsImportUiState
}

class SmsImportViewModel(application: Application) : AndroidViewModel(application) {
    private val expenseRepository: ExpenseRepository = (application as MyBudgetApplication).expenseRepository
    private val accountRepository: AccountRepository = (application as MyBudgetApplication).accountRepository
    private val smsRepository = SmsRepository(application)

    private val _uiState = MutableStateFlow<SmsImportUiState>(SmsImportUiState.Idle)
    val uiState: StateFlow<SmsImportUiState> = _uiState.asStateFlow()

    private val _selectedTransactions = MutableStateFlow<Set<String>>(emptySet())
    val selectedTransactions: StateFlow<Set<String>> = _selectedTransactions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val dateRange: StateFlow<Pair<Long?, Long?>> = _dateRange.asStateFlow()

    private var _accountFilter: String? = null

    fun setAccountFilter(accountName: String?) {
        _accountFilter = accountName
    }

    fun scanSms() {
        viewModelScope.launch {
            _uiState.value = SmsImportUiState.Loading
            android.util.Log.d("SmsImportViewModel", "Starting scan. Filter: $_accountFilter")
            try {
                val messages = smsRepository.fetchSmsMessages()
                val accounts = accountRepository.accounts.value
                val existingPending = expenseRepository.loadPendingReviewExpenses()
                
                // PERFORMANCE OPTIMIZATION: Load all existing rowIds at once
                val allExistingRowIds = expenseRepository.getAllForSync().map { it.rowId }.toSet()
                
                android.util.Log.d("SmsImportViewModel", "Scanning ${messages.size} messages. Already have ${allExistingRowIds.size} transactions in DB.")
                
                val detectedRaw = messages.mapNotNull { TransactionParser.parse(it) }
                android.util.Log.d("SmsImportViewModel", "Parsed ${detectedRaw.size} transactions from SMS")

                val detected = detectedRaw
                    .filter { !allExistingRowIds.contains(it.rowId) }
                    .mapNotNull { expense ->
                        val matchedAccount = accounts.find { acc ->
                            when (acc) {
                                is SavingAccount -> acc.accountNumber.isNotEmpty() && acc.accountNumber != "00000000" && acc.accountNumber.endsWith(expense.account)
                                is LoanAccount -> acc.accountNumber.isNotEmpty() && acc.accountNumber != "00000000" && acc.accountNumber.endsWith(expense.account)
                                is CreditCardAccount -> acc.cardNumber.isNotEmpty() && acc.cardNumber != "0000" && acc.cardNumber.endsWith(expense.account)
                                else -> false
                            }
                        }
                        
                        val expenseWithCorrectAccount = if (matchedAccount != null) {
                            expense.copy(account = matchedAccount.nickName)
                        } else {
                            expense
                        }

                        if (_accountFilter == null || _accountFilter == expenseWithCorrectAccount.account) {
                            expenseWithCorrectAccount
                        } else {
                            null
                        }
                    }
                
                android.util.Log.d("SmsImportViewModel", "Detected ${detected.size} NEW transactions to show in UI")

                val filteredPending = if (_accountFilter != null) {
                    existingPending.filter { it.account == _accountFilter }
                } else {
                    existingPending
                }
                
                android.util.Log.d("SmsImportViewModel", "Pending review count: ${filteredPending.size}")

                _uiState.value = SmsImportUiState.Success(detected, filteredPending)
                _selectedTransactions.value = detected.map { it.rowId }.toSet()
            } catch (e: Exception) {
                android.util.Log.e("SmsImportViewModel", "Error scanning SMS", e)
                _uiState.value = SmsImportUiState.Error(e.message ?: "Failed to scan SMS")
            }
        }
    }

    fun markAsDiscarded(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.markAsDiscarded(expense.rowId)
            scanSms() // Use scanSms to refresh everything correctly
        }
    }

    fun discardMultiple(rowIds: List<String>, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            expenseRepository.discardMultiple(rowIds)
            scanSms()
            onComplete()
        }
    }

    fun discardOlderThan(days: Int, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val date = java.time.LocalDate.now().minusDays(days.toLong())
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            expenseRepository.discardOlderThan(dateStr)
            scanSms()
            onComplete()
        }
    }

    fun discardBetween(start: Long, end: Long, onComplete: () -> Unit = {}) {
        viewModelScope.launch {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val startStr = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            val endStr = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            expenseRepository.discardBetween(startStr, endStr)
            scanSms()
            onComplete()
        }
    }

    fun discardAllMatching(searchQuery: String, start: Long?, end: Long?, tab: Int) {
        viewModelScope.launch {
            val state = _uiState.value
            if (state is SmsImportUiState.Success) {
                val listToDiscard = if (tab == 0) state.toImport else state.pendingReview
                val filtered = listToDiscard.filter { transaction ->
                    val matchesSearch = transaction.description.contains(searchQuery, ignoreCase = true) ||
                            transaction.amount.toString().contains(searchQuery) ||
                            transaction.account.contains(searchQuery, ignoreCase = true)
                    
                    val matchesDate = if (start != null && end != null) {
                        val date = try {
                            java.time.LocalDate.parse(transaction.date, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        } catch (e: Exception) { null }
                        if (date != null) {
                            val startDate = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            val endDate = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                            !date.isBefore(startDate) && !date.isAfter(endDate)
                        } else true
                    } else true
                    matchesSearch && matchesDate
                }
                
                // For tab 0 (TO IMPORT), these aren't in DB yet, so we insert them as discarded
                if (tab == 0) {
                    filtered.forEach { expenseRepository.saveExpense(it, isPendingReview = false, isDiscarded = true) }
                } else {
                    // For tab 1 (TO REVIEW), they are already in DB as pending review, so we just mark them discarded
                    expenseRepository.discardMultiple(filtered.map { it.rowId })
                }
                scanSms()
            }
        }
    }

    private suspend fun refreshData() {
        val currentState = _uiState.value
        if (currentState is SmsImportUiState.Success) {
            val pending = expenseRepository.loadPendingReviewExpenses()
            _uiState.value = currentState.copy(pendingReview = pending)
        }
    }

    fun toggleSelection(rowId: String) {
        val current = _selectedTransactions.value.toMutableSet()
        if (current.contains(rowId)) current.remove(rowId) else current.add(rowId)
        _selectedTransactions.value = current
    }

    fun selectAll(rowIds: List<String>) {
        _selectedTransactions.value = rowIds.toSet()
    }

    fun deselectAll() {
        _selectedTransactions.value = emptySet()
    }

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setDateRange(start: Long?, end: Long?) {
        _dateRange.value = start to end
    }

    fun importSelected(onComplete: () -> Unit) {
        val state = _uiState.value
        if (state is SmsImportUiState.Success) {
            viewModelScope.launch {
                val toImport = state.toImport.filter { _selectedTransactions.value.contains(it.rowId) }
                toImport.forEach { expenseRepository.saveExpense(it, isPendingReview = true, isDiscarded = false) }
                onComplete()
            }
        }
    }

    fun discardSelected(onComplete: () -> Unit) {
        val state = _uiState.value
        if (state is SmsImportUiState.Success) {
            viewModelScope.launch {
                val toDiscard = state.toImport.filter { _selectedTransactions.value.contains(it.rowId) }
                toDiscard.forEach { expenseRepository.saveExpense(it, isPendingReview = false, isDiscarded = true) }
                onComplete()
            }
        }
    }

    fun markAsReviewed(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.approveExpense(expense)
            refreshData()
        }
    }
}
