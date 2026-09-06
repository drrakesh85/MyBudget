package com.hackerai.mybudget.data

import org.junit.Test
import org.junit.Assert.*
import java.io.ByteArrayInputStream
import java.nio.charset.StandardCharsets

class CsvParserTest {

    @Test
    fun parse_validCsv_returnsExpenses() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id
01-01-2024,-100.0,Food,,,Lunch,,,,My Cash,,,1,PCS,,1,1
02-01-2024,500.0,Income,,,,Salary,,,,My Cash,,,1,PCS,,2,2
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        
        assertEquals(2, result.size)
        assertEquals(-100.0, result[0].amount, 0.01)
        assertEquals("Food", result[0].category)
        assertEquals(500.0, result[1].amount, 0.01)
        assertEquals("Income", result[1].category)
    }

    @Test
    fun parse_malformedLine_skipsAndContinues() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id
01-01-2024,-100.0,Food,,,Lunch,,,,My Cash,,,1,PCS,,1,1
malformed line
02-01-2024,500.0,Income,,,,Salary,,,,My Cash,,,1,PCS,,2,2
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        
        assertEquals(2, result.size)
    }

    @Test
    fun parse_emptyCsv_returnsEmptyList() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        
        assertTrue(result.isEmpty())
    }

    @Test
    fun parse_csvWithTransferType() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id,Transaction Type,To Account
01-01-2024,-100.0,Transfer,,,,Transfer to SBI,,,,HDFC,,,,,,Transfer,SBI
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        
        assertEquals(1, result.size)
        assertEquals("Transfer", result[0].transactionType)
        assertEquals("SBI", result[0].toAccount)
    }
}