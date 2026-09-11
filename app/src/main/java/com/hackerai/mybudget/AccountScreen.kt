package com.hackerai.mybudget

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

sealed class AccountData {
    data class Saving(val nickName: String, val bankName: String, val branchName: String, val accountNumber: String) : AccountData()
    data class Loan(val nickName: String, val bankName: String, val branchName: String, val accountNumber: String) : AccountData()
    data class CreditCard(val nickName: String, val bankName: String, val cardNumber: String, val expiry: String, val billingDate: Int, val dueDate: Int) : AccountData()
    data class Cash(val nickName: String) : AccountData()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onNavigateToAccountSummary: (String) -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val accountBalances by viewModel.accountBalances.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val isGrouped by viewModel.isGrouped.collectAsState()
    val deletionPending by viewModel.deletionPendingAccount.collectAsState()
    
    val coroutineScope = rememberCoroutineScope()
    var isEditMode by remember { mutableStateOf(false) }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingAccountData by remember { mutableStateOf<Account?>(null) }

    // State for drag and drop
    val lazyListState = rememberLazyListState()
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }

    // We use the full account list for editing/reordering
    val currentList = remember(accounts) { accounts.toMutableStateList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isEditMode) "Manage Accounts" else "Account", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = { 
                        if (isEditMode) isEditMode = false else onBack()
                    }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    if (isEditMode) {
                        TextButton(onClick = { viewModel.importAccountsFromDatabase() }) {
                            Text("Sync DB", color = Color.White)
                        }
                        IconButton(onClick = { viewModel.toggleGrouping() }) {
                            Icon(
                                if (isGrouped) Icons.Default.ViewList else Icons.Default.ViewModule,
                                contentDescription = "Toggle Grouping",
                                tint = Color.White
                            )
                        }
                    }
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(
                            if (isEditMode) Icons.Default.Check else Icons.Default.Edit,
                            contentDescription = "Edit Mode",
                            tint = if (isEditMode) Color.Yellow else Color.White
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00ACC1))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { 
                    editingAccountData = null
                    showAddDialog = true 
                },
                containerColor = Color(0xFF00ACC1),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        },
        bottomBar = {
            if (!isEditMode) {
                val totalBalance = accountBalances.sumOf { it.balance }
                Surface(
                    color = Color(0xFFE8F5E9),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(8.dp).fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "Total Accounts Balance: ${formatAmount(totalBalance)} INR",
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues).fillMaxSize().background(Color.White)) {
            if (isEditMode) {
                LazyColumn(
                    state = lazyListState,
                    modifier = Modifier.fillMaxSize().pointerInput(Unit) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { offset ->
                                lazyListState.layoutInfo.visibleItemsInfo
                                    .firstOrNull { item -> offset.y.toInt() in item.offset..(item.offset + item.size) }
                                    ?.let { draggedItemIndex = it.index }
                            },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                draggedItemIndex?.let { index ->
                                    dragOffset += dragAmount.y
                                    
                                    // Auto-scroll logic when near edges
                                    val layoutInfo = lazyListState.layoutInfo
                                    val viewportHeight = layoutInfo.viewportSize.height
                                    val edgeThreshold = 150f // pixels from top/bottom to start scrolling
                                    val pointerY = change.position.y
                                    
                                    if (pointerY < edgeThreshold) {
                                        coroutineScope.launch { lazyListState.scrollBy(-15f) }
                                    } else if (pointerY > viewportHeight - edgeThreshold) {
                                        coroutineScope.launch { lazyListState.scrollBy(15f) }
                                    }

                                    val itemHeight = 70.dp.toPx() 
                                    val threshold = itemHeight * 0.6f
                                    
                                    if (dragOffset > threshold && index < currentList.size - 1) {
                                        currentList.add(index + 1, currentList.removeAt(index))
                                        viewModel.moveAccount(index, index + 1)
                                        draggedItemIndex = index + 1
                                        dragOffset = 0f
                                    } else if (dragOffset < -threshold && index > 0) {
                                        currentList.add(index - 1, currentList.removeAt(index))
                                        viewModel.moveAccount(index, index - 1)
                                        draggedItemIndex = index - 1
                                        dragOffset = 0f
                                    }
                                }
                            },
                            onDragEnd = { draggedItemIndex = null; dragOffset = 0f },
                            onDragCancel = { draggedItemIndex = null; dragOffset = 0f }
                        )
                    }
                ) {
                    itemsIndexed(currentList, key = { _, item -> item.id }) { index, item ->
                        val isDragging = draggedItemIndex == index
                        ManageAccountItem(
                            account = item,
                            isDragging = isDragging,
                            onToggleVisibility = { viewModel.toggleAccountVisibility(item.id) },
                            onEdit = { 
                                editingAccountData = item
                                showAddDialog = true 
                            },
                            onDelete = { viewModel.deleteAccount(item.id) },
                            modifier = Modifier
                                .zIndex(if (isDragging) 1f else 0f)
                                .graphicsLayer {
                                    translationY = if (isDragging) dragOffset else 0f
                                }
                                .animateItem()
                        )
                    }
                }
            } else {
                if (isGrouped) {
                    val grouped = accountBalances.groupBy { it.account.type }
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        grouped.forEach { (type, items) ->
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(Color(0xFFF5F5F5))
                                        .padding(horizontal = 16.dp, vertical = 4.dp)
                                ) {
                                    Text(text = type.name, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                                }
                            }
                            itemsIndexed(items) { _, item ->
                                AccountBalanceItem(
                                    accountBalance = item,
                                    onClick = { onNavigateToAccountSummary(item.account.nickName) }
                                )
                            }
                        }
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        itemsIndexed(accountBalances, key = { _, item -> item.account.id }) { _, item ->
                            AccountBalanceItem(
                                accountBalance = item,
                                onClick = { onNavigateToAccountSummary(item.account.nickName) }
                            )
                        }
                    }
                }
            }
        }

        if (showAddDialog) {
            AddAccountDialog(
                editingAccount = editingAccountData,
                onDismiss = { showAddDialog = false },
                onAdd = { data ->
                    if (editingAccountData != null) {
                        val updated = when (data) {
                            is AccountData.Saving -> (editingAccountData as SavingAccount).copy(nickName = data.nickName, bankName = data.bankName, branchName = data.branchName, accountNumber = data.accountNumber)
                            is AccountData.Loan -> (editingAccountData as LoanAccount).copy(nickName = data.nickName, bankName = data.bankName, branchName = data.branchName, accountNumber = data.accountNumber)
                            is AccountData.CreditCard -> (editingAccountData as CreditCardAccount).copy(nickName = data.nickName, bankName = data.bankName, cardNumber = data.cardNumber, expiry = data.expiry, billingDate = data.billingDate, dueDate = data.dueDate)
                            is AccountData.Cash -> (editingAccountData as CashAccount).copy(nickName = data.nickName)
                        }
                        viewModel.updateAccount(updated)
                    } else {
                        when (data) {
                            is AccountData.Saving -> viewModel.addSavingAccount(data.nickName, data.bankName, data.branchName, data.accountNumber)
                            is AccountData.Loan -> viewModel.addLoanAccount(data.nickName, data.bankName, data.branchName, data.accountNumber)
                            is AccountData.CreditCard -> viewModel.addCreditCardAccount(data.nickName, data.bankName, data.cardNumber, data.expiry, data.billingDate, data.dueDate)
                            is AccountData.Cash -> viewModel.addCashAccount(data.nickName)
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
                                modifier = Modifier.fillMaxWidth().clickable { selectedRelocationAccount = other.nickName }.padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = selectedRelocationAccount == other.nickName, onClick = { selectedRelocationAccount = other.nickName })
                                Text(other.nickName, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(onClick = { viewModel.confirmDeletionWithRelocation(account, selectedRelocationAccount) }, enabled = selectedRelocationAccount.isNotEmpty()) {
                        Text("RELOCATE & DELETE")
                    }
                },
                dismissButton = { TextButton(onClick = { viewModel.cancelDeletion() }) { Text("CANCEL") } }
            )
        }
    }
}

