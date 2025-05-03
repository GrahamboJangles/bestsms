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
    val contactName: String = "",
    val hasAttachment: Boolean = false,
    val attachmentType: AttachmentType = AttachmentType.NONE,
    val attachmentUri: String = "",
    val attachmentContentType: String = "",
    val attachmentSize: Long = 0
)

enum class AttachmentType {
    NONE,
    IMAGE,
    VIDEO,
    AUDIO,
    DOCUMENT,
    LOCATION,
    CONTACT,
    OTHER
} 