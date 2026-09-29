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
01-01-2024,-100.0,Transfer,,,,Transfer to SBI,,,,HDFC,,,,,,1,1,Transfer,SBI
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)
        
        assertEquals(1, result.size)
        assertEquals("Transfer", result[0].transactionType)
        assertEquals("SBI", result[0].toAccount)
    }

    @Test
    fun parse_transactionTypeNormalization() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id,Transaction Type,To Account
01-01-2024,100.0,Cat,,,,,,,,My Cash,,,1,PCS,,1,1,INCOME,
01-01-2024,-50.0,Cat,,,,,,,,My Cash,,,1,PCS,,2,1,EXPENSE,
01-01-2024,-100.0,Cat,,,,,,,,My Cash,,,1,PCS,,3,1,TRANSFER,SBI
01-01-2024,-200.0,Cat,,,,,,,,My Cash,,,1,PCS,,4,1,ACCOUNT TRANSFER,SBI
01-01-2024,300.0,Cat,,,,,,,,My Cash,,,1,PCS,,5,1,income,
01-01-2024,-80.0,Cat,,,,,,,,My Cash,,,1,PCS,,6,1,expense,
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)

        assertEquals(6, result.size)
        assertEquals("Income", result[0].transactionType)
        assertEquals("Expense", result[1].transactionType)
        assertEquals("Transfer", result[2].transactionType)
        assertEquals("Transfer", result[3].transactionType)
        assertEquals("Income", result[4].transactionType)
        assertEquals("Expense", result[5].transactionType)
    }

    @Test
    fun parse_18ColumnCsvFallback() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id
01-01-2024,250.0,Income,,,,Salary,,,,My Cash,,,1,PCS,,1,1
01-01-2024,-45.0,Food,,,,Lunch,,,,My Cash,,,1,PCS,,2,1
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)

        assertEquals(2, result.size)
        assertEquals("Income", result[0].transactionType)
        assertEquals("Expense", result[1].transactionType)
    }

    @Test
    fun parse_specificRowsFromAudit() {
        val csv = """Date,Amount,Category,Subcategory,Payment Method,Description,Ref/Check No,Payee/Payer,Status,Receipt Picture,Account,Tag,Tax,Quantity,Unit,Split Total,Row Id,Type Id,Transaction Type,To Account
04-07-2020,7430.00,Income,Initial Balance,Method,Initial Balance,Ref,Payee,Cleared,Pic,My Cash,Tag,Tax,1,PCS,Split,1,TypeId1,INCOME,
04-07-2020,-30.00,Food,Subcat,Method,Lunch,Ref,Payee,Cleared,Pic,My Cash,Tag,Tax,1,PCS,Split,20,TypeId20,EXPENSE,
"""
        val inputStream = ByteArrayInputStream(csv.toByteArray(StandardCharsets.UTF_8))
        val result = CsvParser.parse(inputStream)

        assertEquals(2, result.size)
        assertEquals("1", result[0].rowId)
        assertEquals(7430.00, result[0].amount, 0.01)
        assertEquals("Income", result[0].transactionType)

        assertEquals("20", result[1].rowId)
        assertEquals(-30.00, result[1].amount, 0.01)
        assertEquals("Expense", result[1].transactionType)
    }
}
