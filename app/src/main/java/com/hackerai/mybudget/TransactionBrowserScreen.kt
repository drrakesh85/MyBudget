package com.hackerai.mybudget

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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
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

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Browse Transactions", color = Color.White) },
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
        Column(modifier = Modifier.padding(padding).fillMaxSize()) {
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

            // Applied Filters Chips
            FlowRow(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (selectedAccount != null) {
                    FilterTag("Acc: $selectedAccount") { viewModel.filterByAccount(null) }
                }
                if (selectedCategory != null) {
                    FilterTag("Cat: $selectedCategory") { viewModel.filterByCategory(null) }
                }
                if (selectedType != null) {
                    FilterTag("Type: $selectedType") { viewModel.filterByType(null) }
                }
                if (dateRange.first != null) {
                    FilterTag("Date Filter Active") { viewModel.setDateRange(null, null) }
                }
            }

            HorizontalDivider()

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

                        dateMatches && accountMatches && categoryMatches && typeMatches
                    }.sortedByDescending { parseDate(it.date) ?: 0L }

                    if (filteredList.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("No transactions found")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(filteredList) { expense ->
                                ExpenseItem(expense) {
                                    viewModel.editExpense(expense)
                                    onBack()
                                }
                            }
                        }
                    }
                }
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
