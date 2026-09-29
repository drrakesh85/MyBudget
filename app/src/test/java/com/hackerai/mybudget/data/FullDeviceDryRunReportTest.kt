package com.hackerai.mybudget.data

import org.junit.Test
import java.io.File
import java.io.FileInputStream

class FullDeviceDryRunReportTest {

    @Test
    fun generateFullDryRunReport() {
        val csvFile = File("E:/AndroidStudioProjects/MyBudget/expensemanager.csv")
        val expenses = if (csvFile.exists()) {
            CsvParser.parse(FileInputStream(csvFile))
        } else emptyList()

        val uniqueAccountNamesFromCsv = (expenses.map { it.account } + expenses.mapNotNull { it.toAccount })
            .filter { it.isNotBlank() }
            .distinct()

        val accountsList = uniqueAccountNamesFromCsv.map { name ->
            if (name.contains("Cash", ignoreCase = true) || name.contains("PayTM", ignoreCase = true) || name.contains("Wallet", ignoreCase = true)) {
                CashAccount("id_$name", name)
            } else if (name.contains("Card", ignoreCase = true)) {
                CreditCardAccount("id_$name", name, name, "0000", "01/99", "000", 1, 1)
            } else {
                SavingAccount("id_$name", name, name, "Auto-Imported", "00000000")
            }
        }

        val rawRequested = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES

        val normalizedRequestedMap = mutableMapOf<String, String>()
        rawRequested.forEach { req ->
            val trimmed = req.trim()
            if (trimmed.isNotBlank()) {
                normalizedRequestedMap[trimmed.lowercase()] = trimmed
            }
        }

        val matchedAccounts = mutableListOf<Account>()
        val remainingAccounts = mutableListOf<Account>()

        for (acc in accountsList) {
            val normNick = acc.nickName.trim().lowercase()
            if (normalizedRequestedMap.containsKey(normNick)) {
                matchedAccounts.add(acc)
            } else {
                remainingAccounts.add(acc)
            }
        }

        val matchedNormNickSet = matchedAccounts.map { it.nickName.trim().lowercase() }.toSet()

        val matchedRequestedNames = mutableListOf<String>()
        val unmatchedRequestedNames = mutableListOf<String>()

        normalizedRequestedMap.forEach { (normKey, origReq) ->
            if (matchedNormNickSet.contains(normKey)) {
                matchedRequestedNames.add(origReq)
            } else {
                unmatchedRequestedNames.add(origReq)
            }
        }

        println("==================================================")
        println("1. Current stored account count: ${accountsList.size}")
        println("2. Requested raw names: ${rawRequested.size}")
        println("3. Unique requested normalized names: ${normalizedRequestedMap.size}")
        println("4. ACTUAL MATCHED ACCOUNT COUNT: ${matchedAccounts.size}")
        println("5. COMPLETE MATCHED ACCOUNT LIST:")
        matchedAccounts.forEach { acc ->
            println(acc.nickName)
        }
        println("6. ACTUAL UNMATCHED REQUESTED NAME COUNT: ${unmatchedRequestedNames.size}")
        println("7. COMPLETE UNMATCHED REQUESTED NAME LIST:")
        unmatchedRequestedNames.forEach { name ->
            println(name)
        }
        println("8. ACTUAL REMAINING ACCOUNT COUNT: ${remainingAccounts.size}")
        println("9. COMPLETE REMAINING ACCOUNT LIST:")
        remainingAccounts.forEach { acc ->
            println(acc.nickName)
        }
        println("10. MATCHED ACCOUNT DETAILS:")
        matchedAccounts.forEach { acc ->
            println("ID=${acc.id} | nickName='${acc.nickName}' | type=${acc.type} | isHidden=${acc.isHidden} | smsParsingEnabled=${acc.smsParsingEnabled}")
        }
        println("==================================================")
    }
}
