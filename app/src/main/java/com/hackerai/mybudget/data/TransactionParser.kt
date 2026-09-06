package com.hackerai.mybudget.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.regex.Pattern

object TransactionParser {
    // Improved regex to find amounts in various Indian banking formats:
    // 1. Matches common currency prefixes (Rs, INR, ₹) with optional dots and spaces
    // 2. Looks for keywords like "Amt", "Amount", "spent", "paid" followed by a number
    // 3. Captures numbers with commas (thousands separators) and decimal points
    private val amountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|₹|Amt\\.?|Amount|for)\\s*([\\d,]+\\.\\d{2}|[\\d,]+(?:\\.\\d{1,2})?)")
    
    // Pattern to find account number snippets like "A/c XX1234", "ending in 1234", "Card XX1234", etc.
    private val accountPattern = Pattern.compile("(?i)(?:A/c|Acct|Account|Card|ending in|ending with)\\s*[^0-9]{0,15}?\\s*[X*\\s-]*(\\d{3,4})")

    // Pattern to find Payee/Merchant names like "at merchant", "to person", "VPA vpa@id"
    private val payeePattern = Pattern.compile("(?i)(?:at|to|vpa|info|merch)\\s+([a-z0-9\\.\\s*]+?)(?:on|at|using|date|for|ref|\\.|$)")

    // Keywords to identify transaction type
    private val debitKeywords = listOf("spent", "debited", "paid", "transaction", "purchase", "withdrawn", "withdrawal", "charged", "debit", "paid to", "sent", "transfer")
    private val creditKeywords = listOf("received", "credited", "deposited", "refund", "credited to", "received from", "cashback", "reversal", "interest", "dividend")
    
    // Keywords that indicate a message is likely an OTP or non-transactional, despite containing an amount
    private val excludeKeywords = listOf("OTP", "verification code", "one time password", "code is", "secret code")
    
    private val dateFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy", Locale.getDefault())
    private val zoneId = ZoneId.systemDefault()

    fun parse(sms: SmsMessage): Expense? {
        val body = sms.body
        
        // Exclude OTPs and other non-transactional messages
        if (excludeKeywords.any { body.contains(it, ignoreCase = true) }) return null

        // Refined extraction logic
        val amountMatcher = amountPattern.matcher(body)
        var extractedAmount: Double? = null
        
        if (amountMatcher.find()) {
            val amountStr = amountMatcher.group(1)?.replace(",", "")
            extractedAmount = amountStr?.toDoubleOrNull()
        }
        
        // Fallback: search for any large number if currency prefix wasn't found
        if (extractedAmount == null) {
            val simpleNumberPattern = Pattern.compile("(?<!\\d)([\\d,]+\\.\\d{2})(?!\\d)")
            val simpleMatcher = simpleNumberPattern.matcher(body)
            if (simpleMatcher.find()) {
                extractedAmount = simpleMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }

        if (extractedAmount != null) {
            var amount = extractedAmount
            
            val isDebit = debitKeywords.any { body.contains(it, ignoreCase = true) }
            val isCredit = creditKeywords.any { body.contains(it, ignoreCase = true) }
            
            if (isDebit) {
                amount = -amount
            } else if (!isCredit) {
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

            return Expense(
                date = formattedDate,
                time = formattedTime,
                amount = amount,
                category = "Imported",
                subcategory = "",
                paymentMethod = bankName,
                description = body.take(100), // Increased to see more context
                refCheckNo = "",
                payeePayer = extractedPayee ?: bankName,
                status = "unclear",
                receiptPicture = "",
                account = accountSnippet ?: bankName, // Use snippet if found, otherwise sender ID
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
