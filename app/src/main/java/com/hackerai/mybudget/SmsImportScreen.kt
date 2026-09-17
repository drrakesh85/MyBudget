package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
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
    val sortOrder by viewModel.sortOrder.collectAsState()

    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("TO IMPORT", "TO REVIEW")

    var showDatePicker by remember { mutableStateOf(false) }
    var showCleanupDialog by remember { mutableStateOf(false) }
    var showSafetyConfirm by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showSortMenu by remember { mutableStateOf(false) }
    
    val datePickerState = rememberDateRangePickerState()
    val cleanupRangePickerState = rememberDateRangePickerState()
    var showCleanupRangePicker by remember { mutableStateOf(false) }

    LaunchedEffect(accountFilter) {
        viewModel.setAccountFilter(accountFilter)
        viewModel.scanSms()
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                TopAppBar(
                    title = { 
                        if (selectedTransactions.isNotEmpty()) {
                            Text("${selectedTransactions.size} selected", color = Color.White)
                        } else {
                            Text(if (accountFilter != null) "SMS for $accountFilter" else "SMS Management", color = Color.White)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            if (selectedTransactions.isNotEmpty()) {
                                viewModel.deselectAll()
                            } else {
                                onBack()
                            }
                        }) {
                            Icon(
                                if (selectedTransactions.isNotEmpty()) Icons.Default.Close else Icons.AutoMirrored.Filled.ArrowBack, 
                                contentDescription = "Back", 
                                tint = Color.White
                            )
                        }
                    },
                    actions = {
                        if (selectedTransactions.isNotEmpty()) {
                            IconButton(onClick = { 
                                val state = uiState
                                if (state is SmsImportUiState.Success) {
                                    val ids = selectedTransactions.toList()
                                    showSafetyConfirm = { 
                                        if (selectedTabIndex == 0) {
                                            viewModel.discardSelected { viewModel.scanSms() }
                                        } else {
                                            viewModel.discardMultiple(ids)
                                        }
                                    }
                                }
                            }) {
                                Icon(Icons.Default.Delete, contentDescription = "Discard Selected", tint = Color.White)
                            }
                        }
                        
                        Box {
                            IconButton(onClick = { showSortMenu = true }) {
                                Icon(Icons.Default.Sort, contentDescription = "Sort", tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Newest first") },
                                    onClick = { 
                                        viewModel.setSortOrder(SmsSortOrder.NEWEST_FIRST)
                                        showSortMenu = false 
                                    },
                                    leadingIcon = { 
                                        if (sortOrder == SmsSortOrder.NEWEST_FIRST) 
                                            Icon(Icons.Default.Check, contentDescription = null) 
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Oldest first") },
                                    onClick = { 
                                        viewModel.setSortOrder(SmsSortOrder.OLDEST_FIRST)
                                        showSortMenu = false 
                                    },
                                    leadingIcon = { 
                                        if (sortOrder == SmsSortOrder.OLDEST_FIRST) 
                                            Icon(Icons.Default.Check, contentDescription = null) 
                                    }
                                )
                            }
                        }

                        IconButton(onClick = { showCleanupDialog = true }) {
                            Icon(Icons.Default.CleaningServices, contentDescription = "Clean Up", tint = Color.White)
                        }
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
            if (uiState is SmsImportUiState.Success) {
                val count = selectedTransactions.size
                Row(
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (selectedTabIndex == 0) {
                        OutlinedButton(
                            onClick = { 
                                val state = uiState as SmsImportUiState.Success
                                val selected = state.toImport.filter { selectedTransactions.contains(it.rowId) }
                                showSafetyConfirm = { viewModel.discardSelected { viewModel.scanSms() } }
                            },
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
                    } else {
                        Button(
                            onClick = { 
                                val state = uiState as SmsImportUiState.Success
                                val selected = state.pendingReview.filter { selectedTransactions.contains(it.rowId) }
                                showSafetyConfirm = { viewModel.discardMultiple(selected.map { it.rowId }) }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = count > 0,
                            colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                        ) {
                            Text("Discard Selected ($count)")
                        }
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
                                            isSelected = selectedTransactions.contains(transaction.rowId),
                                            onToggle = { viewModel.toggleSelection(transaction.rowId) },
                                            onClick = { onReviewTransaction(transaction) },
                                            onMarkReviewed = { viewModel.markAsReviewed(transaction) },
                                            onDiscard = { 
                                                showSafetyConfirm = { viewModel.markAsDiscarded(transaction) }
                                            }
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

        if (showCleanupDialog) {
            AlertDialog(
                onDismissRequest = { showCleanupDialog = false },
                title = { Text("Clean Up SMS Data") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Discard messages that are no longer needed:")
                        Button(onClick = { showCleanupDialog = false; showSafetyConfirm = { viewModel.discardOlderThan(7) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Older than 7 days")
                        }
                        Button(onClick = { showCleanupDialog = false; showSafetyConfirm = { viewModel.discardOlderThan(30) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Older than 30 days")
                        }
                        Button(onClick = { showCleanupDialog = false; showSafetyConfirm = { viewModel.discardOlderThan(90) } }, modifier = Modifier.fillMaxWidth()) {
                            Text("Older than 90 days")
                        }
                        OutlinedButton(onClick = { showCleanupDialog = false; showCleanupRangePicker = true }, modifier = Modifier.fillMaxWidth()) {
                            Text("Custom Date Range")
                        }
                        if (searchQuery.isNotBlank() || dateRange.first != null) {
                            TextButton(onClick = { 
                                showCleanupDialog = false
                                showSafetyConfirm = { viewModel.discardAllMatching(searchQuery, dateRange.first, dateRange.second, selectedTabIndex) }
                            }, modifier = Modifier.fillMaxWidth()) {
                                Text("Discard all matching current filter", color = Color.Red)
                            }
                        }
                    }
                },
                confirmButton = {},
                dismissButton = { TextButton(onClick = { showCleanupDialog = false }) { Text("Cancel") } }
            )
        }

        if (showCleanupRangePicker) {
            DatePickerDialog(
                onDismissRequest = { showCleanupRangePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        val start = cleanupRangePickerState.selectedStartDateMillis
                        val end = cleanupRangePickerState.selectedEndDateMillis
                        if (start != null && end != null) {
                            showCleanupRangePicker = false
                            showSafetyConfirm = { viewModel.discardBetween(start, end) }
                        }
                    }) { Text("OK") }
                }
            ) {
                DateRangePicker(state = cleanupRangePickerState, modifier = Modifier.weight(1f))
            }
        }

        showSafetyConfirm?.let { action ->
            AlertDialog(
                onDismissRequest = { showSafetyConfirm = null },
                title = { Text("Confirm Discard") },
                text = { Text("This will mark selected messages as discarded. They will no longer appear in the app. This cannot be undone.") },
                confirmButton = {
                    Button(onClick = { 
                        action()
                        showSafetyConfirm = null
                        viewModel.deselectAll()
                    }, colors = ButtonDefaults.buttonColors(containerColor = Color.Red)) {
                        Text("PROCEED")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSafetyConfirm = null }) { Text("CANCEL") }
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
fun SmsReviewItem(transaction: Expense, isSelected: Boolean, onToggle: () -> Unit, onClick: () -> Unit, onMarkReviewed: () -> Unit, onDiscard: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = isSelected, onCheckedChange = { onToggle() })
        Spacer(modifier = Modifier.width(16.dp))
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
