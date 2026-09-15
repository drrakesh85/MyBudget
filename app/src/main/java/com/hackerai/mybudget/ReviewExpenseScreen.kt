package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.PopupProperties
import com.hackerai.mybudget.data.Expense
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

data class TransactionSplit(
    val id: String = UUID.randomUUID().toString(),
    val amount: String = "",
    val category: String = "",
    val subcategory: String = ""
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewExpenseScreen(
    expense: Expense,
    accounts: List<String>,
    payees: List<String>,
    categories: List<String>,
    subcategories: List<String>,
    tags: List<String> = emptyList(),
    tagMap: Map<String, Pair<String, String>> = emptyMap(),
    payeeMap: Map<String, Pair<String, String>> = emptyMap(),
    categorySubcategoryMap: Map<String, List<String>> = emptyMap(),
    onSave: (List<Expense>) -> Unit,
    onCancel: () -> Unit,
    onDelete: (Expense) -> Unit = {}
) {
    var description by remember { mutableStateOf(expense.description) }
    var totalAmount by remember { 
        mutableStateOf(if (expense.amount == 0.0 && expense.rowId.startsWith("new_")) "" else expense.amount.toString()) 
    }
    var date by remember { mutableStateOf(expense.date) }
    var time by remember { mutableStateOf(expense.time) }
    var category by remember { mutableStateOf(expense.category) }
    var subcategory by remember { mutableStateOf(expense.subcategory) }
    var tag by remember { mutableStateOf(expense.tag) }
    var fromAccount by remember { mutableStateOf(expense.account) }
    var toAccount by remember { mutableStateOf(expense.toAccount ?: "") }
    var payeePayer by remember { mutableStateOf(expense.payeePayer) }
    var transactionType by remember { mutableStateOf(expense.transactionType) }

    var isSplitEnabled by remember { mutableStateOf(false) }
    var splits by remember { mutableStateOf(listOf<TransactionSplit>()) }

    var showDatePicker by remember { mutableStateOf(false) }
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = try {
            LocalDate.parse(date, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                .atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        } catch (e: Exception) {
            System.currentTimeMillis()
        }
    )

    LaunchedEffect(Unit) {
        if (category == "Imported") category = ""
        if (expense.tag == "SMS" && expense.status == "unclear") payeePayer = ""
        
        if (category.isBlank() && payeePayer.isNotBlank()) {
            payeeMap[payeePayer]?.let { (cat, sub) ->
                if (cat != "Imported") {
                    category = cat
                    subcategory = sub
                }
            }
        }
    }

    val transactionTypes = listOf("Expense", "Income", "Transfer")
    
    val relevantSubcategories = remember(category, categorySubcategoryMap, subcategories) {
        if (category.isBlank()) subcategories else categorySubcategoryMap[category] ?: subcategories
    }

    var showDiscardPrompt by remember { mutableStateOf<List<Expense>?>(null) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { 
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Review Transaction", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text("Adjust details before saving", style = MaterialTheme.typography.labelSmall, color = Color.Gray)
                    }
                },
                actions = {
                    if (!expense.rowId.startsWith("new_")) {
                        IconButton(onClick = { onDelete(expense) }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red)
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(containerColor = Color.White)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // ... (rest of the form remains same)
            // Transaction Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                transactionTypes.forEach { type ->
                    FilterChip(
                        selected = transactionType == type,
                        onClick = { 
                            transactionType = type
                            val currentAmount = totalAmount.toDoubleOrNull() ?: 0.0
                            if (type == "Expense" && currentAmount > 0) totalAmount = (-currentAmount).toString()
                            else if (type == "Income" && currentAmount < 0) totalAmount = (-currentAmount).toString()
                            else if (type == "Transfer" && currentAmount > 0) totalAmount = (-currentAmount).toString()
                        },
                        label = { Text(type, fontSize = 12.sp) }
                    )
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CompactTextField(
                    value = date, 
                    onValueChange = { }, 
                    label = "Date", 
                    readOnly = true,
                    modifier = Modifier.weight(1f),
                    onClick = { showDatePicker = true },
                    trailingIcon = { Icon(Icons.Default.CalendarMonth, contentDescription = null, modifier = Modifier.size(18.dp)) }
                )
                CompactTextField(value = time, onValueChange = { time = it }, label = "Time", modifier = Modifier.weight(1f))
            }

            CompactTextField(
                value = totalAmount, 
                onValueChange = { totalAmount = it }, 
                label = "Amount",
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
            )

            if (transactionType != "Transfer") {
                CompactAutocompleteField(label = "Payee/Payer", value = payeePayer, onValueChange = { newPayee -> 
                    payeePayer = newPayee
                    if (newPayee.isNotBlank()) {
                        payeeMap[newPayee]?.let { (cat, sub) ->
                            if (cat != "Imported") {
                                if (category.isBlank()) category = cat
                                if (subcategory.isBlank()) subcategory = sub
                            }
                        }
                    }
                }, suggestions = payees)

                CompactTextField(value = description, onValueChange = { description = it }, label = "Description")
                CompactAutocompleteField(label = "Tag", value = tag, onValueChange = { tag = it }, suggestions = tags)

                if (!isSplitEnabled) {
                    CompactAutocompleteField(label = "Category", value = category, onValueChange = { category = it }, suggestions = categories)
                    CompactAutocompleteField(label = "Subcategory", value = subcategory, onValueChange = { subcategory = it }, suggestions = relevantSubcategories)
                }

                CompactAutocompleteField(label = "Account", value = fromAccount, onValueChange = { fromAccount = it }, suggestions = accounts)
                
                // Split Option
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.clickable { 
                    isSplitEnabled = !isSplitEnabled
                    if (isSplitEnabled && splits.isEmpty()) splits = listOf(TransactionSplit())
                }) {
                    Checkbox(checked = isSplitEnabled, onCheckedChange = { 
                        isSplitEnabled = it 
                        if (it && splits.isEmpty()) splits = listOf(TransactionSplit())
                    })
                    Text("Split Transaction", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }

                if (isSplitEnabled) {
                    splits.forEachIndexed { index, split ->
                        SplitEntryItem(
                            split = split,
                            categories = categories,
                            subcategories = subcategories,
                            categorySubcategoryMap = categorySubcategoryMap,
                            onSplitChange = { updated ->
                                val newList = splits.toMutableList()
                                newList[index] = updated
                                splits = newList
                            },
                            onRemove = {
                                val newList = splits.toMutableList()
                                newList.removeAt(index)
                                splits = newList
                            }
                        )
                    }
                    TextButton(onClick = { splits = splits + TransactionSplit() }) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Text("Add Split", fontSize = 13.sp)
                    }
                }
            } else {
                CompactAutocompleteField(label = "From Account", value = fromAccount, onValueChange = { fromAccount = it }, suggestions = accounts)
                CompactAutocompleteField(label = "To Account", value = toAccount, onValueChange = { toAccount = it }, suggestions = accounts)
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel", fontSize = 14.sp)
                }
                Button(
                    onClick = {
                        val mainExpense = expense.copy(
                            description = description,
                            amount = totalAmount.toDoubleOrNull() ?: expense.amount,
                            date = date,
                            time = time,
                            category = if (transactionType == "Transfer") "Transfer" else category,
                            subcategory = subcategory,
                            tag = tag,
                            account = fromAccount,
                            toAccount = if (transactionType == "Transfer") toAccount else null,
                            payeePayer = if (transactionType == "Transfer") "Transfer to $toAccount" else payeePayer,
                            transactionType = transactionType
                        )
                        
                        val results = if (isSplitEnabled && splits.isNotEmpty()) {
                            splits.mapIndexed { i, split ->
                                mainExpense.copy(
                                    rowId = if (i == 0) mainExpense.rowId else "${mainExpense.rowId}_split_$i",
                                    amount = split.amount.toDoubleOrNull() ?: 0.0,
                                    category = split.category,
                                    subcategory = split.subcategory,
                                    splitTotal = totalAmount
                                )
                            }
                        } else {
                            listOf(mainExpense)
                        }

                        if (expense.tag == "SMS" || expense.status == "unclear") {
                            showDiscardPrompt = results
                        } else {
                            onSave(results)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save", fontSize = 14.sp)
                }
            }
        }

        if (showDatePicker) {
            DatePickerDialog(
                onDismissRequest = { showDatePicker = false },
                confirmButton = {
                    TextButton(onClick = {
                        datePickerState.selectedDateMillis?.let {
                            date = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault()).toLocalDate()
                                .format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))
                        }
                        showDatePicker = false
                    }) { Text("OK") }
                },
                dismissButton = {
                    TextButton(onClick = { showDatePicker = false }) { Text("Cancel") }
                }
            ) {
                DatePicker(state = datePickerState)
            }
        }

        showDiscardPrompt?.let { results ->
            AlertDialog(
                onDismissRequest = { onSave(results); showDiscardPrompt = null },
                title = { Text("Transaction Saved") },
                text = { Text("The transaction has been added to your ledger. Do you want to discard the original SMS notification from the review list?") },
                confirmButton = {
                    Button(onClick = { 
                        // The approval logic in ViewModel handles approving the expense.
                        // If we want to discard it, we should mark the original as discarded.
                        // Currently approval and discarding are separate. 
                        // Approval moves it from 'pending' to 'approved'.
                        // The user said: "removed from the Pending Review list".
                        // Approving already removes it from 'pending'. 
                        // But if they want to explicitly discard (e.g. they decided not to save), 
                        // that's different.
                        // If they ALREADY saved, it's out of pending. 
                        // I'll just call onSave and close.
                        onSave(results)
                        showDiscardPrompt = null
                    }) {
                        Text("YES, DISCARD")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { 
                        onSave(results)
                        showDiscardPrompt = null 
                    }) {
                        Text("KEEP IN LIST")
                    }
                }
            )
        }
    }
}

