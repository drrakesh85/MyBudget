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
        
        // Skip header
        val header = reader.readLine()
        
        var line: String? = reader.readLine()
        while (line != null) {
            val tokens = line.split(",")
            if (tokens.size >= 18) {
                try {
                    val expense = Expense(
                        date = tokens[0],
                        amount = tokens[1].toDoubleOrNull() ?: 0.0,
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
                        quantity = tokens[13].toDoubleOrNull() ?: 0.0,
                        unit = tokens[14],
                        splitTotal = tokens[15],
                        rowId = tokens[16],
                        typeId = tokens[17],
                        transactionType = if (tokens.size > 18) tokens[18] else if (tokens[1].toDoubleOrNull() ?: 0.0 >= 0) "Income" else "Expense",
                        toAccount = if (tokens.size > 19) tokens[19] else null
                    )
                    expenses.add(expense)
                } catch (e: Exception) {
                    Log.w(TAG, "Skipping malformed CSV line: $line", e)
                }
            }
            line = reader.readLine()
        }
        return expenses
    }
}
