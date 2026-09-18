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
        if (account == null) {
            // Net worth view: only consider real Income and Expenses
            return expenses.sumOf { exp ->
                val absVal = kotlin.math.abs(exp.amount)
                when (exp.transactionType) {
                    "Income" -> absVal
                    "Expense" -> -absVal
                    else -> 0.0 // Transfers are neutral across all accounts
                }
            }
        }
        return expenses.sumOf { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            when {
                exp.transactionType == "Transfer" -> {
                    when {
                        exp.toAccount == account -> absVal // Inflow
                        exp.account == account -> -absVal // Outflow
                        else -> 0.0
                    }
                }
                exp.transactionType == "Income" -> absVal
                exp.transactionType == "Expense" -> -absVal
                else -> 0.0
            }
        }
    }

    private fun periodSummary(expenses: List<Expense>, start: Long, end: Long, account: String?): Triple<Double, Double, Double> {
        val inRange = expenses.filter { expense ->
            val time = parseDate(expense.date) ?: return@filter false
            time in start..end
        }
        
        var income = 0.0
        var expense = 0.0
        
        inRange.forEach { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            if (account == null) {
                when (exp.transactionType) {
                    "Income" -> income += absVal
                    "Expense" -> expense -= absVal
                }
            } else {
                when {
                    exp.transactionType == "Transfer" -> {
                        if (exp.toAccount == account) income += absVal
                        else if (exp.account == account) expense -= absVal
                    }
                    exp.transactionType == "Income" -> income += absVal
                    exp.transactionType == "Expense" -> expense -= absVal
                }
            }
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
