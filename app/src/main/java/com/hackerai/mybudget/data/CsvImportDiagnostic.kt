package com.hackerai.mybudget.data

import android.util.Log
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.time.LocalDate
import java.time.format.DateTimeFormatter

data class SuspiciousRow(
    val lineIndex: Int,
    val rawRow: String,
    val reason: String,
    val parsedDate: String,
    val parsedAmount: Double,
    val parsedAccount: String,
    val parsedCategory: String,
    val parsedSubcategory: String,
    val parsedRowId: String,
    val parsedTypeId: String,
    val parsedTransactionType: String,
    val parsedToAccount: String?
)

data class CsvImportDiagnosticResult(
    val totalRows: Int = 0,
    val parsedRows: Int = 0,
    val failedRows: Int = 0,
    val emptyRows: Int = 0,
    val invalidAmountRows: Int = 0,
    val invalidDateRows: Int = 0,
    val suspiciousDateRows: Int = 0,
    val suspiciousAmountRows: Int = 0,
    val duplicateRowIdCount: Int = 0,
    val missingTypeIdCount: Int = 0,
    val inferredExpenseCount: Int = 0,
    val inferredIncomeCount: Int = 0,
    val transferCount: Int = 0,
    val uniqueAccountCount: Int = 0,
    val uniqueCategoryCount: Int = 0,
    val firstErrors: List<String> = emptyList(),
    val suspiciousRows: List<SuspiciousRow> = emptyList()
)

object CsvImportDiagnosticTool {
    private const val TAG = "CSV_IMPORT_DIAGNOSTIC"
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

