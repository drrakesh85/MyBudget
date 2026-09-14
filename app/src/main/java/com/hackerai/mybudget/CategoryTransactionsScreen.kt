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
fun CategoryTransactionsScreen(
    viewModel: ExpenseViewModel = viewModel(),
    filterValue: String,
    filterType: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccountName by viewModel.selectedAccount.collectAsState()
    
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
            val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
            "${currentRange.first!!.format(formatter)} - ${currentRange.second!!.format(formatter)}"
        } else "All Time"
    }

    // Filter and Sort all expenses for this selection
    val filteredExpensesSet = remember(allExpenses, filterValue, filterType, selectedAccountName) {
        val isIncomeMode = filterType in listOf("Income", "Income without transfer", "Payer - Income", "Tag - Income")
        val withoutTransfer = filterType in listOf("Categories without transfer", "Sub Categories without transfer", "Income without transfer")
        
        allExpenses.filter { exp ->
            // 1. Account Filter
            val accountMatches = selectedAccountName == null || exp.account == selectedAccountName
            if (!accountMatches) return@filter false
            
            // 2. Income/Expense Filter
            val amountMatches = if (isIncomeMode) exp.amount > 0 else exp.amount < 0
            if (!amountMatches) return@filter false
            
            // 3. Transfer Filter
            if (withoutTransfer) {
                val isTransfer = exp.transactionType == "Transfer" || exp.category == "Transfer"
                if (isTransfer) return@filter false
            }
            
            // 4. Grouping Value Filter
            val valueMatches = when (filterType) {
                "Category", "Categories without transfer", "Income", "Income without transfer" -> exp.category == filterValue
                "Sub Category", "Sub Categories without transfer" -> exp.subcategory == filterValue
                "Payee", "Payer - Income" -> exp.payeePayer == filterValue
                "Tag - Expense", "Tag - Income" -> exp.tag == filterValue
                else -> exp.category == filterValue
            }
            valueMatches
        }.sortedWith(compareBy({ parseDateLocal(it.date) }, { it.time }, { it.rowId }))
    }

    // Map of rowId to running total
    val runningTotals = remember(filteredExpensesSet) {
        var current = 0.0
        filteredExpensesSet.associate { exp ->
            current += exp.amount
            exp.rowId to current
        }
    }

    // Filter for current view period
    val visibleExpenses = remember(filteredExpensesSet, currentRange, searchQuery) {
        filteredExpensesSet.filter { exp ->
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
                            Column {
                                Text(
                                    text = filterValue.ifBlank { "Uncategorized" }, 
                                    color = Color.White,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = rangeText,
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 12.sp
                                )
                            }
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

                // Date Navigation Controls
                PeriodNavigationBar(timeFilter, periodOffset) { periodOffset = it }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(visibleExpenses)
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Duration Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TimeFilterChip("All", timeFilter == "All") { timeFilter = "All"; periodOffset = 0 }
                    TimeFilterChip("Weekly", timeFilter == "Weekly") { timeFilter = "Weekly"; periodOffset = 0 }
                    TimeFilterChip("Monthly", timeFilter == "Monthly") { timeFilter = "Monthly"; periodOffset = 0 }
                    TimeFilterChip("Yearly", timeFilter == "Yearly") { timeFilter = "Yearly"; periodOffset = 0 }
                    
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = Color(0xFF00897B))
                    }
                }

                if (visibleExpenses.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        val msg = if (searchQuery.isNotBlank()) "No search results" else "No transactions found"
                        Text(msg, color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        groupedExpenses.forEach { (date, expenses) ->
                            val dayEndTotal = runningTotals[expenses.first().rowId] ?: 0.0
                            
                            item {
                                DayHeaderComponent(date, expenses, dayEndTotal)
                            }
                            items(expenses, key = { it.rowId }) { expense ->
                                TransactionListItemComponent(
                                    expense = expense,
                                    closingBalance = runningTotals[expense.rowId] ?: 0.0,
                                    onClick = { viewModel.editExpense(expense) }
                                )
                            }
                        }
                    }
                }
            }

            if (isSearchMode && searchQuery.length >= 2) {
                Box(modifier = Modifier.fillMaxSize()) {
                    SmartSearchOverlay(
                        query = searchQuery,
                        expenses = filteredExpensesSet,
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
fun DayHeaderComponent(date: String, dayExpenses: List<Expense>, dayEndTotal: Double) {
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
                    text = formatSimple(dayEndTotal),
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
fun TransactionListItemComponent(expense: Expense, closingBalance: Double, onClick: () -> Unit) {
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
                    text = expense.payeePayer.ifEmpty { expense.description.ifEmpty { "Transaction" } },
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "${expense.category}:${expense.subcategory} | ${expense.account}",
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
