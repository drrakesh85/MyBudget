package com.hackerai.mybudget.web

import org.junit.Assert.*
import org.junit.Test

class MultiSelectBooleanFilterExpressionTest {

    data class Txn(
        val rowId: String,
        val type: String,
        val category: String,
        val subcategory: String,
        val payee: String
    )

    private val sampleDataset = listOf(
        Txn("1", "Expense", "Food", "Snack", "Swiggy"),
        Txn("2", "Expense", "Travel", "Taxi", "Rapido"),
        Txn("3", "Expense", "Food", "", "Swiggy"), // No Subcategory
        Txn("4", "Income", "Food", "Snack", "Swiggy"), // Income (does not match Expense type)
        Txn("5", "Expense", "Utilities", "Electricity", "Torrent Power"), // Category does not match Food OR Travel
        Txn("6", "Expense", "Travel", "Flight", "Air India"), // Subcategory Flight does not match Snack/Taxi/No Subcategory
        Txn("7", "Expense", "Food", "Snack", "Zomato") // Payee Zomato does not match Swiggy/Rapido
    )

    private fun evaluateFilterExpression(
        selectedTypes: List<String>,
        selectedCategories: List<String>,
        selectedSubcategories: List<String>,
        selectedPayees: List<String>
    ): List<Txn> {
        return sampleDataset.filter { txn ->
            // 1. Type (Expense)
            if (selectedTypes.isNotEmpty()) {
                if (!selectedTypes.contains(txn.type)) return@filter false
            }

            // 2. Category (Food OR Travel)
            if (selectedCategories.isNotEmpty()) {
                if (!selectedCategories.contains(txn.category)) return@filter false
            }

            // 3. Subcategory (Snack OR Taxi OR No Subcategory)
            if (selectedSubcategories.isNotEmpty()) {
                val txnSub = txn.subcategory.trim()
                val matchesSub = selectedSubcategories.some { selectedSub ->
                    if (selectedSub == "__NO_SUBCATEGORY__" || selectedSub == "No Subcategory") {
                        txnSub.isBlank() || txnSub == "—"
                    } else {
                        selectedSub.equals(txnSub, ignoreCase = true)
                    }
                }
                if (!matchesSub) return@filter false
            }

            // 4. Payee/Payer (Swiggy OR Rapido)
            if (selectedPayees.isNotEmpty()) {
                val payeeLower = txn.payee.lowercase()
                val matchesPayee = selectedPayees.some { selectedPayee ->
                    payeeLower.contains(selectedPayee.lowercase())
                }
                if (!matchesPayee) return@filter false
            }

            true
        }
    }

    private fun <T> List<T>.some(predicate: (T) -> Boolean): Boolean {
        return this.any(predicate)
    }

    @Test
    fun testUserBooleanFilterExpressionResult() {
        val selectedTypes = listOf("Expense")
        val selectedCategories = listOf("Food", "Travel")
        val selectedSubcategories = listOf("Snack", "Taxi", "__NO_SUBCATEGORY__")
        val selectedPayees = listOf("Swiggy", "Rapido")

        val result = evaluateFilterExpression(
            selectedTypes,
            selectedCategories,
            selectedSubcategories,
            selectedPayees
        )

        // Expected matching transactions:
        // Txn 1: Expense AND Food AND Snack AND Swiggy -> MATCH
        // Txn 2: Expense AND Travel AND Taxi AND Rapido -> MATCH
        // Txn 3: Expense AND Food AND "" (No Subcategory) AND Swiggy -> MATCH
        assertEquals(3, result.size)

        val matchedIds = result.map { it.rowId }
        assertTrue(matchedIds.contains("1"))
        assertTrue(matchedIds.contains("2"))
        assertTrue(matchedIds.contains("3"))

        assertFalse("Txn 4 (Income) must be excluded by Type filter", matchedIds.contains("4"))
        assertFalse("Txn 5 (Utilities) must be excluded by Category filter", matchedIds.contains("5"))
        assertFalse("Txn 6 (Flight) must be excluded by Subcategory filter", matchedIds.contains("6"))
        assertFalse("Txn 7 (Zomato) must be excluded by Payee filter", matchedIds.contains("7"))
    }
}
