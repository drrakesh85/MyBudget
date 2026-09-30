package com.hackerai.mybudget.web

import com.hackerai.mybudget.data.DateUtils
import org.junit.Assert.*
import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime

class DateFilterUnitTest {

    data class Txn(
        val rowId: String,
        val dateStr: String,
        val type: String,
        val category: String,
        val subcategory: String,
        val payee: String,
        val runningBalance: Double,
        val account: String = "Account1"
    ) {
        fun getDateMillis(): Long = DateUtils.parseDateToMillis(dateStr)
    }

    // Fixed "now" reference date: Wednesday, 30 September 2026
    private val zone = ZoneId.systemDefault()
    private val fixedNow: ZonedDateTime = ZonedDateTime.of(2026, 9, 30, 12, 0, 0, 0, zone)

    private val sampleDataset = listOf(
        Txn("1", "30-09-2026", "Expense", "Food", "Snack", "Swiggy", 1000.0, "Account1"), // Today, Mon, Sept, 2026
        Txn("2", "28-09-2026", "Expense", "Travel", "Taxi", "Rapido", 800.0, "Account1"), // Monday of this week
        Txn("3", "01-09-2026", "Expense", "Food", "", "Swiggy", 500.0, "Account1"), // 1st of Sept 2026
        Txn("4", "15-01-2026", "Income", "Food", "Snack", "Swiggy", 1500.0, "Account1"), // Jan 2026 (This Year)
        Txn("5", "10-05-2025", "Expense", "Utilities", "Electricity", "Torrent Power", 1200.0, "Account1"), // May 2025 (Last Year)
        Txn("6", "01-10-2026", "Expense", "Travel", "Flight", "Air India", 200.0, "Account1"), // Oct 2026 (Tomorrow/Next Month)
        Txn("7", "15-06-2026", "Expense", "Food", "Snack", "Zomato", 100.0, "Account1"), // June 2026
        Txn("8", "30-09-2026", "Income", "Salary", "Bonus", "Employer", 5000.0, "Account2") // Account2
    )

    private fun filterTxns(
        dateFilter: String? = "ALL",
        fromDate: String? = null,
        toDate: String? = null,
        selectedTypes: List<String> = emptyList(),
        selectedCategories: List<String> = emptyList(),
        selectedSubcategories: List<String> = emptyList(),
        selectedPayees: List<String> = emptyList(),
        accountFilter: String? = null
    ): List<Txn> {
        return sampleDataset.filter { txn ->
            if (accountFilter != null && txn.account != accountFilter) return@filter false

            // Date Filter
            val dateMatches = DateFilterEvaluator.evaluateDateFilter(
                expDateMillis = txn.getDateMillis(),
                dateFilter = dateFilter,
                fromDateStr = fromDate,
                toDateStr = toDate,
                now = fixedNow
            )
            if (!dateMatches) return@filter false

            // Type Filter
            if (selectedTypes.isNotEmpty() && !selectedTypes.contains(txn.type)) return@filter false

            // Category Filter
            if (selectedCategories.isNotEmpty() && !selectedCategories.contains(txn.category)) return@filter false

            // Subcategory Filter
            if (selectedSubcategories.isNotEmpty()) {
                val matchesSub = selectedSubcategories.any { sub ->
                    if (sub == "No Subcategory" || sub == "__NO_SUBCATEGORY__" || sub == "[No Subcategory]") {
                        txn.subcategory.isBlank()
                    } else {
                        txn.subcategory.equals(sub, ignoreCase = true)
                    }
                }
                if (!matchesSub) return@filter false
            }

            // Payee Filter
            if (selectedPayees.isNotEmpty()) {
                val matchesPayee = selectedPayees.any { payee ->
                    txn.payee.lowercase().contains(payee.lowercase())
                }
                if (!matchesPayee) return@filter false
            }

            true
        }
    }