@Composable
fun AccountBalanceItem(accountBalance: AccountViewModel.AccountBalance, onClick: () -> Unit) {
    val account = accountBalance.account
    val color = when (account.type) {
        AccountType.SAVING -> Color(0xFF00BCD4)
        AccountType.CREDIT_CARD -> Color(0xFFE91E63)
        AccountType.CASH -> Color(0xFF4CAF50)
        AccountType.LOAN -> Color(0xFFFF9800)
    }

    Column(modifier = Modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(modifier = Modifier.width(6.dp).height(40.dp).background(color))
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.nickName, fontWeight = FontWeight.Bold, fontSize = 15.sp, color = Color.DarkGray)
                Text(text = account.nickName, fontSize = 12.sp, color = Color.Gray)
                Text(text = "Account Balance INR", fontSize = 12.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(text = formatAmount(accountBalance.income), color = Color(0xFF2E7D32), fontSize = 14.sp)
                Text(text = "-${formatAmount(kotlin.math.abs(accountBalance.expense))}", color = Color.Red, fontSize = 14.sp)
                Text(text = formatAmount(accountBalance.balance), color = if (accountBalance.balance >= 0) Color(0xFF2E7D32) else Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Medium)
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

@Composable
fun ManageAccountItem(
    account: Account,
    isDragging: Boolean,
    onToggleVisibility: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "")
    
    Card(
        modifier = modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp).shadow(elevation),
        colors = CardDefaults.cardColors(containerColor = if (isDragging) Color(0xFFF5F5F5) else Color.White)
    ) {
        Row(
            modifier = Modifier.padding(12.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Default.DragHandle, contentDescription = null, tint = Color.Gray)
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(text = account.nickName, fontWeight = FontWeight.Bold, color = if (account.isHidden) Color.Gray else Color.Black)
                Text(text = account.type.name, fontSize = 11.sp, color = Color.Gray)
            }
            Switch(
                checked = !account.isHidden,
                onCheckedChange = { onToggleVisibility() },
                modifier = Modifier.scale(0.8f)
            )
            IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(20.dp)) }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp)) }
        }
    }
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

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}
