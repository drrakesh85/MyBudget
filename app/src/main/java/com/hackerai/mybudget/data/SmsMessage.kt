package com.hackerai.mybudget.data

data class SmsMessage(
    val id: String,
    val address: String, // Sender number/ID
    val body: String,
    val date: Long
)
