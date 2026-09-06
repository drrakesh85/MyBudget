package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.*
import com.hackerai.mybudget.ui.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSummaryScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onBack: () -> Unit,
    onManageAccounts: () -> Unit
) {
    val selectedAccountName by viewModel.selectedAccount.collectAsState()
    val fullAccounts by viewModel.fullAccounts.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var timeFilter by remember { mutableStateOf("Monthly") }
    var periodOffset by remember { mutableIntStateOf(0) }

    val account = fullAccounts.find { it.nickName == selectedAccountName }
    val allExpenses = (uiState as? BudgetUiState.Success)?.expenses ?: emptyList()

    // Calculate effective date range based on filter and offset
    val currentRange = remember(timeFilter, periodOffset) {
        when (timeFilter) {
            "Weekly" -> getOffsetWeekRange(periodOffset)
            "Monthly" -> getOffsetMonthRange(periodOffset)
            "Yearly" -> getOffsetYearRange(periodOffset)
            else -> null to null
        }
    }

    val filteredExpenses = remember(allExpenses, selectedAccountName, currentRange) {
        filterAccountExpenses(allExpenses, selectedAccountName, currentRange)
    }

    val pendingSms = (uiState as? BudgetUiState.Success)?.pendingSms ?: emptyList()
    val accountPendingSms = remember(pendingSms, selectedAccountName) {
        pendingSms.filter { it.account == selectedAccountName }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                TopAppBar(
                    title = { Text(selectedAccountName ?: "Account Summary", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = onManageAccounts) {
                            Icon(Icons.Default.Settings, contentDescription = "Manage Accounts", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
                )
                
                if (timeFilter != "All") {
                    PeriodNavigationBar(timeFilter, periodOffset) { periodOffset = it }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                        Text("All Time Data", color = Color.White, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(filteredExpenses)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
            // Duration Filter Bar
            Surface(modifier = Modifier.fillMaxWidth(), color = Color.White, shadowElevation = 1.dp) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.Center
                ) {
                    TimeFilterChip("All", timeFilter == "All") { 
                        timeFilter = "All"
                        periodOffset = 0
                    }
                    TimeFilterChip("Weekly", timeFilter == "Weekly") { 
                        timeFilter = "Weekly"
                        periodOffset = 0
                    }
                    TimeFilterChip("Monthly", timeFilter == "Monthly") { 
                        timeFilter = "Monthly"
                        periodOffset = 0
                    }
                    TimeFilterChip("Yearly", timeFilter == "Yearly") { 
                        timeFilter = "Yearly"
                        periodOffset = 0
                    }
                }
            }

            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                if (account != null) {
                    item { AccountDetailCard(account) }
                }

                if (accountPendingSms.isNotEmpty()) {
                    item {
                        Text(
                            "Pending Transactions (${accountPendingSms.size})",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    items(accountPendingSms) { expense ->
                        PendingSmsReviewItem(
                            expense = expense,
                            onReview = { viewModel.editExpense(expense) },
                            onMarkReviewed = { viewModel.markAsReviewed(expense) },
                            onDiscard = { viewModel.markAsDiscarded(expense) }
                        )
                    }
                }

                item {
                    PeriodActivityCard(timeFilter, filteredExpenses)
                }

                if (selectedAccountName == null) {
                    items(fullAccounts) { acc ->
                        AccountDetailCard(acc)
                    }
                }
            }
        }
    }
}

@Composable
fun PendingSmsReviewItem(
    expense: Expense,
    onReview: () -> Unit,
    onMarkReviewed: () -> Unit,
    onDiscard: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(expense.description, maxLines = 1, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Text("${expense.date} • ${expense.time}", fontSize = 11.sp, color = Color.Gray)
                }
                Text(
                    text = formatAccountAmount(expense.amount),
                    color = if (expense.amount < 0) Color.Red else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(onClick = onDiscard) {
                    Text("DISCARD", color = Color.Red, fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                TextButton(onClick = onMarkReviewed) {
                    Text("MARK REVIEWED", fontSize = 11.sp)
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onReview,
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text("REVIEW", fontSize = 11.sp)
                }
            }
        }
    }
}

@Composable
fun AccountDetailCard(account: Account) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = when(account.type) {
                        AccountType.SAVING -> Icons.Default.AccountBalance
                        AccountType.CREDIT_CARD -> Icons.Default.CreditCard
                        AccountType.LOAN -> Icons.Default.Payments
                        AccountType.CASH -> Icons.Default.Money
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(text = account.nickName, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(text = account.type.name, fontSize = 12.sp, color = Color.Gray)
                }
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
            when (account) {
                is SavingAccount -> {
                    AccountInfoRow("Bank Name", account.bankName)
                    AccountInfoRow("Branch Name", account.branchName)
                    AccountInfoRow("Account Number", account.accountNumber)
                }
                is LoanAccount -> {
                    AccountInfoRow("Bank Name", account.bankName)
                    AccountInfoRow("Loan Account", account.accountNumber)
                }
                is CreditCardAccount -> {
                    AccountInfoRow("Bank Name", account.bankName)
                    AccountInfoRow("Card Number", "**** **** **** ${account.cardNumber.takeLast(4)}")
                    AccountInfoRow("Billing Date", "Day ${account.billingDate}")
                    AccountInfoRow("Due Date", "Day ${account.dueDate}")
                }
                is CashAccount -> {
                    Text("Physical Cash / Wallet", fontSize = 14.sp, color = Color.Gray)
                }
            }
        }
    }
}

@Composable
fun AccountInfoRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, fontSize = 14.sp, color = Color.Gray)
        Text(text = value, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@Composable
fun PeriodActivityCard(filter: String, filteredExpenses: List<Expense>) {
    val income = filteredExpenses.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = filteredExpenses.filter { it.amount < 0 }.sumOf { it.amount }
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("Activity Summary", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = MaterialTheme.colorScheme.primary)
            Text("Filtered by: $filter", fontSize = 12.sp, color = Color.Gray)
            Spacer(modifier = Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Income", color = Color.Gray)
                Text(formatAccountAmount(income), color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Expense", color = Color.Gray)
                Text(formatAccountAmount(expense), color = Color.Red, fontWeight = FontWeight.Bold)
            }
            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), thickness = 0.5.dp)
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Net Balance", fontWeight = FontWeight.Medium)
                Text(formatAccountAmount(income + expense), color = if (income + expense >= 0) Color(0xFF2E7D32) else Color.Red, fontWeight = FontWeight.Bold)
            }
        }
    }
}

private fun filterAccountExpenses(expenses: List<Expense>, account: String?, range: Pair<LocalDate?, LocalDate?>): List<Expense> {
    return expenses.filter { exp ->
        val accountMatches = account == null || exp.account == account
        val dateMatches = if (range.first != null && range.second != null) {
            val expDate = try { LocalDate.parse(exp.date, DateTimeFormatter.ofPattern("dd-MM-yyyy")) } catch (e: Exception) { null }
            expDate != null && !expDate.isBefore(range.first) && !expDate.isAfter(range.second)
        } else true
        accountMatches && dateMatches
    }
}

private fun formatAccountAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}
