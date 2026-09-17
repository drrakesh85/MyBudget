package com.hackerai.mybudget.data

sealed interface Account {
    val id: String
    val nickName: String
    val type: AccountType
    val isHidden: Boolean
    val smsSenderKeywords: String
}

enum class AccountType {
    SAVING, LOAN, CREDIT_CARD, CASH
}

data class SavingAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val branchName: String,
    val accountNumber: String,
    override val isHidden: Boolean = false,
    override val smsSenderKeywords: String = ""
) : Account {
    override val type: AccountType = AccountType.SAVING
}

data class LoanAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val branchName: String,
    val accountNumber: String,
    override val isHidden: Boolean = false,
    override val smsSenderKeywords: String = ""
) : Account {
    override val type: AccountType = AccountType.LOAN
}

data class CreditCardAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val cardNumber: String,
    val expiry: String,
    val cvv: String,
    val billingDate: Int,
    val dueDate: Int,
    override val isHidden: Boolean = false,
    override val smsSenderKeywords: String = ""
) : Account {
    override val type: AccountType = AccountType.CREDIT_CARD
}

data class CashAccount(
    override val id: String,
    override val nickName: String,
    override val isHidden: Boolean = false,
    override val smsSenderKeywords: String = ""
) : Account {
    override val type: AccountType = AccountType.CASH
}
