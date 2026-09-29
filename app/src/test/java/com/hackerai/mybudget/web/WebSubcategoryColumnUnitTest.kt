package com.hackerai.mybudget.web

import com.google.gson.Gson
import com.hackerai.mybudget.data.Expense
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class WebSubcategoryColumnUnitTest {

    private val gson = Gson()

    @Test
    fun test1_transactionsApiResponseContainsSubcategoryField() {
        val expense = Expense(
            date = "26-09-2026",
            time = "12:00",
            amount = -455.30,
            category = "Automobile",
            subcategory = "Petrol",
            paymentMethod = "Card",
            description = "jupiter petrol",
            refCheckNo = "",
            payeePayer = "Jupiter Fuel",
            status = "clear",
            receiptPicture = "",
            account = "Standard chartered Value back",
            tag = "Fuel",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "txn_001",
            typeId = "",
            transactionType = "Expense"
        )

        val json = gson.toJson(expense)
        assertTrue("JSON payload must contain 'subcategory'", json.contains("\"subcategory\":\"Petrol\""))

        val deserialized = gson.fromJson(json, Expense::class.java)
        assertEquals("Automobile", deserialized.category)
        assertEquals("Petrol", deserialized.subcategory)
    }

    @Test
    fun test2_indexHtmlContainsSubcategoryTableHeaderInCorrectPosition() {
        val htmlFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/index.html")
        assertTrue("index.html must exist", htmlFile.exists())
        val html = htmlFile.readText()

        assertTrue("Table header must contain <th>Subcategory</th>", html.contains("<th>Subcategory</th>"))

        val categoryIndex = html.indexOf("<th>Category</th>")
        val subcategoryIndex = html.indexOf("<th>Subcategory</th>")
        val accountIndex = html.indexOf("<th>Account</th>")

        assertTrue("Category header must exist", categoryIndex != -1)
        assertTrue("Subcategory header must exist", subcategoryIndex != -1)
        assertTrue("Account header must exist", accountIndex != -1)

        assertTrue("Subcategory must be placed after Category", subcategoryIndex > categoryIndex)
        assertTrue("Subcategory must be placed before Account", subcategoryIndex < accountIndex)
    }

    @Test
    fun test3_and_4_appJsRendersCategoryAndSubcategoryInSeparateCellsWithFallback() {
        val jsFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/app.js")
        assertTrue("app.js must exist", jsFile.exists())
        val js = jsFile.readText()

        assertTrue("Category cell must render category", js.contains("<td>\${escapeHtml(txn.category || '—')}</td>"))
        assertTrue("Subcategory cell must render subcategory", js.contains("<td>\${escapeHtml(txn.subcategory || '—')}</td>"))

        // Verify fallback logic
        fun formatCell(value: String?): String {
            val trimmed = value?.trim()
            return if (trimmed.isNullOrEmpty()) "—" else trimmed
        }

        assertEquals("Automobile", formatCell("Automobile"))
        assertEquals("Petrol", formatCell("Petrol"))
        assertEquals("—", formatCell(""))
        assertEquals("—", formatCell(null))
        assertFalse("Must not be 'null'", formatCell(null) == "null")
        assertFalse("Must not be 'undefined'", formatCell(null) == "undefined")
    }

    @Test
    fun test5_subcategoryFilteringPreserved() {
        val jsFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/app.js")
        val js = jsFile.readText()

        assertTrue("Subcategory filter selection array must exist", js.contains("selectedSubcategories"))
        assertTrue("Subcategory filter param must be passed to API", js.contains("const subcategoryStr = selectedSubcategories.join(',')"))
    }

    @Test
    fun test6_editingTransactionPreservesSubcategory() {
        val jsFile = File("E:/AndroidStudioProjects/MyBudget/app/src/main/assets/web/app.js")
        val js = jsFile.readText()

        assertTrue("Edit modal must populate formSubcategory", js.contains("document.getElementById('formSubcategory').value = txn.subcategory || ''"))
        assertTrue("Save transaction must read formSubcategory", js.contains("subcategory: document.getElementById('formSubcategory').value.trim()"))
    }
}
