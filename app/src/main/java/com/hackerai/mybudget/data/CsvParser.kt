package com.hackerai.mybudget.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader

object CsvParser {
    private const val TAG = "CsvParser"

    fun parse(inputStream: InputStream): List<Expense> {
        val expenses = mutableListOf<Expense>()
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
                            val expense = Expense(
                                date = tokens[0],
                                amount = amount,
                                category = if (tokens.size > 2) tokens[2] else "",
                                subcategory = if (tokens.size > 3) tokens[3] else "",
                                paymentMethod = if (tokens.size > 4) tokens[4] else "",
                                description = if (tokens.size > 5) tokens[5] else "",
                                refCheckNo = if (tokens.size > 6) tokens[6] else "",
                                payeePayer = if (tokens.size > 7) tokens[7] else "",
                                status = if (tokens.size > 8) tokens[8] else "",
                                receiptPicture = if (tokens.size > 9) tokens[9] else "",
                                account = if (tokens.size > 10) tokens[10] else "",
                                tag = if (tokens.size > 11) tokens[11] else "",
                                tax = if (tokens.size > 12) tokens[12] else "",
                                quantity = if (tokens.size > 13) tokens[13].toDoubleOrNull() ?: 1.0 else 1.0,
                                unit = if (tokens.size > 14) tokens[14] else "",
                                splitTotal = if (tokens.size > 15) tokens[15] else "",
                                rowId = tokens[16],
                                typeId = if (tokens.size > 17) tokens[17] else "",
                                transactionType = if (tokens.size > 18 && tokens[18].isNotBlank()) tokens[18] else (if (amount >= 0) "Income" else "Expense"),
                                toAccount = if (tokens.size > 19 && tokens[19].isNotBlank()) tokens[19] else null
                            )
                            expenses.add(expense)
                        } else {
                            Log.w(TAG, "Line $lineCount: Insufficient columns (${tokens.size}). Expected at least 17.")
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Line $lineCount: Skipping malformed CSV line: $line", e)
                    }
                }
                line = reader.readLine()
            }
            Log.d(TAG, "Parsed $lineCount lines, generated ${expenses.size} expense objects")
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CSV", e)
            throw e
        } finally {
            // We do NOT close the reader here because the stream is managed by the caller
            // Wait, the user said "ViewModel/repository opens the InputStream INSIDE the coroutine and closes it after parsing/import completes."
            // So if CsvParser is used inside a .use {} block in the repository, it's fine.
        }
        return expenses
    }
}
