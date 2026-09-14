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
    categoryName: String,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccountName by viewModel.selectedAccount.collectAsState()
    
    var timeFilter by remember { mutableStateOf("Monthly") }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }

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

    // Filter and Sort all expenses for this category
    val categoryExpenses = remember(allExpenses, categoryName, selectedAccountName) {
        allExpenses.filter { it.category == categoryName && (selectedAccountName == null || it.account == selectedAccountName) }
            .sortedWith(compareBy({ parseDateLocal(it.date) }, { it.time }, { it.rowId }))
    }

    // Map of rowId to running total for this category
    val runningTotals = remember(categoryExpenses) {
        var current = 0.0
        categoryExpenses.associate { exp ->
            current += exp.amount
            exp.rowId to current
        }
    }

    // Filter for current view period
    val visibleExpenses = remember(categoryExpenses, currentRange) {
        categoryExpenses.filter { exp ->
            if (currentRange.first != null && currentRange.second != null) {
                val expDate = parseDateLocal(exp.date)
                expDate != null && !expDate.isBefore(currentRange.first) && !expDate.isAfter(currentRange.second)
            } else true
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
                TopAppBar(
                    title = { 
                        Column {
                            Text(
                                text = categoryName, 
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
                        IconButton(onClick = { /* Search */ }) {
                            Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                        }
                        IconButton(onClick = { /* Filter */ }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                // Date Navigation Controls (similar to the screenshot)
                PeriodNavigationBar(timeFilter, periodOffset) { periodOffset = it }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(visibleExpenses)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color(0xFFF5F5F5))) {
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
                    Text("No transactions found for this category", color = Color.Gray)
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

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = datePickerState.selectedStartDateMillis
                        val end = datePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            timeFilter = "Custom"
                            // Custom range logic would go here
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
