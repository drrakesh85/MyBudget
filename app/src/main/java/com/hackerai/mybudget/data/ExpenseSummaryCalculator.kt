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
        return expenses.filter { it.account == account }
    }

    fun calculateSummaries(expenses: List<Expense>): Map<String, Triple<Double, Double, Double>> {
        val now = LocalDate.now(zoneId)
        return mapOf(
            "Today" to periodSummary(expenses, now.atStartOfDay(zoneId).toInstant().toEpochMilli(), now.atTime(23, 59, 59).atZone(zoneId).toInstant().toEpochMilli()),
            "This Week" to periodSummary(expenses, weekStart(now), weekEnd(now)),
            "This Month" to periodSummary(expenses, monthStart(now), monthEnd(now)),
            "Year to Date" to periodSummary(expenses, yearStart(now), yearEnd(now))
        )
    }

    fun currentBalance(expenses: List<Expense>): Double = expenses.sumOf { it.amount }

    private fun periodSummary(expenses: List<Expense>, start: Long, end: Long): Triple<Double, Double, Double> {
        val inRange = expenses.filter { expense ->
            val time = parseDate(expense.date) ?: return@filter false
            time in start..end
        }
        val income = inRange.filter { it.amount > 0 }.sumOf { it.amount }
        val expense = inRange.filter { it.amount < 0 }.sumOf { it.amount }
        return Triple(income, expense, income + expense)
    }

    private fun parseDate(dateStr: String): Long? {
        return try {
            LocalDate.parse(dateStr, dateFormatter).atStartOfDay(zoneId).toInstant().toEpochMilli()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to parse date: $dateStr", e)
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
