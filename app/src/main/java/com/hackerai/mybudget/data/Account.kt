package com.hackerai.mybudget.data

sealed interface Account {
    val id: String
    val nickName: String
    val type: AccountType
}

enum class AccountType {
    SAVING, LOAN, CREDIT_CARD, CASH
}

class SavingAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val branchName: String,
    val accountNumber: String
) : Account {
    override val type: AccountType = AccountType.SAVING
}

class LoanAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val branchName: String,
    val accountNumber: String
) : Account {
    override val type: AccountType = AccountType.LOAN
}

class CreditCardAccount(
    override val id: String,
    override val nickName: String,
    val bankName: String,
    val cardNumber: String,
    val expiry: String,
    val billingDate: Int,
    val dueDate: Int
) : Account {
    override val type: AccountType = AccountType.CREDIT_CARD
}

class CashAccount(
    override val id: String,
    override val nickName: String
) : Account {
    override val type: AccountType = AccountType.CASH
}
