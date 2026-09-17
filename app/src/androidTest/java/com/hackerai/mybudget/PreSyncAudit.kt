package com.hackerai.mybudget

import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.hackerai.mybudget.data.*
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PreSyncAudit {

    private val TAG = "PreSyncAudit"

    @Test
    fun performAudit() {
        runBlocking {
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val app = context.applicationContext as MyBudgetApplication
            val dao = app.database.expenseDao()
            val accountRepo = app.accountRepository
            
            val report = StringBuilder()

            report.append("==================================================\n")
            report.append("1. LOCAL DATABASE AUDIT\n")
            report.append("==================================================\n")

            val allForSyncEntities = dao.getAllForSync()
            val allExpenses = allForSyncEntities.filter { !it.isPendingReview && !it.isDiscarded && !it.isDeleted }.map { it.toExpense() }
            
            val expenseCount = allExpenses.count { it.transactionType == "Expense" }
            val incomeCount = allExpenses.count { it.transactionType == "Income" }
            val transferCount = allExpenses.count { it.transactionType == "Transfer" || it.toAccount != null }
            val pendingCount = allForSyncEntities.count { it.isPendingReview }
            val discardedCount = allForSyncEntities.count { it.isDiscarded }
            val accounts = accountRepo.accounts.value

            report.append("Total Active Transactions: ${allExpenses.size}\n")
            report.append("Expenses: $expenseCount\n")
            report.append("Incomes: $incomeCount\n")
            report.append("Transfers: $transferCount\n")
            report.append("Pending Review: $pendingCount\n")
            report.append("Discarded: $discardedCount\n")
            report.append("Accounts Count: ${accounts.size}\n")

            accounts.forEach { acc ->
                // Corrected balance calculation for audit (application's actual logic is buggy)
                val accountTxns = allExpenses.filter { it.account == acc.nickName || it.toAccount == acc.nickName }
                val balance = ExpenseSummaryCalculator.currentBalance(accountTxns, acc.nickName)
                report.append("Account: ${acc.nickName} | Txns: ${accountTxns.size} | Balance: $balance\n")
            }

            report.append("==================================================\n")
            report.append("2. DUPLICATE ANALYSIS\n")
            report.append("==================================================\n")

            val rowIdGroups = allForSyncEntities.groupBy { it.rowId }.filter { it.value.size > 1 }
            report.append("Duplicate rowIds found: ${rowIdGroups.size}\n")

            val fingerprintGroups = allExpenses.groupBy { it.calculateFingerprint() }.filter { it.value.size > 1 }
            report.append("Duplicate Fingerprints: ${fingerprintGroups.size}\n")
            
            var highConfidenceSms = 0
            var ambiguous = 0

            fingerprintGroups.forEach { (_, list) ->
                val sample = list[0]
                val isSms = list.all { it.tag == "SMS" }
                if (isSms) highConfidenceSms++ else ambiguous++
                val cat = if (isSms) "SMS" else "Ambiguous"
                report.append("Group: ${sample.date} | ${sample.amount} | ${sample.account} | Count: ${list.size} | $cat\n")
            }
            
            report.append("Summary: SMS Dups: $highConfidenceSms | Ambiguous Dups: $ambiguous\n")

            report.append("==================================================\n")
            report.append("3. EXTREME VALUE ANALYSIS\n")
            report.append("==================================================\n")

            val largeValues = allForSyncEntities.filter { Math.abs(it.amount) >= 1_000_000_000.0 }
            report.append("Transactions >= 1,000,000,000: ${largeValues.size}\n")
            largeValues.forEach { exp ->
                report.append("Large: ${exp.date} | Amount: ${exp.amount} | Account: ${exp.account} | Desc: ${exp.description.take(50)}\n")
            }

            report.append("==================================================\n")
            report.append("4. GOOGLE DRIVE AUDIT (READ ONLY)\n")
            report.append("==================================================\n")

            val gDrive = GoogleDriveHelper(context)
            val gAccount = gDrive.getLastSignedInAccount()
            if (gAccount != null && gDrive.hasDrivePermission(gAccount)) {
                try {
                    val gData = gDrive.downloadSyncData(gAccount)
                    report.append("Found Google Drive SyncData: v${gData.schemaVersion}\n")
                    report.append("Transactions: ${gData.expenses.size} | Accounts: ${gData.accounts.size}\n")
                    val gTotal = gData.expenses.sumOf { it.amount }
                    report.append("Remote Total Amount: $gTotal\n")
                } catch (e: Exception) {
                    report.append("Google Drive Download Failed: ${e.message}\n")
                }
            } else {
                report.append("Google Drive Not Connected or No Permissions.\n")
            }

            report.append("==================================================\n")
            report.append("5. DROPBOX AUDIT (READ ONLY)\n")
            report.append("==================================================\n")

            val dBox = DropboxHelper(context)
            if (dBox.isConnected()) {
                try {
                    val dData = dBox.downloadSyncData()
                    if (dData != null) {
                        report.append("Found Dropbox SyncData: v${dData.schemaVersion}\n")
                        report.append("Transactions: ${dData.expenses.size} | Accounts: ${dData.accounts.size}\n")
                        val dTotal = dData.expenses.sumOf { it.amount }
                        report.append("Remote Total Amount: $dTotal\n")
                    } else {
                        report.append("Dropbox Sync file not found.\n")
                    }
                } catch (e: Exception) {
                    report.append("Dropbox Download Failed: ${e.message}\n")
                }
            } else {
                report.append("Dropbox Not Connected.\n")
            }

            report.append("==================================================\n")
            report.append("7. TYPEID AUDIT\n")
            report.append("==================================================\n")
            
            val withTypeId = allForSyncEntities.filter { it.typeId.isNotBlank() }
            report.append("Transactions with typeId: ${withTypeId.size} / ${allForSyncEntities.size}\n")

            val file = File(context.getExternalFilesDir(null), "audit_report.txt")
            file.writeText(report.toString())
            Log.i(TAG, "Audit report written to: ${file.absolutePath}")
            println(report.toString())
        }
    }
}
