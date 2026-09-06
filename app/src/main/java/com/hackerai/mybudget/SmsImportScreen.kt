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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.Expense

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsImportScreen(
    onBack: () -> Unit,
    onReviewTransaction: (Expense) -> Unit,
    accountFilter: String? = null,
    viewModel: SmsImportViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedTransactions by viewModel.selectedTransactions.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val dateRange by viewModel.dateRange.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("TO IMPORT", "TO REVIEW")

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDateRangePickerState()

    LaunchedEffect(accountFilter) {
        viewModel.setAccountFilter(accountFilter)
        viewModel.scanSms()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                TopAppBar(
                    title = { Text(if (accountFilter != null) "SMS for $accountFilter" else "SMS Management", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { showDatePicker = true }) {
                            Icon(Icons.Default.CalendarMonth, contentDescription = "Filter by Date", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTabIndex]),
                            color = Color.White
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, color = Color.White) }
                        )
                    }
                }
                
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.setSearchQuery(it) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(8.dp),
                    placeholder = { Text("Search by description or amount...", color = Color.LightGray) },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, tint = Color.White) },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = Color.White)
                            }
                        }
                    },
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.White.copy(alpha = 0.2f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.1f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                        cursorColor = Color.White,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    shape = MaterialTheme.shapes.medium,
                    singleLine = true
                )

                if (dateRange.first != null && dateRange.second != null) {
                    val start = java.time.Instant.ofEpochMilli(dateRange.first!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    val end = java.time.Instant.ofEpochMilli(dateRange.second!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Range: $start to $end",
                            color = Color.White,
                            fontSize = 12.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.setDateRange(null, null) }, modifier = Modifier.size(16.dp)) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear Date", tint = Color.White)
                        }
                    }
                }
            }
        },
        bottomBar = {
            if (selectedTabIndex == 0 && uiState is SmsImportUiState.Success) {
                val count = (uiState as SmsImportUiState.Success).toImport.count { selectedTransactions.contains(it.rowId) }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.discardSelected { viewModel.scanSms() } },
                        modifier = Modifier.weight(1f),
                        enabled = count > 0,
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red)
                    ) {
                        Text("Discard ($count)")
                    }
                    Button(
                        onClick = { viewModel.importSelected { viewModel.scanSms() } },
                        modifier = Modifier.weight(1f),
                        enabled = count > 0
                    ) {
                        Text("Import ($count)")
                    }
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is SmsImportUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is SmsImportUiState.Error -> Text(state.message, modifier = Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
                is SmsImportUiState.Success -> {
                    val rawList = if (selectedTabIndex == 0) state.toImport else state.pendingReview
                    
                    val filtered = remember(rawList, searchQuery, dateRange) {
                        rawList.filter { transaction ->
                            val matchesSearch = transaction.description.contains(searchQuery, ignoreCase = true) ||
                                    transaction.amount.toString().contains(searchQuery) ||
                                    transaction.account.contains(searchQuery, ignoreCase = true)
                            
                            val matchesDate = if (dateRange.first != null && dateRange.second != null) {
                                val date = try {
                                    java.time.LocalDate.parse(transaction.date, java.time.format.DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                                } catch (e: Exception) { null }
                                if (date != null) {
                                    val start = java.time.Instant.ofEpochMilli(dateRange.first!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    val end = java.time.Instant.ofEpochMilli(dateRange.second!!).atZone(java.time.ZoneId.systemDefault()).toLocalDate()
                                    !date.isBefore(start) && !date.isAfter(end)
                                } else true
                            } else true
                            
                            matchesSearch && matchesDate
                        }
                    }

                    if (filtered.isEmpty()) {
                        Text(if (selectedTabIndex == 0) "No new SMS found" else "No transactions pending review", modifier = Modifier.align(Alignment.Center))
                    } else {
                        Column {
                            if (selectedTabIndex == 0) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("${filtered.size} results", style = MaterialTheme.typography.labelMedium)
                                    Row {
                                        TextButton(onClick = { viewModel.selectAll(filtered.map { it.rowId }) }) {
                                            Text("Select All")
                                        }
                                        TextButton(onClick = { viewModel.deselectAll() }) {
                                            Text("Deselect All")
                                        }
                                    }
                                }
                            }
                            
                            LazyColumn(modifier = Modifier.fillMaxSize()) {
                                items(filtered) { transaction ->
                                    if (selectedTabIndex == 0) {
                                        SmsTransactionItem(
                                            transaction = transaction,
                                            isSelected = selectedTransactions.contains(transaction.rowId),
                                            onToggle = { viewModel.toggleSelection(transaction.rowId) }
                                        )
                                    } else {
                                        SmsReviewItem(
                                            transaction = transaction,
                                            onClick = { onReviewTransaction(transaction) },
                                            onMarkReviewed = { viewModel.markAsReviewed(transaction) },
                                            onDiscard = { viewModel.markAsDiscarded(transaction) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
                else -> {}
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.setDateRange(datePickerState.selectedStartDateMillis, datePickerState.selectedEndDateMillis)
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DateRangePicker(state = datePickerState, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun SmsTransactionItem(transaction: Expense, isSelected: Boolean, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onToggle() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description, maxLines = 1, fontWeight = FontWeight.Medium)
            Text("${transaction.date} ${transaction.time} • ${transaction.account}", fontSize = 12.sp, color = Color.Gray)
        }
        Text(
            text = String.format(java.util.Locale.getDefault(), "%.2f", transaction.amount),
            color = if (transaction.amount < 0) Color.Red else Color(0xFF2E7D32),
            fontWeight = FontWeight.Bold
        )
    }
    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}

@Composable
fun SmsReviewItem(transaction: Expense, onClick: () -> Unit, onMarkReviewed: () -> Unit, onDiscard: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(transaction.description, maxLines = 1, fontWeight = FontWeight.Medium)
            Text("${transaction.date} • ${transaction.account}", fontSize = 12.sp, color = Color.Gray)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = String.format(java.util.Locale.getDefault(), "%.2f", transaction.amount),
                color = if (transaction.amount < 0) Color.Red else Color(0xFF2E7D32),
                fontWeight = FontWeight.Bold
            )
            Row {
                TextButton(
                    onClick = { onDiscard() },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("DISCARD", fontSize = 10.sp, color = Color.Red)
                }
                TextButton(
                    onClick = { onMarkReviewed() },
                    contentPadding = PaddingValues(0.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Text("MARK REVIEWED", fontSize = 10.sp)
                }
            }
        }
    }
    HorizontalDivider(thickness = 0.5.dp, modifier = Modifier.padding(horizontal = 16.dp))
}
