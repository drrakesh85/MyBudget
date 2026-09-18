package com.hackerai.mybudget.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
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
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = { onOffsetChange(currentOffset - 1) }) {
            Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = Color.White.copy(alpha = 0.9f))
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
            } else "All Records"
            
            Surface(
                color = Color.White.copy(alpha = 0.15f),
                shape = CircleShape
            ) {
                Text(
                    text = text,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        IconButton(onClick = { onOffsetChange(currentOffset + 1) }) {
            Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = Color.White.copy(alpha = 0.9f))
        }
    }
}

@Composable
fun TimeFilterChip(label: String, isSelected: Boolean, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .clickable { onClick() },
        color = if (isSelected) Color(0xFF00796B) else Color.Transparent,
        shape = CircleShape,
        border = if (isSelected) null else androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF00796B).copy(alpha = 0.3f))
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
            color = if (isSelected) Color.White else Color(0xFF00796B),
            fontSize = 12.sp,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}

@Composable
fun BottomSummaryBarFiltered(filtered: List<Expense>) {
    val income = filtered.filter { it.amount > 0 }.sumOf { it.amount }
    val expense = filtered.filter { it.amount < 0 }.sumOf { it.amount }
    val balance = income + expense
    
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = Color.White,
        shadowElevation = 16.dp,
        tonalElevation = 4.dp
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 12.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceAround
        ) {
            SummaryField("INCOME", income, Color(0xFF2E7D32))
            SummaryField("EXPENSE", expense, Color(0xFFC62828))
            val balanceColor = when {
                balance > 0 -> Color(0xFF2E7D32)
                balance < 0 -> Color(0xFFC62828)
                else -> Color.Gray
            }
            SummaryField("BALANCE", balance, balanceColor)
        }
    }
}

@Composable
fun SummaryField(label: String, amount: Double, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = label,
            fontSize = 10.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.5.sp
        )
        Text(
            text = formatSummaryAmount(amount),
            fontSize = 15.sp,
            fontWeight = FontWeight.ExtraBold,
            color = color
        )
    }
}

fun formatSummaryAmount(amount: Double): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    return formatter.format(amount).replace("₹", "").trim()
}

/**
 * Formats a transaction amount with semantic signs: + for Credit/Income, − for Debit/Expense.
 * Prevents double signs by taking the absolute value before prefixing.
 */
fun formatTransactionAmount(amount: Double, type: String, isIncomingTransfer: Boolean = false): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
    val absAmount = kotlin.math.abs(amount)
    val formattedValue = formatter.format(absAmount).replace("₹", "").trim()
    
    return when {
        type == "Income" || isIncomingTransfer -> "+$formattedValue"
        type == "Expense" || type == "Transfer" -> "−$formattedValue"
        amount >= 0 -> "+$formattedValue"
        else -> "−$formattedValue"
    }
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

data class SearchSuggestion(val text: String, val type: String)

@Composable
fun SmartSearchOverlay(
    query: String,
    expenses: List<Expense>,
    onSuggestionClick: (String) -> Unit
) {
    if (query.length < 2) return

    val suggestions = remember(query, expenses) {
        val list = mutableListOf<SearchSuggestion>()
        
        // Payee/Payer
        expenses.asSequence()
            .map { it.payeePayer }
            .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(5)
            .forEach { list.add(SearchSuggestion(it, "Payee/Payer")) }
            
        // Category
        expenses.asSequence()
            .map { it.category }
            .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(3)
            .forEach { list.add(SearchSuggestion(it, "Category")) }
            
        // Sub Category
        expenses.asSequence()
            .map { it.subcategory }
            .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(5)
            .forEach { list.add(SearchSuggestion(it, "Sub Category")) }
            
        // Tag
        expenses.asSequence()
            .map { it.tag }
            .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(3)
            .forEach { list.add(SearchSuggestion(it, "Tag")) }
            
        // Description
        expenses.asSequence()
            .map { it.description }
            .filter { it.isNotBlank() && it.contains(query, ignoreCase = true) }
            .distinct()
            .take(8)
            .forEach { list.add(SearchSuggestion(it, "Description")) }
            
        list.distinctBy { it.text.lowercase() + it.type }
    }

    if (suggestions.isEmpty()) return

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .heightIn(max = 400.dp),
        color = Color(0xFF333333),
        shape = MaterialTheme.shapes.small,
        shadowElevation = 8.dp
    ) {
        androidx.compose.foundation.lazy.LazyColumn {
            items(suggestions) { suggestion ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onSuggestionClick(suggestion.text) }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = suggestion.text, 
                        color = Color.White, 
                        fontSize = 15.sp, 
                        modifier = Modifier.weight(1f),
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = suggestion.type, 
                        color = Color.LightGray, 
                        fontSize = 11.sp
                    )
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color.Gray.copy(alpha = 0.3f))
            }
        }
    }
}