    @Test
    fun test1_defaultAllTillDateIncludesAllEligible() {
        val result = filterTxns(dateFilter = "ALL")
        assertEquals(8, result.size)
    }

    @Test
    fun test2_todayFilterMatchesCurrentDateOnly() {
        val result = filterTxns(dateFilter = "TODAY")
        // Txn 1 (30-09-2026, Account1) and Txn 8 (30-09-2026, Account2)
        assertEquals(2, result.size)
        assertTrue(result.all { it.dateStr == "30-09-2026" })
    }

    @Test
    fun test3_thisWeekFilterMatchesMondayToSunday() {
        // Monday 28-09-2026 to Sunday 04-10-2026
        val result = filterTxns(dateFilter = "THIS_WEEK")
        // Matches Txn 1 (30-09), Txn 2 (28-09), Txn 6 (01-10), Txn 8 (30-09)
        assertEquals(4, result.size)
        val ids = result.map { it.rowId }
        assertTrue(ids.contains("1"))
        assertTrue(ids.contains("2"))
        assertTrue(ids.contains("6"))
        assertTrue(ids.contains("8"))
        assertFalse(ids.contains("3")) // 01-09-2026 is outside this week
    }

    @Test
    fun test4_thisMonthFilterMatchesSeptember2026() {
        val result = filterTxns(dateFilter = "THIS_MONTH")
        // Matches Txn 1 (30-09), Txn 2 (28-09), Txn 3 (01-09), Txn 8 (30-09)
        assertEquals(4, result.size)
        assertTrue(result.all { it.dateStr.endsWith("-09-2026") })
    }

    @Test
    fun test5_thisYearFilterMatches2026Only() {
        val result = filterTxns(dateFilter = "THIS_YEAR")
        // Matches all except Txn 5 (which is 2025)
        assertEquals(7, result.size)
        assertFalse(result.any { it.rowId == "5" })
    }

    @Test
    fun test6_allTillDateExplicitPreset() {
        val result = filterTxns(dateFilter = "ALL_TILL_DATE")
        assertEquals(8, result.size)
    }

    @Test
    fun test7_customDateRange() {
        val result = filterTxns(
            dateFilter = "CUSTOM",
            fromDate = "01-06-2026",
            toDate = "30-06-2026"
        )
        // Matches Txn 7 (15-06-2026)
        assertEquals(1, result.size)
        assertEquals("7", result[0].rowId)
    }

    @Test
    fun test8_customRangeIncludesFromDateBoundary() {
        val result = filterTxns(
            dateFilter = "CUSTOM",
            fromDate = "28-09-2026",
            toDate = "29-09-2026"
        )
        // Matches Txn 2 (28-09-2026)
        assertEquals(1, result.size)
        assertEquals("2", result[0].rowId)
    }

    @Test
    fun test9_customRangeIncludesToDateBoundary() {
        val result = filterTxns(
            dateFilter = "CUSTOM",
            fromDate = "29-09-2026",
            toDate = "30-09-2026"
        )
        // Matches Txn 1 and Txn 8 (30-09-2026)
        assertEquals(2, result.size)
        assertTrue(result.all { it.dateStr == "30-09-2026" })
    }

    @Test
    fun test10_invalidFromAfterToReturnsEmptyAndPreventsIncorrectResults() {
        val result = filterTxns(
            dateFilter = "CUSTOM",
            fromDate = "30-09-2026",
            toDate = "01-09-2026"
        )
        assertTrue("Invalid From > To range must return empty list safely", result.isEmpty())
    }

    @Test
    fun test11_combinedDateAndTypeFilter() {
        val result = filterTxns(
            dateFilter = "THIS_MONTH",
            selectedTypes = listOf("Expense")
        )
        // Sept 2026 Expenses on Account1: Txn 1, Txn 2, Txn 3
        assertEquals(3, result.size)
        assertTrue(result.all { it.type == "Expense" })
    }

