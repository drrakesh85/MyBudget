package com.hackerai.mybudget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.hackerai.mybudget.data.SmsMessage
import com.hackerai.mybudget.data.TransactionParser
import kotlinx.coroutines.launch

class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        val app = context.applicationContext as? MyBudgetApplication ?: return
        val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return

        val pendingResult = goAsync()
        app.applicationScope.launch {
            try {
                for (msg in messages) {
                    val smsMessage = SmsMessage(
                        id = System.currentTimeMillis().toString() + msg.originatingAddress.orEmpty(),
                        address = msg.originatingAddress ?: "Unknown",
                        body = msg.messageBody ?: "",
                        date = msg.timestampMillis
                    )
                    val expense = TransactionParser.parse(smsMessage) ?: continue
                    app.expenseRepository.insertPendingIfNew(expense)
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
