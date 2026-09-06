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
            try {
                val messages = smsRepository.fetchSmsMessages()
                val accounts = accountRepository.accounts.value
                val existingPending = expenseRepository.loadPendingReviewExpenses()
                
                val detected = messages.mapNotNull { TransactionParser.parse(it) }
                    .filter { !expenseRepository.exists(it.rowId) }
                    .mapNotNull { expense ->
                        val matchedAccount = accounts.find { acc ->
                            when (acc) {
                                is SavingAccount -> acc.accountNumber.endsWith(expense.account)
                                is LoanAccount -> acc.accountNumber.endsWith(expense.account)
                                is CreditCardAccount -> acc.cardNumber.endsWith(expense.account)
                                else -> false
                            }
                        }
                        
                        if (matchedAccount != null) {
                            val expenseWithAccount = expense.copy(account = matchedAccount.nickName)
                            // APPLY ACCOUNT FILTER IF SET
                            if (_accountFilter == null || _accountFilter == matchedAccount.nickName) {
                                expenseWithAccount
                            } else {
                                null
                            }
                        } else {
                            null
                        }
                    }
                
                val filteredPending = if (_accountFilter != null) {
                    existingPending.filter { it.account == _accountFilter }
                } else {
                    existingPending
                }

                _uiState.value = SmsImportUiState.Success(detected, filteredPending)
                _selectedTransactions.value = detected.map { it.rowId }.toSet()
            } catch (e: Exception) {
                _uiState.value = SmsImportUiState.Error(e.message ?: "Failed to scan SMS")
            }
        }
    }

    fun markAsDiscarded(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.markAsDiscarded(expense.rowId)
            refreshData()
        }
    }

    private suspend fun refreshData() {
        // Logic to refresh Success state with latest pending
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
