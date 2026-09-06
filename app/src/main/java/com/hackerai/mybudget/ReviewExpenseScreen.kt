package com.hackerai.mybudget

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.PopupProperties
import com.hackerai.mybudget.data.Expense

@Composable
fun ReviewExpenseScreen(
    expense: Expense,
    accounts: List<String>,
    payees: List<String>,
    categories: List<String>,
    subcategories: List<String>,
    tags: List<String> = emptyList(),
    tagMap: Map<String, Pair<String, String>> = emptyMap(),
    categorySubcategoryMap: Map<String, List<String>> = emptyMap(),
    onSave: (Expense) -> Unit,
    onCancel: () -> Unit
) {
    var description by remember { mutableStateOf(expense.description) }
    var amount by remember { 
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

    val transactionTypes = listOf("Expense", "Income", "Transfer")
    
    // Filter subcategories based on the selected category
    val relevantSubcategories = remember(category, categorySubcategoryMap, subcategories) {
        if (category.isBlank()) {
            subcategories
        } else {
            categorySubcategoryMap[category] ?: subcategories
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    "Review Transaction",
                    style = MaterialTheme.typography.headlineSmall
                )
                Text(
                    "Adjust details before saving",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 16.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Transaction Type Selector
            Column {
                Text("Transaction Type", style = MaterialTheme.typography.labelMedium)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    transactionTypes.forEach { type ->
                        FilterChip(
                            selected = transactionType == type,
                            onClick = { 
                                transactionType = type
                                // Auto-adjust amount sign if user changes type
                                val currentAmount = amount.toDoubleOrNull() ?: 0.0
                                if (type == "Expense" && currentAmount > 0) {
                                    amount = (-currentAmount).toString()
                                } else if (type == "Income" && currentAmount < 0) {
                                    amount = (-currentAmount).toString()
                                } else if (type == "Transfer") {
                                    // Transfers usually represent an outflow from 'account'
                                    if (currentAmount > 0) amount = (-currentAmount).toString()
                                }
                            },
                            label = { Text(type) }
                        )
                    }
                }
            }

            TextField(
                value = date,
                onValueChange = { date = it },
                label = { Text("Date") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = time,
                onValueChange = { time = it },
                label = { Text("Time") },
                modifier = Modifier.fillMaxWidth()
            )
            TextField(
                value = amount,
                onValueChange = { amount = it },
                label = { Text("Amount") },
                modifier = Modifier.fillMaxWidth()
            )

            if (transactionType != "Transfer") {
                AutocompleteField(
                    label = "Payee/Payer",
                    value = payeePayer,
                    onValueChange = { payeePayer = it },
                    suggestions = payees
                )

                TextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    modifier = Modifier.fillMaxWidth()
                )

                AutocompleteField(
                    label = "Tag",
                    value = tag,
                    onValueChange = { newTag -> 
                        tag = newTag
                        if (newTag.isNotBlank()) {
                            tagMap[newTag]?.let { (cat, sub) ->
                                if (category.isBlank()) category = cat
                                if (subcategory.isBlank()) subcategory = sub
                            }
                        }
                    },
                    suggestions = tags
                )

                AutocompleteField(
                    label = "Category",
                    value = category,
                    onValueChange = { category = it },
                    suggestions = categories
                )

                AutocompleteField(
                    label = "Subcategory",
                    value = subcategory,
                    onValueChange = { subcategory = it },
                    suggestions = relevantSubcategories
                )

                AutocompleteField(
                    label = "Account",
                    value = fromAccount,
                    onValueChange = { fromAccount = it },
                    suggestions = accounts
                )
            } else {
                // Transfer specific fields
                AutocompleteField(
                    label = "From Account",
                    value = fromAccount,
                    onValueChange = { fromAccount = it },
                    suggestions = accounts
                )
                
                AutocompleteField(
                    label = "To Account",
                    value = toAccount,
                    onValueChange = { toAccount = it },
                    suggestions = accounts
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Cancel")
                }
                Button(
                    onClick = {
                        onSave(
                            expense.copy(
                                description = description,
                                amount = amount.toDoubleOrNull() ?: expense.amount,
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
                        )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Save")
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutocompleteField(
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
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            trailingIcon = {
                if (value.isNotEmpty() && !suggestions.contains(value)) {
                    IconButton(onClick = { 
                        expanded = false 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "New item detected")
                    }
                }
            }
        )

        if (expanded && filteredSuggestions.isNotEmpty()) {
            DropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false },
                modifier = Modifier.fillMaxWidth(0.9f),
                properties = PopupProperties(focusable = false)
            ) {
                filteredSuggestions.forEach { suggestion ->
                    DropdownMenuItem(
                        text = { Text(suggestion) },
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
