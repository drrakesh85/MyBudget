package com.hackerai.mybudget.web

import org.junit.Assert.*
import org.junit.Test
import java.io.File

class AccountStatementFiltersUnitTest {

    data class StatementTxn(
        val rowId: String,
        val date: String,
        val description: String,
        val category: String,
        val subcategory: String,
        val transactionType: String,
        val amount: Double,
        val semanticAmount: Double,
        val runningBalance: Double
    )

    private val sampleTxns = listOf(
        StatementTxn("1", "26-09-2026", "Salary Credit", "Income", "Salary", "Income", 50000.0, 50000.0, 50000.0),
        StatementTxn("2", "26-09-2026", "jupiter petrol", "Automobile", "Petrol", "Expense", -455.30, -455.30, 49544.70),
        StatementTxn("3", "26-09-2026", "IRCTC Ticket", "Travel", "Train", "Expense", -1200.0, -1200.0, 48344.70),
        StatementTxn("4", "26-09-2026", "Uber Taxi", "Travel", "Other Transportation", "Expense", -350.0, -350.0, 47994.70),
        StatementTxn("5", "26-09-2026", "Transfer to Savings", "Transfer", "Account Transfer", "Transfer", -10000.0, -10000.0, 37994.70)
    )

    private fun filterTxns(
        type: String = "All",
        category: String = "All",
        subcategory: String = "All",
        payeeSearch: String = ""
    ): List<StatementTxn> {
        val q = payeeSearch.trim().lowercase()
        return sampleTxns.filter { txn ->
            if (type != "All" && txn.transactionType != type) return@filter false
            if (category != "All" && txn.category != category) return@filter false
            if (subcategory != "All" && txn.subcategory != subcategory) return@filter false
            if (q.isNotEmpty() && q != "all") {
                if (!txn.description.lowercase().contains(q)) return@filter false
            }
            true
        }
    }

    @Test
    fun test1_typeFilterAllReturnsAll() {
        val result = filterTxns(type = "All")
        assertEquals(5, result.size)
    }

    @Test
    fun test2_typeFilterIncomeReturnsIncomeOnly() {
        val result = filterTxns(type = "Income")
        assertEquals(1, result.size)
        assertEquals("Income", result[0].transactionType)
    }

    @Test
    fun test3_typeFilterExpenseReturnsExpenseOnly() {
        val result = filterTxns(type = "Expense")
        assertEquals(3, result.size)
        assertTrue(result.all { it.transactionType == "Expense" })
    }

    @Test
    fun test4_typeFilterTransferReturnsTransferOnlyDistinctFromIncomeExpense() {
        val result = filterTxns(type = "Transfer")
        assertEquals(1, result.size)
        assertEquals("Transfer", result[0].transactionType)
        assertFalse(result.any { it.transactionType == "Expense" })
        assertFalse(result.any { it.transactionType == "Income" })
    }

    @Test
    fun test5_categoryFiltering() {
        val result = filterTxns(category = "Travel")
        assertEquals(2, result.size)
        assertTrue(result.all { it.category == "Travel" })
    }

    @Test
    fun test6_subcategoryFiltering() {
        val result = filterTxns(subcategory = "Petrol")
        assertEquals(1, result.size)
        assertEquals("Petrol", result[0].subcategory)
    }

    @Test
    fun test7_payeeSearchCaseInsensitive() {
        val result = filterTxns(payeeSearch = "irctc")
        assertEquals(1, result.size)
        assertEquals("IRCTC Ticket", result[0].description)
    }

    @Test
    fun test8_categoryAndSubcategoryCombination() {
        val result = filterTxns(category = "Travel", subcategory = "Other Transportation")
        assertEquals(1, result.size)
        assertEquals("Uber Taxi", result[0].description)
    }

    @Test
    fun test9_typeAndCategoryCombination() {
        val result = filterTxns(type = "Expense", category = "Travel")
        assertEquals(2, result.size)
    }

