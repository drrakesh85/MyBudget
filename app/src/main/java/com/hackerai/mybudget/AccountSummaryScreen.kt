package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
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
    onManageAccounts: () -> Unit
) {
    val selectedAccountName by viewModel.selectedAccount.collectAsState()
    val uiState by viewModel.uiState.collectAsState()
    
    var timeFilter by remember { mutableStateOf("All") }
    var periodOffset by remember { mutableIntStateOf(0) }
    var showDatePicker by remember { mutableStateOf(false) }
    var isSearchMode by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showFilters by remember { mutableStateOf(false) }

    val dateRange by viewModel.dateRange.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()

    // Sync with Activity links: Detect the intended period from the range duration
    LaunchedEffect(dateRange) {
        val start = dateRange.first
        val end = dateRange.second
        if (start != null && end != null) {
            val durationDays = (end - start) / (24 * 60 * 60 * 1000L)
            timeFilter = when {
                durationDays <= 1 -> "All" // Today/Day view
                durationDays <= 7 -> "Weekly"
                durationDays <= 31 -> "Monthly"
                durationDays <= 366 -> "Yearly"
                else -> "All"
            }
            periodOffset = 0
        }
    }

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

    val rangeText = remember(currentRange, dateRange) {
        if (dateRange.first != null && dateRange.second != null) {
            val start = java.time.Instant.ofEpochMilli(dateRange.first!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val end = java.time.Instant.ofEpochMilli(dateRange.second!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
            val fmt = DateTimeFormatter.ofPattern("dd-MM")
            "${start.format(fmt)} - ${end.format(fmt)}"
        } else if (currentRange.first != null && currentRange.second != null) {
            val formatter = DateTimeFormatter.ofPattern("dd-MM")
            "${currentRange.first!!.format(formatter)} - ${currentRange.second!!.format(formatter)}"
        } else "All Time"
    }

    // Filter and Sort
    val accountExpenses = remember(allExpenses, selectedAccountName) {
        allExpenses.filter { selectedAccountName == null || it.account == selectedAccountName || (it.transactionType == "Transfer" && it.toAccount == selectedAccountName) }
            .sortedWith(compareBy({ parseDateLocal(it.date) }, { it.time }, { it.rowId }))
    }

    val runningBalances = remember(accountExpenses, selectedAccountName) {
        var current = 0.0
        accountExpenses.associate { exp ->
            val actualAmount = if (exp.transactionType == "Transfer" && exp.toAccount == selectedAccountName) {
                kotlin.math.abs(exp.amount)
            } else {
                exp.amount
            }
            current += actualAmount
            exp.rowId to current
        }
    }

    val visibleExpenses = remember(accountExpenses, currentRange, dateRange, searchQuery, selectedCategory, selectedType) {
        accountExpenses.filter { exp ->
            val dateMatches = if (dateRange.first != null && dateRange.second != null) {
                val expDateMillis = parseDate(exp.date) ?: 0L
                expDateMillis in dateRange.first!!..dateRange.second!!
            } else if (currentRange.first != null && currentRange.second != null) {
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
            
            val categoryMatches = selectedCategory == null || exp.category == selectedCategory
            
            val isTransfer = exp.transactionType == "Transfer"
            val typeMatches = when (selectedType) {
                null -> true
                "Income" -> exp.transactionType == "Income" || (isTransfer && exp.toAccount == selectedAccountName)
                "Expense" -> exp.transactionType == "Expense" || (isTransfer && exp.account == selectedAccountName)
                else -> exp.transactionType == selectedType
            }

            dateMatches && searchMatches && categoryMatches && typeMatches
        }.sortedWith(compareByDescending<Expense> { parseDateLocal(it.date) }.thenByDescending { it.time }.thenByDescending { it.rowId })
    }

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
                            IconButton(onClick = { showFilters = true }) {
                                Icon(Icons.Default.FilterList, contentDescription = "Filter", tint = Color.White)
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PeriodButton("Week", timeFilter == "Weekly", Modifier.weight(1f)) { 
                        viewModel.setDateRange(null, null)
                        timeFilter = "Weekly"; periodOffset = 0 
                    }
                    PeriodButton("Month", timeFilter == "Monthly", Modifier.weight(1f)) { 
                        viewModel.setDateRange(null, null)
                        timeFilter = "Monthly"; periodOffset = 0 
                    }
                    PeriodButton("Year", timeFilter == "Yearly", Modifier.weight(1f)) { 
                        viewModel.setDateRange(null, null)
                        timeFilter = "Yearly"; periodOffset = 0 
                    }
                    PeriodButton("All", timeFilter == "All", Modifier.weight(1f)) { 
                        viewModel.setDateRange(null, null)
                        timeFilter = "All"; periodOffset = 0 
                    }
                    
                    IconButton(
                        onClick = { showDatePicker = true },
                        modifier = Modifier
                            .size(36.dp)
                            .background(Color.White.copy(alpha = 0.2f), CircleShape)
                    ) {
                        Icon(Icons.Default.CalendarMonth, contentDescription = "Calendar", tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(visibleExpenses)
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.addNewExpense(selectedAccountName) },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                shape = CircleShape
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Transaction")
            }
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
                                    selectedAccount = selectedAccountName,
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

        if (showFilters) {
            FilterDialog(
                accounts = accounts,
                categories = categories,
                selectedAccount = selectedAccountName,
                selectedCategory = selectedCategory,
                selectedType = selectedType,
                onDismiss = { showFilters = false },
                onApply = { acc, cat, type ->
                    viewModel.filterByAccount(acc)
                    viewModel.filterByCategory(cat)
                    viewModel.filterByType(type)
                    showFilters = false
                }
            )
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = datePickerState.selectedStartDateMillis
                        val end = datePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            viewModel.setDateRange(start, end)
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
fun PeriodButton(label: String, isSelected: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Surface(
        modifier = modifier
            .height(34.dp)
            .clickable { onClick() },
        color = if (isSelected) Color.White.copy(alpha = 0.2f) else Color.Transparent,
        shape = CircleShape,
        border = if (isSelected) androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.5f)) else null
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = Color.White,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Medium
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

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color(0xFFEEEEEE),
        tonalElevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$date $dayName",
                fontSize = 11.sp,
                fontWeight = FontWeight.ExtraBold,
                color = Color(0xFF546E7A),
                letterSpacing = 0.5.sp
            )
            
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                if (income != 0.0) {
                    Text(formatSimple(income), color = Color(0xFF2E7D32), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                if (expense != 0.0) {
                    Text(formatSimple(expense), color = Color(0xFFC62828), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
                Surface(
                    color = Color.White.copy(alpha = 0.5f),
                    shape = MaterialTheme.shapes.extraSmall
                ) {
                    Text(
                        text = formatSimple(dayEndBalance),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 11.sp,
                        color = Color.Black,
                        modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun TransactionListItem(expense: Expense, closingBalance: Double, selectedAccount: String?, onClick: () -> Unit) {
    val isTransfer = expense.transactionType == "Transfer"
    val isIncomingTransfer = isTransfer && expense.toAccount == selectedAccount
    val displayAmount = if (isIncomingTransfer) kotlin.math.abs(expense.amount) else expense.amount

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        color = Color.White
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp).fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val title = if (isIncomingTransfer) {
                        "Incoming from ${expense.account}"
                    } else if (isTransfer) {
                        "Transfer to ${expense.toAccount}"
                    } else {
                        expense.payeePayer.ifEmpty { expense.description.ifEmpty { "Transaction" } }
                    }

                    Text(
                        text = title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFF263238)
                    )
                    Text(
                        text = "${expense.category} : ${expense.subcategory}",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatSimple(displayAmount),
                        color = if (displayAmount < 0) Color(0xFFC62828) else Color(0xFF2E7D32),
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            color = Color(0xFFF5F5F5),
                            shape = CircleShape,
                            modifier = Modifier.padding(end = 6.dp)
                        ) {
                            Text(
                                text = (if (isIncomingTransfer) "RECEIVED" else expense.status.ifEmpty { "clear" }).uppercase(),
                                fontSize = 9.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                        Text(
                            text = formatSimple(closingBalance),
                            fontSize = 12.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = 16.dp),
                thickness = 0.5.dp,
                color = Color.LightGray.copy(alpha = 0.2f)
            )
        }
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

private fun parseDate(dateStr: String): Long? {
    if (dateStr.isBlank() || dateStr == "Date") return null
    return try {
        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            .atStartOfDay(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        null
    }
}
