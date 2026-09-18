package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

enum class SmsSortOrder {
    NEWEST_FIRST,
    OLDEST_FIRST
}

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
    private val accountRepository = (application as MyBudgetApplication).accountRepository
    private val smsRepository = SmsRepository(application)

    private val _scannedToImport = MutableStateFlow<List<Expense>>(emptyList())
    private val _isLoading = MutableStateFlow(false)
    private val _error = MutableStateFlow<String?>(null)
    private val _sortOrder = MutableStateFlow(SmsSortOrder.NEWEST_FIRST)
    val sortOrder: StateFlow<SmsSortOrder> = _sortOrder.asStateFlow()

    private var _accountFilter: String? = null

    val uiState: StateFlow<SmsImportUiState> = combine(
        _scannedToImport,
        expenseRepository.getPendingReviewExpensesFlow(),
        _isLoading,
        _error,
        _sortOrder
    ) { scanned, pending, loading, error, order ->
        when {
            loading -> SmsImportUiState.Loading
            error != null -> SmsImportUiState.Error(error)
            else -> {
                val filteredPending = if (_accountFilter != null) {
                    pending.filter { it.account == _accountFilter }
                } else {
                    pending
                }
                SmsImportUiState.Success(
                    toImport = sortExpenses(scanned, order),
                    pendingReview = sortExpenses(filteredPending, order)
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SmsImportUiState.Idle)

    private fun sortExpenses(list: List<Expense>, order: SmsSortOrder): List<Expense> {
        return when (order) {
            SmsSortOrder.NEWEST_FIRST -> list.sortedWith(
                compareByDescending<Expense> { it.typeId.toLongOrNull() ?: 0L }
                    .thenByDescending { it.rowId }
            )
            SmsSortOrder.OLDEST_FIRST -> list.sortedWith(
                compareBy<Expense> { it.typeId.toLongOrNull() ?: 0L }
                    .thenBy { it.rowId }
            )
        }
    }

    fun setSortOrder(order: SmsSortOrder) {
        _sortOrder.value = order
    }

    private val _selectedTransactions = MutableStateFlow<Set<String>>(emptySet())
    val selectedTransactions: StateFlow<Set<String>> = _selectedTransactions.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _dateRange = MutableStateFlow<Pair<Long?, Long?>>(null to null)
    val dateRange: StateFlow<Pair<Long?, Long?>> = _dateRange.asStateFlow()

    fun setAccountFilter(accountName: String?) {
        _accountFilter = accountName
    }

    fun scanSms() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null
            val startTime = System.currentTimeMillis()
            try {
                // Safe boundary: Scan last 90 days. 
                // This covers all relevant recent transactions while avoiding reading years of spam.
                val ninetyDaysAgo = System.currentTimeMillis() - (90L * 24 * 60 * 60 * 1000)
                
                val messages = smsRepository.fetchSmsMessages(since = ninetyDaysAgo)
                val accounts = accountRepository.accounts.value
                
                // Optimized DB call: Only fetch rowIds, not full objects
                val allExistingRowIds = expenseRepository.getAllRowIds()
                
                val detectedRaw = messages.mapNotNull { TransactionParser.parse(it) }
                val detected = detectedRaw
                    .filter { !allExistingRowIds.contains(it.rowId) }
                    .mapNotNull { expense ->
                        val smsAddress = expense.paymentMethod
                        val smsBody = expense.description

                        val matchedAccount = accounts.find { acc ->
                            val numericMatch = when (acc) {
                                is SavingAccount -> acc.accountNumber.isNotEmpty() && acc.accountNumber != "00000000" && acc.accountNumber.endsWith(expense.account)
                                is LoanAccount -> acc.accountNumber.isNotEmpty() && acc.accountNumber != "00000000" && acc.accountNumber.endsWith(expense.account)
                                is CreditCardAccount -> acc.cardNumber.isNotEmpty() && acc.cardNumber != "0000" && acc.cardNumber.endsWith(expense.account)
                                else -> false
                            }
                            if (numericMatch) return@find true

                            val keywordsString = acc.smsSenderKeywords ?: ""
                            if (keywordsString.isNotBlank()) {
                                val keywords = keywordsString.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                                val senderMatches = keywords.any { smsAddress.contains(it, ignoreCase = true) }
                                val bodyMatches = keywords.any { smsBody.contains(it, ignoreCase = true) }
                                if (senderMatches || bodyMatches) return@find true
                            }
                            false
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
                
                val duration = System.currentTimeMillis() - startTime
                android.util.Log.i("SMS_PERF", "Scan complete in ${duration}ms. Read: ${messages.size}, Parsed: ${detectedRaw.size}, Found new: ${detected.size}")

                _scannedToImport.value = detected
                _selectedTransactions.value = detected.map { it.rowId }.toSet()
                _isLoading.value = false
            } catch (e: Exception) {
                android.util.Log.e("SMS_PERF", "Error scanning SMS", e)
                _error.value = e.message ?: "Failed to scan SMS"
                _isLoading.value = false
            }
        }
    }

    fun markAsDiscarded(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.markAsDiscarded(expense.rowId)
        }
    }

    fun discardMultiple(rowIds: List<String>) {
        viewModelScope.launch {
            expenseRepository.discardMultiple(rowIds)
        }
    }

    fun discardOlderThan(days: Int) {
        viewModelScope.launch {
            val date = java.time.LocalDate.now().minusDays(days.toLong())
            val dateStr = date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            expenseRepository.discardOlderThan(dateStr)
        }
    }

    fun discardBetween(start: Long, end: Long) {
        viewModelScope.launch {
            val fmt = java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy")
            val startStr = java.time.Instant.ofEpochMilli(start).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            val endStr = java.time.Instant.ofEpochMilli(end).atZone(java.time.ZoneId.systemDefault()).toLocalDate().format(fmt)
            expenseRepository.discardBetween(startStr, endStr)
        }
    }

    fun discardAllMatching(searchQuery: String, start: Long?, end: Long?, tab: Int) {
        viewModelScope.launch {
            val state = uiState.value
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
                
                if (tab == 0) {
                    filtered.forEach { expenseRepository.saveExpense(it, isPendingReview = false, isDiscarded = true) }
                    _scannedToImport.value = _scannedToImport.value.filter { item -> !filtered.any { it.rowId == item.rowId } }
                } else {
                    expenseRepository.discardMultiple(filtered.map { it.rowId })
                }
            }
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
        viewModelScope.launch {
            val state = uiState.value
            if (state is SmsImportUiState.Success) {
                val toImport = state.toImport.filter { _selectedTransactions.value.contains(it.rowId) }
                // Optimized bulk save
                expenseRepository.saveExpenses(toImport, isPendingReview = true, isDiscarded = false)
                _scannedToImport.value = _scannedToImport.value.filter { item -> !toImport.any { it.rowId == item.rowId } }
                onComplete()
            }
        }
    }

    fun discardSelected(onComplete: () -> Unit) {
        viewModelScope.launch {
            val state = uiState.value
            if (state is SmsImportUiState.Success) {
                val toDiscard = state.toImport.filter { _selectedTransactions.value.contains(it.rowId) }
                // Optimized bulk save
                expenseRepository.saveExpenses(toDiscard, isPendingReview = false, isDiscarded = true)
                _scannedToImport.value = _scannedToImport.value.filter { item -> !toDiscard.any { it.rowId == item.rowId } }
                onComplete()
            }
        }
    }

    fun markAsReviewed(expense: Expense) {
        viewModelScope.launch {
            expenseRepository.approveExpense(expense)
        }
    }
}