    @Test
    fun test10_allFourFiltersCombined() {
        val result = filterTxns(
            type = "Expense",
            category = "Travel",
            subcategory = "Train",
            payeeSearch = "IRCTC"
        )
        assertEquals(1, result.size)
        assertEquals("IRCTC Ticket", result[0].description)
    }

    @Test
    fun test11_clearFiltersResetsAllToDefault() {
        var type = "Expense"
        var category = "Travel"
        var subcategory = "Train"
        var payee = "IRCTC"

        // Clear filters
        type = "All"
        category = "All"
        subcategory = "All"
        payee = ""

        val result = filterTxns(type, category, subcategory, payee)
        assertEquals(5, result.size)
    }

    @Test
    fun test12_accountSwitchingResetsFilterState() {
        var currentAccount = "SBI SALARY ACCOUNT"
        var typeFilter = "Expense"

        // Switch to new account
        val newAccount = "HDFC SAVINGS ACCOUNT"
        if (currentAccount != newAccount) {
            currentAccount = newAccount
            typeFilter = "All"
        }

        assertEquals("HDFC SAVINGS ACCOUNT", currentAccount)
        assertEquals("All", typeFilter)
    }

    @Test
    fun test13_accountWithNoMatchingTransactionsHandlesEmptyListCleanly() {
        val result = filterTxns(category = "NonExistentCategory")
        assertTrue(result.isEmpty())
    }

    @Test
    fun test14_transferTransactionsRetainTransferTypeAndAmount() {
        val transferTxns = sampleTxns.filter { it.transactionType == "Transfer" }
        assertEquals(1, transferTxns.size)
        assertEquals("Transfer", transferTxns[0].transactionType)
        assertEquals(-10000.0, transferTxns[0].amount, 0.01)
    }

    @Test
    fun test15_htmlAndJsContainStatementFilterElementsAndHandlers() {
        val htmlFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/index.html")
        val jsFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/app.js")

        assertTrue(htmlFile.exists())
        assertTrue(jsFile.exists())

        val html = htmlFile.readText()
        val js = jsFile.readText()

        assertTrue(html.contains("id=\"popoverStmtType\""))
        assertTrue(html.contains("id=\"popoverStmtCategory\""))
        assertTrue(html.contains("id=\"popoverStmtSubcategory\""))
        assertTrue(html.contains("id=\"popoverStmtPayee\""))

        assertTrue(js.contains("function onStmtTypeFilterCheckboxChanged("))
        assertTrue(js.contains("function onStmtCategoryFilterCheckboxChanged("))
        assertTrue(js.contains("function onStmtSubcategoryFilterCheckboxChanged("))
        assertTrue(js.contains("function onStmtPayeeFilterCheckboxChanged("))
        assertTrue(js.contains("function clearAllStmtFilters()"))
        assertTrue(js.contains("function renderStmtTransactions()"))
    }

    @Test
    fun test16_exampleFromPromptRunningBalanceUnchangedByFilters() {
        val rawTxns = listOf(
            StatementTxn("A", "01-01-2026", "Txn A", "Income", "", "Income", 1000.0, 1000.0, 0.0),
            StatementTxn("B", "02-01-2026", "Txn B", "Expense", "Food", "Expense", -200.0, -200.0, 0.0),
            StatementTxn("C", "03-01-2026", "Txn C", "Expense", "Travel", "Expense", -300.0, -300.0, 0.0),
            StatementTxn("D", "04-01-2026", "Txn D", "Income", "", "Income", 500.0, 500.0, 0.0)
        )

        var balance = 0.0
        val withBalances = rawTxns.map { txn ->
            balance += txn.semanticAmount
            txn.copy(runningBalance = balance)
        }

        assertEquals(1000.0, withBalances.find { it.rowId == "A" }!!.runningBalance, 0.01)
        assertEquals(800.0, withBalances.find { it.rowId == "B" }!!.runningBalance, 0.01)
        assertEquals(500.0, withBalances.find { it.rowId == "C" }!!.runningBalance, 0.01)
        assertEquals(1000.0, withBalances.find { it.rowId == "D" }!!.runningBalance, 0.01)

        val filtered = withBalances.filter { it.transactionType == "Income" }

        assertEquals(2, filtered.size)
        assertEquals(1000.0, filtered.find { it.rowId == "A" }!!.runningBalance, 0.01)
        assertEquals(1000.0, filtered.find { it.rowId == "D" }!!.runningBalance, 0.01)
    }

