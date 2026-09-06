package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

sealed interface AuditUiState {
    object Loading : AuditUiState
    data class Success(val issues: List<AuditIssue>) : AuditUiState
}

data class AuditIssue(
    val title: String,
    val description: String,
    val type: IssueType,
    val affectedCount: Int,
    val data: Any? = null
)

enum class IssueType {
    ORPHANED_TRANSACTIONS,
    EMPTY_ACCOUNT_NAME,
    MISMATCHED_TYPES
}

class AuditViewModel(application: Application) : AndroidViewModel(application) {
    private val expenseRepository: ExpenseRepository = (application as MyBudgetApplication).expenseRepository
    private val accountRepository: AccountRepository = (application as MyBudgetApplication).accountRepository

    private val _uiState = MutableStateFlow<AuditUiState>(AuditUiState.Loading)
    val uiState: StateFlow<AuditUiState> = _uiState.asStateFlow()

    init {
        runAudit()
    }

    fun runAudit() {
        viewModelScope.launch {
            _uiState.value = AuditUiState.Loading
            
            val expenses = expenseRepository.loadExpenses()
            val accounts = accountRepository.accounts.value
            val accountNames = accounts.map { it.nickName }.toSet()
            
            val issues = mutableListOf<AuditIssue>()
            
            // 1. Find Orphaned Transactions (Transactions with account names that don't exist in Settings)
            val orphaned = expenses.filter { it.account.isNotBlank() && !accountNames.contains(it.account) }
            if (orphaned.isNotEmpty()) {
                val uniqueOrphans = orphaned.map { it.account }.distinct()
                issues.add(AuditIssue(
                    title = "Orphaned Transactions",
                    description = "Found transactions linked to accounts that don't exist: ${uniqueOrphans.joinToString()}",
                    type = IssueType.ORPHANED_TRANSACTIONS,
                    affectedCount = orphaned.size,
                    data = uniqueOrphans
                ))
            }
            
            // 2. Find Transactions with blank account names
            val blankAccounts = expenses.filter { it.account.isBlank() }
            if (blankAccounts.isNotEmpty()) {
                issues.add(AuditIssue(
                    title = "Missing Account Names",
                    description = "Found transactions with no account assigned.",
                    type = IssueType.EMPTY_ACCOUNT_NAME,
                    affectedCount = blankAccounts.size
                ))
            }
            
            _uiState.value = AuditUiState.Success(issues)
        }
    }

    fun fixOrphaned(oldName: String, newName: String) {
        viewModelScope.launch {
            expenseRepository.relocateTransactions(oldName, newName)
            runAudit()
        }
    }
    
    fun fixBlank(newName: String) {
        viewModelScope.launch {
            expenseRepository.relocateTransactions("", newName)
            runAudit()
        }
    }
}
