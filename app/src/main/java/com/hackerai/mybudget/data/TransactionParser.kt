package com.hackerai.mybudget.data

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

object TransactionParser {
    // 1. Pattern to find amount with currency prefix. 
    // Constrained to max 9 digits before decimal to avoid matching account numbers/utility IDs.
    private val currencyAmountPattern = Pattern.compile("(?i)(?:Rs\\.?|INR|₹|Amt\\.?|Amount|txn)\\s*([\\d,]{1,12}\\.\\d{2}|[\\d,]{1,12}+(?:\\.\\d{1,2})?)")

    // 2. Generic pattern for any number that looks like a decimal amount.
    // Strict boundary checks and length limits.
    private val genericAmountPattern = Pattern.compile("(?<!\\d)([\\d,]{1,9}\\.\\d{2})(?!\\d)")

    // 3. Risky keywords that often precede IDs/Account numbers instead of amounts.
    // We only allow these if they are followed by a currency symbol or a very short number.
    private val riskyAmountPattern = Pattern.compile("(?i)(?:for|spent|paid)\\s*(?:Rs\\.?|INR|₹)\\s*([\\d,]+\\.\\d{2}|[\\d,]+(?:\\.\\d{1,2})?)")

    // Patterns to find the masked account/card tail: "A/c XX4521", "Card xx1234",
    // "ending in 7788", "AC X1234", "acct no XXXXXX7890", "A/c *5678".
    // Tried in order; the first match wins. Anchored on a real account word or on a
    // mask of 2+ X/* characters, so stray letters ("Txn", "Max") can no longer match.
    private val accountDigitPatterns = listOf(
        Pattern.compile("(?i)\\b(?:a/?c|acct|account|card|ac)\\b[^0-9\\n]{0,12}?[xX*\\s-]*?(\\d{3,4})\\b"),
        Pattern.compile("(?i)\\bending\\s*(?:in|with)?\\s*[xX*\\s-]*(\\d{3,4})\\b"),
        Pattern.compile("(?<![\\d.])[xX*]{2,}\\s*-?\\s*(\\d{3,4})\\b")
    )

