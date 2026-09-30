package com.hackerai.mybudget.web

import org.junit.Assert.*
import org.junit.Test

class MultiSelectBooleanFilterExpressionTest {

    data class Txn(
        val rowId: String,
        val type: String,
        val category: String,
        val subcategory: String,
        val payee: String,
        val runningBalance: Double = 0.0,
        val account: String = "Account1"
    )

    private val sampleDataset = listOf(
        Txn("1", "Expense", "Food", "Snack", "Swiggy", 1000.0, "Account1"),
        Txn("2", "Expense", "Travel", "Taxi", "Rapido", 800.0, "Account1"),
        Txn("3", "Expense", "Food", "", "Swiggy", 500.0, "Account1"), // No Subcategory
        Txn("4", "Income", "Food", "Snack", "Swiggy", 1500.0, "Account1"), // Income
        Txn("5", "Expense", "Utilities", "Electricity", "Torrent Power", 1200.0, "Account1"),
        Txn("6", "Expense", "Travel", "Flight", "Air India", 200.0, "Account1"),
        Txn("7", "Expense", "Food", "Snack", "Zomato", 100.0, "Account1"),
        Txn("8", "Income", "Salary", "Bonus", "Employer", 5000.0, "Account2") // Account2
    )

    private fun evaluateFilterExpression(
        selectedTypes: List<String> = emptyList(),
        selectedCategories: List<String> = emptyList(),
        selectedSubcategories: List<String> = emptyList(),
        selectedPayees: List<String> = emptyList(),
        accountFilter: String? = null
    ): List<Txn> {
        return sampleDataset.filter { txn ->
            if (accountFilter != null && txn.account != accountFilter) return@filter false

            // 1. Type
            if (selectedTypes.isNotEmpty()) {
                if (!selectedTypes.contains(txn.type)) return@filter false
            }

            // 2. Category
            if (selectedCategories.isNotEmpty()) {
                if (!selectedCategories.contains(txn.category)) return@filter false
            }

            // 3. Subcategory
            if (selectedSubcategories.isNotEmpty()) {
                val txnSub = txn.subcategory.trim()
                val matchesSub = selectedSubcategories.any { selectedSub ->
                    if (selectedSub == "__NO_SUBCATEGORY__" || selectedSub == "No Subcategory" || selectedSub == "[No Subcategory]") {
                        txnSub.isBlank() || txnSub == "—"
                    } else {
                        selectedSub.equals(txnSub, ignoreCase = true)
                    }
                }
                if (!matchesSub) return@filter false
            }

            // 4. Payee/Payer
            if (selectedPayees.isNotEmpty()) {
                val payeeLower = txn.payee.lowercase()
                val matchesPayee = selectedPayees.any { selectedPayee ->
                    payeeLower.contains(selectedPayee.lowercase())
                }
                if (!matchesPayee) return@filter false
            }

            true
        }
    }

    @Test
    fun test1_singleCategorySelection() {
        val result = evaluateFilterExpression(selectedCategories = listOf("Travel"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.category == "Travel" })
    }

    @Test
    fun test2_multipleCategorySelections() {
        val result = evaluateFilterExpression(selectedCategories = listOf("Food", "Travel"))
        assertEquals(6, result.size)
        assertTrue(result.all { it.category == "Food" || it.category == "Travel" })
    }

    @Test
    fun test3_singleSubcategorySelection() {
        val result = evaluateFilterExpression(selectedSubcategories = listOf("Taxi"))
        assertEquals(1, result.size)
        assertEquals("Taxi", result[0].subcategory)
    }

    @Test
    fun test4_multipleSubcategorySelections() {
        val result = evaluateFilterExpression(selectedSubcategories = listOf("Snack", "Taxi"))
        assertEquals(4, result.size)
        assertTrue(result.all { it.subcategory == "Snack" || it.subcategory == "Taxi" })
    }

    @Test
    fun test5_blankNoSubcategorySelection() {
        val result = evaluateFilterExpression(selectedSubcategories = listOf("No Subcategory"))
        assertEquals(1, result.size)
        assertEquals("3", result[0].rowId)
        assertTrue(result[0].subcategory.isBlank())
    }

