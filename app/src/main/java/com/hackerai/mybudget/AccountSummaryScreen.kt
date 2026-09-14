package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.*
import com.hackerai.mybudget.ui.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountSummaryScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onNavigateToBrowser: () -> Unit = {}
) {
    val selectedAccountName by viewModel.selectedAccount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var timeFilter by remember { mutableStateOf("Monthly") }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }

    val allExpenses = (uiState as? BudgetUiState.Success)?.expenses ?: emptyList()

    // Calculate effective date range
    val currentRange = remember(timeFilter, periodOffset) {
        when (timeFilter) {
            "Weekly" -> getOffsetWeekRange(periodOffset)
            "Monthly" -> getOffsetMonthRange(periodOffset)
            "Yearly" -> getOffsetYearRange(periodOffset)
            else -> null to null
        }
    }

    val rangeText = remember(currentRange) {
        if (currentRange.first != null && currentRange.second != null) {
            val formatter = DateTimeFormatter.ofPattern("dd-MM")
            "${currentRange.first!!.format(formatter)} - ${currentRange.second!!.format(formatter)}"
        } else "All Time"
    }

    // Filter and Sort all expenses for this account to calculate running balance correctly
    val accountExpenses = remember(allExpenses, selectedAccountName) {
        allExpenses.filter { it.account == selectedAccountName }
            .sortedWith(compareBy({ parseDateLocal(it.date) }, { it.time }, { it.rowId }))
    }

    // Map of rowId to running balance
    val runningBalances = remember(accountExpenses) {
        var current = 0.0
        accountExpenses.associate { exp ->
            current += exp.amount
            exp.rowId to current
        }
    }

    // Filter for current view period
    val visibleExpenses = remember(accountExpenses, currentRange, searchQuery) {
        accountExpenses.filter { exp ->
            val dateMatches = if (currentRange.first != null && currentRange.second != null) {
                val expDate = parseDateLocal(exp.date)
                expDate != null && !expDate.isBefore(currentRange.first) && !expDate.isAfter(currentRange.second)
            } else true
            
            val searchMatches = if (searchQuery.isNotBlank()) {
                exp.payeePayer.contains(searchQuery, ignoreCase = true) ||
                exp.tag.contains(searchQuery, ignoreCase = true) ||
                exp.category.contains(searchQuery, ignoreCase = true) ||
                exp.subcategory.contains(searchQuery, ignoreCase = true) ||
                exp.description.contains(searchQuery, ignoreCase = true)
            } else true

            dateMatches && searchMatches
        }.sortedWith(compareByDescending<Expense> { parseDateLocal(it.date) }.thenByDescending { it.time }.thenByDescending { it.rowId })
    }

    // Grouping for the list
    val groupedExpenses = remember(visibleExpenses) {
        visibleExpenses.groupBy { it.date }
    }

    val datePickerState = rememberDateRangePickerState()

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                if (isSearchMode) {
                    TopAppBar(
                        title = {
                            TextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                placeholder = { Text("Search transactions...", color = Color.White.copy(alpha = 0.7f)) },
                                modifier = Modifier.fillMaxWidth(),
                                colors = TextFieldDefaults.colors(
                                    focusedContainerColor = Color.Transparent,
                                    unfocusedContainerColor = Color.Transparent,
                                    disabledContainerColor = Color.Transparent,
                                    cursorColor = Color.White,
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedIndicatorColor = Color.White,
                                    unfocusedIndicatorColor = Color.White.copy(alpha = 0.5f)
                                ),
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(onClick = { searchQuery = ""; isSearchMode = false }) {
                                        Icon(Icons.Default.Close, contentDescription = "Close Search", tint = Color.White)
                                    }
                                }
                            )
                        },
                        navigationIcon = {
                            IconButton(onClick = { isSearchMode = false; searchQuery = "" }) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                } else {
                    TopAppBar(
                        title = { 
                            Text(
                                text = "${selectedAccountName ?: "Account"}: $rangeText", 
                                color = Color.White,
                                fontSize = 18.sp
                            ) 
                        },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                            }
                        },
                        actions = {
                            IconButton(onClick = { isSearchMode = true }) {
                                Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                            }
                            IconButton(onClick = { /* Filter */ }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }

                // Period Selector (Week, Month, Year, All, Calendar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeriodButton("Week", timeFilter == "Weekly") { timeFilter = "Weekly"; periodOffset = 0 }
                    PeriodButton("Month", timeFilter == "Monthly") { timeFilter = "Monthly"; periodOffset = 0 }
                    PeriodButton("Year", timeFilter == "Yearly") { timeFilter = "Yearly"; periodOffset = 0 }
                    PeriodButton("All", timeFilter == "All") { timeFilter = "All"; periodOffset = 0 }
                    
                    Spacer(modifier = Modifier.weight(1f))
                    
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), MaterialTheme.shapes.small)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(visibleExpenses)
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
            Column(modifier = Modifier.fillMaxSize()) {
                if (visibleExpenses.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No transactions found", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        groupedExpenses.forEach { (date, expenses) ->
                            val dayEndBalance = runningBalances[expenses.first().rowId] ?: 0.0
                            item {
                                DayHeader(date, expenses, dayEndBalance)
                            }
                            items(expenses, key = { it.rowId }) { expense ->
                                TransactionListItem(
                                    expense = expense,
                                    closingBalance = runningBalances[expense.rowId] ?: 0.0,
                                    onClick = { viewModel.editExpense(expense) }
                                )
                            }
                        }
                    }
                }
            }
            
            if (isSearchMode && searchQuery.length >= 2) {
                Box(modifier = Modifier.fillMaxSize().padding(top = 0.dp)) {
                    SmartSearchOverlay(
                        query = searchQuery,
                        expenses = accountExpenses,
                        onSuggestionClick = { 
                            searchQuery = it
                        }
                    )
                }
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = datePickerState.selectedStartDateMillis
                        val end = datePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            timeFilter = "Custom"
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                }
            ) {
                DateRangePicker(state = datePickerState, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun PeriodButton(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .width(80.dp)
            .height(32.dp)
            .clickable { onClick() },
        color = if (isSelected) Color(0xFFE0E0E0) else Color.White.copy(alpha = 0.1f),
        shape = MaterialTheme.shapes.small,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (isSelected) Color.Black else Color.White,
                fontSize = 14.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun DayHeader(date: String, dayExpenses: List<Expense>, dayEndBalance: Double) {
    val parsedDate = parseDateLocal(date)
    val dayName = parsedDate?.dayOfWeek?.getDisplayName(TextStyle.SHORT, Locale.getDefault())?.uppercase() ?: ""
    
    val income = dayExpenses.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = dayExpenses.filter { it.amount < 0 }.sumOf { it.amount }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$date $dayName",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = Color.DarkGray
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                if (income != 0.0) {
                    Text(formatSimple(income), color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                if (expense != 0.0) {
                    Text(formatSimple(expense), color = Color.Red, fontSize = 11.sp, fontWeight = FontWeight.Medium)
                }
                Text(
                    text = formatSimple(dayEndBalance),
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.Black,
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@Composable
fun TransactionListItem(expense: Expense, closingBalance: Double, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White)
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.payeePayer.ifEmpty { expense.description.ifEmpty { "Transaction" } }.lowercase(),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${expense.category}:${expense.subcategory}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatSimple(expense.amount),
                    color = if (expense.amount < 0) Color.Red else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = expense.status.ifEmpty { "clear" },
                        fontSize = 11.sp,
                        color = Color.Gray
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = formatSimple(closingBalance),
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

private fun parseDateLocal(dateStr: String): LocalDate? {
    return try {
        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
    } catch (e: Exception) {
        null
    }
}

private fun formatSimple(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale("en", "IN"))
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}
