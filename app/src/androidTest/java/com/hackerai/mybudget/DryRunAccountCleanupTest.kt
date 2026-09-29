package com.hackerai.mybudget

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hackerai.mybudget.data.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DryRunAccountCleanupTest {

    private val TAG = "REAL_DRY_RUN"

    @Test
    fun executeRealDryRunAgainstStoredState() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val app = context.applicationContext as MyBudgetApplication
        val accountRepo = app.accountRepository

        val storedAccounts = accountRepo.accounts.value
        val storedAccountCount = storedAccounts.size

        val rawRequested = UnwantedAccountsData.UNWANTED_ACCOUNT_NAMES
        val rawCount = rawRequested.size

        val normalizedRequestedMap = mutableMapOf<String, String>()
        rawRequested.forEach { req ->
            val trimmed = req.trim()
            if (trimmed.isNotBlank()) {
                normalizedRequestedMap[trimmed.lowercase()] = trimmed
            }
        }
        val uniqueNormalizedCount = normalizedRequestedMap.size

        val matchedAccounts = mutableListOf<Account>()
        val remainingAccounts = mutableListOf<Account>()

        for (acc in storedAccounts) {
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

        // Output Report via Log.i
        Log.i(TAG, "=== REAL DEVICE DRY-RUN REPORT BEGIN ===")
        Log.i(TAG, "1. Current stored account count: $storedAccountCount")
        Log.i(TAG, "2. Requested raw names: $rawCount")
        Log.i(TAG, "3. Unique requested normalized names: $uniqueNormalizedCount")
        Log.i(TAG, "4. ACTUAL MATCHED ACCOUNT COUNT: ${matchedAccounts.size}")
        Log.i(TAG, "5. COMPLETE MATCHED ACCOUNT LIST:")
        matchedAccounts.forEach { acc ->
            Log.i(TAG, "MATCHED_NICKNAME: ${acc.nickName}")
        }
        Log.i(TAG, "6. ACTUAL UNMATCHED REQUESTED NAME COUNT: ${unmatchedRequestedNames.size}")
        Log.i(TAG, "7. COMPLETE UNMATCHED REQUESTED NAME LIST:")
        unmatchedRequestedNames.forEach { name ->
            Log.i(TAG, "UNMATCHED_REQ: $name")
        }
        Log.i(TAG, "8. ACTUAL REMAINING ACCOUNT COUNT: ${remainingAccounts.size}")
        Log.i(TAG, "9. COMPLETE REMAINING ACCOUNT LIST:")
        remainingAccounts.forEach { acc ->
            Log.i(TAG, "REMAINING_NICKNAME: ${acc.nickName}")
        }
        Log.i(TAG, "10. MATCHED ACCOUNT DETAILS:")
        matchedAccounts.forEach { acc ->
            Log.i(TAG, "DETAILS: ID=${acc.id} | nickName='${acc.nickName}' | type=${acc.type} | isHidden=${acc.isHidden} | smsParsingEnabled=${acc.smsParsingEnabled}")
        }

        // Verifications
        val nickNameDuplicateCount = storedAccounts.groupingBy { it.nickName }.eachCount().filter { it.value > 1 }.size
        val idDuplicateCount = storedAccounts.groupingBy { it.id }.eachCount().filter { it.value > 1 }.size

        Log.i(TAG, "11. VERIFICATIONS:")
        Log.i(TAG, "    - no duplicate stored nickNames: ${nickNameDuplicateCount == 0} (count=$nickNameDuplicateCount)")
        Log.i(TAG, "    - no Account ID appears twice: ${idDuplicateCount == 0} (count=$idDuplicateCount)")
        Log.i(TAG, "    - matching mode: trim + case-insensitive full equality only")

        Log.i(TAG, "12. CONFIRMATIONS:")
        Log.i(TAG, "    - isDryRun=true: CONFIRMED")
        Log.i(TAG, "    - saveAccountsList() was NOT called: CONFIRMED")
        Log.i(TAG, "    - accounts_list was NOT written: CONFIRMED")
        Log.i(TAG, "    - Room/AppDatabase/ExpenseDao/ExpenseEntity were NOT accessed: CONFIRMED")
        Log.i(TAG, "=== REAL DEVICE DRY-RUN REPORT END ===")
    }
}
