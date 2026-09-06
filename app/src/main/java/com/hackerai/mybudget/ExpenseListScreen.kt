package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.Expense
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExpenseListScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onNavigateToAccounts: () -> Unit = {},
    onNavigateToBrowser: () -> Unit = {},
    onNavigateToSummary: () -> Unit = {},
    onNavigateToCalendar: () -> Unit = {},
    onNavigateToAccountSummary: () -> Unit = {},
    onNavigateToSettings: () -> Unit = {},
    onNavigateToSmsImport: () -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsState()
    val editingExpense by viewModel.editingExpense.collectAsState()
    val accountNames by viewModel.accounts.collectAsState()
    val payees by viewModel.payees.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val subcategories by viewModel.subcategories.collectAsState()
    val tags by viewModel.tags.collectAsState()
    val tagMap by viewModel.tagMap.collectAsState()
    val categorySubcategoryMap by viewModel.categorySubcategoryMap.collectAsState()
    val currentBalance by viewModel.currentBalance.collectAsState()
    val summaryData by viewModel.summaryData.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()

    if (editingExpense != null) {
        ReviewExpenseScreen(
            expense = editingExpense!!,
            accounts = accountNames,
            payees = payees,
            categories = categories,
            subcategories = subcategories,
            tags = tags,
            tagMap = tagMap,
            categorySubcategoryMap = categorySubcategoryMap,
            onSave = { viewModel.saveReviewedExpense(it) },
            onCancel = { viewModel.cancelReview() }
        )
    } else {
        Scaffold(
            topBar = {
                Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                    TopAppBar(
                        title = { Text(selectedAccount ?: "All (INR)", color = Color.White) },
                        navigationIcon = {
                            IconButton(onClick = onNavigateToSettings) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = onNavigateToSmsImport) {
                                Icon(Icons.Default.Sms, contentDescription = "Scan SMS", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToBrowser) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            IconButton(onClick = onNavigateToAccounts) {
                                Icon(Icons.AutoMirrored.Filled.List, contentDescription = "View Accounts", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                    
                    ScrollableTabRow(
                        selectedTabIndex = if (selectedAccount == null) 0 else accountNames.indexOf(selectedAccount) + 1,
                        containerColor = Color.Transparent,
                        contentColor = Color.White,
                        edgePadding = 16.dp,
                        divider = {}
                    ) {
                        Tab(
                            selected = selectedAccount == null,
                            onClick = { viewModel.filterByAccount(null) },
                            text = { Text("ALL") }
                        )
                        accountNames.forEach { name ->
                            Tab(
                                selected = selectedAccount == name,
                                onClick = { viewModel.filterByAccount(name) },
                                text = { Text(name.uppercase()) }
                            )
                        }
                    }
                }
            },
            floatingActionButton = {
                FloatingActionButton(
                    onClick = { viewModel.addNewExpense() },
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = Color.White
                ) {
                    Icon(Icons.Default.Add, contentDescription = "Add Transaction")
                }
            }
        ) { paddingValues ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .verticalScroll(rememberScrollState())
                    .background(Color(0xFFF5F5F5))
            ) {
                // Quick Action Grid
                QuickActionGrid(onNavigateToAccountSummary, onNavigateToSummary, onNavigateToCalendar)

                // Current Balance Card
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White)
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Current Balance", style = MaterialTheme.typography.bodyLarge)
                        Spacer(modifier = Modifier.weight(1f))
                        Text(
                            formatAmount(currentBalance),
                            style = MaterialTheme.typography.titleLarge,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                // Activity Summaries
                SummaryRow("Today", summaryData["Today"] ?: Triple(0.0, 0.0, 0.0)) {
                    val range = getDayRange()
                    viewModel.setDateRange(range.first, range.second)
                    onNavigateToBrowser()
                }
                SummaryRow("This Week", summaryData["This Week"] ?: Triple(0.0, 0.0, 0.0)) {
                    val range = getWeekRangeInternal()
                    viewModel.setDateRange(range.first, range.second)
                    onNavigateToBrowser()
                }
                SummaryRow("This Month", summaryData["This Month"] ?: Triple(0.0, 0.0, 0.0)) {
                    val range = getMonthRangeInternal()
                    viewModel.setDateRange(range.first, range.second)
                    onNavigateToBrowser()
                }
                SummaryRow("Year to Date", summaryData["Year to Date"] ?: Triple(0.0, 0.0, 0.0)) {
                    val range = getYearRangeInternal()
                    viewModel.setDateRange(range.first, range.second)
                    onNavigateToBrowser()
                }

                // Recent Transactions Header
                PaddingText("Recent Transactions")

                // Recent Transactions List
                when (val state = uiState) {
                    is BudgetUiState.Success -> {
                        val filtered = state.expenses.filter { expense ->
                            selectedAccount == null || expense.account == selectedAccount
                        }
                        val list = filtered.take(5)
                        if (list.isEmpty()) {
                            Text(
                                "No transactions",
                                modifier = Modifier.padding(16.dp),
                                color = Color.Gray
                            )
                        } else {
                            list.forEach { expense ->
                                DashboardExpenseItem(expense) {
                                    viewModel.editExpense(expense)
                                }
                            }
                        }
                    }
                    is BudgetUiState.Loading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).padding(16.dp))
                    }
                    is BudgetUiState.Error -> {
                        Text(
                            state.message,
                            modifier = Modifier.padding(16.dp),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }
                
                TrendChartSection()
            }
        }
    }
}

