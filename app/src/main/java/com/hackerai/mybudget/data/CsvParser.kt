package com.hackerai.mybudget.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.Locale

object CsvParser {
    private const val TAG = "CsvParser"

    fun parse(inputStream: InputStream): List<Expense> {
        val rawExpenses = mutableListOf<Expense>()
        val reader = BufferedReader(InputStreamReader(inputStream))
        
        try {
            // Skip header
            val headerLine = reader.readLine()
            Log.d(TAG, "Parsing CSV. Header: $headerLine")
            
            var lineCount = 0
            var line: String? = reader.readLine()
            while (line != null) {
                lineCount++
                if (line.isNotBlank() && !line.startsWith("Date,Amount", ignoreCase = true)) {
                    try {
                        // Use a regex that handles commas inside quotes
                        val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                            .map { it.trim().removeSurrounding("\"") }
                        
                        // We expect at least 17 columns (up to Row ID)
                        if (tokens.size >= 17) {
                            val amount = tokens[1].toDoubleOrNull() ?: 0.0
                            val category = if (tokens.size > 2) tokens[2] else ""
                            val subcategory = if (tokens.size > 3) tokens[3] else ""
                            val rawType = if (tokens.size > 18 && tokens[18].isNotBlank()) tokens[18].trim() else ""
                            
                            val isTransferCategory = category.equals("Account Transfer", ignoreCase = true) || 
                                                 subcategory.equals("Account Transfer", ignoreCase = true)

                            val normalizedType = when {
                                rawType.uppercase(Locale.US) in listOf("TRANSFER", "ACCOUNT TRANSFER") || isTransferCategory -> "Transfer"
                                rawType.uppercase(Locale.US) == "INCOME" -> "Income"
                                rawType.uppercase(Locale.US) == "EXPENSE" -> "Expense"
                                else -> if (amount >= 0) "Income" else "Expense"
                            }

                            val dateStr = tokens[0]
                            val rawAccount = if (tokens.size > 10) tokens[10] else ""
                            val rawPayeePayer = if (tokens.size > 7) tokens[7] else ""
                            val rawToAccount = if (tokens.size > 19 && tokens[19].isNotBlank()) tokens[19] else rawPayeePayer

                            var finalAccount = rawAccount
                            var finalToAccount: String? = if (tokens.size > 19 && tokens[19].isNotBlank()) tokens[19] else null
                            var finalAmount = amount

                            if (normalizedType == "Transfer") {
                                finalAmount = kotlin.math.abs(amount)
                                val otherAccount = if (rawToAccount.isNotBlank()) rawToAccount else rawPayeePayer
                                if (amount > 0 && otherAccount.isNotBlank() && otherAccount != rawAccount) {
                                    // Positive amount means rawAccount received money from otherAccount
                                    finalAccount = otherAccount // Sender
                                    finalToAccount = rawAccount // Receiver
                                } else {
                                    // Negative amount or equal accounts: rawAccount sent money to otherAccount
                                    finalAccount = rawAccount // Sender
                                    finalToAccount = if (otherAccount.isNotBlank() && otherAccount != rawAccount) otherAccount else null // Receiver
                                }
                            }

                            val expense = Expense(
                                date = dateStr,
                                amount = finalAmount,
                                category = category,
                                subcategory = subcategory,
                                paymentMethod = if (tokens.size > 4) tokens[4] else "",
                                description = if (tokens.size > 5) tokens[5] else "",
                                refCheckNo = if (tokens.size > 6) tokens[6] else "",
                                payeePayer = if (tokens.size > 7) tokens[7] else "",
                                status = if (tokens.size > 8) tokens[8] else "",
                                receiptPicture = if (tokens.size > 9) tokens[9] else "",
                                account = finalAccount,
                                tag = if (tokens.size > 11) tokens[11] else "",
                                tax = if (tokens.size > 12) tokens[12] else "",
                                quantity = if (tokens.size > 13) tokens[13].toDoubleOrNull() ?: 1.0 else 1.0,
                                unit = if (tokens.size > 14) tokens[14] else "",
                                splitTotal = if (tokens.size > 15) tokens[15] else "",
                                rowId = tokens[16],
                                typeId = if (tokens.size > 17) tokens[17] else "",
                                transactionType = normalizedType,
                                toAccount = finalToAccount,
                                dateMillis = DateUtils.parseDateToMillis(dateStr)
                            )
                            rawExpenses.add(expense)
                        } else {
                            Log.w(TAG, "Line $lineCount: Insufficient columns (${tokens.size}). Expected at least 17.")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Line $lineCount: Skipping malformed CSV line: $line", e)
                    }
                }
                line = reader.readLine()
            }

            // Deduplicate dual-entry transfer rows from ExpenseManager exports
            val deduplicated = mutableListOf<Expense>()
            val seenTransferKeys = mutableSetOf<String>()

            for (exp in rawExpenses) {
                if (exp.transactionType == "Transfer" && exp.description.startsWith("Transfer:", ignoreCase = true) && exp.toAccount != null) {
                    val acc1 = if (exp.account < exp.toAccount) exp.account else exp.toAccount
                    val acc2 = if (exp.account < exp.toAccount) exp.toAccount else exp.account
                    val transferKey = "${exp.date}|${exp.description}|${kotlin.math.abs(exp.amount)}|$acc1|$acc2"
                    if (seenTransferKeys.contains(transferKey)) {
                        continue
                    }
                    seenTransferKeys.add(transferKey)
                }
                deduplicated.add(exp)
            }

            Log.d(TAG, "Parsed $lineCount lines, generated ${deduplicated.size} expense objects")
            return deduplicated
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CSV", e)
            throw e
        }
    }
}
