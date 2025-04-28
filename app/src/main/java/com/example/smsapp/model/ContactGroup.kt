package com.example.smsapp.model

data class ContactGroup(
    val id: String,
    val name: String,
    val contacts: List<String> = listOf()  // List of contact addresses in this group
) 