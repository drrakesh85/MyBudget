package com.hackerai.mybudget.data

import org.junit.Test
import org.junit.Assert.*
import java.text.SimpleDateFormat
import java.util.*

class ExpenseSummaryCalculatorTest {

    @Test
    fun currentBalance_calculatesCorrectly() {
        val expenses = listOf(
            createExpense("01-01-2024", 100.0),
            createExpense("02-01-2024", -50.0),
            createExpense("03-01-2024", 25.0)
        )
        val balance = ExpenseSummaryCalculator.currentBalance(expenses)
        assertEquals(75.0, balance, 0.01)
    }

    @Test
    fun filterByAccount_filtersCorrectly() {
        val expenses = listOf(
            createExpense("01-01-2024", 100.0, account = "HDFC"),
            createExpense("02-01-2024", -50.0, account = "SBI"),
            createExpense("03-01-2024", 25.0, account = "HDFC")
        )
        val filtered = ExpenseSummaryCalculator.filterByAccount(expenses, "HDFC")
        assertEquals(2, filtered.size)
        assertEquals("HDFC", filtered[0].account)
    }

    @Test
    fun calculateSummaries_returnsAllPeriods() {
        val expenses = listOf(
            createExpense("01-01-2024", 100.0),
            createExpense("02-01-2024", -50.0)
        )
        val summaries = ExpenseSummaryCalculator.calculateSummaries(expenses)
        assertTrue(summaries.containsKey("Today"))
        assertTrue(summaries.containsKey("This Week"))
        assertTrue(summaries.containsKey("This Month"))
        assertTrue(summaries.containsKey("Year to Date"))
    }

    private fun createExpense(
        date: String,
        amount: Double,
        category: String = "Test",
        subcategory: String = "",
        paymentMethod: String = "",
        description: String = "",
        refCheckNo: String = "",
        payeePayer: String = "",
        status: String = "",
        receiptPicture: String = "",
        account: String = "",
        tag: String = "",
        tax: String = "",
        quantity: Double = 1.0,
        unit: String = "PCS",
        splitTotal: String = "",
        rowId: String = UUID.randomUUID().toString(),
        typeId: String = "",
        transactionType: String = "Expense",
        toAccount: String? = null
    ): Expense {
        return Expense(
            date = date,
            amount = amount,
            category = category,
            subcategory = subcategory,
            paymentMethod = paymentMethod,
            description = description,
            refCheckNo = refCheckNo,
            payeePayer = payeePayer,
            status = status,
            receiptPicture = receiptPicture,
            account = account,
            tag = tag,
            tax = tax,
            quantity = quantity,
            unit = unit,
            splitTotal = splitTotal,
            rowId = rowId,
            typeId = typeId,
            transactionType = transactionType,
            toAccount = toAccount
        )
    }
}