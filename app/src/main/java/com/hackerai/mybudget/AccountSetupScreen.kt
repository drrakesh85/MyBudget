package com.hackerai.mybudget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.*

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AccountSetupScreen(
    viewModel: AccountViewModel = viewModel(),
    onBack: () -> Unit
) {
    val accounts by viewModel.accounts.collectAsState()
    val deletionPending by viewModel.deletionPendingAccount.collectAsState()
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccount by remember { mutableStateOf<Account?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account Management", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    TextButton(onClick = { viewModel.importAccountsFromDatabase() }) {
                        Text("Sync from DB", color = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { 
                editingAccount = null
                showAddDialog = true 
            }) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        }
    ) { padding ->
        if (accounts.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("No accounts added yet.")
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize().padding(padding)) {
                items(accounts) { account ->
                    AccountItem(
                        account = account,
                        onEdit = { 
                            editingAccount = account
                            showAddDialog = true
                        },
                        onDelete = { viewModel.deleteAccount(account.id) }
                    )
                }
            }
        }

        if (showAddDialog) {
            AddAccountDialog(
                editingAccount = editingAccount,
                onDismiss = { showAddDialog = false },
                onAdd = { accountData ->
                    if (editingAccount != null) {
                        val updated = when (accountData) {
                            is AccountData.Saving -> SavingAccount(editingAccount!!.id, accountData.nickName, accountData.bankName, accountData.branchName, accountData.accountNumber)
                            is AccountData.Loan -> LoanAccount(editingAccount!!.id, accountData.nickName, accountData.bankName, accountData.branchName, accountData.accountNumber)
                            is AccountData.CreditCard -> CreditCardAccount(editingAccount!!.id, accountData.nickName, accountData.bankName, accountData.cardNumber, accountData.expiry, accountData.billingDate, accountData.dueDate)
                            is AccountData.Cash -> CashAccount(editingAccount!!.id, accountData.nickName)
                        }
                        viewModel.updateAccount(updated)
                    } else {
                        when (accountData) {
                            is AccountData.Saving -> viewModel.addSavingAccount(accountData.nickName, accountData.bankName, accountData.branchName, accountData.accountNumber)
                            is AccountData.Loan -> viewModel.addLoanAccount(accountData.nickName, accountData.bankName, accountData.branchName, accountData.accountNumber)
                            is AccountData.CreditCard -> viewModel.addCreditCardAccount(accountData.nickName, accountData.bankName, accountData.cardNumber, accountData.expiry, accountData.billingDate, accountData.dueDate)
                            is AccountData.Cash -> viewModel.addCashAccount(accountData.nickName)
                        }
                    }
                    showAddDialog = false
                }
            )
        }

        if (deletionPending != null) {
            val (account, count) = deletionPending!!
            var selectedRelocationAccount by remember { mutableStateOf("") }
            val otherAccounts = accounts.filter { it.id != account.id }

            AlertDialog(
                onDismissRequest = { viewModel.cancelDeletion() },
                title = { Text("Relocate Transactions") },
                text = {
                    Column {
                        Text("Account '${account.nickName}' has $count transactions. Before deleting, please select an account to relocate them to:")
                        Spacer(modifier = Modifier.height(16.dp))
                        
                        otherAccounts.forEach { other ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedRelocationAccount = other.nickName }
                                    .padding(vertical = 8.dp)
                            ) {
                                RadioButton(
                                    selected = selectedRelocationAccount == other.nickName,
                                    onClick = { selectedRelocationAccount = other.nickName }
                                )
                                Text(other.nickName, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                        
                        if (otherAccounts.isEmpty()) {
                            Text("No other accounts available for relocation. Please create another account first.", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.confirmDeletionWithRelocation(account, selectedRelocationAccount) },
                        enabled = selectedRelocationAccount.isNotEmpty()
                    ) {
                        Text("RELOCATE & DELETE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.cancelDeletion() }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
fun AccountItem(account: Account, onEdit: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.nickName, style = MaterialTheme.typography.titleLarge)
                Text(text = "Type: ${account.type.name}", style = MaterialTheme.typography.bodyMedium)
                when (account) {
                    is SavingAccount -> Text("Bank: ${account.bankName} | A/C: ${account.accountNumber}")
                    is LoanAccount -> Text("Bank: ${account.bankName} | Loan A/C: ${account.accountNumber}")
                    is CreditCardAccount -> Text("Bank: ${account.bankName} | Card: ****${account.cardNumber.takeLast(4)}")
                    is CashAccount -> {}
                }
            }
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = "Edit")
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                }
            }
        }
    }
}

