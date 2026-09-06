package com.hackerai.mybudget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerai.mybudget.data.Expense
import java.text.NumberFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.*

@Composable
fun PeriodNavigationBar(timeFilter: String, currentOffset: Int, onOffsetChange: (Int) -> Unit) {
    val formatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onOffsetChange(currentOffset - 1) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = Color.White)
        }
        
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            val period = when (timeFilter) {
                "Weekly" -> getOffsetWeekRange(currentOffset)
                "Monthly" -> getOffsetMonthRange(currentOffset)
                "Yearly" -> getOffsetYearRange(currentOffset)
                else -> null to null
            }
            val text = if (period.first != null && period.second != null) {
                "${period.first!!.format(formatter)} - ${period.second!!.format(formatter)}"
            } else ""
            
            Text(text = text, color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
        }

        IconButton(onClick = { onOffsetChange(currentOffset + 1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White)
        }
    }
}

@Composable
fun TimeFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier.padding(horizontal = 4.dp).clickable { onClick() },
        color = if (isSelected) Color(0xFF00897B) else Color.Transparent,
        shape = MaterialTheme.shapes.small,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00897B))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            color = if (isSelected) Color.White else Color(0xFF00897B),
            fontSize = 12.sp
        )
    }
}

@Composable
fun BottomSummaryBarFiltered(filtered: List<Expense>) {
    val income = filtered.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = filtered.filter { it.amount < 0 }.sumOf { it.amount }
    val balance = income + expense
    
    Surface(modifier = Modifier.fillMaxWidth(), color = Color(0xFFE0E0E0), shadowElevation = 8.dp) {
        Row(modifier = Modifier.padding(8.dp).fillMaxWidth(), horizontalArrangement = Arrangement.SpaceAround) {
            SummaryField("Income", income, Color(0xFF2E7D32))
            SummaryField("Expense", expense, Color.Red)
            SummaryField("Balance (-/+)", balance, if (balance >= 0) Color(0xFF2E7D32) else Color.Red)
        }
    }
}

@Composable
fun SummaryField(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 12.sp, color = Color.Gray)
        Text(formatSummaryAmount(amount), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = color)
    }
}

fun formatSummaryAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}

fun getOffsetWeekRange(offset: Int): Pair<LocalDate, LocalDate> {
    val base = LocalDate.now().plusWeeks(offset.toLong())
    val start = base.minusDays((base.dayOfWeek.value - 1).toLong())
    val end = base.plusDays((7 - base.dayOfWeek.value).toLong())
    return start to end
}

fun getOffsetMonthRange(offset: Int): Pair<LocalDate, LocalDate> {
    val base = LocalDate.now().plusMonths(offset.toLong())
    val start = base.withDayOfMonth(1)
    val end = base.withDayOfMonth(base.lengthOfMonth())
    return start to end
}

fun getOffsetYearRange(offset: Int): Pair<LocalDate, LocalDate> {
    val base = LocalDate.now().plusYears(offset.toLong())
    val start = base.withDayOfYear(1)
    val end = base.withDayOfYear(base.lengthOfYear())
    return start to end
}