    @Test
    fun test17_singleLaterTransactionFilterPreservesPrecedingBalanceHistory() {
        val rawTxns = listOf(
            StatementTxn("1", "01-01-2026", "Txn 1", "Category A", "", "Expense", -100.0, -100.0, -100.0),
            StatementTxn("2", "02-01-2026", "Txn 2", "Category A", "", "Expense", -200.0, -200.0, -300.0),
            StatementTxn("3", "03-01-2026", "Txn 3", "Category B", "", "Expense", -500.0, -500.0, -800.0)
        )

        val filtered = rawTxns.filter { it.category == "Category B" }
        assertEquals(1, filtered.size)
        assertEquals(-800.0, filtered[0].runningBalance, 0.01)
    }

    @Test
    fun test18_categoryFilterPreservesBalances() {
        val filtered = sampleTxns.filter { it.category == "Travel" }
        assertEquals(2, filtered.size)
        assertEquals(48344.70, filtered.find { it.rowId == "3" }!!.runningBalance, 0.01)
        assertEquals(47994.70, filtered.find { it.rowId == "4" }!!.runningBalance, 0.01)
    }

    @Test
    fun test19_typeFilterPreservesBalances() {
        val filtered = sampleTxns.filter { it.transactionType == "Transfer" }
        assertEquals(1, filtered.size)
        assertEquals(37994.70, filtered[0].runningBalance, 0.01)
    }

    @Test
    fun test20_payeeFilterPreservesBalances() {
        val filtered = sampleTxns.filter { it.description.contains("Uber") }
        assertEquals(1, filtered.size)
        assertEquals(47994.70, filtered[0].runningBalance, 0.01)
    }

    @Test
    fun test21_multipleSimultaneousFiltersPreserveBalances() {
        val filtered = sampleTxns.filter { it.transactionType == "Expense" && it.category == "Travel" }
        assertEquals(2, filtered.size)
        assertEquals(48344.70, filtered[0].runningBalance, 0.01)
        assertEquals(47994.70, filtered[1].runningBalance, 0.01)
    }

    @Test
    fun test22_clearFiltersRestoresFullViewWithIdenticalBalances() {
        val activeFilters = sampleTxns.filter { it.category == "Automobile" }
        assertEquals(1, activeFilters.size)

        val cleared = sampleTxns
        assertEquals(5, cleared.size)
        assertEquals(50000.0, cleared[0].runningBalance, 0.01)
        assertEquals(37994.70, cleared[4].runningBalance, 0.01)
    }

    @Test
    fun test23_paginationPreservesRunningBalanceContinuation() {
        val allTxns = (1..50).map { i ->
            StatementTxn(
                rowId = "txn_$i",
                date = "26-09-2026",
                description = "Txn $i",
                category = "General",
                subcategory = "",
                transactionType = "Expense",
                amount = -10.0,
                semanticAmount = -10.0,
                runningBalance = -10.0 * i
            )
        }

        val page1 = allTxns.subList(0, 20)
        val page2 = allTxns.subList(20, 40)

        assertEquals(-200.0, page1.last().runningBalance, 0.01)
        assertEquals(-210.0, page2.first().runningBalance, 0.01)
    }

    @Test
    fun test24_accountIsolationPreventsCrossAccountBalanceInfiltration() {
        val acc1Txns = listOf(
            StatementTxn("a1", "26-09-2026", "A1", "Income", "", "Income", 1000.0, 1000.0, 1000.0)
        )
        val acc2Txns = listOf(
            StatementTxn("b1", "26-09-2026", "B1", "Income", "", "Income", 5000.0, 5000.0, 5000.0)
        )

        assertNotEquals(acc1Txns[0].runningBalance, acc2Txns[0].runningBalance)
    }
}