    /** A masked account/card tail like "XX4521" or "ending 7788". Most bank SMS have this. */
    private fun extractAccountDigits(body: String): String? {
        for (pattern in accountDigitPatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) return matcher.group(1)
        }
        return null
    }

    // Some issuers (Jupiter/CSB, several neobanks) never send a masked last-4 at all —
    // they name the account/card in full instead: "paid from your Edge CSB Bank RuPay
    // Credit Card to X", "credited to your Jupiter Savings Account from Y". These
    // patterns pull that name out as a fallback when no digit tail exists.
    private val accountNamePatterns = listOf(
        Pattern.compile("(?i)from\\s+your\\s+(.+?)\\s+to\\b"),
        Pattern.compile("(?i)(?:credited|deposited)\\s+to\\s+your\\s+(.+?)\\s+(?:a/?c|account|card)\\b"),
        Pattern.compile("(?i)\\byour\\s+([A-Z][A-Za-z0-9&.\\-]*(?:\\s[A-Za-z0-9&.\\-]+){0,6}?\\s(?:credit card|debit card|card|account))\\b")
    )

    private fun extractAccountName(body: String): String? {
        for (pattern in accountNamePatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) return matcher.group(1)?.trim()?.take(60)
        }
        return null
    }

    // Indian DLT sender IDs are routed per-telecom-operator, so the SAME entity shows
    // up under different prefixes depending on which carrier delivered the SMS:
    // "VA-JTEDE-S" (Vodafone route), "TX-JTEDE-S" (Tata route), "AX-JTEDE-S" (Airtel
    // route) are all Jupiter/CSB — only the 2-letter operator prefix changes. The middle
    // token ("JTEDE") is the entity's registered peer code and stays constant. Stripping
    // the prefix/suffix lets us treat all three as one sender for account learning below.
    private val dltSenderPattern = Pattern.compile("(?i)^[A-Za-z]{2}-([A-Za-z0-9]{3,8})-[A-Za-z]$")

    private fun normalizeSenderCore(address: String): String {
        val matcher = dltSenderPattern.matcher(address.trim())
        return if (matcher.matches()) matcher.group(1).uppercase(Locale.ROOT) else address.uppercase(Locale.ROOT)
    }

    // Learned sender-core -> account-name map. Populated whenever a message names its
    // account in full, and reused for later, terser alerts from the same entity (e.g. a
    // "low balance" ping from the same DLT core that never repeats the card name).
    private val knownSenderAccounts = ConcurrentHashMap<String, String>()

    // Pattern to find Payee/Merchant names like "at merchant", "to person", "VPA vpa@id"
    private val payeePattern = Pattern.compile("(?i)(?:at|to|vpa|info|merch|paid to)\\s+([a-z0-9\\.\\s*]+?)(?:on|at|using|date|for|ref|\\.|$)")

    // Keywords to identify transaction type
    private val debitKeywords = listOf("spent", "debited", "paid", "transaction", "purchase", "withdrawn", "withdrawal", "charged", "debit", "paid to", "sent", "transfer", "txn")
    private val creditKeywords = listOf("received", "credited", "deposited", "refund", "credited to", "received from", "cashback", "reversal", "interest", "dividend")

    // Keywords that indicate a message is likely an OTP or non-transactional
    private val excludeKeywords = listOf("OTP", "verification code", "one time password", "code is", "secret code", "is your code")

    // Advertisements, reminders and "will happen later" notices quote an amount but
    // are not a movement of money. Checked before anything else.
    private val futureOrPromoKeywords = listOf(
        "is due", "due on", "due date", "will be credited", "will be debited", "will be charged",
        "apply now", "t&c apply", "offer", "eligible for", "pre-approved", "pre approved",
        "click here", "download", "emi of", "outstanding", "min amt due", "minimum amount due"
    )

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

        if (futureOrPromoKeywords.any { body.contains(it, ignoreCase = true) }) {
            android.util.Log.d("TransactionParser", "Excluded as reminder/promo, not a completed txn")
            return null
        }

        val senderCore = normalizeSenderCore(sms.address)

        // Best-effort account label for display only. NOT a gate — the ViewModel's
        // AccountRepository match (numeric account/card tail, or the user's own
        // smsSenderKeywords against sender+body) runs after this and does the real
        // account assignment. A regex miss here must never drop a real transaction.
        val digitTail = extractAccountDigits(body)
        val nameTail = extractAccountName(body)
        val rememberedName = knownSenderAccounts[senderCore]
        if (nameTail != null && nameTail != rememberedName) {
            knownSenderAccounts[senderCore] = nameTail
        }

        // Try patterns in order of confidence
        var extractedAmount: Double? = null
        
        // Confidence A: Explicit currency symbols
        val currencyMatcher = currencyAmountPattern.matcher(body)
        if (currencyMatcher.find()) {
            extractedAmount = currencyMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
        }

        // Confidence B: Action keywords with symbols
        if (extractedAmount == null) {
            val riskyMatcher = riskyAmountPattern.matcher(body)
            if (riskyMatcher.find()) {
                extractedAmount = riskyMatcher.group(1)?.replace(",", "")?.toDoubleOrNull()
            }
        }

        // Confidence C: Generic decimals (only as last resort)
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

            // THE actual transaction/not-a-transaction gate: require an explicit
            // debit or credit action word.
            if (!isDebit && !isCredit) {
                android.util.Log.d("TransactionParser", "Excluded: amount found but no debit/credit action keyword")
                return null
            }
            if (isDebit) amount = -amount

            val type = if (isCredit) "Income" else "Expense"

            val bankName = sms.address

            val formattedDate = Instant.ofEpochMilli(sms.date).atZone(zoneId).toLocalDate().format(dateFormatter)
            val formattedTime = Instant.ofEpochMilli(sms.date).atZone(zoneId).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))

            // Try to extract Payee
            val payeeMatcher = payeePattern.matcher(body)
            var extractedPayee = if (payeeMatcher.find()) payeeMatcher.group(1)?.trim() else null

            // Clean up common noise in extracted payee
            extractedPayee = extractedPayee?.let {
                it.split("  ")[0] // Stop at double spaces
                  .take(30)
                  .trim()
            }

            // Prefer a masked digit tail ("XX4521"), then a name found in this message,
            // then a name learned earlier for this sender core, then the raw sender ID.
            val account = digitTail ?: nameTail ?: rememberedName ?: bankName

            android.util.Log.d("TransactionParser", "Successfully parsed transaction: $amount from $bankName, account=$account")

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
                account = account,
                tag = "SMS",
                tax = "",
                quantity = 1.0,
                unit = "PCS",
                splitTotal = "",
                rowId = "sms_${sms.id}",
                typeId = sms.date.toString(), // Store millisecond timestamp as stable provenance
                transactionType = type
            )
        }
        return null
    }
}
