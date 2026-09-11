package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.Expense
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun TransactionBrowserScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by viewModel.accounts.collectAsState()
    val categories by viewModel.categories.collectAsState()
    
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    val selectedCategory by viewModel.selectedCategory.collectAsState()
    val selectedType by viewModel.selectedType.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()

    var showFilters by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }

    val datePickerState = rememberDateRangePickerState()

    val title = remember(selectedAccount, dateRange) {
        val accPart = selectedAccount ?: "All Accounts"
        val datePart = if (dateRange.first != null && dateRange.second != null) {
            val start = Instant.ofEpochMilli(dateRange.first!!).atZone(ZoneId.systemDefault()).toLocalDate()
            val end = Instant.ofEpochMilli(dateRange.second!!).atZone(ZoneId.systemDefault()).toLocalDate()
            ": ${start.format(DateTimeFormatter.ofPattern("dd-MM"))} - ${end.format(DateTimeFormatter.ofPattern("dd-MM"))}"
        } else ""
        "$accPart$datePart"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title, color = Color.White, fontSize = 18.sp) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { showFilters = true }) {
                        Icon(Icons.Default.FilterList, contentDescription = "Filters", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            // Quick Date Filters
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                QuickFilterChip("Week", dateRange, viewModel) { getWeekRange() }
                QuickFilterChip("Month", dateRange, viewModel) { getMonthRange() }
                QuickFilterChip("Year", dateRange, viewModel) { getYearRange() }
                IconButton(onClick = { showDatePicker = true }) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = "Custom Range")
                }
            }

            HorizontalDivider(thickness = 0.5.dp)

            when (val state = uiState) {
                is BudgetUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
                is BudgetUiState.Error -> Text("Error: ${state.message}", color = MaterialTheme.colorScheme.error)
                is BudgetUiState.Success -> {
                    val filteredList = state.expenses.filter { expense ->
                        val dateMatches = if (dateRange.first != null && dateRange.second != null) {
                            val expDate = parseDate(expense.date) ?: 0L
                            expDate in dateRange.first!!..dateRange.second!!
                        } else true

                        val accountMatches = selectedAccount == null || expense.account == selectedAccount
                        val categoryMatches = selectedCategory == null || expense.category == selectedCategory
                        val typeMatches = selectedType == null || expense.transactionType == selectedType

                        !expense.isDeleted && dateMatches && accountMatches && categoryMatches && typeMatches
                    }.sortedByDescending { parseDate(it.date) ?: 0L }

                    if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No transactions found")
                        }
                    } else {
                        val grouped = filteredList.groupBy { it.date }
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            grouped.forEach { (date, items) ->
                                item {
                                    DateHeader(date)
                                }
                                items(items) { expense ->
                                    DetailedExpenseItem(expense) {
                                        viewModel.editExpense(expense)
                                        onBack()
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        if (showFilters) {
            FilterDialog(
                accounts = accounts,
                categories = categories,
                selectedAccount = selectedAccount,
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
                        viewModel.setDateRange(datePickerState.selectedStartDateMillis, datePickerState.selectedEndDateMillis)
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
fun DateHeader(dateStr: String) {
    val formatted = remember(dateStr) {
        try {
            val date = LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy EEE")).uppercase()
        } catch (e: Exception) {
            dateStr
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color(0xFFE0E0E0))
            .padding(horizontal = 16.dp, vertical = 4.dp)
    ) {
        Text(text = formatted, fontSize = 12.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray)
    }
}

@Composable
fun DetailedExpenseItem(expense: Expense, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = expense.payeePayer.ifEmpty { expense.description.ifEmpty { "Transaction" } },
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${expense.category}:${expense.subcategory}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                if (expense.time.isNotBlank()) {
                    Text(text = expense.time, fontSize = 11.sp, color = Color.LightGray)
                }
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = formatBrowserAmount(expense.amount),
                    color = if (expense.amount < 0) Color.Red else Color(0xFF2E7D32),
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Text(
                    text = expense.status.ifEmpty { "clear" },
                    fontSize = 11.sp,
                    color = Color.Gray
                )
            }
        }
        HorizontalDivider(modifier = Modifier.padding(top = 8.dp), thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

private fun formatBrowserAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}

@Composable
fun QuickFilterChip(label: String, currentRange: Pair<Long?, Long?>, viewModel: ExpenseViewModel, rangeProvider: () -> Pair<Long, Long>) {
    val range = rangeProvider()
    val isSelected = currentRange.first == range.first && currentRange.second == range.second
    
    FilterChip(
        selected = isSelected,
        onClick = {
            if (isSelected) viewModel.setDateRange(null, null)
            else viewModel.setDateRange(range.first, range.second)
        },
        label = { Text(label) }
    )
}

@Composable
fun FilterTag(text: String, onClear: () -> Unit) {
    InputChip(
        selected = true,
        onClick = {},
        label = { Text(text) },
        trailingIcon = {
            Icon(Icons.Default.Clear, contentDescription = "Clear", modifier = Modifier.size(16.dp).clickable { onClear() })
        },
        modifier = Modifier.padding(vertical = 4.dp)
    )
}

@Composable
fun FilterDialog(
    accounts: List<String>,
    categories: List<String>,
    selectedAccount: String?,
    selectedCategory: String?,
    selectedType: String?,
    onDismiss: () -> Unit,
    onApply: (String?, String?, String?) -> Unit
) {
    var acc by remember { mutableStateOf(selectedAccount) }
    var cat by remember { mutableStateOf(selectedCategory) }
    var type by remember { mutableStateOf(selectedType) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter Transactions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                DropdownSelector("Account", accounts, acc) { acc = it }
                DropdownSelector("Category", categories, cat) { cat = it }
                DropdownSelector("Type", listOf("Income", "Expense", "Transfer"), type) { type = it }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(acc, cat, type) }) { Text("Apply") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(null, null, null) }) { Text("Clear All") }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DropdownSelector(label: String, items: List<String>, selected: String?, onSelect: (String?) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        OutlinedTextField(
            value = selected ?: "All",
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier.fillMaxWidth().clickable { expanded = true },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) }
        )
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(text = { Text("All") }, onClick = { onSelect(null); expanded = false })
            items.forEach { item ->
                DropdownMenuItem(text = { Text(item) }, onClick = { onSelect(item); expanded = false })
            }
        }
    }
}

private fun parseDate(dateStr: String): Long? {
    return try {
        LocalDate.parse(dateStr, DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault()))
            .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    } catch (e: Exception) {
        android.util.Log.w("TransactionBrowser", "Failed to parse date: $dateStr", e)
        null
    }
}

private fun getWeekRange(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.minusDays((now.dayOfWeek.value - 1).toLong()).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.plusDays((7 - now.dayOfWeek.value).toLong()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}

private fun getMonthRange(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.withDayOfMonth(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.withDayOfMonth(now.lengthOfMonth()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}

private fun getYearRange(): Pair<Long, Long> {
    val now = LocalDate.now(ZoneId.systemDefault())
    val start = now.withDayOfYear(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val end = now.withDayOfYear(now.lengthOfYear()).atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
    return start to end
}
