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
import com.hackerai.mybudget.data.Expense
import com.hackerai.mybudget.ui.*
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*
import kotlin.math.abs

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CategorySummaryScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onBack: () -> Unit,
    onCategoryClick: (String, String) -> Unit = { _, _ -> }
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    
    var timeFilter by remember { mutableStateOf("Monthly") }
    var periodOffset by remember { mutableIntStateOf(0) }
    var summaryType by remember { mutableStateOf("Category") }
    var showMenu by remember { mutableStateOf(false) }

    val allExpenses = (uiState as? BudgetUiState.Success)?.expenses ?: emptyList()
    
    val currentRange = remember(timeFilter, periodOffset) {
        when (timeFilter) {
            "Weekly" -> getOffsetWeekRange(periodOffset)
            "Monthly" -> getOffsetMonthRange(periodOffset)
            "Yearly" -> getOffsetYearRange(periodOffset)
            else -> null to null
        }
    }

    val filteredExpenses = remember(allExpenses, selectedAccount, currentRange) {
        filterExpenses(allExpenses, selectedAccount, currentRange)
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                TopAppBar(
                    title = {
                        Box {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable { showMenu = true }
                            ) {
                                Text(summaryType, color = Color.White)
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                            }
                            DropdownMenu(
                                expanded = showMenu,
                                onDismissRequest = { showMenu = false },
                                modifier = Modifier.background(Color(0xFF333333)) // Dark background like image
                            ) {
                                val options = listOf(
                                    "Category",
                                    "Sub Category",
                                    "Payee",
                                    "Tag - Expense",
                                    "Categories without transfer",
                                    "Sub Categories without transfer",
                                    "Income",
                                    "Income without transfer",
                                    "Payer - Income",
                                    "Tag - Income"
                                )
                                options.forEach { option ->
                                    DropdownMenuItem(
                                        text = { Text(option, color = Color.White) },
                                        onClick = {
                                            summaryType = option
                                            showMenu = false
                                        }
                                    )
                                }
                            }
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) { Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White) }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )
                
                if (timeFilter != "All") {
                    PeriodNavigationBar(timeFilter, periodOffset) { periodOffset = it }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text(text = "All Data", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        bottomBar = {
            BottomSummaryBarFiltered(filteredExpenses)
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Account", color = Color.Gray, fontSize = 14.sp)
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = selectedAccount ?: "ALL ACCOUNTS",
                        color = Color.Red,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center
                    )
                    Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.LightGray)
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                    TimeFilterChip("All", timeFilter == "All") { 
                        timeFilter = "All" 
                        periodOffset = 0
                    }
                    TimeFilterChip("Weekly", timeFilter == "Weekly") { 
                        timeFilter = "Weekly" 
                        periodOffset = 0
                    }
                    TimeFilterChip("Monthly", timeFilter == "Monthly") { 
                        timeFilter = "Monthly" 
                        periodOffset = 0
                    }
                    TimeFilterChip("Yearly", timeFilter == "Yearly") { 
                        timeFilter = "Yearly" 
                        periodOffset = 0
                    }
                }
                
                Spacer(modifier = Modifier.height(8.dp))
                Text("Date Range", color = Color(0xFF00897B), fontSize = 14.sp, fontWeight = FontWeight.Bold)
            }

            HorizontalDivider(thickness = 0.5.dp)

            if (uiState is BudgetUiState.Loading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                val categoryData = calculateCategorySummaryFiltered(filteredExpenses, summaryType)
                if (categoryData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions for this period", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(categoryData) { data ->
                            CategorySummaryItem(data) {
                                onCategoryClick(data.name, summaryType)
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun filterExpenses(
    expenses: List<Expense>,
    account: String?,
    range: Pair<LocalDate?, LocalDate?>
): List<Expense> {
    return expenses.filter { exp ->
        val accountMatches = account == null || exp.account == account
        val dateMatches = if (range.first != null && range.second != null) {
            val expDate = try {
                LocalDate.parse(exp.date, DateTimeFormatter.ofPattern("dd-MM-yyyy"))
            } catch (e: Exception) {
                null
            }
            expDate != null && !expDate.isBefore(range.first) && !expDate.isAfter(range.second)
        } else true
        accountMatches && dateMatches
    }
}

@Composable
fun CategorySummaryItem(data: CategorySummaryData, onClick: () -> Unit) {
    val isIncome = data.isIncome
    Column(modifier = Modifier.clickable { onClick() }) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = data.color.copy(alpha = 0.8f)) {
                Box(contentAlignment = Alignment.Center) {
                    val initial = if (data.name.isNotBlank()) data.name.take(1).uppercase() else "?"
                    Text(text = initial, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = data.name.ifBlank { "Uncategorized" }, modifier = Modifier.weight(1f), fontSize = 16.sp, color = Color.DarkGray)
            Text(
                text = "${formatSummaryAmount(data.amount)} | ${String.format("%.2f", data.percentage)}%",
                color = if (isIncome) Color(0xFF2E7D32) else Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), thickness = 0.5.dp)
    }
}

data class CategorySummaryData(val name: String, val amount: Double, val percentage: Double, val color: Color, val isIncome: Boolean = false)

fun calculateCategorySummaryFiltered(expenses: List<Expense>, summaryType: String): List<CategorySummaryData> {
    val isIncomeMode = summaryType in listOf("Income", "Income without transfer", "Payer - Income", "Tag - Income")
    
    val baseList = if (isIncomeMode) {
        expenses.filter { it.amount > 0 }
    } else {
        expenses.filter { it.amount < 0 }
    }

    val filteredList = when (summaryType) {
        "Categories without transfer", "Sub Categories without transfer", "Income without transfer" -> {
            baseList.filter { it.transactionType != "Transfer" && it.category != "Transfer" }
        }
        else -> baseList
    }

    val totalAmount = filteredList.sumOf { abs(it.amount) }
    
    if (totalAmount == 0.0) return emptyList()
    
    val grouped = when (summaryType) {
        "Category", "Categories without transfer", "Income", "Income without transfer" -> filteredList.groupBy { it.category }
        "Sub Category", "Sub Categories without transfer" -> filteredList.groupBy { it.subcategory }
        "Payee", "Payer - Income" -> filteredList.groupBy { it.payeePayer }
        "Tag - Expense", "Tag - Income" -> filteredList.groupBy { it.tag }
        else -> filteredList.groupBy { it.category }
    }

    val colors = listOf(
        Color(0xFFE91E63), Color(0xFF9C27B0), Color(0xFF673AB7), 
        Color(0xFF3F51B5), Color(0xFF2196F3), Color(0xFF03A9F4),
        Color(0xFF00BCD4), Color(0xFF009688), Color(0xFF4CAF50),
        Color(0xFF8BC34A), Color(0xFFCDDC39), Color(0xFFFFEB3B),
        Color(0xFFFFC107), Color(0xFFFF9800), Color(0xFFFF5722)
    )
    
    var colorIndex = 0
    return grouped.map { (name, list) ->
        val groupAmount = list.sumOf { abs(it.amount) }
        CategorySummaryData(
            name = name,
            amount = groupAmount,
            percentage = (groupAmount / totalAmount) * 100,
            color = colors[(colorIndex++) % colors.size],
            isIncome = isIncomeMode
        )
    }.sortedByDescending { it.amount }
}