sealed class AccountData {
    data class Saving(val nickName: String, val bankName: String, val branchName: String, val accountNumber: String) : AccountData()
    data class Loan(val nickName: String, val bankName: String, val branchName: String, val accountNumber: String) : AccountData()
    data class CreditCard(val nickName: String, val bankName: String, val cardNumber: String, val expiry: String, val billingDate: Int, val dueDate: Int) : AccountData()
    data class Cash(val nickName: String) : AccountData()
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun AddAccountDialog(editingAccount: Account?, onDismiss: () -> Unit, onAdd: (AccountData) -> Unit) {
    var selectedType by remember { mutableStateOf(editingAccount?.type ?: AccountType.SAVING) }
    var nickName by remember { mutableStateOf(editingAccount?.nickName ?: "") }
    
    var bankName by remember { mutableStateOf((editingAccount as? SavingAccount)?.bankName ?: (editingAccount as? LoanAccount)?.bankName ?: (editingAccount as? CreditCardAccount)?.bankName ?: "") }
    var branchName by remember { mutableStateOf((editingAccount as? SavingAccount)?.branchName ?: (editingAccount as? LoanAccount)?.branchName ?: "") }
    var accountNumber by remember { mutableStateOf((editingAccount as? SavingAccount)?.accountNumber ?: (editingAccount as? LoanAccount)?.accountNumber ?: "") }
    var cardNumber by remember { mutableStateOf((editingAccount as? CreditCardAccount)?.cardNumber ?: "") }
    var expiry by remember { mutableStateOf((editingAccount as? CreditCardAccount)?.expiry ?: "") }
    var billingDate by remember { mutableStateOf((editingAccount as? CreditCardAccount)?.billingDate?.toString() ?: "") }
    var dueDate by remember { mutableStateOf((editingAccount as? CreditCardAccount)?.dueDate?.toString() ?: "") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (editingAccount == null) "Add New Account" else "Edit Account") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                // Allow type change even during edit
                Text("Select Type:")
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    AccountType.entries.forEach { type ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(type.name) }
                        )
                    }
                }

                TextField(value = nickName, onValueChange = { nickName = it }, label = { Text("Nick Name") }, modifier = Modifier.fillMaxWidth())
                
                if (selectedType != AccountType.CASH) {
                    TextField(value = bankName, onValueChange = { bankName = it }, label = { Text("Bank Name") }, modifier = Modifier.fillMaxWidth())
                }

                when (selectedType) {
                    AccountType.SAVING, AccountType.LOAN -> {
                        TextField(value = branchName, onValueChange = { branchName = it }, label = { Text("Branch Name") }, modifier = Modifier.fillMaxWidth())
                        TextField(value = accountNumber, onValueChange = { accountNumber = it }, label = { Text("Account Number") }, modifier = Modifier.fillMaxWidth())
                    }
                    AccountType.CREDIT_CARD -> {
                        TextField(value = cardNumber, onValueChange = { cardNumber = it }, label = { Text("Card Number") }, modifier = Modifier.fillMaxWidth())
                        TextField(value = expiry, onValueChange = { expiry = it }, label = { Text("Expiry (MM/YY)") }, modifier = Modifier.fillMaxWidth())
                        TextField(value = billingDate, onValueChange = { billingDate = it }, label = { Text("Billing Date (1-31)") }, modifier = Modifier.fillMaxWidth())
                        TextField(value = dueDate, onValueChange = { dueDate = it }, label = { Text("Due Date (1-31)") }, modifier = Modifier.fillMaxWidth())
                    }
                    AccountType.CASH -> {}
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val data = when (selectedType) {
                    AccountType.SAVING -> AccountData.Saving(nickName, bankName, branchName, accountNumber)
                    AccountType.LOAN -> AccountData.Loan(nickName, bankName, branchName, accountNumber)
                    AccountType.CREDIT_CARD -> AccountData.CreditCard(nickName, bankName, cardNumber, expiry, billingDate.toIntOrNull() ?: 1, dueDate.toIntOrNull() ?: 1)
                    AccountType.CASH -> AccountData.Cash(nickName)
                }
                onAdd(data)
            }) { Text(if (editingAccount == null) "Add" else "Update") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
