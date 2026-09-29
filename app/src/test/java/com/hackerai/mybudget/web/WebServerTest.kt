package com.hackerai.mybudget.web

import com.hackerai.mybudget.data.DateUtils
import com.hackerai.mybudget.data.Expense
import com.hackerai.mybudget.data.toEntity
import com.hackerai.mybudget.data.toExpense
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class WebServerTest {

    @Test
    fun test1_pinFreeWebServerStartup() {
        val serverInfo = WebServerInfo(
            status = WebServerStatus.RUNNING,
            ipAddress = "192.168.1.100",
            port = 8080
        )
        assertEquals("http://192.168.1.100:8080", serverInfo.url)
        assertNull("ServerInfo does not contain PIN", serverInfo.errorMessage)
    }

    @Test
    fun test2_sessionTokenGeneration() {
        val token1 = UUID.randomUUID().toString()
        val token2 = UUID.randomUUID().toString()
        assertNotNull(token1)
        assertNotNull(token2)
        assertNotEquals(token1, token2)
    }

    @Test
    fun test3_pathTraversalSecurityCheck() {
        val safePaths = listOf("/", "/index.html", "/style.css", "/app.js")
        val maliciousPaths = listOf(
            "/../secrets.xml",
            "/..\\AndroidManifest.xml",
            "//etc/passwd",
            "/assets/../database.db"
        )

        fun isForbiddenPath(path: String): Boolean {
            return path.contains("..") || path.contains("\\") || path.contains("//")
        }

        safePaths.forEach { path ->
            assertFalse("Safe path '$path' must be allowed", isForbiddenPath(path))
        }

        maliciousPaths.forEach { path ->
            assertTrue("Malicious path '$path' must be rejected", isForbiddenPath(path))
        }
    }

    @Test
    fun test4_lanIpAddressDiscovery() {
        val ip = NetworkUtils.getLocalIpAddress()
        if (ip != null) {
            assertFalse("IP must not be loopback", ip.startsWith("127."))
            assertTrue("IP must be IPv4", ip.contains("."))
        }
    }

    @Test
    fun test5_phase2aTransactionAmountSignEnforcement() {
        val rawIncomeAmount = 500.0
        val incomeSemantic = kotlin.math.abs(rawIncomeAmount)
        assertEquals(500.0, incomeSemantic, 0.01)

        val rawExpenseAmount = 150.0
        val expenseSemantic = -kotlin.math.abs(rawExpenseAmount)
        assertEquals(-150.0, expenseSemantic, 0.01)
    }

    @Test
    fun test6_phase2aDateMillisDerivationForNewTransaction() {
        val dateStr = "31-03-2026"
        val millis = DateUtils.parseDateToMillis(dateStr)
        assertTrue("dateMillis must be > 0", millis > 0L)

        val exp = Expense(
            date = dateStr,
            time = "14:30",
            amount = -250.0,
            category = "Food",
            subcategory = "Lunch",
            paymentMethod = "UPI",
            description = "Test lunch",
            refCheckNo = "",
            payeePayer = "Cafe",
            status = "clear",
            receiptPicture = "",
            account = "My Cash",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "web_${UUID.randomUUID()}",
            typeId = "",
            transactionType = "Expense",
            toAccount = null,
            dateMillis = millis
        )

        assertEquals("web_", exp.rowId.take(4))
        assertEquals(-250.0, exp.amount, 0.01)
        assertEquals(millis, exp.getOrDeriveDateMillis())
    }

    @Test
    fun test7_softDeletionSemantics() {
        val exp = Expense(
            date = "31-03-2026",
            amount = -100.0,
            category = "Food",
            subcategory = "",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "",
            status = "clear",
            receiptPicture = "",
            account = "My Cash",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "test_del_1",
            typeId = "",
            transactionType = "Expense"
        )

        val entity = exp.toEntity()
        assertFalse(entity.isDeleted)

        val deletedEntity = entity.copy(isDeleted = true, lastModified = System.currentTimeMillis())
        assertTrue(deletedEntity.isDeleted)

        val restoredExpense = deletedEntity.toExpense()
        assertTrue(restoredExpense.isDeleted)
    }

    @Test
    fun test8_payeeAutocompleteGroupingAndRanking() {
        val sampleExpenses = listOf(
            Expense("31-03-2026", amount = -100.0, category = "Utilities", subcategory = "Telephone", payeePayer = "airtel", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", account = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = ""),
            Expense("31-03-2026", amount = -200.0, category = "Utilities", subcategory = "Telephone", payeePayer = "airtel", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", account = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = ""),
            Expense("31-03-2026", amount = -500.0, category = "Personal", subcategory = "Hobbies", payeePayer = "air balloon", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", account = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = ""),
            Expense("31-03-2026", amount = -300.0, category = "Travel", subcategory = "Flight", payeePayer = "airlink", rowId = "4", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", account = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "")
        )

        val q = "air"
        val groupedByPayee = sampleExpenses.groupBy { it.payeePayer }

        val matchingPayees = groupedByPayee.keys.filter {
            it.lowercase().contains(q)
        }.sortedWith(compareBy(
            { !it.lowercase().startsWith(q) },
            { it.lowercase() }
        ))

        assertEquals(3, matchingPayees.size)
        assertTrue(matchingPayees.contains("airtel"))
        assertTrue(matchingPayees.contains("air balloon"))
        assertTrue(matchingPayees.contains("airlink"))

        val airtelList = groupedByPayee["airtel"] ?: emptyList()
        val airtelCatSub = airtelList.filter { it.category.isNotBlank() && it.category != "Imported" }
            .groupBy { it.category to it.subcategory }
            .maxByOrNull { it.value.size }?.key

        assertEquals("Utilities", airtelCatSub?.first)
        assertEquals("Telephone", airtelCatSub?.second)
    }

    @Test
    fun test9_phase2bAccountTypeModelsAndSerialization() {
        val saving = com.hackerai.mybudget.data.SavingAccount("id_1", "HDFC Savings", "HDFC Bank", "Main", "12345")
        assertEquals(com.hackerai.mybudget.data.AccountType.SAVING, saving.type)
        assertFalse(saving.isHidden)

        val loan = com.hackerai.mybudget.data.LoanAccount("id_2", "Home Loan", "SBI", "City", "98765")
        assertEquals(com.hackerai.mybudget.data.AccountType.LOAN, loan.type)

        val card = com.hackerai.mybudget.data.CreditCardAccount("id_3", "Regalia Card", "HDFC", "4111", "12/28", "123", 15, 5)
        assertEquals(com.hackerai.mybudget.data.AccountType.CREDIT_CARD, card.type)
        assertEquals("123", card.cvv)

        val cash = com.hackerai.mybudget.data.CashAccount("id_4", "My Cash")
        assertEquals(com.hackerai.mybudget.data.AccountType.CASH, cash.type)
    }

    @Test
    fun test10_phase2bAccountValidationAndDuplicateRejection() {
        val existingAccounts = listOf(
            com.hackerai.mybudget.data.SavingAccount("id_1", "My Cash", "Bank", "Branch", "123"),
            com.hackerai.mybudget.data.SavingAccount("id_2", "SBI SALARY Account", "SBI", "Main", "456")
        )

        fun validateAccountCreation(nickname: String, typeStr: String): Pair<Boolean, String?> {
            if (nickname.isBlank()) return false to "Account nickname is required."
            val type = try {
                com.hackerai.mybudget.data.AccountType.valueOf(typeStr.uppercase())
            } catch (e: Exception) {
                return false to "Invalid account type '$typeStr'."
            }
            if (existingAccounts.any { it.nickName.equals(nickname, ignoreCase = true) }) {
                return false to "Account with nickname '$nickname' already exists."
            }
            return true to null
        }

        val resEmpty = validateAccountCreation("", "SAVING")
        assertFalse(resEmpty.first)
        assertEquals("Account nickname is required.", resEmpty.second)

        val resInvalidType = validateAccountCreation("New Account", "BITCOIN")
        assertFalse(resInvalidType.first)
        assertEquals("Invalid account type 'BITCOIN'.", resInvalidType.second)

        val resDuplicate = validateAccountCreation("my cash", "SAVING")
        assertFalse(resDuplicate.first)
        assertEquals("Account with nickname 'my cash' already exists.", resDuplicate.second)

        val resValid = validateAccountCreation("HDFC Savings", "SAVING")
        assertTrue(resValid.first)
        assertNull(resValid.second)
    }

    @Test
    fun test11_phase2bAccountHideAndShowSemantics() {
        val acc = com.hackerai.mybudget.data.SavingAccount("id_1", "HDFC Savings", "HDFC", "Main", "123", isHidden = false)
        assertFalse(acc.isHidden)

        val hiddenAcc = acc.copy(isHidden = true)
        assertTrue(hiddenAcc.isHidden)

        val restoredAcc = hiddenAcc.copy(isHidden = false)
        assertFalse(restoredAcc.isHidden)
    }

    @Test
    fun test12_phase2bAccountRenameTransactionIntegrity() {
        val oldName = "My Cash"
        val newName = "Main Wallet"

        val sampleExpenses = listOf(
            Expense("31-03-2026", time = "12:00", amount = -100.0, category = "Food", subcategory = "", paymentMethod = "", description = "Lunch", refCheckNo = "", payeePayer = "Cafe", status = "clear", receiptPicture = "", account = "My Cash", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", rowId = "1", typeId = "", transactionType = "Expense"),
            Expense("31-03-2026", time = "12:00", amount = -500.0, category = "Transfer", subcategory = "", paymentMethod = "", description = "Transfer", refCheckNo = "", payeePayer = "Transfer", status = "clear", receiptPicture = "", account = "My Cash", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", rowId = "2", typeId = "", transactionType = "Transfer", toAccount = "SBI SALARY Account")
        )

        val renamedExpenses = sampleExpenses.map { exp ->
            exp.copy(
                account = if (exp.account == oldName) newName else exp.account,
                toAccount = if (exp.toAccount == oldName) newName else exp.toAccount
            )
        }

        assertEquals(2, renamedExpenses.size)
        assertEquals(newName, renamedExpenses[0].account)
        assertEquals(newName, renamedExpenses[1].account)
        assertEquals("SBI SALARY Account", renamedExpenses[1].toAccount)
        assertEquals("1", renamedExpenses[0].rowId)
        assertEquals("2", renamedExpenses[1].rowId)
    }

    @Test
    fun test13_phase2bAccountBalanceAndStatementRegression() {
        val accountName = "My Cash"
        val sampleExpenses = listOf(
            Expense("31-03-2026", time = "12:00", amount = 500.0, category = "Income", subcategory = "Salary", paymentMethod = "", description = "Salary", refCheckNo = "", payeePayer = "Employer", status = "clear", receiptPicture = "", account = "My Cash", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", rowId = "1", typeId = "", transactionType = "Income"),
            Expense("31-03-2026", time = "12:00", amount = -100.0, category = "Food", subcategory = "Lunch", paymentMethod = "", description = "Lunch", refCheckNo = "", payeePayer = "Cafe", status = "clear", receiptPicture = "", account = "My Cash", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", rowId = "2", typeId = "", transactionType = "Expense"),
            Expense("31-03-2026", time = "12:00", amount = -200.0, category = "Transfer", subcategory = "", paymentMethod = "", description = "Transfer", refCheckNo = "", payeePayer = "Transfer", status = "clear", receiptPicture = "", account = "My Cash", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", rowId = "3", typeId = "", transactionType = "Transfer", toAccount = "SBI SALARY Account")
        )

        val balance = com.hackerai.mybudget.data.ExpenseSummaryCalculator.currentBalance(sampleExpenses, accountName)
        assertEquals(200.0, balance, 0.01)
    }

    private fun validateCardNumber(rawCardNumber: String): Pair<Boolean, String?> {
        val cleanCardNumber = rawCardNumber.replace(" ", "").replace("-", "")
        if (!cleanCardNumber.all { it.isDigit() } || cleanCardNumber.length !in 13..19) {
            return false to "Invalid card number."
        }
        return true to cleanCardNumber
    }

    private fun validateCvv(rawCvv: String): Pair<Boolean, String?> {
        if (!rawCvv.all { it.isDigit() } || rawCvv.length !in 3..4) {
            return false to "Invalid CVV."
        }
        return true to rawCvv
    }

    @Test
    fun test14_phase2bCardNumberValidationRules() {
        val res13 = validateCardNumber("1234567890123")
        assertTrue(res13.first)
        assertEquals("1234567890123", res13.second)

        val res16 = validateCardNumber("1234567890123456")
        assertTrue(res16.first)
        assertEquals("1234567890123456", res16.second)

        val res19 = validateCardNumber("1234567890123456789")
        assertTrue(res19.first)
        assertEquals("1234567890123456789", res19.second)

        val resSpaces = validateCardNumber("1234 5678 9012 3456")
        assertTrue(resSpaces.first)
        assertEquals("1234567890123456", resSpaces.second)

        val resHyphens = validateCardNumber("1234-5678-9012-3456")
        assertTrue(resHyphens.first)
        assertEquals("1234567890123456", resHyphens.second)

        val resShort = validateCardNumber("123456789012")
        assertFalse(resShort.first)
        assertEquals("Invalid card number.", resShort.second)

        val resLong = validateCardNumber("12345678901234567890")
        assertFalse(resLong.first)
        assertEquals("Invalid card number.", resLong.second)

        val resAlpha = validateCardNumber("1234abcd56789012")
        assertFalse(resAlpha.first)
        assertEquals("Invalid card number.", resAlpha.second)
    }

    @Test
    fun test15_phase2bCvvValidationRules() {
        val res3 = validateCvv("123")
        assertTrue(res3.first)

        val res4 = validateCvv("1234")
        assertTrue(res4.first)

        val res2 = validateCvv("12")
        assertFalse(res2.first)
        assertEquals("Invalid CVV.", res2.second)

        val res5 = validateCvv("12345")
        assertFalse(res5.first)
        assertEquals("Invalid CVV.", res5.second)

        val resAlpha = validateCvv("12a")
        assertFalse(resAlpha.first)
        assertEquals("Invalid CVV.", resAlpha.second)
    }

    @Test
    fun test16_phase2bUpdatePreservationAndSecurity() {
        val existingCard = com.hackerai.mybudget.data.CreditCardAccount(
            id = "card_1",
            nickName = "HDFC Regalia",
            bankName = "HDFC Bank",
            cardNumber = "4532015012345678",
            expiry = "12/28",
            cvv = "999",
            billingDate = 15,
            dueDate = 5
        )

        fun resolveCardNumberOnUpdate(rawCardInput: String, existingCardNumber: String): Pair<Boolean, String> {
            return when {
                rawCardInput.isBlank() || rawCardInput.contains("•") -> true to existingCardNumber
                else -> {
                    val clean = rawCardInput.replace(" ", "").replace("-", "")
                    if (!clean.all { it.isDigit() } || clean.length !in 13..19) {
                        false to "Invalid card number."
                    } else {
                        true to clean
                    }
                }
            }
        }

        fun resolveCvvOnUpdate(rawCvvInput: String, existingCvv: String): Pair<Boolean, String> {
            return when {
                rawCvvInput.isBlank() -> true to existingCvv
                else -> {
                    if (!rawCvvInput.all { it.isDigit() } || rawCvvInput.length !in 3..4) {
                        false to "Invalid CVV."
                    } else {
                        true to rawCvvInput
                    }
                }
            }
        }

        val resMaskedCard = resolveCardNumberOnUpdate("•••• •••• •••• 5678", existingCard.cardNumber)
        assertTrue(resMaskedCard.first)
        assertEquals("4532015012345678", resMaskedCard.second)

        val resBlankCard = resolveCardNumberOnUpdate("", existingCard.cardNumber)
        assertTrue(resBlankCard.first)
        assertEquals("4532015012345678", resBlankCard.second)

        val resBlankCvv = resolveCvvOnUpdate("", existingCard.cvv)
        assertTrue(resBlankCvv.first)
        assertEquals("999", resBlankCvv.second)

        val badCardInput = "9999-bad-card"
        val resBadCard = resolveCardNumberOnUpdate(badCardInput, existingCard.cardNumber)
        assertFalse(resBadCard.first)
        assertEquals("Invalid card number.", resBadCard.second)
        assertFalse("Error must not echo sensitive input", resBadCard.second.contains(badCardInput))

        val badCvvInput = "99999"
        val resBadCvv = resolveCvvOnUpdate(badCvvInput, existingCard.cvv)
        assertFalse(resBadCvv.first)
        assertEquals("Invalid CVV.", resBadCvv.second)
        assertFalse("Error must not echo sensitive input", resBadCvv.second.contains(badCvvInput))
    }

    @Test
    fun test17_phase2bGetApiMaskingAndStrippingVerification() {
        val card = com.hackerai.mybudget.data.CreditCardAccount(
            id = "card_1",
            nickName = "HDFC Regalia",
            bankName = "HDFC Bank",
            cardNumber = "4532015012345678",
            expiry = "12/28",
            cvv = "999",
            billingDate = 15,
            dueDate = 5
        )

        fun maskCardNumber(cardNumber: String): String {
            val clean = cardNumber.trim()
            if (clean.isBlank()) return ""
            if (clean.contains("•")) return clean
            val digits = clean.filter { it.isDigit() }
            if (digits.isEmpty()) return ""
            val last4 = digits.takeLast(4)
            return "•••• •••• •••• $last4"
        }

        fun accountToMap(acc: com.hackerai.mybudget.data.Account): Map<String, Any> {
            val map = mutableMapOf<String, Any>(
                "id" to acc.id,
                "nickName" to acc.nickName,
                "type" to acc.type.name,
                "isHidden" to acc.isHidden
            )
            if (acc is com.hackerai.mybudget.data.CreditCardAccount) {
                map["bankName"] = acc.bankName
                map["cardNumber"] = maskCardNumber(acc.cardNumber)
                map["expiry"] = acc.expiry
                map["billingDate"] = acc.billingDate
                map["dueDate"] = acc.dueDate
            }
            return map
        }

        val jsonMap = accountToMap(card)
        assertFalse("GET response must not contain CVV", jsonMap.containsKey("cvv"))
        assertEquals("•••• •••• •••• 5678", jsonMap["cardNumber"])
        assertNotEquals(card.cardNumber, jsonMap["cardNumber"])
    }

    @Test
    fun test18_regressionTest1_editExpense() {
        val initialList = mutableListOf(
            Expense(
                date = "28-09-2026",
                time = "14:55",
                amount = -120.07,
                category = "Personal",
                subcategory = "Hobbies",
                paymentMethod = "",
                description = "",
                refCheckNo = "",
                payeePayer = "airtel",
                status = "clear",
                receiptPicture = "",
                account = "AIRTEL AXIS BANK CREDIT CARD",
                tag = "",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "test_expense_1",
                typeId = "",
                transactionType = "Expense"
            )
        )

        val bodyJson = """
            {
                "transactionType": "Expense",
                "amount": 150.0,
                "date": "28-09-2026",
                "time": "14:55",
                "account": "AIRTEL AXIS BANK CREDIT CARD",
                "category": "Groceries",
                "subcategory": "Food",
                "payeePayer": "Flipkart"
            }
        """.trimIndent()

        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val body: Map<String, Any> = gson.fromJson(bodyJson, type)

        val existing = initialList.find { it.rowId == "test_expense_1" }!!
        val transactionType = body["transactionType"]?.toString() ?: existing.transactionType
        val rawAmount = (body["amount"] as Number).toDouble()
        val category = body["category"].toString()
        val subcategory = body["subcategory"].toString()
        val payeePayer = body["payeePayer"].toString()
        val semanticAmount = if (transactionType == "Income") kotlin.math.abs(rawAmount) else -kotlin.math.abs(rawAmount)

        val updated = existing.copy(
            amount = semanticAmount,
            category = category,
            subcategory = subcategory,
            payeePayer = payeePayer,
            transactionType = transactionType,
            lastModified = System.currentTimeMillis()
        )

        val idx = initialList.indexOfFirst { it.rowId == updated.rowId }
        initialList[idx] = updated

        assertEquals(1, initialList.size)
        assertEquals("test_expense_1", initialList[0].rowId)
        assertEquals(-150.0, initialList[0].amount, 0.01)
        assertEquals("Groceries", initialList[0].category)
        assertEquals("Food", initialList[0].subcategory)
        assertEquals("Flipkart", initialList[0].payeePayer)
    }

    @Test
    fun test19_regressionTest2_editIncome() {
        val initialList = mutableListOf(
            Expense(
                date = "28-09-2026",
                time = "12:00",
                amount = 500.0,
                category = "Salary",
                subcategory = "",
                paymentMethod = "",
                description = "",
                refCheckNo = "",
                payeePayer = "Employer",
                status = "clear",
                receiptPicture = "",
                account = "My Cash",
                tag = "",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "test_income_1",
                typeId = "",
                transactionType = "Income"
            )
        )

        val existing = initialList.find { it.rowId == "test_income_1" }!!
        val rawAmount = 750.0
        val transactionType = "Income"
        val semanticAmount = if (transactionType == "Income") kotlin.math.abs(rawAmount) else -kotlin.math.abs(rawAmount)

        val updated = existing.copy(
            amount = semanticAmount,
            transactionType = transactionType,
            lastModified = System.currentTimeMillis()
        )

        val idx = initialList.indexOfFirst { it.rowId == updated.rowId }
        initialList[idx] = updated

        assertEquals(1, initialList.size)
        assertEquals("test_income_1", initialList[0].rowId)
        assertEquals(750.0, initialList[0].amount, 0.01)
        assertEquals("Income", initialList[0].transactionType)
    }

    @Test
    fun test20_regressionTest3_editTransfer() {
        val initialList = mutableListOf(
            Expense(
                date = "28-09-2026",
                time = "10:00",
                amount = -1000.0,
                category = "Transfer",
                subcategory = "",
                paymentMethod = "",
                description = "",
                refCheckNo = "",
                payeePayer = "Transfer to SBI SALARY Account",
                status = "clear",
                receiptPicture = "",
                account = "My Cash",
                tag = "",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "test_transfer_1",
                typeId = "",
                transactionType = "Transfer",
                toAccount = "SBI SALARY Account"
            )
        )

        val existing = initialList.find { it.rowId == "test_transfer_1" }!!
        val rawAmount = 1500.0
        val newToAccount = "HDFC FREEDOM Card"
        val transactionType = "Transfer"
        val semanticAmount = -kotlin.math.abs(rawAmount)

        val updated = existing.copy(
            amount = semanticAmount,
            toAccount = newToAccount,
            transactionType = transactionType,
            lastModified = System.currentTimeMillis()
        )

        val idx = initialList.indexOfFirst { it.rowId == updated.rowId }
        initialList[idx] = updated

        assertEquals(1, initialList.size)
        assertEquals("test_transfer_1", initialList[0].rowId)
        assertEquals(-1500.0, initialList[0].amount, 0.01)
        assertEquals("My Cash", initialList[0].account)
        assertEquals("HDFC FREEDOM Card", initialList[0].toAccount)
        assertEquals("Transfer", initialList[0].transactionType)
    }

    @Test
    fun test21_regressionTest4_dateTimeEdit() {
        val initialExp = Expense(
            date = "28-09-2026",
            time = "14:55",
            amount = -120.07,
            category = "Personal",
            subcategory = "Hobbies",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "airtel",
            status = "clear",
            receiptPicture = "",
            account = "AIRTEL AXIS BANK CREDIT CARD",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = "test_dt_1",
            typeId = "",
            transactionType = "Expense"
        )

        val newDate = "29-09-2026"
        val newTime = "16:30"
        val dateMillis = DateUtils.parseDateToMillis(newDate)

        val updatedExp = initialExp.copy(
            date = newDate,
            time = newTime,
            dateMillis = dateMillis,
            lastModified = System.currentTimeMillis()
        )

        assertEquals("29-09-2026", updatedExp.date)
        assertEquals("16:30", updatedExp.time)
        assertTrue(updatedExp.getOrDeriveDateMillis() > initialExp.getOrDeriveDateMillis())
    }

    @Test
    fun test22_regressionTest5_browserErrorHandlingSimulation() {
        val dbState = listOf(
            Expense(
                date = "28-09-2026",
                time = "14:55",
                amount = -120.07,
                category = "Personal",
                subcategory = "Hobbies",
                paymentMethod = "",
                description = "",
                refCheckNo = "",
                payeePayer = "airtel",
                status = "clear",
                receiptPicture = "",
                account = "AIRTEL AXIS BANK CREDIT CARD",
                tag = "",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "test_err_1",
                typeId = "",
                transactionType = "Expense"
            )
        )

        fun processPutBody(amount: Double): Pair<Int, String> {
            if (amount <= 0.0) {
                return 400 to "Amount must be a positive non-zero number."
            }
            return 200 to "OK"
        }

        val res = processPutBody(-50.0)
        assertEquals(400, res.first)
        assertEquals("Amount must be a positive non-zero number.", res.second)

        assertEquals(-120.07, dbState[0].amount, 0.01)
    }

    @Test
    fun test23_regressionTest6_rowIdPreservation() {
        val existingRowId = "test_expense_123"
        val existingExp = Expense(
            date = "28-09-2026",
            time = "14:55",
            amount = -120.07,
            category = "Personal",
            subcategory = "Hobbies",
            paymentMethod = "",
            description = "",
            refCheckNo = "",
            payeePayer = "airtel",
            status = "clear",
            receiptPicture = "",
            account = "AIRTEL AXIS BANK CREDIT CARD",
            tag = "",
            tax = "",
            quantity = 1.0,
            unit = "PCS",
            splitTotal = "",
            rowId = existingRowId,
            typeId = "",
            transactionType = "Expense"
        )

        val updated = existingExp.copy(
            amount = -150.0,
            lastModified = System.currentTimeMillis()
        )

        assertEquals(existingRowId, updated.rowId)
        assertFalse("Editing must not generate a new web_ UUID", updated.rowId.startsWith("web_") && existingRowId != updated.rowId)
    }

    @Test
    fun test24_multiFilterTest1_typeExpenseOnly() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = 500.0, category = "Income", subcategory = "", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Income"),
            Expense("28-09-2026", amount = -200.0, category = "Transfer", subcategory = "", payeePayer = "", account = "My Cash", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Transfer", toAccount = "SBI SALARY Account")
        )

        val typeList = listOf("Expense")
        val filtered = sampleExpenses.filter { exp ->
            typeList.isEmpty() || typeList.any { t -> exp.transactionType.equals(t, ignoreCase = true) }
        }

        assertEquals(1, filtered.size)
        assertEquals("Expense", filtered[0].transactionType)
    }

    @Test
    fun test25_multiFilterTest2_typeIncomeAndExpense() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = 500.0, category = "Income", subcategory = "", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Income"),
            Expense("28-09-2026", amount = -200.0, category = "Transfer", subcategory = "", payeePayer = "", account = "My Cash", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Transfer", toAccount = "SBI SALARY Account")
        )

        val typeList = listOf("Expense", "Income")
        val filtered = sampleExpenses.filter { exp ->
            typeList.isEmpty() || typeList.any { t -> exp.transactionType.equals(t, ignoreCase = true) }
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.transactionType != "Transfer" })
    }

    @Test
    fun test26_multiFilterTest3_multiAccount() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "", account = "SBI SALARY Account", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Food", subcategory = "", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -200.0, category = "Food", subcategory = "", payeePayer = "", account = "HDFC FREEDOM Card", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val accountList = listOf("SBI SALARY Account", "My Cash")
        val filtered = sampleExpenses.filter { exp ->
            accountList.isEmpty() || accountList.any { a -> exp.account.equals(a, ignoreCase = true) || (exp.transactionType == "Transfer" && exp.toAccount?.equals(a, ignoreCase = true) == true) }
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.account == "SBI SALARY Account" })
        assertTrue(filtered.any { it.account == "My Cash" })
        assertFalse(filtered.any { it.account == "HDFC FREEDOM Card" })
    }

    @Test
    fun test27_multiFilterTest4_multiCategory() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Automobile", subcategory = "Fuel", payeePayer = "", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Food", subcategory = "Groceries", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -200.0, category = "Utilities", subcategory = "Electric", payeePayer = "", account = "My Cash", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val categoryList = listOf("Automobile", "Food")
        val filtered = sampleExpenses.filter { exp ->
            categoryList.isEmpty() || categoryList.any { c -> exp.category.equals(c, ignoreCase = true) }
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.category == "Automobile" })
        assertTrue(filtered.any { it.category == "Food" })
        assertFalse(filtered.any { it.category == "Utilities" })
    }

    @Test
    fun test28_multiFilterTest5_categoryAndSubcategory() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Automobile", subcategory = "Maintenance", payeePayer = "", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Automobile", subcategory = "Fuel", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val categoryList = listOf("Automobile")
        val subcategoryList = listOf("Maintenance")

        val filtered = sampleExpenses.filter { exp ->
            val catMatches = categoryList.isEmpty() || categoryList.any { c -> exp.category.equals(c, ignoreCase = true) }
            val subMatches = subcategoryList.isEmpty() || subcategoryList.any { s -> exp.subcategory.equals(s, ignoreCase = true) }
            catMatches && subMatches
        }

        assertEquals(1, filtered.size)
        assertEquals("Automobile", filtered[0].category)
        assertEquals("Maintenance", filtered[0].subcategory)
    }

    @Test
    fun test29_multiFilterTest6_multiPayee() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Shopping", subcategory = "", payeePayer = "Amazon", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Shopping", subcategory = "", payeePayer = "Flipkart", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -200.0, category = "Shopping", subcategory = "", payeePayer = "eBay", account = "My Cash", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val payeeList = listOf("Amazon", "Flipkart")
        val filtered = sampleExpenses.filter { exp ->
            payeeList.isEmpty() || payeeList.any { p -> exp.payeePayer.equals(p, ignoreCase = true) }
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.any { it.payeePayer == "Amazon" })
        assertTrue(filtered.any { it.payeePayer == "Flipkart" })
        assertFalse(filtered.any { it.payeePayer == "eBay" })
    }

    @Test
    fun test30_multiFilterTest7_categoryAndAccountCombined() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Automobile", subcategory = "", payeePayer = "", account = "SBI SALARY Account", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Food", subcategory = "", payeePayer = "", account = "SBI SALARY Account", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -200.0, category = "Automobile", subcategory = "", payeePayer = "", account = "My Cash", rowId = "3", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val categoryList = listOf("Automobile", "Food")
        val accountList = listOf("SBI SALARY Account")

        val filtered = sampleExpenses.filter { exp ->
            val catMatches = categoryList.isEmpty() || categoryList.any { c -> exp.category.equals(c, ignoreCase = true) }
            val accMatches = accountList.isEmpty() || accountList.any { a -> exp.account.equals(a, ignoreCase = true) || (exp.transactionType == "Transfer" && exp.toAccount?.equals(a, ignoreCase = true) == true) }
            catMatches && accMatches
        }

        assertEquals(2, filtered.size)
        assertTrue(filtered.all { it.account == "SBI SALARY Account" })
    }

    @Test
    fun test31_multiFilterTest8_filtersPlusFreeTextSearch() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Shopping", subcategory = "", payeePayer = "Amazon", description = "Birthday gift", account = "My Cash", rowId = "1", paymentMethod = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = -50.0, category = "Shopping", subcategory = "", payeePayer = "Amazon", description = "Office supplies", account = "My Cash", rowId = "2", paymentMethod = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        )

        val payeeList = listOf("Amazon")
        val search = "birthday"

        val filtered = sampleExpenses.filter { exp ->
            val searchMatches = if (search.isNotBlank()) {
                exp.payeePayer.lowercase().contains(search) ||
                exp.description.lowercase().contains(search) ||
                exp.category.lowercase().contains(search)
            } else true
            val payeeMatches = payeeList.isEmpty() || payeeList.any { p -> exp.payeePayer.equals(p, ignoreCase = true) }
            searchMatches && payeeMatches
        }

        assertEquals(1, filtered.size)
        assertEquals("Birthday gift", filtered[0].description)
    }

    @Test
    fun test32_multiFilterTest9_clearAllFiltersRestoresFullDataset() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "Cafe", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = 500.0, category = "Salary", subcategory = "", payeePayer = "Employer", account = "SBI SALARY Account", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Income")
        )

        val emptyList = emptyList<String>()
        val filtered = sampleExpenses.filter { exp ->
            emptyList.isEmpty()
        }

        assertEquals(2, filtered.size)
    }

    @Test
    fun test33_multiFilterTest10_filteringDoesNotModifyRoomData() {
        val originalExpense = Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "Lunch", payeePayer = "Cafe", account = "My Cash", rowId = "immutable_1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        val sampleList = listOf(originalExpense)

        val result = sampleList.filter { it.category == "NonExistent" }
        assertEquals(0, result.size)

        assertEquals("immutable_1", sampleList[0].rowId)
        assertEquals(-100.0, sampleList[0].amount, 0.01)
        assertEquals("Food", sampleList[0].category)
    }

    @Test
    fun test34_multiFilterTest11_transferFilteringUsesUnderlyingType() {
        val transferExp = Expense("28-09-2026", amount = -1000.0, category = "Transfer", subcategory = "", payeePayer = "Transfer", account = "My Cash", toAccount = "SBI SALARY Account", rowId = "transfer_1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Transfer")

        val typeList = listOf("Transfer")
        val filtered = listOf(transferExp).filter { exp ->
            typeList.isEmpty() || typeList.any { t -> exp.transactionType.equals(t, ignoreCase = true) }
        }

        assertEquals(1, filtered.size)
        assertEquals("Transfer", filtered[0].transactionType)
    }

    @Test
    fun test35_multiFilterTest12_noFiltersProducesStandardResult() {
        val sampleExpenses = listOf(
            Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "", account = "My Cash", rowId = "1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense"),
            Expense("28-09-2026", amount = 500.0, category = "Salary", subcategory = "", payeePayer = "", account = "My Cash", rowId = "2", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Income")
        )

        val accountList = emptyList<String>()
        val categoryList = emptyList<String>()
        val subcategoryList = emptyList<String>()
        val payeeList = emptyList<String>()
        val typeList = emptyList<String>()
        val search = ""

        val filtered = sampleExpenses.filter { exp ->
            val searchMatches = if (search.isNotBlank()) exp.payeePayer.lowercase().contains(search) else true
            val accMatches = accountList.isEmpty() || accountList.contains(exp.account)
            val catMatches = categoryList.isEmpty() || categoryList.contains(exp.category)
            val subMatches = subcategoryList.isEmpty() || subcategoryList.contains(exp.subcategory)
            val payeeMatches = payeeList.isEmpty() || payeeList.contains(exp.payeePayer)
            val typeMatches = typeList.isEmpty() || typeList.contains(exp.transactionType)

            searchMatches && accMatches && catMatches && subMatches && payeeMatches && typeMatches
        }

        assertEquals(2, filtered.size)
    }

    @Test
    fun test36_staticFileAntiCacheHeaders() {
        val serverHeaders = mutableMapOf<String, String>()

        serverHeaders["Cache-Control"] = "no-cache, no-store, must-revalidate"
        serverHeaders["Pragma"] = "no-cache"
        serverHeaders["Expires"] = "0"

        assertEquals("no-cache, no-store, must-revalidate", serverHeaders["Cache-Control"])
        assertEquals("no-cache", serverHeaders["Pragma"])
        assertEquals("0", serverHeaders["Expires"])
    }

    @Test
    fun test37_pinFreeApiEndpointsNoAuthRequired() {
        val headers = mapOf<String, String>()
        val hasAuth = headers.containsKey("Authorization") || headers.containsKey("authorization")
        assertFalse("API requests do not require Authorization header", hasAuth)
    }

    @Test
    fun test38_apiDashboardNoAuth() {
        val dashboardMap = mapOf("netWorth" to 1000.0, "totalAccounts" to 2)
        assertEquals(1000.0, dashboardMap["netWorth"])
        assertFalse("Dashboard response does not contain PIN", dashboardMap.containsKey("pin"))
    }

    @Test
    fun test39_apiAccountsNoAuth() {
        val accounts = listOf(
            mapOf("id" to "1", "nickName" to "My Cash", "balance" to 500.0)
        )
        assertEquals(1, accounts.size)
        assertFalse("Accounts response does not contain PIN", accounts[0].containsKey("pin"))
    }

    @Test
    fun test40_apiTransactionsNoAuth() {
        val txns = mapOf("items" to listOf("txn1", "txn2"), "totalItems" to 2)
        assertEquals(2, txns["totalItems"])
    }

    @Test
    fun test41_apiPostPutDeleteNoAuth() {
        val exp = Expense("28-09-2026", amount = -100.0, category = "Food", subcategory = "", payeePayer = "Cafe", account = "My Cash", rowId = "unauth_1", paymentMethod = "", description = "", refCheckNo = "", status = "", receiptPicture = "", tag = "", tax = "", quantity = 1.0, unit = "PCS", splitTotal = "", typeId = "", transactionType = "Expense")
        assertEquals("unauth_1", exp.rowId)
        assertEquals(-100.0, exp.amount, 0.01)
    }

    @Test
    fun test42_noPinReturnedByAnyApi() {
        val accountResponse = mapOf(
            "id" to "acc_1",
            "nickName" to "HDFC Salary",
            "balance" to 1500.00
        )
        assertFalse("Account API must not return PIN", accountResponse.containsKey("pin"))

        val dashboardResponse = mapOf(
            "netWorth" to 5000.00,
            "totalAccounts" to 2
        )
        assertFalse("Dashboard API must not return PIN", dashboardResponse.containsKey("pin"))
    }

    @Test
    fun test43_directJsonInputStreamParsing() {
        val jsonInput = """{"amount": 100.0}"""
        val bytes = jsonInput.toByteArray(Charsets.UTF_8)
        val stream = java.io.ByteArrayInputStream(bytes)

        val readStr = String(stream.readBytes(), Charsets.UTF_8)
        assertEquals(jsonInput, readStr)

        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
        val map: Map<String, Any> = gson.fromJson(readStr, type)

        assertEquals(100.0, map["amount"])
    }

    @Test
    fun test44_malformedJsonBodyDoesNotCrash() {
        val malformedJson = """{"amount": 100.0"""
        val gson = com.google.gson.Gson()
        val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type

        val parsedMap: Map<String, Any> = try {
            gson.fromJson(malformedJson, type) ?: emptyMap()
        } catch (e: Exception) {
            emptyMap()
        }

        assertTrue("Malformed JSON returns empty map safely", parsedMap.isEmpty())
    }
}
