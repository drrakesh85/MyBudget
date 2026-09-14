package com.hackerai.mybudget.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

object TransactionParser {
    // 1. Pattern to find amount with currency prefix
    private val currencyAmountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|₹|Amt\\.?|Amount|for|debited|credited|paid|spent|received|txn)\\s*([\\d,]+\\.\\d{2}|[\\d,]+(?:\\.\\d{1,2})?)")
    
    // 2. Fallback pattern for any number that looks like a decimal amount (max 8 digits before decimal)
    private val genericAmountPattern = Pattern.compile("(?<!\\d)([\\d,]{1,9}\\.\\d{2})(?!\\d)")

    // Pattern to find account number snippets like "A/c XX1234", "ending in 1234", "Card XX1234", etc.
    private val accountPattern = Pattern.compile("(?i)(?:A/c|Acct|Account|Card|ending in|ending with|no\\.|X{1,10}|[*]{1,10})\\s*[^0-9]{0,15}?\\s*[X*\\s-]*(\\d{3,4})")

    // Pattern to find Payee/Merchant names like "at merchant", "to person", "VPA vpa@id"
    private val payeePattern = Pattern.compile("(?i)(?:at|to|vpa|info|merch|paid to)\\s+([a-z0-9\\.\\s*]+?)(?:on|at|using|date|for|ref|\\.|$)")

    // Keywords to identify transaction type
    private val debitKeywords = listOf("spent", "debited", "paid", "transaction", "purchase", "withdrawn", "withdrawal", "charged", "debit", "paid to", "sent", "transfer", "txn")
    private val creditKeywords = listOf("received", "credited", "deposited", "refund", "credited to", "received from", "cashback", "reversal", "interest", "dividend")
    
    // Keywords that indicate a message is likely an OTP or non-transactional
    private val excludeKeywords = listOf("OTP", "verification code", "one time password", "code is", "secret code", "is your code")
    
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    fun parse(sms: SmsMessage): Expense? {
        val body = sms.body
        android.util.Log.d("TransactionParser", "Parsing SMS from ${sms.address}: $body")
        
        // Exclude OTPs and other non-transactional messages
        if (excludeKeywords.any { body.contains(it, ignoreCase = true) }) {
            android.util.Log.d("TransactionParser", "Excluded as OTP/non-transactional")
            return null
        }

        // Try currency pattern first
        var extractedAmount: Double? = null
        val currencyMatcher = currencyAmountPattern.matcher(body)
        if (currencyMatcher.find()) {
            extractedAmount = currencyMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }

        // Try generic pattern if currency pattern failed
        if (extractedAmount == null) {
            val genericMatcher = genericAmountPattern.matcher(body)
            if (genericMatcher.find()) {
                extractedAmount = genericMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }

        if (extractedAmount != null) {
            var amount = extractedAmount
            
            val isDebit = debitKeywords.any { body.contains(it, ignoreCase = true) }
            val isCredit = creditKeywords.any { body.contains(it, ignoreCase = true) }
            
            // If it's not clearly a credit, treat it as an expense for safety if it contains debit keywords
            if (isDebit) {
                amount = -amount
            } else if (!isCredit) {
                // If neither found, default to expense if message is from a bank-like sender
                amount = -amount
            }

            val type = when {
                isCredit -> "Income"
                isDebit -> "Expense"
                else -> "Expense"
            }

            val bankName = sms.address
            
            val formattedDate = Instant.ofEpochMilli(sms.date).atZone(zoneId).toLocalDate().format(dateFormatter)
            val formattedTime = Instant.ofEpochMilli(sms.date).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

            // Try to extract account snippet
            val acctMatcher = accountPattern.matcher(body)
            val accountSnippet = if (acctMatcher.find()) acctMatcher.group(1) else null

            // Try to extract Payee
            val payeeMatcher = payeePattern.matcher(body)
            var extractedPayee = if (payeeMatcher.find()) payeeMatcher.group(1)?.trim() else null
            
            // Clean up common noise in extracted payee
            extractedPayee = extractedPayee?.let { 
                it.split("  ")[0] // Stop at double spaces
                  .take(30)
                  .trim()
            }

            android.util.Log.d("TransactionParser", "Successfully parsed transaction: $amount from $bankName")

            return Expense(
                date = formattedDate,
                time = formattedTime,
                amount = amount,
                category = "",
                subcategory = "",
                paymentMethod = bankName,
                description = body.take(100),
                refCheckNo = "",
                payeePayer = extractedPayee ?: "",
                status = "unclear",
                receiptPicture = "",
                account = accountSnippet ?: bankName,
                tag = "SMS",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "sms_${sms.id}",
                typeId = "",
                transactionType = type
            )
        }
        return null
    }
}
