package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.nio.charset.StandardCharsets

class GlobalDateOrderingTest {

    @Test
    fun test1_toExpense_derivesDateMillisWhenZero() {
        val entity = ExpenseEntity(
            rowId = "1",
            date = "14-03-2025",
            time = "10:00",
            amount = -100.0,
            category = "Test",
            subcategory = "",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "",
            status = "",
            receiptPicture = "",
            account = "My Cash",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            typeId = "",
            transactionType = "Expense",
            toAccount = null
        )
        val expense = entity.toExpense()
        assertTrue("dateMillis must be > 0L", expense.dateMillis > 0L)
        assertEquals("14-03-2025", expense.date)
    }

    @Test
    fun test2_invalidDate_doesNotCrash() {
        val entity = ExpenseEntity(
            rowId = "1",
            date = "invalid-date-string",
            time = "10:00",
            amount = -100.0,
            category = "Test",
            subcategory = "",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "",
            status = "",
            receiptPicture = "",
            account = "My Cash",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            typeId = "",
            transactionType = "Expense",
            toAccount = null
        )
        val expense = entity.toExpense()
        assertEquals(0L, expense.dateMillis)
        assertEquals("invalid-date-string", expense.date)
    }

    @Test
    fun test3_csvImportedExpense_hasValidDateMillis() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id
14-03-2025,-100.0,Food,,,Lunch,,,,My Cash,,,1,PCS,,1,1
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        assertEquals(1, result.size)
        assertTrue("CSV imported Expense dateMillis must be > 0L", result[0].dateMillis > 0L)
        assertEquals("14-03-2025", result[0].date)
    }

    @Test
    fun test4_manuallyCreatedExpense_hasValidDateMillis() {
        val empty = Expense.createEmpty()
        assertTrue("createEmpty dateMillis must be > 0L", empty.dateMillis > 0L)
        assertTrue(empty.date.isNotBlank())
    }

    @Test
    fun test5_multiYearAndSameDateSorting() {
        val exp2020 = Expense.createEmpty().copy(rowId = "1", date = "01-01-2020", dateMillis = DateUtils.parseDateToMillis("01-01-2020"))
        val exp2025_a = Expense.createEmpty().copy(rowId = "2", date = "14-03-2025", time = "09:00", dateMillis = DateUtils.parseDateToMillis("14-03-2025"))
        val exp2025_b = Expense.createEmpty().copy(rowId = "3", date = "14-03-2025", time = "10:00", dateMillis = DateUtils.parseDateToMillis("14-03-2025"))
        val exp2026 = Expense.createEmpty().copy(rowId = "4", date = "04-06-2026", dateMillis = DateUtils.parseDateToMillis("04-06-2026"))

        val unsorted = listOf(exp2026, exp2025_b, exp2020, exp2025_a)
        val sorted = unsorted.sortedWith(compareBy({ it.getOrDeriveDateMillis() }, { it.time }, { it.rowId }))

        assertEquals("1", sorted[0].rowId)
        assertEquals("2", sorted[1].rowId)
        assertEquals("3", sorted[2].rowId)
        assertEquals("4", sorted[3].rowId)
    }

    @Test
    fun test6_hdfcRegaliaRegression() {
        val file = File("src/main/assets/expensemanager.csv")
        assertTrue(file.exists())

        val allExpenses = FileInputStream(file).use { CsvParser.parse(it) }
        val accountName = "HDFC REGALIA"

        val hdfcExpenses = allExpenses.filter {
            !it.isDeleted && (it.account == accountName || (it.transactionType == "Transfer" && it.toAccount == accountName))
        }

        assertEquals(11, hdfcExpenses.size)

        val sortedAsc = hdfcExpenses.sortedWith(compareBy({ it.getOrDeriveDateMillis() }, { it.time }, { it.rowId }))

        // Verify exact chronological dates
        val dates = sortedAsc.map { it.date }
        val expectedDates = listOf(
            "14-03-2025",
            "30-03-2025",
            "21-10-2025",
            "03-11-2025",
            "03-11-2025",
            "28-02-2026",
            "17-03-2026",
            "17-03-2026",
            "08-05-2026",
            "12-05-2026",
            "04-06-2026"
        )
        assertEquals(expectedDates, dates)

        // Verify running balances
        var current = 0.0
        val running = sortedAsc.map { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            val effect = when {
                exp.transactionType == "Transfer" -> if (exp.toAccount == accountName) absVal else -absVal
                exp.transactionType == "Income" -> absVal
                exp.transactionType == "Expense" -> -absVal
                else -> 0.0
            }
            current += effect
            current
        }

        assertEquals(-38338.0, running[0], 0.01)
        assertEquals(0.0, running[1], 0.01)
        assertEquals(-6957.0, running[2], 0.01)
        assertEquals(-50.0, running[3], 0.01)
        assertEquals(0.0, running[4], 0.01)
        assertEquals(-7824.0, running[5], 0.01)
        assertEquals(-5.0, running[6], 0.01)
        assertEquals(0.0, running[7], 0.01)
        assertEquals(-138189.0, running[8], 0.01)
        assertEquals(-145344.64, running[9], 0.01)
        assertEquals(0.36, running[10], 0.01)

        val finalBalance = ExpenseSummaryCalculator.currentBalance(hdfcExpenses, accountName)
        assertEquals(0.36, finalBalance, 0.01)
    }

    @Test
    fun test7_sbiSalaryAccountRegression() {
        val file = File("src/main/assets/expensemanager.csv")
        assertTrue(file.exists())

        val allExpenses = FileInputStream(file).use { CsvParser.parse(it) }
        val accountName = "SBI SALARY Account"

        val sbiExpenses = allExpenses.filter {
            !it.isDeleted && (it.account == accountName || (it.transactionType == "Transfer" && it.toAccount == accountName))
        }

        assertTrue("SBI SALARY Account must have transactions", sbiExpenses.isNotEmpty())

        val sortedAsc = sbiExpenses.sortedWith(compareBy({ it.getOrDeriveDateMillis() }, { it.time }, { it.rowId }))

        // Ensure chronological monotonicity of derived dateMillis
        var lastMillis = 0L
        sortedAsc.forEach { exp ->
            val millis = exp.getOrDeriveDateMillis()
            assertTrue("dateMillis must be monotonic in sorted order: $lastMillis <= $millis", millis >= lastMillis)
            lastMillis = millis
        }
    }
}
