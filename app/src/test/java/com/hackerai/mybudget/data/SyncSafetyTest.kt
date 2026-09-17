package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class SyncSafetyTest {

    @Test
    fun testFingerprintLocaleIndependence() {
        val expense = Expense(
            date = "16-09-2026",
            time = "10:30",
            amount = 1234.56,
            category = "Food",
            subcategory = "Lunch",
            paymentMethod = "Cash",
            description = "Burger",
            refCheckNo = "",
            payeePayer = "McDonalds",
            status = "clear",
            receiptPicture = "",
            account = "Wallet",
            tag = "Personal",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "test_1",
            typeId = "source_123"
        )

        // Test with US Locale
        Locale.setDefault(Locale.US)
        val fingerprintUS = expense.calculateFingerprint()
        assertTrue("Fingerprint should contain dot as decimal separator", fingerprintUS.contains("1234.56"))

        // Test with German Locale (uses comma for decimal)
        Locale.setDefault(Locale.GERMANY)
        val fingerprintDE = expense.calculateFingerprint()
        assertEquals("Fingerprint must be identical regardless of locale", fingerprintUS, fingerprintDE)
        assertTrue("German locale fingerprint should still use dot", fingerprintDE.contains("1234.56"))
    }

    @Test
    fun testDuplicateResolutionStrategy() {
        // Case B: Two legitimate identical transactions (Manual)
        val manual1 = Expense.createEmpty().copy(
            amount = 500.0, description = "Amazon", rowId = "new_1", typeId = ""
        )
        val manual2 = Expense.createEmpty().copy(
            amount = 500.0, description = "Amazon", rowId = "new_2", typeId = ""
        )
        
        // Manual entries should stay separate if rowIds differ, even if fingerprint is same
        assertEquals(manual1.calculateFingerprint(), manual2.calculateFingerprint())
        assertNotEquals(manual1.rowId, manual2.rowId)

        // Case A: Same logical transaction on two devices (SMS)
        val smsDeviceA = Expense.createEmpty().copy(
            amount = -500.0, tag = "SMS", rowId = "sms_1", typeId = "1600000000"
        )
        val smsDeviceB = Expense.createEmpty().copy(
            amount = -500.0, tag = "SMS", rowId = "sms_99", typeId = "1600000000"
        )
        
        assertEquals("Logical SMS duplicates must have same fingerprint", 
            smsDeviceA.calculateFingerprint(), smsDeviceB.calculateFingerprint())
            
        // Case B: Two legitimate identical SMS (different timestamps)
        val sms1 = Expense.createEmpty().copy(
            amount = -500.0, tag = "SMS", rowId = "sms_1", typeId = "1600000000"
        )
        val sms2 = Expense.createEmpty().copy(
            amount = -500.0, tag = "SMS", rowId = "sms_2", typeId = "1600000005"
        )
        assertNotEquals("Identical SMS with different timestamps must have different fingerprints",
            sms1.calculateFingerprint(), sms2.calculateFingerprint())
    }

    @Test
    fun testSyncDataBackwardCompatibility() {
        val gson = com.google.gson.Gson()
        
        // 1. Legacy List<Expense> format (JSON array)
        val legacyJson = "[{\"date\":\"16-09-2026\",\"amount\":500.0,\"rowId\":\"old_1\",\"typeId\":\"\"}]"
        
        // Use a wrapper to simulate ViewModel logic
        val syncData = try {
            // Try SyncData object
            val data = gson.fromJson(legacyJson, SyncData::class.java)
            if (data?.expenses == null) {
                val listType = object : com.google.gson.reflect.TypeToken<List<Expense>>() {}.type
                val list: List<Expense> = gson.fromJson(legacyJson, listType)
                SyncData(expenses = list)
            } else data
        } catch (e: Exception) {
            // Try legacy array
            val listType = object : com.google.gson.reflect.TypeToken<List<Expense>>() {}.type
            val list: List<Expense> = gson.fromJson(legacyJson, listType)
            SyncData(expenses = list)
        }
        
        assertEquals(1, syncData.expenses.size)
        assertEquals("old_1", syncData.expenses[0].rowId)
        assertTrue(syncData.accounts.isEmpty())

        // 2. SyncData v2 (Missing accounts field)
        val v2Json = "{\"schemaVersion\":2, \"expenses\":[{\"date\":\"16-09-2026\",\"amount\":500.0,\"rowId\":\"v2_1\",\"typeId\":\"\"}]}"
        val syncDataV2 = gson.fromJson(v2Json, SyncData::class.java)
        assertEquals(2, syncDataV2.schemaVersion)
        assertEquals(1, syncDataV2.expenses.size)
    }
}