    @Test
    fun test12_combinedDateAndCategoryFilter() {
        val result = filterTxns(
            dateFilter = "THIS_YEAR",
            selectedCategories = listOf("Food")
        )
        // Food in 2026: Txn 1, Txn 3, Txn 4, Txn 7
        assertEquals(4, result.size)
        assertTrue(result.all { it.category == "Food" })
    }

    @Test
    fun test13_combinedDateAndSubcategoryFilter() {
        val result = filterTxns(
            dateFilter = "THIS_MONTH",
            selectedSubcategories = listOf("No Subcategory")
        )
        // Sept 2026 with no subcategory: Txn 3
        assertEquals(1, result.size)
        assertEquals("3", result[0].rowId)
    }

    @Test
    fun test14_combinedDateAndPayeeFilter() {
        val result = filterTxns(
            dateFilter = "THIS_YEAR",
            selectedPayees = listOf("Swiggy")
        )
        // Swiggy in 2026: Txn 1, Txn 3, Txn 4
        assertEquals(3, result.size)
        assertTrue(result.all { it.payee == "Swiggy" })
    }

    @Test
    fun test15_datePlusAllExistingFilterGroupsSimultaneously() {
        val result = filterTxns(
            dateFilter = "THIS_MONTH",
            selectedTypes = listOf("Expense"),
            selectedCategories = listOf("Food"),
            selectedSubcategories = listOf("Snack"),
            selectedPayees = listOf("Swiggy")
        )
        // Sept 2026 Expense Food Snack Swiggy: Txn 1
        assertEquals(1, result.size)
        assertEquals("1", result[0].rowId)
    }

    @Test
    fun test16_clearFiltersResetsDateToAllTillDate() {
        val filtered = filterTxns(
            dateFilter = "TODAY",
            selectedTypes = listOf("Expense")
        )
        assertEquals(1, filtered.size)

        // Clear filters resets dateFilter to "ALL"
        val cleared = filterTxns(dateFilter = "ALL")
        assertEquals(8, cleared.size)
    }

    @Test
    fun test17_paginationAfterDateFilteringOrder() {
        // Date filtering occurs BEFORE pagination
        val dateFiltered = filterTxns(dateFilter = "THIS_MONTH", accountFilter = "Account1")
        assertEquals(3, dateFiltered.size)

        val page1 = dateFiltered.subList(0, 2)
        val page2 = dateFiltered.subList(2, 3)

        assertEquals(2, page1.size)
        assertEquals(1, page2.size)
    }

    @Test
    fun test18_accountIsolationWithDateFiltering() {
        val acc1Today = filterTxns(dateFilter = "TODAY", accountFilter = "Account1")
        val acc2Today = filterTxns(dateFilter = "TODAY", accountFilter = "Account2")

        assertEquals(1, acc1Today.size)
        assertEquals("1", acc1Today[0].rowId)

        assertEquals(1, acc2Today.size)
        assertEquals("8", acc2Today[0].rowId)
    }

    @Test
    fun test19_noMatchingTransactionsForDateRange() {
        val result = filterTxns(
            dateFilter = "CUSTOM",
            fromDate = "01-01-2020",
            toDate = "31-12-2020"
        )
        assertTrue(result.isEmpty())
    }

    @Test
    fun test20_existingRunningBalancesRemainUnchangedAfterDateFiltering() {
        val originalTxn1 = sampleDataset.find { it.rowId == "1" }!!
        val originalTxn2 = sampleDataset.find { it.rowId == "2" }!!

        assertEquals(1000.0, originalTxn1.runningBalance, 0.01)
        assertEquals(800.0, originalTxn2.runningBalance, 0.01)

        val filtered = filterTxns(dateFilter = "THIS_WEEK", accountFilter = "Account1")
        val filteredTxn2 = filtered.find { it.rowId == "2" }!!

        // Running balance on Txn 2 remains 800.0 (historical server-calculated value)
        assertEquals(800.0, filteredTxn2.runningBalance, 0.01)
    }
}