    @Test
    fun test6_multiplePayeePayerSelections() {
        val result = evaluateFilterExpression(selectedPayees = listOf("Swiggy", "Zomato"))
        assertEquals(4, result.size)
        assertTrue(result.all { it.payee == "Swiggy" || it.payee == "Zomato" })
    }

    @Test
    fun test7_typeFiltering() {
        val result = evaluateFilterExpression(selectedTypes = listOf("Income"))
        assertEquals(2, result.size)
        assertTrue(result.all { it.type == "Income" })
    }

    @Test
    fun test8_categoryAndSubcategoryCombination() {
        val result = evaluateFilterExpression(
            selectedCategories = listOf("Travel"),
            selectedSubcategories = listOf("Taxi")
        )
        assertEquals(1, result.size)
        assertEquals("2", result[0].rowId)
    }

    @Test
    fun test9_categoryAndPayeePayerCombination() {
        val result = evaluateFilterExpression(
            selectedCategories = listOf("Food"),
            selectedPayees = listOf("Zomato")
        )
        assertEquals(1, result.size)
        assertEquals("7", result[0].rowId)
    }

    @Test
    fun test10_subcategoryAndPayeePayerCombination() {
        val result = evaluateFilterExpression(
            selectedSubcategories = listOf("Snack"),
            selectedPayees = listOf("Swiggy")
        )
        assertEquals(2, result.size)
        assertTrue(result.all { it.subcategory == "Snack" && it.payee == "Swiggy" })
    }

    @Test
    fun test11_categorySubcategoryPayeeCombination() {
        val result = evaluateFilterExpression(
            selectedCategories = listOf("Food"),
            selectedSubcategories = listOf("Snack"),
            selectedPayees = listOf("Zomato")
        )
        assertEquals(1, result.size)
        assertEquals("7", result[0].rowId)
    }

    @Test
    fun test12_allFilterGroupsSimultaneously() {
        val result = evaluateFilterExpression(
            selectedTypes = listOf("Expense"),
            selectedCategories = listOf("Food", "Travel"),
            selectedSubcategories = listOf("Snack", "Taxi", "No Subcategory"),
            selectedPayees = listOf("Swiggy", "Rapido")
        )

        assertEquals(3, result.size)
        val matchedIds = result.map { it.rowId }
        assertTrue(matchedIds.contains("1"))
        assertTrue(matchedIds.contains("2"))
        assertTrue(matchedIds.contains("3"))
    }

    @Test
    fun test13_clearFilters() {
        val filtered = evaluateFilterExpression(
            selectedTypes = listOf("Expense"),
            selectedCategories = listOf("Food")
        )
        assertEquals(3, filtered.size)

        // Clear filters
        val cleared = evaluateFilterExpression()
        assertEquals(8, cleared.size)
    }

    @Test
    fun test14_emptyResultSet() {
        val result = evaluateFilterExpression(
            selectedCategories = listOf("Food"),
            selectedSubcategories = listOf("Flight")
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun test15_runningBalancesUnchangedAfterFiltering() {
        val originalTxn1 = sampleDataset.find { it.rowId == "1" }!!
        val originalTxn2 = sampleDataset.find { it.rowId == "2" }!!

        assertEquals(1000.0, originalTxn1.runningBalance, 0.01)
        assertEquals(800.0, originalTxn2.runningBalance, 0.01)

        val filtered = evaluateFilterExpression(selectedCategories = listOf("Travel"))
        assertEquals(2, filtered.size)

        val filteredTxn2 = filtered.find { it.rowId == "2" }!!
        assertEquals(800.0, filteredTxn2.runningBalance, 0.01)
    }

    @Test
    fun test16_accountIsolationRemainsIntact() {
        val acc1Results = evaluateFilterExpression(accountFilter = "Account1")
        val acc2Results = evaluateFilterExpression(accountFilter = "Account2")

        assertEquals(7, acc1Results.size)
        assertEquals(1, acc2Results.size)

        assertTrue(acc1Results.all { it.account == "Account1" })
        assertTrue(acc2Results.all { it.account == "Account2" })
    }
}
