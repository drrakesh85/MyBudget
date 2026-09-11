package com.hackerai.mybudget.data

sealed interface Account {
    val id: String
    val nickName: String
    val type: AccountType
    val isHidden: Boolean
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
    override val isHidden: Boolean = false
) : Account {
    override val type: AccountType = AccountType.SAVING
}

data class LoanAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val branchName: String,
    val accountNumber: String,
    override val isHidden: Boolean = false
) : Account {
    override val type: AccountType = AccountType.LOAN
}

data class CreditCardAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val cardNumber: String,
    val expiry: String,
    val billingDate: Int,
    val dueDate: Int,
    override val isHidden: Boolean = false
) : Account {
    override val type: AccountType = AccountType.CREDIT_CARD
}

data class CashAccount(
    override val id: String,
    override val nickName: String,
    override val isHidden: Boolean = false
) : Account {
    override val type: AccountType = AccountType.CASH
}
