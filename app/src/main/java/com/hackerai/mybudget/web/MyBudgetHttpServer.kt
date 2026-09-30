package com.hackerai.mybudget.web

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.hackerai.mybudget.MyBudgetApplication
import com.hackerai.mybudget.data.DateUtils
import com.hackerai.mybudget.data.Expense
import com.hackerai.mybudget.data.ExpenseSummaryCalculator
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import java.io.InputStream
import java.net.URLDecoder
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

class MyBudgetHttpServer(
    private val context: Context,
    port: Int = 8080
) : NanoHTTPD(port) {

    private val gson = Gson()

    private val repository = MyBudgetApplication.instance.expenseRepository
    private val accountRepository = MyBudgetApplication.instance.accountRepository

    override fun serve(session: IHTTPSession): Response {
        return try {
            val uri = session.uri
            val method = session.method

            if (uri.startsWith("/api/")) {
                handleApi(session, uri, method)
            } else {
                handleStaticFile(uri)
            }
        } catch (e: Exception) {
            Log.e("MyBudgetHttpServer", "Error serving HTTP request", e)
            newJsonResponse(Response.Status.INTERNAL_ERROR, mapOf("error" to (e.message ?: "Internal Server Error")))
        }
    }

    private fun handleApi(session: IHTTPSession, uri: String, method: Method): Response {
        return when {
            uri == "/api/dashboard" && method == Method.GET -> {
                handleDashboard()
            }
            uri == "/api/accounts" -> {
                when (method) {
                    Method.GET -> handleAccounts()
                    Method.POST -> handleCreateAccount(session)
                    else -> newJsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error" to "Method not allowed"))
                }
            }
            uri.startsWith("/api/accounts/") -> {
                val pathStr = uri.removePrefix("/api/accounts/")
                when {
                    pathStr.endsWith("/statement") && method == Method.GET -> {
                        val accountName = URLDecoder.decode(pathStr.removeSuffix("/statement"), "UTF-8")
                        handleAccountStatement(accountName)
                    }
                    (pathStr.endsWith("/toggle-visibility") || pathStr.endsWith("/toggle")) && (method == Method.POST || method == Method.PUT) -> {
                        val rawName = pathStr.removeSuffix("/toggle-visibility").removeSuffix("/toggle")
                        val accountName = URLDecoder.decode(rawName, "UTF-8")
                        handleToggleAccountVisibility(accountName)
                    }
                    else -> {
                        val accountName = URLDecoder.decode(pathStr, "UTF-8")
                        when (method) {
                            Method.GET -> handleGetSingleAccount(accountName)
                            Method.PUT -> handleUpdateAccount(accountName, session)
                            Method.DELETE -> handleDeleteAccount(accountName)
                            else -> newJsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error" to "Method not allowed"))
                        }
                    }
                }
            }
            uri == "/api/transactions" -> {
                when (method) {
                    Method.GET -> handleTransactions(session.parms)
                    Method.POST -> handleCreateTransaction(session)
                    else -> newJsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error" to "Method not allowed"))
                }
            }
            uri.startsWith("/api/transactions/") -> {
                val pathStr = uri.removePrefix("/api/transactions/")
                if (pathStr.endsWith("/approve")) {
                    val rawRowId = pathStr.removeSuffix("/approve")
                    val rowId = URLDecoder.decode(rawRowId, "UTF-8")
                    if (method == Method.POST) handleApproveTransaction(rowId)
                    else newJsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error" to "Method not allowed"))
                } else {
                    val rowId = URLDecoder.decode(pathStr, "UTF-8")
                    when (method) {
                        Method.GET -> handleGetSingleTransaction(rowId)
                        Method.PUT -> handleUpdateTransaction(rowId, session)
                        Method.DELETE -> handleDeleteTransaction(rowId)
                        else -> newJsonResponse(Response.Status.METHOD_NOT_ALLOWED, mapOf("error" to "Method not allowed"))
                    }
                }
            }
            uri == "/api/categories" && method == Method.GET -> {
                handleCategories()
            }
            uri == "/api/payees" && method == Method.GET -> {
                handlePayees()
            }
            uri.startsWith("/api/autocomplete/payees") && method == Method.GET -> {
                val query = session.parms["q"] ?: ""
                handlePayeeAutocomplete(query)
            }
            else -> {
                newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "API endpoint not found"))
            }
        }
    }

    private fun handlePayeeAutocomplete(query: String): Response = runBlocking {
        val q = query.trim().lowercase()
        if (q.isBlank()) {
            return@runBlocking newJsonResponse(
                Response.Status.OK,
                mapOf("query" to query, "suggestions" to emptyList<Map<String, String>>())
            )
        }

        val allExpenses = repository.loadExpenses().filter {
            !it.isDeleted && it.status != "system" && it.payeePayer.isNotBlank()
        }

        val groupedByPayee = allExpenses.groupBy { it.payeePayer }

        val matchingPayees = groupedByPayee.keys.filter {
            it.lowercase().contains(q)
        }.sortedWith(compareBy(
            { !it.lowercase().startsWith(q) },
            { it.lowercase() }
        ))

        val maxSuggestions = 10
        val suggestions = matchingPayees.take(maxSuggestions).map { payee ->
            val list = groupedByPayee[payee] ?: emptyList()
            val mostFrequentCatSub = list.filter { it.category.isNotBlank() && it.category != "Imported" }
                .groupBy { it.category to it.subcategory }
                .maxByOrNull { it.value.size }?.key

            mapOf(
                "payeePayer" to payee,
                "category" to (mostFrequentCatSub?.first ?: ""),
                "subcategory" to (mostFrequentCatSub?.second ?: "")
            )
        }

        newJsonResponse(Response.Status.OK, mapOf("query" to query, "suggestions" to suggestions))
    }

    private fun handleCreateTransaction(session: IHTTPSession): Response = runBlocking {
        val body = parseJsonBody(session)
        val transactionType = body["transactionType"]?.toString() ?: "Expense"
        if (transactionType != "Expense" && transactionType != "Income" && transactionType != "Transfer") {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid transactionType."))
        }

        val rawAmount = (body["amount"] as? Number)?.toDouble() ?: (body["amount"]?.toString()?.toDoubleOrNull()) ?: 0.0
        if (rawAmount <= 0.0 || rawAmount.isNaN() || rawAmount.isInfinite()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Amount must be a positive non-zero number."))
        }

        val date = body["date"]?.toString()?.trim() ?: ""
        val dateMillis = DateUtils.parseDateToMillis(date)
        if (dateMillis <= 0L) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid date format. Expected dd-MM-yyyy or similar."))
        }

        val account = body["account"]?.toString()?.trim() ?: ""
        if (account.isBlank()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Account name is required."))
        }

        val toAccount = body["toAccount"]?.toString()?.trim()
        if (transactionType == "Transfer") {
            if (toAccount.isNullOrBlank()) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Destination 'To Account' is required for Transfer."))
            }
            if (toAccount == account) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "From Account and To Account must be different."))
            }
        }

        val time = body["time"]?.toString()?.trim() ?: "12:00"
        val rawCategory = body["category"]?.toString()?.trim() ?: ""
        val category = if (transactionType == "Transfer" && (rawCategory.isBlank() || rawCategory == "Uncategorized")) "Transfer" else rawCategory.ifBlank { "Uncategorized" }
        val subcategory = body["subcategory"]?.toString()?.trim() ?: ""
        val paymentMethod = body["paymentMethod"]?.toString()?.trim() ?: ""
        val description = body["description"]?.toString()?.trim() ?: ""
        val refCheckNo = body["refCheckNo"]?.toString()?.trim() ?: ""
        val rawPayeePayer = body["payeePayer"]?.toString()?.trim() ?: ""
        val payeePayer = if (transactionType == "Transfer" && rawPayeePayer.isBlank()) "Transfer to $toAccount" else rawPayeePayer
        val tag = body["tag"]?.toString()?.trim() ?: ""
        val tax = body["tax"]?.toString()?.trim() ?: ""
        val quantity = (body["quantity"] as? Number)?.toDouble() ?: (body["quantity"]?.toString()?.toDoubleOrNull()) ?: 1.0
        val unit = body["unit"]?.toString()?.trim() ?: "PCS"
        val splitTotal = body["splitTotal"]?.toString()?.trim() ?: ""

        val semanticAmount = if (transactionType == "Income") kotlin.math.abs(rawAmount) else -kotlin.math.abs(rawAmount)
        val rowId = "web_${UUID.randomUUID()}"

        val newExpense = Expense(
            date = date,
            time = time,
            amount = semanticAmount,
            category = category,
            subcategory = subcategory,
            paymentMethod = paymentMethod,
            description = description,
            refCheckNo = refCheckNo,
            payeePayer = payeePayer,
            status = "clear",
            receiptPicture = "",
            account = account,
            tag = tag,
            tax = tax,
            quantity = quantity,
            unit = unit,
            splitTotal = splitTotal,
            rowId = rowId,
            typeId = "",
            transactionType = transactionType,
            toAccount = if (transactionType == "Transfer") toAccount else null,
            dateMillis = dateMillis,
            lastModified = System.currentTimeMillis(),
            isDeleted = false
        )

        repository.saveExpense(newExpense)
        newJsonResponse(Response.Status.CREATED, newExpense)
    }

    private fun handleGetSingleTransaction(rowId: String): Response = runBlocking {
        val allExpenses = repository.loadExpenses() + repository.loadPendingReviewExpenses()
        val found = allExpenses.find { it.rowId == rowId && !it.isDeleted }
        if (found != null) {
            newJsonResponse(Response.Status.OK, found)
        } else {
            newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Transaction not found ($rowId)"))
        }
    }

    private fun handleUpdateTransaction(rowId: String, session: IHTTPSession): Response = runBlocking {
        val allExpenses = repository.loadExpenses() + repository.loadPendingReviewExpenses()
        val existing = allExpenses.find { it.rowId == rowId && !it.isDeleted }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Transaction not found ($rowId)"))

        val body = parseJsonBody(session)
        val transactionType = body["transactionType"]?.toString() ?: existing.transactionType
        if (transactionType != "Expense" && transactionType != "Income" && transactionType != "Transfer") {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid transactionType."))
        }

        val rawAmount = (body["amount"] as? Number)?.toDouble()
            ?: (body["amount"]?.toString()?.toDoubleOrNull())
            ?: kotlin.math.abs(existing.amount)
        if (rawAmount <= 0.0 || rawAmount.isNaN() || rawAmount.isInfinite()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Amount must be a positive non-zero number."))
        }

        val date = body["date"]?.toString()?.trim() ?: existing.date
        val dateMillis = DateUtils.parseDateToMillis(date)
        if (dateMillis <= 0L) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid date format."))
        }

        val account = body["account"]?.toString()?.trim() ?: existing.account
        if (account.isBlank()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Account name is required."))
        }

        val toAccount = if (body.containsKey("toAccount")) {
            body["toAccount"]?.toString()?.trim()?.ifBlank { null }
        } else {
            existing.toAccount
        }

        if (transactionType == "Transfer") {
            if (toAccount.isNullOrBlank()) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Destination 'To Account' is required for Transfer."))
            }
            if (toAccount == account) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "From Account and To Account must be different."))
            }
        }

        val time = if (body.containsKey("time")) (body["time"]?.toString()?.trim() ?: "") else existing.time
        val rawCategory = if (body.containsKey("category")) (body["category"]?.toString()?.trim() ?: "") else existing.category
        val category = if (transactionType == "Transfer" && (rawCategory.isBlank() || rawCategory == "Uncategorized")) "Transfer" else rawCategory.ifBlank { "Uncategorized" }
        val subcategory = if (body.containsKey("subcategory")) (body["subcategory"]?.toString()?.trim() ?: "") else existing.subcategory
        val paymentMethod = if (body.containsKey("paymentMethod")) (body["paymentMethod"]?.toString()?.trim() ?: "") else existing.paymentMethod
        val description = if (body.containsKey("description")) (body["description"]?.toString()?.trim() ?: "") else existing.description
        val refCheckNo = if (body.containsKey("refCheckNo")) (body["refCheckNo"]?.toString()?.trim() ?: "") else existing.refCheckNo
        val rawPayeePayer = if (body.containsKey("payeePayer")) (body["payeePayer"]?.toString()?.trim() ?: "") else existing.payeePayer
        val payeePayer = if (transactionType == "Transfer" && rawPayeePayer.isBlank()) "Transfer to $toAccount" else rawPayeePayer
        val tag = if (body.containsKey("tag")) (body["tag"]?.toString()?.trim() ?: "") else existing.tag
        val tax = if (body.containsKey("tax")) (body["tax"]?.toString()?.trim() ?: "") else existing.tax
        val quantity = (body["quantity"] as? Number)?.toDouble() ?: (body["quantity"]?.toString()?.toDoubleOrNull()) ?: existing.quantity
        val unit = if (body.containsKey("unit")) (body["unit"]?.toString()?.trim() ?: "") else existing.unit
        val splitTotal = if (body.containsKey("splitTotal")) (body["splitTotal"]?.toString()?.trim() ?: "") else existing.splitTotal

        val semanticAmount = if (transactionType == "Income") kotlin.math.abs(rawAmount) else -kotlin.math.abs(rawAmount)

        val updatedExpense = existing.copy(
            date = date,
            time = time,
            amount = semanticAmount,
            category = category,
            subcategory = subcategory,
            paymentMethod = paymentMethod,
            description = description,
            refCheckNo = refCheckNo,
            payeePayer = payeePayer,
            account = account,
            tag = tag,
            tax = tax,
            quantity = quantity,
            unit = unit,
            splitTotal = splitTotal,
            transactionType = transactionType,
            toAccount = if (transactionType == "Transfer") toAccount else null,
            dateMillis = dateMillis,
            lastModified = System.currentTimeMillis()
        )

        repository.saveExpense(updatedExpense)
        newJsonResponse(Response.Status.OK, updatedExpense)
    }

    private fun handleDeleteTransaction(rowId: String): Response = runBlocking {
        val exists = repository.exists(rowId)
        if (!exists) {
            return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Transaction not found ($rowId)"))
        }
        repository.deleteById(rowId)
        newJsonResponse(Response.Status.OK, mapOf("success" to true, "rowId" to rowId))
    }

    private fun handleApproveTransaction(rowId: String): Response = runBlocking {
        val pending = repository.loadPendingReviewExpenses()
        val found = pending.find { it.rowId == rowId }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Pending transaction not found ($rowId)"))

        repository.approveExpense(found)
        newJsonResponse(Response.Status.OK, mapOf("success" to true, "rowId" to rowId))
    }

    private fun handleDashboard(): Response = runBlocking {
        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accounts = accountRepository.accounts.value
        val accountNames = accounts.map { it.nickName }

        val currentNetWorth = ExpenseSummaryCalculator.currentBalance(allExpenses, account = null)
        val summaries = ExpenseSummaryCalculator.calculateSummaries(allExpenses, account = null)

        val data = mapOf(
            "netWorth" to currentNetWorth,
            "totalAccounts" to accountNames.size,
            "today" to summaries["Today"],
            "thisWeek" to summaries["This Week"],
            "thisMonth" to summaries["This Month"],
            "yearToDate" to summaries["Year to Date"]
        )
        newJsonResponse(Response.Status.OK, data)
    }

    private fun maskCardNumber(cardNumber: String): String {
        val clean = cardNumber.trim()
        if (clean.isBlank()) return ""
        if (clean.contains("•")) return clean
        val digits = clean.filter { it.isDigit() }
        if (digits.isEmpty()) return ""
        val last4 = digits.takeLast(4)
        return "•••• •••• •••• $last4"
    }

    private fun accountToMap(acc: com.hackerai.mybudget.data.Account, balance: Double): Map<String, Any> {
        val map = mutableMapOf<String, Any>(
            "id" to acc.id,
            "nickName" to acc.nickName,
            "type" to acc.type.name,
            "isHidden" to acc.isHidden,
            "balance" to balance,
            "smsSenderKeywords" to acc.smsSenderKeywords,
            "smsParsingEnabled" to acc.smsParsingEnabled
        )
        when (acc) {
            is com.hackerai.mybudget.data.SavingAccount -> {
                map["bankName"] = acc.bankName
                map["branchName"] = acc.branchName
                map["accountNumber"] = acc.accountNumber
            }
            is com.hackerai.mybudget.data.LoanAccount -> {
                map["bankName"] = acc.bankName
                map["branchName"] = acc.branchName
                map["accountNumber"] = acc.accountNumber
            }
            is com.hackerai.mybudget.data.CreditCardAccount -> {
                map["bankName"] = acc.bankName
                map["cardNumber"] = maskCardNumber(acc.cardNumber)
                map["expiry"] = acc.expiry
                map["billingDate"] = acc.billingDate
                map["dueDate"] = acc.dueDate
                // Note: CVV is intentionally omitted from all API responses for security
            }
            is com.hackerai.mybudget.data.CashAccount -> {}
        }
        return map
    }

    private fun handleAccounts(): Response = runBlocking {
        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accounts = accountRepository.accounts.value

        val accountList = accounts.map { acc ->
            val accExpenses = ExpenseSummaryCalculator.filterByAccount(allExpenses, acc.nickName)
            val balance = ExpenseSummaryCalculator.currentBalance(accExpenses, acc.nickName)
            accountToMap(acc, balance)
        }
        newJsonResponse(Response.Status.OK, accountList)
    }

    private fun handleGetSingleAccount(accountNameOrId: String): Response = runBlocking {
        val accounts = accountRepository.accounts.value
        val acc = accounts.find { it.id == accountNameOrId || it.nickName.equals(accountNameOrId, ignoreCase = true) }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Account not found ($accountNameOrId)"))

        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accExpenses = ExpenseSummaryCalculator.filterByAccount(allExpenses, acc.nickName)
        val balance = ExpenseSummaryCalculator.currentBalance(accExpenses, acc.nickName)

        newJsonResponse(Response.Status.OK, accountToMap(acc, balance))
    }

    private fun handleCreateAccount(session: IHTTPSession): Response = runBlocking {
        val body = parseJsonBody(session)
        val nickName = body["nickName"]?.toString()?.trim() ?: ""
        if (nickName.isBlank()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Account nickname is required."))
        }

        val typeStr = body["type"]?.toString()?.trim()?.uppercase() ?: ""
        val type = try {
            com.hackerai.mybudget.data.AccountType.valueOf(typeStr)
        } catch (e: Exception) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid account type '$typeStr'. Must be SAVING, LOAN, CREDIT_CARD, or CASH."))
        }

        val existingAccounts = accountRepository.accounts.value
        if (existingAccounts.any { it.nickName.equals(nickName, ignoreCase = true) }) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Account with nickname '$nickName' already exists."))
        }

        val id = UUID.randomUUID().toString()
        val bankName = body["bankName"]?.toString()?.trim() ?: ""
        val branchName = body["branchName"]?.toString()?.trim() ?: ""
        val accountNumber = body["accountNumber"]?.toString()?.trim() ?: ""
        val rawCardNumber = body["cardNumber"]?.toString()?.trim() ?: ""
        val expiry = body["expiry"]?.toString()?.trim() ?: ""
        val rawCvv = body["cvv"]?.toString()?.trim() ?: ""
        val billingDate = (body["billingDate"] as? Number)?.toInt() ?: (body["billingDate"]?.toString()?.toIntOrNull()) ?: 1
        val dueDate = (body["dueDate"] as? Number)?.toInt() ?: (body["dueDate"]?.toString()?.toIntOrNull()) ?: 1
        val smsSenderKeywords = body["smsSenderKeywords"]?.toString()?.trim() ?: ""
        val smsParsingEnabled = (body["smsParsingEnabled"] as? Boolean) ?: (body["smsParsingEnabled"]?.toString()?.toBooleanStrictOrNull()) ?: true
        val isHidden = (body["isHidden"] as? Boolean) ?: (body["isHidden"]?.toString()?.toBooleanStrictOrNull()) ?: false

        val cardNumber: String
        val cvv: String

        if (type == com.hackerai.mybudget.data.AccountType.CREDIT_CARD) {
            val cleanCardNumber = rawCardNumber.replace(" ", "").replace("-", "")
            if (!cleanCardNumber.all { it.isDigit() } || cleanCardNumber.length !in 13..19) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid card number."))
            }
            cardNumber = cleanCardNumber

            if (!rawCvv.all { it.isDigit() } || rawCvv.length !in 3..4) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid CVV."))
            }
            cvv = rawCvv
        } else {
            cardNumber = rawCardNumber
            cvv = rawCvv
        }

        val account: com.hackerai.mybudget.data.Account = when (type) {
            com.hackerai.mybudget.data.AccountType.SAVING -> com.hackerai.mybudget.data.SavingAccount(id, nickName, bankName, branchName, accountNumber, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.LOAN -> com.hackerai.mybudget.data.LoanAccount(id, nickName, bankName, branchName, accountNumber, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.CREDIT_CARD -> com.hackerai.mybudget.data.CreditCardAccount(id, nickName, bankName, cardNumber, expiry, cvv, billingDate, dueDate, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.CASH -> com.hackerai.mybudget.data.CashAccount(id, nickName, isHidden, smsSenderKeywords, smsParsingEnabled)
        }

        accountRepository.addAccount(account)
        newJsonResponse(Response.Status.CREATED, accountToMap(account, 0.0))
    }

    private fun handleUpdateAccount(accountNameOrId: String, session: IHTTPSession): Response = runBlocking {
        val accounts = accountRepository.accounts.value
        val existing = accounts.find { it.id == accountNameOrId || it.nickName.equals(accountNameOrId, ignoreCase = true) }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Account not found ($accountNameOrId)"))

        val body = parseJsonBody(session)
        val newNickName = body["nickName"]?.toString()?.trim() ?: existing.nickName
        if (newNickName.isBlank()) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Account nickname cannot be empty."))
        }

        if (!newNickName.equals(existing.nickName, ignoreCase = true)) {
            if (accounts.any { it.id != existing.id && it.nickName.equals(newNickName, ignoreCase = true) }) {
                return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "An account with nickname '$newNickName' already exists."))
            }
        }

        val typeStr = body["type"]?.toString()?.trim()?.uppercase() ?: existing.type.name
        val type = try {
            com.hackerai.mybudget.data.AccountType.valueOf(typeStr)
        } catch (e: Exception) {
            return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid account type '$typeStr'."))
        }

        val bankName = body["bankName"]?.toString()?.trim() ?: (existing as? com.hackerai.mybudget.data.SavingAccount)?.bankName ?: (existing as? com.hackerai.mybudget.data.LoanAccount)?.bankName ?: (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.bankName ?: ""
        val branchName = body["branchName"]?.toString()?.trim() ?: (existing as? com.hackerai.mybudget.data.SavingAccount)?.branchName ?: (existing as? com.hackerai.mybudget.data.LoanAccount)?.branchName ?: ""
        val accountNumber = body["accountNumber"]?.toString()?.trim() ?: (existing as? com.hackerai.mybudget.data.SavingAccount)?.accountNumber ?: (existing as? com.hackerai.mybudget.data.LoanAccount)?.accountNumber ?: ""

        val existingCardNumber = (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.cardNumber ?: ""
        val rawCardNumber = body["cardNumber"]?.toString()?.trim() ?: ""
        val cardNumber: String = when {
            rawCardNumber.isBlank() || rawCardNumber.contains("•") -> existingCardNumber
            else -> {
                if (type == com.hackerai.mybudget.data.AccountType.CREDIT_CARD) {
                    val cleanCardNumber = rawCardNumber.replace(" ", "").replace("-", "")
                    if (!cleanCardNumber.all { it.isDigit() } || cleanCardNumber.length !in 13..19) {
                        return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid card number."))
                    }
                    cleanCardNumber
                } else {
                    rawCardNumber
                }
            }
        }

        val expiry = body["expiry"]?.toString()?.trim() ?: (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.expiry ?: ""

        val existingCvv = (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.cvv ?: ""
        val rawCvv = body["cvv"]?.toString()?.trim() ?: ""
        val cvv: String = when {
            rawCvv.isBlank() -> existingCvv
            else -> {
                if (type == com.hackerai.mybudget.data.AccountType.CREDIT_CARD) {
                    if (!rawCvv.all { it.isDigit() } || rawCvv.length !in 3..4) {
                        return@runBlocking newJsonResponse(Response.Status.BAD_REQUEST, mapOf("error" to "Invalid CVV."))
                    }
                    rawCvv
                } else {
                    rawCvv
                }
            }
        }
        val billingDate = (body["billingDate"] as? Number)?.toInt() ?: (body["billingDate"]?.toString()?.toIntOrNull()) ?: (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.billingDate ?: 1
        val dueDate = (body["dueDate"] as? Number)?.toInt() ?: (body["dueDate"]?.toString()?.toIntOrNull()) ?: (existing as? com.hackerai.mybudget.data.CreditCardAccount)?.dueDate ?: 1
        val smsSenderKeywords = body["smsSenderKeywords"]?.toString()?.trim() ?: existing.smsSenderKeywords
        val smsParsingEnabled = (body["smsParsingEnabled"] as? Boolean) ?: (body["smsParsingEnabled"]?.toString()?.toBooleanStrictOrNull()) ?: existing.smsParsingEnabled
        val isHidden = (body["isHidden"] as? Boolean) ?: (body["isHidden"]?.toString()?.toBooleanStrictOrNull()) ?: existing.isHidden

        val updatedAccount: com.hackerai.mybudget.data.Account = when (type) {
            com.hackerai.mybudget.data.AccountType.SAVING -> com.hackerai.mybudget.data.SavingAccount(existing.id, newNickName, bankName, branchName, accountNumber, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.LOAN -> com.hackerai.mybudget.data.LoanAccount(existing.id, newNickName, bankName, branchName, accountNumber, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.CREDIT_CARD -> com.hackerai.mybudget.data.CreditCardAccount(existing.id, newNickName, bankName, cardNumber, expiry, cvv, billingDate, dueDate, isHidden, smsSenderKeywords, smsParsingEnabled)
            com.hackerai.mybudget.data.AccountType.CASH -> com.hackerai.mybudget.data.CashAccount(existing.id, newNickName, isHidden, smsSenderKeywords, smsParsingEnabled)
        }

        val oldNickname = existing.nickName
        accountRepository.addAccount(updatedAccount)

        // Global cascade if nickname changed
        if (oldNickname != newNickName) {
            repository.renameAccount(oldNickname, newNickName)
        }

        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accExpenses = ExpenseSummaryCalculator.filterByAccount(allExpenses, updatedAccount.nickName)
        val balance = ExpenseSummaryCalculator.currentBalance(accExpenses, updatedAccount.nickName)

        newJsonResponse(Response.Status.OK, accountToMap(updatedAccount, balance))
    }

    private fun handleDeleteAccount(accountNameOrId: String): Response = runBlocking {
        val accounts = accountRepository.accounts.value
        val existing = accounts.find { it.id == accountNameOrId || it.nickName.equals(accountNameOrId, ignoreCase = true) }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Account not found ($accountNameOrId)"))

        val count = repository.getTransactionCountForAccount(existing.nickName)
        if (count == 0) {
            accountRepository.deleteAccount(existing.id)
            newJsonResponse(Response.Status.OK, mapOf("success" to true, "deleted" to true, "id" to existing.id, "nickName" to existing.nickName))
        } else {
            // Soft hide
            val updated = when (existing) {
                is com.hackerai.mybudget.data.SavingAccount -> existing.copy(isHidden = true)
                is com.hackerai.mybudget.data.LoanAccount -> existing.copy(isHidden = true)
                is com.hackerai.mybudget.data.CreditCardAccount -> existing.copy(isHidden = true)
                is com.hackerai.mybudget.data.CashAccount -> existing.copy(isHidden = true)
            }
            accountRepository.addAccount(updated)
            newJsonResponse(Response.Status.OK, mapOf(
                "success" to true,
                "deleted" to false,
                "hidden" to true,
                "message" to "Account has $count transactions. It was hidden from lists rather than physically deleted.",
                "account" to accountToMap(updated, 0.0)
            ))
        }
    }

    private fun handleToggleAccountVisibility(accountNameOrId: String): Response = runBlocking {
        val accounts = accountRepository.accounts.value
        val existing = accounts.find { it.id == accountNameOrId || it.nickName.equals(accountNameOrId, ignoreCase = true) }
            ?: return@runBlocking newJsonResponse(Response.Status.NOT_FOUND, mapOf("error" to "Account not found ($accountNameOrId)"))

        val newHidden = !existing.isHidden
        val updated = when (existing) {
            is com.hackerai.mybudget.data.SavingAccount -> existing.copy(isHidden = newHidden)
            is com.hackerai.mybudget.data.LoanAccount -> existing.copy(isHidden = newHidden)
            is com.hackerai.mybudget.data.CreditCardAccount -> existing.copy(isHidden = newHidden)
            is com.hackerai.mybudget.data.CashAccount -> existing.copy(isHidden = newHidden)
        }
        accountRepository.addAccount(updated)

        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accExpenses = ExpenseSummaryCalculator.filterByAccount(allExpenses, updated.nickName)
        val balance = ExpenseSummaryCalculator.currentBalance(accExpenses, updated.nickName)

        newJsonResponse(Response.Status.OK, accountToMap(updated, balance))
    }

    private fun handleAccountStatement(accountName: String): Response = runBlocking {
        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val accExpenses = ExpenseSummaryCalculator.filterByAccount(allExpenses, accountName)

        val sortedAsc = accExpenses.sortedWith(compareBy({ it.getOrDeriveDateMillis() }, { it.time }, { it.rowId }))
        var current = 0.0

        val transactionsWithBalance = sortedAsc.map { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            val semanticAmount = when {
                exp.transactionType == "Transfer" -> if (exp.toAccount == accountName) absVal else -absVal
                exp.transactionType == "Income" -> if (exp.amount < 0) -absVal else absVal
                exp.transactionType == "Expense" -> -absVal
                else -> if (exp.amount >= 0) absVal else -absVal
            }
            current += semanticAmount

            mapOf(
                "rowId" to exp.rowId,
                "date" to exp.date,
                "time" to exp.time,
                "description" to exp.payeePayer.ifEmpty { exp.description.ifEmpty { "Transaction" } },
                "category" to exp.category,
                "subcategory" to exp.subcategory,
                "transactionType" to exp.transactionType,
                "amount" to exp.amount,
                "semanticAmount" to semanticAmount,
                "runningBalance" to current,
                "account" to exp.account,
                "toAccount" to exp.toAccount
            )
        }.reversed()

        val accountObj = accountRepository.accounts.value.find { it.nickName == accountName }
        val currentBalance = ExpenseSummaryCalculator.currentBalance(accExpenses, accountName)

        val response = mapOf(
            "accountName" to accountName,
            "accountType" to (accountObj?.type?.name ?: "SAVING"),
            "currentBalance" to currentBalance,
            "totalTransactions" to transactionsWithBalance.size,
            "transactions" to transactionsWithBalance
        )
        newJsonResponse(Response.Status.OK, response)
    }

    private fun handlePayees(): Response = runBlocking {
        val payees = repository.getDistinctPayees()
        newJsonResponse(Response.Status.OK, payees)
    }

    private fun handleTransactions(parms: Map<String, String>): Response = runBlocking {
        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }

        val page = (parms["page"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val pageSize = (parms["pageSize"]?.toIntOrNull() ?: 20).coerceIn(1, 100)
        val search = parms["search"]?.lowercase()?.trim() ?: ""

        fun parseMultiParam(key: String): List<String> {
            val raw = parms[key] ?: return emptyList()
            if (raw.isBlank()) return emptyList()
            return raw.split(",").map { it.trim() }.filter { it.isNotBlank() }
        }

        val accountList = parseMultiParam("account")
        val categoryList = parseMultiParam("category")
        val subcategoryList = parseMultiParam("subcategory")
        val payeeList = parseMultiParam("payee")
        val typeList = parseMultiParam("transactionType").ifEmpty { parseMultiParam("type") }

        val dateFilter = parms["dateFilter"] ?: parms["date"]
        val fromDate = parms["fromDate"] ?: parms["from"]
        val toDate = parms["toDate"] ?: parms["to"]

        val filtered = allExpenses.filter { exp ->
            val searchMatches = if (search.isNotBlank()) {
                exp.payeePayer.lowercase().contains(search) ||
                exp.description.lowercase().contains(search) ||
                exp.category.lowercase().contains(search) ||
                exp.subcategory.lowercase().contains(search) ||
                exp.tag.lowercase().contains(search)
            } else true

            val accMatches = accountList.isEmpty() ||
                accountList.any { a -> exp.account.equals(a, ignoreCase = true) || (exp.transactionType == "Transfer" && exp.toAccount?.equals(a, ignoreCase = true) == true) }

            val catMatches = categoryList.isEmpty() ||
                categoryList.any { c -> exp.category.equals(c, ignoreCase = true) }

            val subMatches = subcategoryList.isEmpty() ||
                subcategoryList.any { s ->
                    if (s.equals("__NO_SUBCATEGORY__", ignoreCase = true) || s.equals("No Subcategory", ignoreCase = true) || s.equals("[No Subcategory]", ignoreCase = true)) {
                        exp.subcategory.isBlank()
                    } else {
                        exp.subcategory.equals(s, ignoreCase = true)
                    }
                }

            val payeeMatches = payeeList.isEmpty() ||
                payeeList.any { p -> exp.payeePayer.equals(p, ignoreCase = true) }

            val typeMatches = typeList.isEmpty() ||
                typeList.any { t -> exp.transactionType.equals(t, ignoreCase = true) }

            val dateMatches = DateFilterEvaluator.evaluateDateFilter(exp.getOrDeriveDateMillis(), dateFilter, fromDate, toDate)

            searchMatches && accMatches && catMatches && subMatches && payeeMatches && typeMatches && dateMatches
        }.sortedWith(compareByDescending<Expense> { it.getOrDeriveDateMillis() }.thenByDescending { it.time }.thenByDescending { it.rowId })

        val filteredImpact = filtered.sumOf { FilteredBalanceCalculator.calculateSemanticAmount(it) }
        val filteredBalMap = FilteredBalanceCalculator.computeMultiAccountFilteredRunningBalances(filtered)

        val totalItems = filtered.size
        val totalPages = (totalItems + pageSize - 1) / pageSize
        val fromIndex = ((page - 1) * pageSize).coerceAtMost(totalItems)
        val toIndex = (fromIndex + pageSize).coerceAtMost(totalItems)

        val pageItems = if (fromIndex < toIndex) filtered.subList(fromIndex, toIndex) else emptyList()

        val pageItemsWithFilteredBalance = pageItems.map { exp ->
            mapOf(
                "rowId" to exp.rowId,
                "date" to exp.date,
                "time" to exp.time,
                "description" to exp.description,
                "payeePayer" to exp.payeePayer,
                "category" to exp.category,
                "subcategory" to exp.subcategory,
                "account" to exp.account,
                "toAccount" to exp.toAccount,
                "transactionType" to exp.transactionType,
                "amount" to exp.amount,
                "filteredRunningBalance" to (filteredBalMap[exp.rowId] ?: 0.0)
            )
        }

        val response = mapOf(
            "page" to page,
            "pageSize" to pageSize,
            "totalItems" to totalItems,
            "totalPages" to totalPages,
            "filteredImpact" to filteredImpact,
            "items" to pageItemsWithFilteredBalance
        )
        newJsonResponse(Response.Status.OK, response)
    }

    private fun handleCategories(): Response = runBlocking {
        val allExpenses = repository.loadExpenses().filter { !it.isDeleted && it.status != "system" }
        val categoryMap = allExpenses.groupBy { it.category }
            .mapValues { (_, list) -> list.map { it.subcategory }.filter { it.isNotBlank() }.distinct().sorted() }
        newJsonResponse(Response.Status.OK, categoryMap)
    }

    private fun handleStaticFile(uriStr: String): Response {
        var path = uriStr.trim()
        if (path == "/" || path.isEmpty()) {
            path = "/index.html"
        }

        val cleanPath = path.substringBefore("?")

        if (cleanPath.contains("..") || cleanPath.contains("\\") || cleanPath.contains("//")) {
            return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "403 Forbidden")
        }

        val assetPath = "web${cleanPath}"
        val mimeType = getMimeType(cleanPath)

        val response = try {
            val inputStream: InputStream = context.assets.open(assetPath)
            newChunkedResponse(Response.Status.OK, mimeType, inputStream)
        } catch (e: Exception) {
            newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "404 Not Found")
        }

        response.addHeader("Cache-Control", "no-cache, no-store, must-revalidate")
        response.addHeader("Pragma", "no-cache")
        response.addHeader("Expires", "0")
        return response
    }

    private fun getMimeType(path: String): String {
        return when {
            path.endsWith(".html", ignoreCase = true) -> "text/html; charset=UTF-8"
            path.endsWith(".css", ignoreCase = true) -> "text/css; charset=UTF-8"
            path.endsWith(".js", ignoreCase = true) -> "application/javascript; charset=UTF-8"
            path.endsWith(".json", ignoreCase = true) -> "application/json; charset=UTF-8"
            path.endsWith(".png", ignoreCase = true) -> "image/png"
            path.endsWith(".ico", ignoreCase = true) -> "image/x-icon"
            else -> MIME_PLAINTEXT
        }
    }

    private fun parseJsonBody(session: IHTTPSession): Map<String, Any> {
        val contentType = session.headers["content-type"] ?: session.headers["Content-Type"] ?: ""
        val isJson = contentType.contains("application/json", ignoreCase = true)

        val resultMap = mutableMapOf<String, Any>()

        if (isJson) {
            val jsonString = readDirectBody(session)
            val trimmedBody = jsonString.trim()
            if (trimmedBody.isNotBlank()) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val parsed: Map<String, Any>? = gson.fromJson(trimmedBody, type)
                    if (parsed != null) {
                        resultMap.putAll(parsed)
                    }
                } catch (e: Exception) {
                    Log.w("MyBudgetHttpServer", "JSON parse error: ${e.message}")
                }
            }
            for ((k, v) in session.parms) {
                if (!v.isNullOrBlank() && !resultMap.containsKey(k)) {
                    resultMap[k] = v
                }
            }
            return resultMap
        }

        for ((k, v) in session.parms) {
            if (!v.isNullOrBlank()) {
                resultMap[k] = v
            }
        }

        val map = HashMap<String, String>()
        try {
            session.parseBody(map)
        } catch (e: Exception) {
            Log.w("MyBudgetHttpServer", "session.parseBody exception: ${e.message}")
        }

        for ((k, v) in map) {
            if (k != "postData" && k != "content" && !v.isNullOrBlank()) {
                resultMap[k] = v
            }
        }

        val rawBody = map["postData"]?.ifBlank { null }
            ?: map["content"]?.let { filePath ->
                try {
                    val file = java.io.File(filePath)
                    if (file.exists()) file.readText() else null
                } catch (e: Exception) {
                    null
                }
            }
            ?: ""

        val trimmedBody = rawBody.trim()
        if (trimmedBody.isNotBlank()) {
            if (trimmedBody.startsWith("{") && trimmedBody.endsWith("}")) {
                try {
                    val type = object : com.google.gson.reflect.TypeToken<Map<String, Any>>() {}.type
                    val parsed: Map<String, Any>? = gson.fromJson(trimmedBody, type)
                    if (parsed != null) {
                        resultMap.putAll(parsed)
                    }
                } catch (e: Exception) {
                    Log.w("MyBudgetHttpServer", "JSON parse error: ${e.message}")
                }
            } else if (trimmedBody.contains("=")) {
                for (pair in trimmedBody.split("&")) {
                    val parts = pair.split("=")
                    if (parts.size == 2) {
                        val key = URLDecoder.decode(parts[0].trim(), "UTF-8")
                        val value = URLDecoder.decode(parts[1].trim(), "UTF-8")
                        resultMap[key] = value
                    }
                }
            }
        }

        return resultMap
    }

    private fun readDirectBody(session: IHTTPSession): String {
        return try {
            val contentLengthStr = session.headers["content-length"] ?: session.headers["Content-Length"]
            val contentLength = contentLengthStr?.toIntOrNull() ?: -1

            val inputStream = session.inputStream
            if (contentLength == 0) return ""

            val bytes = if (contentLength > 0) {
                val buffer = ByteArray(contentLength)
                var totalRead = 0
                while (totalRead < contentLength) {
                    val read = inputStream.read(buffer, totalRead, contentLength - totalRead)
                    if (read <= 0) break
                    totalRead += read
                }
                if (totalRead > 0) buffer.copyOf(totalRead) else ByteArray(0)
            } else {
                val baos = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                var read: Int
                var total = 0
                val maxBytes = 10 * 1024 * 1024 // 10MB safety limit
                while (inputStream.read(buffer).also { read = it } != -1) {
                    baos.write(buffer, 0, read)
                    total += read
                    if (total >= maxBytes) break
                }
                baos.toByteArray()
            }
            String(bytes, Charsets.UTF_8)
        } catch (e: Exception) {
            Log.w("MyBudgetHttpServer", "Direct inputStream read error: ${e.message}")
            ""
        }
    }

    private fun newJsonResponse(status: Response.Status, body: Any): Response {
        val json = gson.toJson(body)
        val response = newFixedLengthResponse(status, "application/json; charset=UTF-8", json)
        response.addHeader("Access-Control-Allow-Origin", "*")
        return response
    }
}