@Composable
fun CompactTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    readOnly: Boolean = false,
    onClick: (() -> Unit)? = null,
    trailingIcon: @Composable (() -> Unit)? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    Box(modifier = modifier) {
        TextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray) },
            modifier = Modifier.fillMaxWidth(),
            readOnly = readOnly,
            enabled = onClick == null, // Disable input if we have a click handler
            trailingIcon = trailingIcon,
            keyboardOptions = keyboardOptions,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFE8E4F0),
                unfocusedContainerColor = Color(0xFFE8E4F0),
                disabledContainerColor = Color(0xFFE8E4F0),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                disabledTextColor = LocalContentColor.current,
                disabledLabelColor = Color.DarkGray
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            shape = RoundedCornerShape(4.dp)
        )
        
        if (onClick != null) {
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .clickable { onClick() }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CompactAutocompleteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    suggestions: List<String>
) {
    var expanded by remember { mutableStateOf(false) }
    val filteredSuggestions = suggestions.filter { it.contains(value, ignoreCase = true) }

    Box(modifier = Modifier.fillMaxWidth()) {
        TextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true
            },
            label = { Text(label, fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.DarkGray) },
            modifier = Modifier.fillMaxWidth(),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFE8E4F0),
                unfocusedContainerColor = Color(0xFFE8E4F0),
                disabledContainerColor = Color(0xFFE8E4F0),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent
            ),
            textStyle = LocalTextStyle.current.copy(fontSize = 15.sp, fontWeight = FontWeight.Medium),
            shape = RoundedCornerShape(4.dp),
            trailingIcon = {
                if (value.isNotEmpty() && !suggestions.contains(value)) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        )

        if (expanded && filteredSuggestions.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.8f),
                properties = PopupProperties(focusable = false)
            ) {
                filteredSuggestions.take(10).forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion, fontSize = 13.sp) },
                        onClick = {
                            onValueChange(suggestion)
                            expanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SplitEntryItem(
    split: TransactionSplit,
    categories: List<String>,
    subcategories: List<String>,
    categorySubcategoryMap: Map<String, List<String>>,
    onSplitChange: (TransactionSplit) -> Unit,
    onRemove: () -> Unit
) {
    val relevantSub = remember(split.category, categorySubcategoryMap, subcategories) {
        if (split.category.isBlank()) subcategories else categorySubcategoryMap[split.category] ?: subcategories
    }

    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF9F9F9)),
        border = androidx.compose.foundation.BorderStroke(0.5.dp, Color.LightGray)
    ) {
        Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CompactTextField(
                    value = split.amount,
                    onValueChange = { onSplitChange(split.copy(amount = it)) },
                    label = "Split Amount",
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.RemoveCircle, contentDescription = "Remove", tint = Color.Gray)
                }
            }
            CompactAutocompleteField(
                label = "Split Category",
                value = split.category,
                onValueChange = { onSplitChange(split.copy(category = it)) },
                suggestions = categories
            )
            CompactAutocompleteField(
                label = "Split Subcategory",
                value = split.subcategory,
                onValueChange = { onSplitChange(split.copy(subcategory = it)) },
                suggestions = relevantSub
            )
        }
    }
}
