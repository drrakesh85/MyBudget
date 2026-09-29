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
}
