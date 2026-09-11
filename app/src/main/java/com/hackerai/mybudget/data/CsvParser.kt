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
            reader.readLine()
            
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) {
                    try {
                        // Use a regex that handles commas inside quotes
                        val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                            .map { it.trim().removeSurrounding("\"") }
                        
                        if (tokens.size >= 18) {
                            val amount = tokens[1].toDoubleOrNull() ?: 0.0
                            val expense = Expense(
                                date = tokens[0],
                                amount = amount,
                                category = tokens[2],
                                subcategory = tokens[3],
                                paymentMethod = tokens[4],
                                description = tokens[5],
                                refCheckNo = tokens[6],
                                payeePayer = tokens[7],
                                status = tokens[8],
                                receiptPicture = tokens[9],
                                account = tokens[10],
                                tag = tokens[11],
                                tax = tokens[12],
                                quantity = tokens[13].toDoubleOrNull() ?: 1.0,
                                unit = tokens[14],
                                splitTotal = tokens[15],
                                rowId = tokens[16],
                                typeId = tokens[17],
                                transactionType = if (tokens.size > 18) tokens[18] else if (amount >= 0) "Income" else "Expense",
                                toAccount = if (tokens.size > 19) tokens[19] else null
                            )
                            expenses.add(expense)
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Skipping malformed CSV line: $line", e)
                    }
                }
                line = reader.readLine()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error reading CSV", e)
        } finally {
            try {
                reader.close()
            } catch (e: Exception) {
                // ignore
            }
        }
        return expenses
    }
}