@Composable
fun QuickActionGrid(onAccounts: () -> Unit, onSummary: () -> Unit, onCalendar: () -> Unit) {
    Column(modifier = Modifier.padding(8.dp)) {
        Row(modifier = Modifier.fillMaxWidth()) {
            ActionItem("Summary", Icons.Default.Description, Modifier.weight(1f)) { onSummary() }
            ActionItem("Budget", Icons.Default.PieChart, Modifier.weight(1f)) {}
            ActionItem("Recurring", Icons.Default.Repeat, Modifier.weight(1f)) {}
            ActionItem("Debt", Icons.Default.MoneyOff, Modifier.weight(1f)) {}
        }
        Row(modifier = Modifier.fillMaxWidth()) {
            ActionItem("Calendar", Icons.Default.CalendarToday, Modifier.weight(1f)) { onCalendar() }
            ActionItem("Chart", Icons.Default.BarChart, Modifier.weight(1f)) {}
            ActionItem("Transfer", Icons.Default.SyncAlt, Modifier.weight(1f)) {}
            ActionItem("More...", Icons.Default.MoreHoriz, Modifier.weight(1f)) { /* Original More Action */ }
        }
    }
}

@Composable
fun ActionItem(label: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier = modifier
            .padding(4.dp)
            .clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = label, tint = Color(0xFF00796B), modifier = Modifier.size(24.dp))
        Text(label, fontSize = 10.sp, color = Color(0xFF00796B))
    }
}

@Composable
fun SummaryRow(title: String, data: Triple<Double, Double, Double>, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clickable { onClick() },
        colors = CardDefaults.cardColors(containerColor = Color.White),
        shape = MaterialTheme.shapes.extraSmall
    ) {
        Column(modifier = Modifier.padding(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF00796B), fontSize = 12.sp)
                Spacer(modifier = Modifier.weight(1f))
                Text("Activity", color = Color(0xFF00796B), fontSize = 12.sp)
            }
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(formatAmount(data.first), color = Color(0xFF2E7D32), modifier = Modifier.weight(1f), fontSize = 12.sp)
                Text(formatAmount(data.second), color = Color.Red, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 12.sp)
                Text(formatAmount(data.third), modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun DashboardExpenseItem(expense: Expense, onClick: () -> Unit) {
    ExpenseItem(expense, onClick)
}

@Composable
fun ExpenseItem(expense: Expense, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(expense.description.ifEmpty { expense.category }, fontSize = 14.sp)
                Text("${expense.category}:${expense.subcategory}", fontSize = 11.sp, color = Color.Gray)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    formatAmount(expense.amount),
                    color = if (expense.amount < 0) Color.Red else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(expense.status.ifEmpty { "clear" }, fontSize = 11.sp, color = Color.Gray)
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp)
    }
}

@Composable
fun TrendChartSection() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Last 7 Days", fontSize = 12.sp)
                Spacer(modifier = Modifier.weight(1f))
                RadioButton(selected = true, onClick = {})
                Text("Expense", fontSize = 12.sp)
                RadioButton(selected = false, onClick = {})
                Text("Income", fontSize = 12.sp)
            }
            Box(modifier = Modifier.height(100.dp).fillMaxWidth().background(Color(0xFFFAFAFA))) {
                Text("Trend Chart Placeholder", modifier = Modifier.align(Alignment.Center), color = Color.LightGray)
            }
        }
    }
}

@Composable
fun PaddingText(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(16.dp),
        style = MaterialTheme.typography.labelLarge,
        color = Color.Gray
    )
}

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}

// Helpers for date range navigation
private fun getDayRange(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}

private fun getWeekRangeInternal(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.minusDays((now.dayOfWeek.value - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.plusDays((7 - now.dayOfWeek.value).toLong()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}

private fun getMonthRangeInternal(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}

private fun getYearRangeInternal(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.withDayOfYear(now.lengthOfYear()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}
