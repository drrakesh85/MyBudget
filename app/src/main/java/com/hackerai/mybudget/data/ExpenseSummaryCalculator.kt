package com.hackerai.mybudget.data

import android.util.Log
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

object ExpenseSummaryCalculator {

    private const val TAG = "ExpenseSummaryCalculator"
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    fun filterByAccount(expenses: List<Expense>, account: String?): List<Expense> {
        if (account == null) return expenses
        return expenses.filter { it.account == account || (it.transactionType == "Transfer" && it.toAccount == account) }
    }

    fun calculateSummaries(expenses: List<Expense>, account: String? = null): Map<String, Triple<Double, Double, Double>> {
        val now = LocalDate.now(zoneId)
        return mapOf(
            "Today" to periodSummary(expenses, now.atStartOfDay(zoneId).toInstant().toEpochMilli(), now.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli(), account),
            "This Week" to periodSummary(expenses, weekStart(now), weekEnd(now), account),
            "This Month" to periodSummary(expenses, monthStart(now), monthEnd(now), account),
            "Year to Date" to periodSummary(expenses, yearStart(now), yearEnd(now), account)
        )
    }

    fun currentBalance(expenses: List<Expense>, account: String? = null): Double {
        if (account == null) return expenses.sumOf { it.amount }
        return expenses.sumOf { exp ->
            when {
                // Incoming transfer
                exp.transactionType == "Transfer" && exp.toAccount == account -> kotlin.math.abs(exp.amount)
                // Outgoing transaction from this account (Transfer or Expense)
                exp.account == account -> exp.amount
                else -> 0.0
            }
        }
    }

    private fun periodSummary(expenses: List<Expense>, start: Long, end: Long, account: String?): Triple<Double, Double, Double> {
        val inRange = expenses.filter { expense ->
            val time = parseDate(expense.date) ?: return@filter false
            time in start..end
        }
        val income = inRange.sumOf { exp ->
            if (exp.amount > 0) exp.amount 
            else if (account != null && exp.transactionType == "Transfer" && exp.toAccount == account) kotlin.math.abs(exp.amount)
            else 0.0
        }
        val expense = inRange.sumOf { exp ->
            if (exp.amount < 0 && (account == null || exp.account == account)) exp.amount else 0.0
        }
        return Triple(income, expense, income + expense)
    }

    private fun parseDate(dateStr: String): Long? {
        if (dateStr.equals("Date", ignoreCase = true)) return null
        return try {
            LocalDate.parse(dateStr, dateFormatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } catch (e: Exception) {
            // Only log if it's not the header
            if (!dateStr.equals("Date", ignoreCase = true)) {
                Log.w(TAG, "Failed to parse date: $dateStr")
            }
            null
        }
    }

    private fun weekStart(date: LocalDate): Long {
        val start = date.minusDays((date.dayOfWeek.value - 1).toLong())
        return start.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun weekEnd(date: LocalDate): Long {
        val end = date.plusDays((7 - date.dayOfWeek.value).toLong())
        return end.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun monthStart(date: LocalDate): Long {
        val start = date.withDayOfMonth(1)
        return start.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun monthEnd(date: LocalDate): Long {
        val end = date.withDayOfMonth(date.lengthOfMonth())
        return end.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
    }

    private fun yearStart(date: LocalDate): Long {
        val start = date.withDayOfYear(1)
        return start.atStartOfDay(zoneId).toInstant().toEpochMilli()
    }

    private fun yearEnd(date: LocalDate): Long {
        val end = date.withDayOfYear(date.lengthOfYear())
        return end.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()
    }
}