    fun runDiagnostic(inputStream: InputStream): CsvImportDiagnosticResult {
        var totalRows = 0
        var parsedRows = 0
        var failedRows = 0
        var emptyRows = 0
        var invalidAmountRows = 0
        var invalidDateRows = 0
        var suspiciousDateRows = 0
        var suspiciousAmountRows = 0
        var duplicateRowIdCount = 0
        var missingTypeIdCount = 0
        var inferredExpenseCount = 0
        var inferredIncomeCount = 0
        var transferCount = 0
        
        val uniqueAccounts = mutableSetOf<String>()
        val uniqueCategories = mutableSetOf<String>()
        val rowIds = mutableSetOf<String>()
        
        val firstErrors = mutableListOf<String>()
        val suspiciousRows = mutableListOf<SuspiciousRow>()

        val reader = BufferedReader(InputStreamReader(inputStream))
        
        try {
            val headerLine = reader.readLine()
            Log.d(TAG, "Header: $headerLine")
            
            var line: String? = reader.readLine()
            var lineIndex = 2 // Start after header
            
            while (line != null) {
                if (line.isBlank()) {
                    emptyRows++
                } else if (line.startsWith("Date,Amount", ignoreCase = true)) {
                    // Another header line found in middle of file, skip it but don't count as transaction row
                } else {
                    totalRows++
                    try {
                        // Use a regex that handles commas inside quotes (mirroring CsvParser)
                        val tokens = line.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)".toRegex())
                            .map { it.trim().removeSurrounding("\"") }
                        
                        if (tokens.size >= 17) {
                            val rawDate = tokens[0]
                            val rawAmountStr = tokens[1]
                            val amount = rawAmountStr.toDoubleOrNull()
                            
                            val rowId = tokens[16]
                            val typeId = if (tokens.size > 17) tokens[17] else ""
                            val rawType = if (tokens.size > 18 && tokens[18].isNotBlank()) tokens[18].trim() else ""
                            val transactionType = when (rawType.uppercase(java.util.Locale.US)) {
                                "INCOME" -> "Income"
                                "EXPENSE" -> "Expense"
                                "TRANSFER", "ACCOUNT TRANSFER" -> "Transfer"
                                else -> if ((amount ?: 0.0) >= 0.0) "Income" else "Expense"
                            }
                            val toAccount = if (tokens.size > 19 && tokens[19].isNotBlank()) tokens[19] else null
                            
                            val account = tokens[10]
                            val category = tokens[2]
                            val subcategory = tokens[3]

                            var isSuspicious = false
                            val reasons = mutableListOf<String>()

                            // 1. Check Amount
                            if (amount == null) {
                                invalidAmountRows++
                                reasons.add("Invalid amount format: $rawAmountStr")
                                isSuspicious = true
                            } else {
                                if (amount == 0.0) {
                                    suspiciousAmountRows++
                                    reasons.add("Zero amount")
                                    isSuspicious = true
                                } else if (kotlin.math.abs(amount) > 1000000.0) {
                                    suspiciousAmountRows++
                                    reasons.add("Unusually large amount: $amount")
                                    isSuspicious = true
                                }
                            }

                            // 2. Check Date
                            val parsedDate = try {
                                LocalDate.parse(rawDate, dateFormatter)
                            } catch (e: Exception) {
                                null
                            }
                            
                            if (parsedDate == null) {
                                invalidDateRows++
                                reasons.add("Invalid date format: $rawDate")
                                isSuspicious = true
                            } else {
                                if (parsedDate.year == 1970) {
                                    suspiciousDateRows++
                                    reasons.add("Date resolves to 1970")
                                    isSuspicious = true
                                } else if (parsedDate.isAfter(LocalDate.now().plusYears(1)) || parsedDate.isBefore(LocalDate.of(2000, 1, 1))) {
                                    suspiciousDateRows++
                                    reasons.add("Date outside reasonable range: $rawDate")
                                    isSuspicious = true
                                }
                            }

                            // 3. Check Row IDs
                            if (rowId.isBlank()) {
                                reasons.add("Missing Row ID")
                                isSuspicious = true
                            } else if (rowIds.contains(rowId)) {
                                duplicateRowIdCount++
                                reasons.add("Duplicate Row ID: $rowId")
                                isSuspicious = true
                            } else {
                                rowIds.add(rowId)
                            }

                            // 4. Check Type IDs
                            if (typeId.isBlank()) {
                                missingTypeIdCount++
                                reasons.add("Missing Type ID")
                                isSuspicious = true
                            }

                            // Update stats
                            parsedRows++
                            uniqueAccounts.add(account)
                            uniqueCategories.add(category)
                            
                            when (transactionType) {
                                "Expense" -> inferredExpenseCount++
                                "Income" -> inferredIncomeCount++
                                "Transfer" -> transferCount++
                            }

                            if (isSuspicious && suspiciousRows.size < 50) {
                                suspiciousRows.add(SuspiciousRow(
                                    lineIndex = lineIndex,
                                    rawRow = line,
                                    reason = reasons.joinToString("; "),
                                    parsedDate = rawDate,
                                    parsedAmount = amount ?: 0.0,
                                    parsedAccount = account,
                                    parsedCategory = category,
                                    parsedSubcategory = subcategory,
                                    parsedRowId = rowId,
                                    parsedTypeId = typeId,
                                    parsedTransactionType = transactionType,
                                    parsedToAccount = toAccount
                                ))
                            }

                        } else {
                            failedRows++
                            if (firstErrors.size < 50) {
                                firstErrors.add("Line $lineIndex: Insufficient columns (${tokens.size})")
                            }
                        }
                    } catch (e: Exception) {
                        failedRows++
                        if (firstErrors.size < 50) {
                            firstErrors.add("Line $lineIndex: Exception during parsing: ${e.message}")
                        }
                    }
                }
                line = reader.readLine()
                lineIndex++
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error during diagnostic run", e)
            firstErrors.add("Fatal error: ${e.message}")
        }
        
        return CsvImportDiagnosticResult(
            totalRows = totalRows,
            parsedRows = parsedRows,
            failedRows = failedRows,
            emptyRows = emptyRows,
            invalidAmountRows = invalidAmountRows,
            invalidDateRows = invalidDateRows,
            suspiciousDateRows = suspiciousDateRows,
            suspiciousAmountRows = suspiciousAmountRows,
            duplicateRowIdCount = duplicateRowIdCount,
            missingTypeIdCount = missingTypeIdCount,
            inferredExpenseCount = inferredExpenseCount,
            inferredIncomeCount = inferredIncomeCount,
            transferCount = transferCount,
            uniqueAccountCount = uniqueAccounts.size,
            uniqueCategoryCount = uniqueCategories.size,
            firstErrors = firstErrors,
            suspiciousRows = suspiciousRows
        )
    }
}
