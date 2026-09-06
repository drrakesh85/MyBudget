package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
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
import java.text.NumberFormat
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(
    viewModel: ExpenseViewModel = viewModel(),
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsState()
    val selectedAccount by viewModel.selectedAccount.collectAsState()
    
    var currentMonth by remember { mutableStateOf(LocalDate.now(ZoneId.systemDefault())) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }

    val expenses = (uiState as? BudgetUiState.Success)?.expenses?.filter {
        selectedAccount == null || it.account == selectedAccount
    } ?: emptyList()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Calendar", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(modifier = Modifier.padding(padding).fillMaxSize().background(Color.White)) {
            // Month Selector
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    currentMonth = currentMonth.minusMonths(1)
                }) {
                    Icon(Icons.Default.ChevronLeft, contentDescription = "Previous Month")
                }
                
                Text(
                    text = currentMonth.getMonth().getDisplayName(TextStyle.FULL, Locale.getDefault()) + " " + currentMonth.year,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                
                IconButton(onClick = {
                    currentMonth = currentMonth.plusMonths(1)
                }) {
                    Icon(Icons.Default.ChevronRight, contentDescription = "Next Month")
                }
            }

            // Calendar Grid
            CalendarGrid(currentMonth, expenses, selectedDate) { date ->
                selectedDate = date
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // Daily Transactions List
            if (selectedDate != null) {
                val dateStr = selectedDate!!.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault()))
                val dailyExpenses = expenses.filter { it.date == dateStr }
                
                Text(
                    text = "Transactions on $dateStr",
                    modifier = Modifier.padding(16.dp),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                
                if (dailyExpenses.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("No transactions for this day")
                    }
                } else {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        items(dailyExpenses) { expense ->
                            ExpenseItem(expense) {
                                viewModel.editExpense(expense)
                            }
                        }
                    }
                }
            } else {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("Select a date to see transactions")
                }
            }
        }
    }
}

@Composable
fun CalendarGrid(
    currentMonth: LocalDate,
    expenses: List<Expense>,
    selectedDate: LocalDate?,
    onDateSelected: (LocalDate) -> Unit
) {
    val firstOfMonth = currentMonth.withDayOfMonth(1)
    val daysInMonth = currentMonth.lengthOfMonth()
    val firstDayOfWeek = firstOfMonth.dayOfWeek.value // 1=Monday, 7=Sunday
    // Adjust to Sunday=1 for calendar display
    val firstDayOfWeekAdjusted = if (firstDayOfWeek == 7) 1 else firstDayOfWeek + 1
    
    val weekDays = listOf("Sun", "Mon", "Tue", "Wed", "Thu", "Fri", "Sat")
    
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        // Week Header
        Row(modifier = Modifier.fillMaxWidth()) {
            weekDays.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
        
        // Days Grid
        var dayOfMonth = 1
        for (row in 0..5) {
            Row(modifier = Modifier.fillMaxWidth()) {
                for (col in 1..7) {
                    val currentDayIndex = row * 7 + col
                    if (currentDayIndex < firstDayOfWeekAdjusted || dayOfMonth > daysInMonth) {
                        Spacer(modifier = Modifier.weight(1f))
                    } else {
                        val date = currentMonth.withDayOfMonth(dayOfMonth)
                        val dateStr = date.format(DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault()))
                        val dayExpenses = expenses.filter { it.date == dateStr }
                        val dayTotal = dayExpenses.sumOf { it.amount }
                        
                        val isSelected = selectedDate?.let {
                            it.year == date.year && it.dayOfYear == date.dayOfYear
                        } ?: false

                        CalendarDay(
                            day = dayOfMonth,
                            total = dayTotal,
                            isSelected = isSelected,
                            modifier = Modifier.weight(1f)
                        ) {
                            onDateSelected(date)
                        }
                        dayOfMonth++
                    }
                }
            }
            if (dayOfMonth > daysInMonth) break
        }
    }
}

@Composable
fun CalendarDay(
    day: Int,
    total: Double,
    isSelected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit
) {
    Column(
        modifier = modifier
            .padding(2.dp)
            .background(if (isSelected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent)
            .clickable { onClick() }
            .padding(4.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(text = day.toString(), fontSize = 14.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
        if (total != 0.0) {
            Text(
                text = formatCalendarAmount(total),
                fontSize = 8.sp,
                color = if (total < 0) Color.Red else Color(0xFF2E7D32),
                maxLines = 1
            )
        } else {
            Spacer(modifier = Modifier.height(10.dp))
        }
    }
}

private fun formatCalendarAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}
