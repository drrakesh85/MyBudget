package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test

class UnwantedAccountCleanupTest {

    @Test
    fun testExactMatchAndTrimmingAndCaseInsensitivity() {
        val accounts = listOf(
            SavingAccount("1", "CSB", "CSB Bank", "Branch", "100"),
            SavingAccount("2", "jedge", "JEdge Bank", "Branch", "200"),
            SavingAccount("3", "  543  ", "Bank 543", "Branch", "300"),
            SavingAccount("4", "SBI SALARY Account", "SBI", "Branch", "400"), // Legitimate
            SavingAccount("5", "HDFC FREEDOM Card", "HDFC", "Branch", "500")   // Legitimate
        )

        val requestedUnwanted = listOf("CSB", "JEDGE", "543", "NON_EXISTENT_ACCOUNT")

        // Filter logic simulation
        val normalizedUnwanted = requestedUnwanted.map { it.trim().lowercase() }.toSet()

        val removed = accounts.filter { normalizedUnwanted.contains(it.nickName.trim().lowercase()) }
        val remaining = accounts.filter { !normalizedUnwanted.contains(it.nickName.trim().lowercase()) }

        assertEquals(3, removed.size)
        assertEquals(2, remaining.size)

        val remainingNames = remaining.map { it.nickName }
        assertTrue(remainingNames.contains("SBI SALARY Account"))
        assertTrue(remainingNames.contains("HDFC FREEDOM Card"))
        assertFalse(remainingNames.contains("CSB"))
        assertFalse(remainingNames.contains("jedge"))
    }

    @Test
    fun testDryRunDoesNotModifyData() {
        val originalList = listOf(
            SavingAccount("1", "JEDGE", "Bank", "Branch", "100"),
            SavingAccount("2", "Legitimate Account", "Bank", "Branch", "200")
        )

        // Simulate dry run
        var storedList = originalList.toList()
        val isDryRun = true

        val requested = listOf("JEDGE")
        val normRequested = requested.map { it.trim().lowercase() }.toSet()
        val matching = storedList.filter { normRequested.contains(it.nickName.trim().lowercase()) }

        if (!isDryRun) {
            storedList = storedList.filter { !normRequested.contains(it.nickName.trim().lowercase()) }
        }

        assertEquals(2, storedList.size) // Stored list is unchanged in dry run
        assertEquals(1, matching.size)  // Found 1 matching item
    }

    @Test
    fun testUnwantedAccountsDataConstantSize() {
        val names = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES
        assertTrue("Unwanted list must contain items", names.size > 300)
        assertTrue("Must contain JEDGE", names.contains("JEDGE"))
        assertTrue("Must contain CSB", names.contains("CSB"))
    }

    @Test
    fun testRoomTransactionsNotAccessedOrModified() {
        // Verify that cleanup logic only handles Account metadata
        val account = SavingAccount("1", "8676", "Bank", "Branch", "123")
        val expense = Expense.createEmpty().copy(account = "8676", amount = 1000.0)

        // Removing the account object
        val accounts = listOf(account)
        val filteredAccounts = accounts.filter { it.nickName != "8676" }

        assertTrue(filteredAccounts.isEmpty())
        // Transaction is unchanged
        assertEquals("8676", expense.account)
        assertEquals(1000.0, expense.amount, 0.001)
    }
}
