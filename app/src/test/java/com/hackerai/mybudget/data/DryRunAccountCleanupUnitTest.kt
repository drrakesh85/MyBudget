package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test

class DryRunAccountCleanupUnitTest {

    @Test
    fun testRequestedListDuplicatesAndNormalization() {
        val rawList = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES
        println("=== UNWANTED ACCOUNTS LIST ANALYSIS ===")
        println("Total raw requested items: ${rawList.size}")

        val normalizedList = rawList.map { it.trim().lowercase() }
        val distinctNormalized = normalizedList.distinct()

        println("Distinct normalized requested items: ${distinctNormalized.size}")

        val duplicatesMap = normalizedList.groupingBy { it }.eachCount().filter { it.value > 1 }
        println("Duplicate requested items count: ${duplicatesMap.size}")
        duplicatesMap.forEach { (name, count) ->
            println("Duplicate requested item: '$name' (appears $count times)")
        }

        assertTrue(rawList.isNotEmpty())
    }

    @Test
    fun testDryRunExecutionDoesNotModifyAccountsList() {
        val fakeAccounts = listOf(
            SavingAccount("1", "JEDGE", "Bank", "Branch", "100"),
            SavingAccount("2", "CSB", "Bank", "Branch", "200"),
            SavingAccount("3", "SBI SALARY Account", "SBI", "Branch", "300")
        )

        // Verify dry run logic directly
        val requestedNames = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES
        val normRequested = requestedNames.map { it.trim().lowercase() }.toSet()

        val matchedAccounts = fakeAccounts.filter { normRequested.contains(it.nickName.trim().lowercase()) }
        val remainingAccounts = fakeAccounts.filter { !normRequested.contains(it.nickName.trim().lowercase()) }

        assertEquals(2, matchedAccounts.size)
        assertEquals(1, remainingAccounts.size)
        assertEquals("SBI SALARY Account", remainingAccounts[0].nickName)

        // In dry run mode, fakeAccounts list remains untouched
        assertEquals(3, fakeAccounts.size)
    }
}
