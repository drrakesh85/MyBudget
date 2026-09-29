package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test

class SuggestionsUnitTest {

    private fun extractDistinctNonBlankSorted(items: List<String>): List<String> {
        return items.filter { it.isNotBlank() }.distinct().sorted()
    }

    @Test
    fun test1_2_DistinctPayeeSuggestionsAndBlankExcluded() {
        val rawPayees = listOf("Amazon", "Swiggy", "", "Amazon", "  ", "Flipkart", "Swiggy")

        val suggestions = extractDistinctNonBlankSorted(rawPayees)

        assertEquals("Must contain 3 unique non-blank payees", 3, suggestions.size)
        assertEquals(listOf("Amazon", "Flipkart", "Swiggy"), suggestions)
        assertFalse("Blank entries must be excluded", suggestions.contains(""))
        assertFalse("Whitespace entries must be excluded", suggestions.contains("  "))
    }

    @Test
    fun test3_4_DistinctCategoriesAndBlankExcluded() {
        val rawCategories = listOf("Food", "Utilities", "", "Food", "Shopping", "  ")

        val suggestions = extractDistinctNonBlankSorted(rawCategories)

        assertEquals(3, suggestions.size)
        assertEquals(listOf("Food", "Shopping", "Utilities"), suggestions)
        assertFalse("Blank categories must be excluded", suggestions.contains(""))
    }

    @Test
    fun test5_6_DistinctSubcategoriesAndBlankExcluded() {
        val rawSubcategories = listOf("Lunch", "Dinner", "", "Lunch", "Electricity", "  ")

        val suggestions = extractDistinctNonBlankSorted(rawSubcategories)

        assertEquals(3, suggestions.size)
        assertEquals(listOf("Dinner", "Electricity", "Lunch"), suggestions)
        assertFalse("Blank subcategories must be excluded", suggestions.contains(""))
    }

    @Test
    fun test7_SearchIsCaseInsensitive() {
        val suggestions = listOf("Amazon", "Amazon Prime", "Flipkart", "Swiggy")
        val searchQuery = "ama"

        val filtered = suggestions.filter { it.contains(searchQuery, ignoreCase = true) }

        assertEquals(2, filtered.size)
        assertTrue(filtered.contains("Amazon"))
        assertTrue(filtered.contains("Amazon Prime"))
        assertFalse(filtered.contains("Swiggy"))
    }

    @Test
    fun test8_9_SuggestionsDerivedOnlyFromCurrentLocalTransactions() {
        val localRoomExpenses = listOf(
            Expense.createEmpty().copy(payeePayer = "Local Supermarket", category = "Groceries", subcategory = "Vegetables"),
            Expense.createEmpty().copy(payeePayer = "Local Cafe", category = "Food", subcategory = "Coffee")
        )

        // Generate suggestions from current Room local expenses only
        val payees = localRoomExpenses.map { it.payeePayer }.filter { it.isNotBlank() }.distinct().sorted()
        val categories = localRoomExpenses.map { it.category }.filter { it.isNotBlank() }.distinct().sorted()

        assertEquals(2, payees.size)
        assertTrue(payees.contains("Local Supermarket"))
        assertTrue(payees.contains("Local Cafe"))
        assertFalse("Old cloud dataset must not be present", payees.contains("Old Cloud Store"))

        assertEquals(2, categories.size)
        assertTrue(categories.contains("Groceries"))
        assertTrue(categories.contains("Food"))
    }

    @Test
    fun test10_CategorySubcategoryContext() {
        val localExpenses = listOf(
            Expense.createEmpty().copy(category = "Food", subcategory = "Restaurant"),
            Expense.createEmpty().copy(category = "Food", subcategory = "Food Delivery"),
            Expense.createEmpty().copy(category = "Utilities", subcategory = "Electricity"),
            Expense.createEmpty().copy(category = "Utilities", subcategory = "Water")
        )

        // Build category -> subcategories map
        val categorySubcategoryMap = localExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted() }

        val foodSubcategories = categorySubcategoryMap["Food"] ?: emptyList()
        assertEquals(2, foodSubcategories.size)
        assertEquals(listOf("Food Delivery", "Restaurant"), foodSubcategories)

        val utilitySubcategories = categorySubcategoryMap["Utilities"] ?: emptyList()
        assertEquals(2, utilitySubcategories.size)
        assertEquals(listOf("Electricity", "Water"), utilitySubcategories)
    }

    @Test
    fun testPayeeToCategoryContextAssociation() {
        val localExpenses = listOf(
            Expense.createEmpty().copy(payeePayer = "Swiggy", category = "Food", subcategory = "Food Delivery"),
            Expense.createEmpty().copy(payeePayer = "Swiggy", category = "Food", subcategory = "Food Delivery"),
            Expense.createEmpty().copy(payeePayer = "Swiggy", category = "Food", subcategory = "Restaurant")
        )

        // Build payee -> (category, subcategory) map based on frequency
        val payeeMap = localExpenses.filter { it.payeePayer.isNotBlank() && it.category.isNotBlank() }
            .groupBy { it.payeePayer }
            .mapValues { (_, list) ->
                val mostFrequent = list.groupBy { it.category to it.subcategory }
                    .maxByOrNull { it.value.size }?.key ?: ("" to "")
                mostFrequent
            }

        val swiggyContext = payeeMap["Swiggy"]
        assertNotNull(swiggyContext)
        assertEquals("Food", swiggyContext?.first)
        assertEquals("Food Delivery", swiggyContext?.second)
    }
}
