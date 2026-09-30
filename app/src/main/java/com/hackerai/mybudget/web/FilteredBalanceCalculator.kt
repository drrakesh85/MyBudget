package com.hackerai.mybudget.web

import com.hackerai.mybudget.data.Expense
import kotlin.math.abs

object FilteredBalanceCalculator {

    /**
     * Calculates semantic amount for an expense in the context of a target account.
     * Preserves existing sign conventions:
     * - Income: positive
     * - Expense: negative
     * - Transfer: positive if receiving account, negative if sending account
     */
    fun calculateSemanticAmount(exp: Expense, contextAccountName: String? = null): Double {
        val absVal = abs(exp.amount)
        val targetAcc = contextAccountName ?: exp.account
        return when {
            exp.transactionType == "Transfer" -> {
                if (exp.toAccount.equals(targetAcc, ignoreCase = true)) absVal else -absVal
            }
            exp.transactionType == "Income" -> if (exp.amount < 0) -absVal else absVal
            exp.transactionType == "Expense" -> -absVal
            else -> if (exp.amount >= 0) absVal else -absVal
        }
    }

    /**
     * Computes Filtered Running Balance for a list of expenses belonging to a single account.
     * Expenses MUST be sorted chronologically ASCENDING (dateMillis/date, time, rowId).
     * Filtered Running Balance starts at 0.0 and accumulates semanticAmount.
     * Returns a map of rowId -> filteredRunningBalance.
     */
    fun computeFilteredRunningBalances(
        filteredExpensesAsc: List<Expense>,
        accountName: String? = null
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        var currentFiltered = 0.0

        for (exp in filteredExpensesAsc) {
            val semAmount = calculateSemanticAmount(exp, accountName)
            currentFiltered += semAmount
            result[exp.rowId] = currentFiltered
        }

        return result
    }

    /**
     * Computes Filtered Running Balances grouped by account for multi-account filtered datasets.
     * Each account sequence starts at 0.0 independently (account isolation).
     */
    fun computeMultiAccountFilteredRunningBalances(
        filteredExpenses: List<Expense>
    ): Map<String, Double> {
        val result = mutableMapOf<String, Double>()
        val grouped = filteredExpenses.groupBy { it.account }

        for ((acc, expList) in grouped) {
            val sortedAsc = expList.sortedWith(compareBy({ it.getOrDeriveDateMillis() }, { it.time }, { it.rowId }))
            var currentFiltered = 0.0
            for (exp in sortedAsc) {
                val semAmount = calculateSemanticAmount(exp, acc)
                currentFiltered += semAmount
                result[exp.rowId] = currentFiltered
            }
        }

        return result
    }
}
