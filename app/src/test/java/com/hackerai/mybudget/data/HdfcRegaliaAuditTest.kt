package com.hackerai.mybudget.data

import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class HdfcRegaliaAuditTest {

    @Test
    fun fullAuditHdfcRegalia() {
        val file = File("src/main/assets/expensemanager.csv")
        assertTrue("expensemanager.csv must exist", file.exists())

        val allExpenses = FileInputStream(file).use { stream ->
            CsvParser.parse(stream)
        }

        val accountName = "HDFC REGALIA"

        // Filter active transactions for HDFC REGALIA
        val hdfcExpenses = allExpenses.filter { 
            !it.isDeleted &&
            (it.account == accountName || (it.transactionType == "Transfer" && it.toAccount == accountName))
        }

        val report = StringBuilder()

        report.append("==================================================\n")
        report.append("HDFC REGALIA TRANSACTION COUNT: ${hdfcExpenses.size}\n")
        report.append("==================================================\n")

        // 1. App's Current Summary Calculation
        val appSummary = ExpenseSummaryCalculator.calculateListSummary(hdfcExpenses, accountName)
        val appCurrentBalance = ExpenseSummaryCalculator.currentBalance(hdfcExpenses, accountName)

        report.append("\nAPP DISPLAYED SUMMARY (from ExpenseSummaryCalculator):\n")
        report.append("  Displayed Income: ${appSummary.first}\n")
        report.append("  Displayed Expense: ${appSummary.second}\n")
        report.append("  Displayed Final Balance: $appCurrentBalance\n")

        // 2. Independent Breakdown
        var normalIncomeSum = 0.0
        var normalExpenseSum = 0.0
        var transferOutSum = 0.0
        var transferInSum = 0.0

        hdfcExpenses.forEach { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            val isTransfer = exp.transactionType == "Transfer" || exp.category.equals("Account Transfer", ignoreCase = true)
            
            val isHdfcSource = exp.account == accountName
            val isHdfcDest = exp.toAccount == accountName || exp.payeePayer == accountName

            if (isTransfer) {
                if (isHdfcSource) {
                    transferOutSum += absVal
                } else if (isHdfcDest) {
                    transferInSum += absVal
                } else {
                    if (exp.amount < 0) transferOutSum += absVal else transferInSum += absVal
                }
            } else {
                when (exp.transactionType) {
                    "Income" -> {
                        if (exp.amount < 0) {
                            normalExpenseSum += absVal
                        } else {
                            normalIncomeSum += absVal
                        }
                    }
                    "Expense" -> {
                        normalExpenseSum += absVal
                    }
                }
            }
        }

        val expectedBalance = normalIncomeSum - normalExpenseSum - transferOutSum + transferInSum

        report.append("\nINDEPENDENT CORRECT BREAKDOWN:\n")
        report.append("  1. Normal Income: $normalIncomeSum\n")
        report.append("  2. Normal Expense: $normalExpenseSum\n")
        report.append("  3. Transfers OUT: $transferOutSum\n")
        report.append("  4. Transfers IN: $transferInSum\n")
        report.append("  5. Expected Final Balance: $expectedBalance\n")
        report.append("  6. App Displayed Balance: $appCurrentBalance\n")
        report.append("  7. Difference (Displayed - Expected): ${appCurrentBalance - expectedBalance}\n")

        // 3. Print Transaction-by-Transaction Table
        report.append("\n========================================================================================================================\n")
        report.append("TRANSACTION TABLE FOR HDFC REGALIA (Ascending Order as processed for Running Balance):\n")
        report.append("========================================================================================================================\n")

        val sortedAsc = hdfcExpenses.sortedWith(compareBy({ it.dateMillis }, { it.time }, { it.rowId }))

        var runningApp = 0.0
        var runningCorrect = 0.0

        report.append(String.format("%-12s | %-30s | %-12s | %-15s | %-18s | %-20s | %-22s | %-15s | %-15s\n", 
            "Date", "Description/Payee", "Amount", "Type", "account", "toAccount", "Effect (App/Correct)", "App Running", "Correct Running"))
        report.append("-".repeat(170)).append("\n")

        sortedAsc.forEach { exp ->
            val absVal = kotlin.math.abs(exp.amount)
            
            // App's semantic calculation for this item
            val appEffect = when {
                exp.transactionType == "Transfer" -> if (exp.toAccount == accountName) absVal else -absVal
                exp.transactionType == "Income" -> absVal
                exp.transactionType == "Expense" -> -absVal
                else -> 0.0
            }
            runningApp += appEffect

            // Correct semantic effect
            val isTransfer = exp.transactionType == "Transfer" || exp.category.equals("Account Transfer", ignoreCase = true)
            val correctEffect = when {
                isTransfer -> {
                    if (exp.account == accountName) -absVal
                    else if (exp.toAccount == accountName) absVal
                    else if (exp.amount < 0) -absVal
                    else absVal
                }
                exp.transactionType == "Income" -> if (exp.amount < 0) -absVal else absVal
                exp.transactionType == "Expense" -> -absVal
                else -> if (exp.amount < 0) -absVal else absVal
            }
            runningCorrect += correctEffect

            val desc = if (exp.description.isNotBlank()) exp.description else exp.payeePayer

            report.append(String.format("%-12s | %-30s | %-12.2f | %-15s | %-18s | %-20s | %+10.2f / %+10.2f | %-15.2f | %-15.2f\n",
                exp.date,
                desc.take(30),
                exp.amount,
                exp.transactionType,
                exp.account,
                exp.toAccount ?: "null",
                appEffect,
                correctEffect,
                runningApp,
                runningCorrect
            ))
        }

        File("hdfc_regalia_audit.txt").writeText(report.toString())
    }
}
