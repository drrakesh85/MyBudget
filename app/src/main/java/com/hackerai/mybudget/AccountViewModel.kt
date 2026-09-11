package com.hackerai.mybudget

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

class AccountViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = AccountRepository(application)
    private val expenseRepository: ExpenseRepository by lazy {
        ExpenseRepository(application, AppDatabase.getInstance(application).expenseDao())
    }
    val accounts: StateFlow<List<Account>> = repository.accounts

    private val _isGrouped = MutableStateFlow(false)
    val isGrouped: StateFlow<Boolean> = _isGrouped.asStateFlow()

    data class AccountBalance(
        val account: Account,
        val income: Double,
        val expense: Double,
        val balance: Double
    )

    val accountBalances: StateFlow<List<AccountBalance>> = combine(
        accounts,
        expenseRepository.allExpensesFlow()
    ) { accountList, expenseList ->
        accountList.filter { !it.isHidden }.map { account ->
            val accountExpenses = expenseList.filter { it.account == account.nickName }
            val income = accountExpenses.filter { it.transactionType == "Income" }.sumOf { it.amount }
            val expense = accountExpenses.filter { it.transactionType == "Expense" }.sumOf { it.amount }
            AccountBalance(account, income, expense, income + expense)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun toggleGrouping() {
        _isGrouped.value = !_isGrouped.value
    }

    fun toggleAccountVisibility(accountId: String) {
        val account = accounts.value.find { it.id == accountId } ?: return
        val updated = when (account) {
            is SavingAccount -> account.copy(isHidden = !account.isHidden)
            is LoanAccount -> account.copy(isHidden = !account.isHidden)
            is CreditCardAccount -> account.copy(isHidden = !account.isHidden)
            is CashAccount -> account.copy(isHidden = !account.isHidden)
        }
        repository.addAccount(updated)
    }

    fun moveAccount(fromIndex: Int, toIndex: Int) {
        val list = accounts.value.toMutableList()
        if (fromIndex in list.indices && toIndex in list.indices) {
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            repository.updateOrder(list)
        }
    }

    fun importAccountsFromDatabase() {
        viewModelScope.launch {
            val csvExpenses = expenseRepository.loadExpenses()
            val uniqueAccountNames = csvExpenses.map { it.account }.filter { it.isNotBlank() }.distinct()
            
            val currentNicknames = repository.getUniqueNickNames()
            
            uniqueAccountNames.forEach { name ->
                if (!currentNicknames.contains(name)) {
                    // Default to SAVING if it looks like a bank, or CASH if it says 'Cash'
                    if (name.contains("Cash", ignoreCase = true) || name.contains("PayTM", ignoreCase = true) || name.contains("Wallet", ignoreCase = true)) {
                        addCashAccount(name)
                    } else if (name.contains("Card", ignoreCase = true)) {
                        addCreditCardAccount(name, name, "0000", "01/99", 1, 1)
                    } else {
                        addSavingAccount(name, name, "Auto-Imported", "00000000")
                    }
                }
            }
        }
    }

    fun addSavingAccount(nickName: String, bankName: String, branchName: String, accountNumber: String) {
        val account = SavingAccount(UUID.randomUUID().toString(), nickName, bankName, branchName, accountNumber)
        repository.addAccount(account)
    }

    fun addLoanAccount(nickName: String, bankName: String, branchName: String, accountNumber: String) {
        val account = LoanAccount(UUID.randomUUID().toString(), nickName, bankName, branchName, accountNumber)
        repository.addAccount(account)
    }

    fun addCreditCardAccount(nickName: String, bankName: String, cardNumber: String, expiry: String, billingDate: Int, dueDate: Int) {
        val account = CreditCardAccount(UUID.randomUUID().toString(), nickName, bankName, cardNumber, expiry, billingDate, dueDate)
        repository.addAccount(account)
    }

    fun addCashAccount(nickName: String) {
        val account = CashAccount(UUID.randomUUID().toString(), nickName)
        repository.addAccount(account)
    }

    fun updateAccount(account: Account) {
        val oldNickname = accounts.value.find { it.id == account.id }?.nickName
        repository.addAccount(account)
        
        // If the name changed, update all transactions globally
        if (oldNickname != null && oldNickname != account.nickName) {
            viewModelScope.launch {
                expenseRepository.renameAccount(oldNickname, account.nickName)
            }
        }
    }

    fun deleteAccount(accountId: String) {
        val account = accounts.value.find { it.id == accountId } ?: return
        viewModelScope.launch {
            val count = expenseRepository.getTransactionCountForAccount(account.nickName)
            if (count == 0) {
                // Safe to delete immediately
                repository.deleteAccount(accountId)
            } else {
                // Notify UI that relocation is needed
                _deletionPendingAccount.value = account to count
            }
        }
    }

    private val _deletionPendingAccount = MutableStateFlow<Pair<Account, Int>?>(null)
    val deletionPendingAccount: StateFlow<Pair<Account, Int>?> = _deletionPendingAccount.asStateFlow()

    fun confirmDeletionWithRelocation(oldAccount: Account, newAccountName: String) {
        viewModelScope.launch {
            expenseRepository.relocateTransactions(oldAccount.nickName, newAccountName)
            repository.deleteAccount(oldAccount.id)
            _deletionPendingAccount.value = null
        }
    }

    fun cancelDeletion() {
        _deletionPendingAccount.value = null
    }
}
