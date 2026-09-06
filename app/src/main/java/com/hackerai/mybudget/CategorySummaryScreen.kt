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
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    
    var timeFilter by remember { mutableStateOf("Monthly") }
    var periodOffset by remember { mutableIntStateOf(0) }

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
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Category", color = Color.White)
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.White)
                        }
                    },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    actions = {
                        IconButton(onClick = { }) { Icon(Icons.Default.Email, contentDescription = "Email", tint = Color.White) }
                        IconButton(onClick = { }) { Icon(Icons.Default.List, contentDescription = "List", tint = Color.White) }
                        IconButton(onClick = { }) { Icon(Icons.Default.Dashboard, contentDescription = "Grid", tint = Color.White) }
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
                val categoryData = calculateCategorySummaryFiltered(filteredExpenses)
                if (categoryData.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions for this period", color = Color.Gray)
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(categoryData) { data ->
                            CategorySummaryItem(data)
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
fun CategorySummaryItem(data: CategorySummaryData) {
    Column {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Surface(modifier = Modifier.size(40.dp), shape = CircleShape, color = data.color.copy(alpha = 0.8f)) {
                Box(contentAlignment = Alignment.Center) {
                    Text(text = data.name.take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(text = data.name, modifier = Modifier.weight(1f), fontSize = 16.sp, color = Color.DarkGray)
            Text(
                text = "${formatSummaryAmount(data.amount)} | ${String.format("%.2f", data.percentage)}%",
                color = Color.Red,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
        }
        HorizontalDivider(modifier = Modifier.padding(start = 72.dp), thickness = 0.5.dp)
    }
}

data class CategorySummaryData(val name: String, val amount: Double, val percentage: Double, val color: Color)

fun calculateCategorySummaryFiltered(expenses: List<Expense>): List<CategorySummaryData> {
    val expenseList = expenses.filter { it.amount < 0 }
    val totalExpense = expenseList.sumOf { abs(it.amount) }
    
    if (totalExpense == 0.0) return emptyList()
    
    val grouped = expenseList.groupBy { it.category }
    val colors = listOf(Color.Green, Color.Blue, Color.Yellow, Color.Red, Color.Magenta, Color.Cyan, Color.DarkGray, Color.Gray, Color.Black)
    
    var colorIndex = 0
    return grouped.map { (category, list) ->
        val categoryAmount = list.sumOf { abs(it.amount) }
        CategorySummaryData(
            name = category,
            amount = categoryAmount,
            percentage = (categoryAmount / totalExpense) * 100,
            color = colors[(colorIndex++) % colors.size]
        )
    }.sortedByDescending { it.amount }
}
