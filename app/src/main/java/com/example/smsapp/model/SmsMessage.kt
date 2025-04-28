package com.example.smsapp.model

data class SmsMessage(
    val id: String = "",
    val address: String,
    val body: String,
    val date: Long = System.currentTimeMillis(),
    val type: Int = 1, // 1 = inbox, 2 = sent
    val read: Int = 0, // 0 = unread, 1 = read
    val threadId: Long = 0,
    val isRcs: Boolean = false,
    val contactName: String = ""
) 