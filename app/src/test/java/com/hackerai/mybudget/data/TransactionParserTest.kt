package com.hackerai.mybudget.data

import org.junit.Test
import org.junit.Assert.*

class TransactionParserTest {

    @Test
    fun parse_debitSms_returnsExpense() {
        val sms = SmsMessage(
            id = "1",
            address = "HDFCBK",
            body = "Rs 500.00 debited from your A/c",
            date = System.currentTimeMillis()
        )
        val result = TransactionParser.parse(sms)
        assertNotNull(result)
        assertEquals(-500.0, result?.amount, 0.01)
        assertEquals("Expense", result?.transactionType)
        assertEquals("HDFCBK", result?.account)
    }

    @Test
    fun parse_creditSms_returnsIncome() {
        val sms = SmsMessage(
            id = "2",
            address = "SBI",
            body = "INR 1,200.00 credited to your account",
            date = System.currentTimeMillis()
        )
        val result = TransactionParser.parse(sms)
        assertNotNull(result)
        assertEquals(1200.0, result?.amount, 0.01)
        assertEquals("Income", result?.transactionType)
    }

    @Test
    fun parse_invalidSms_returnsNull() {
        val sms = SmsMessage(
            id = "3",
            address = "UNKNOWN",
            body = "Hello, this is not a transaction message",
            date = System.currentTimeMillis()
        )
        val result = TransactionParser.parse(sms)
        assertNull(result)
    }

    @Test
    fun parse_smsWithDollarAmount() {
        val sms = SmsMessage(
            id = "4",
            address = "BANK",
            body = "$ 100.00 was spent on purchase",
            date = System.currentTimeMillis()
        )
        val result = TransactionParser.parse(sms)
        assertNotNull(result)
        assertEquals(-100.0, result?.amount, 0.01)
    }
}