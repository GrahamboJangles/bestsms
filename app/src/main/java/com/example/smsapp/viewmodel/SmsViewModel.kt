package com.example.smsapp.viewmodel

import android.app.Application
import android.util.Log
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.smsapp.model.ContactGroup
import com.example.smsapp.model.Conversation
import com.example.smsapp.model.SmsMessage
import com.example.smsapp.util.SmsUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SmsViewModel(application: Application) : AndroidViewModel(application) {
    
    private companion object {
        const val TAG = "SmsViewModel"
    }
    
    // Messages for all conversations
    private val _allMessages = mutableStateListOf<SmsMessage>()
    
    // Messages for the current conversation
    private val _messages = mutableStateListOf<SmsMessage>()
    val messages: List<SmsMessage> get() = _messages
    
    // List of all conversations
    private val _allConversations = mutableStateListOf<Conversation>()
    
    // List of visible conversations (filtered by selected group)
    private val _conversations = mutableStateListOf<Conversation>()
    val conversations: List<Conversation> get() = _conversations
    
    // Contact groups
    private val _groups = mutableStateListOf<ContactGroup>()
    val groups: List<ContactGroup> get() = _groups
    
    // Current group selection ("all" is a special value for all conversations)
    val selectedGroupId = mutableStateOf("all")
    
    // Dialog state for group creation
    val showGroupDialog = mutableStateOf(false)
    val newGroupName = mutableStateOf("")
    
    // Current conversation contact
    val currentContact = mutableStateOf("")
    
    // New message composition
    val currentRecipient = mutableStateOf("")
    val currentMessage = mutableStateOf("")
    
    val permissionsGranted = mutableStateOf(false)
    
    // App navigation state
    val isInConversationList = mutableStateOf(true)
    
    // Use for diagnostic info
    val diagnosticInfo = mutableStateOf("")
    
    // New state for loading messages
    private val _isLoadingMessages = mutableStateOf(false)
    
    init {
        checkPermissions()
        initDefaultGroups()
    }
    
    private fun initDefaultGroups() {
        // Add default "All" group (implicit, not needed in the actual group list)
        // Add a "Favorites" group
        _groups.add(ContactGroup(
            id = "favorites",
            name = "Favorites"
        ))
        
        // Add "Family" group
        _groups.add(ContactGroup(
            id = "family",
            name = "Family"
        ))
        
        // Add "Work" group
        _groups.add(ContactGroup(
            id = "work",
            name = "Work"
        ))
    }
    
    fun checkPermissions() {
        permissionsGranted.value = SmsUtils.hasPermissions(getApplication())
        if (permissionsGranted.value) {
            loadMessages()
        }
    }
    
    fun loadMessages() {
        viewModelScope.launch {
            _isLoadingMessages.value = true

            withContext(Dispatchers.IO) {
                try {
                    val allMessages = SmsUtils.retrieveAllMessages(getApplication())
                    
                    // Update diagnostic info
                    diagnosticInfo.value = SmsUtils.appDiagnosticInfo
                    
                    // Create conversation map from messages
                    val conversationMap = mutableMapOf<Long, MutableList<SmsMessage>>()
                    
                    for (message in allMessages) {
                        val threadId = message.threadId
                        if (!conversationMap.containsKey(threadId)) {
                            conversationMap[threadId] = mutableListOf()
                        }
                        conversationMap[threadId]?.add(message)
                    }
                    
                    // Process the conversations
                    val conversations = mutableListOf<Conversation>()
                    
                    for ((threadId, messages) in conversationMap) {
                        if (messages.isNotEmpty()) {
                            // Sort messages by date
                            messages.sortByDescending { it.date }
                            
                            // Use the newest message details for the conversation
                            val latestMessage = messages.first()
                            val contactName = if (latestMessage.contactName.isNotEmpty()) {
                                latestMessage.contactName
                            } else {
                                // Try to get contact name from the address
                                SmsUtils.getContactName(getApplication(), latestMessage.address)
                            }
                            
                            // Create conversation object
                            val conversation = Conversation(
                                id = threadId,
                                address = latestMessage.address,
                                contactName = contactName,
                                lastMessagePreview = latestMessage.body,
                                lastMessageTimestamp = latestMessage.date,
                                unreadCount = messages.count { it.read == 0 },
                                hasRcsMessages = messages.any { it.isRcs }
                            )
                            
                            conversations.add(conversation)
                        }
                    }
                    
                    // Sort conversations by timestamp (newest first)
                    conversations.sortByDescending { it.lastMessageTimestamp }
                    
                    withContext(Dispatchers.Main) {
                        _allMessages.clear()
                        _allMessages.addAll(allMessages)
                        _allConversations.clear()
                        _allConversations.addAll(conversations)
                        _conversations.clear()
                        _conversations.addAll(conversations)
                        updateFilteredConversations()
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error loading messages", e)
                    diagnosticInfo.value = "Error loading messages: ${e.message}"
                }
                
                _isLoadingMessages.value = false
            }
        }
    }
    
    private fun updateFilteredConversations() {
        filterConversationsByGroup()
    }
    
    fun filterConversationsByGroup() {
        _conversations.clear()
        
        if (selectedGroupId.value == "all") {
            // Show all conversations
            _conversations.addAll(_allConversations)
        } else {
            // Filter by group
            val group = _groups.find { it.id == selectedGroupId.value }
            if (group != null) {
                val filteredConversations = _allConversations.filter { 
                    group.contacts.contains(it.address) 
                }
                _conversations.addAll(filteredConversations)
            }
        }
    }
    
    fun selectGroup(groupId: String) {
        selectedGroupId.value = groupId
        filterConversationsByGroup()
    }
    
    fun addGroup(name: String) {
        if (name.isNotEmpty()) {
            val newGroupId = "group_${System.currentTimeMillis()}"
            _groups.add(ContactGroup(id = newGroupId, name = name))
            newGroupName.value = ""
            showGroupDialog.value = false
        }
    }
    
    fun deleteGroup(groupId: String) {
        _groups.removeIf { it.id == groupId }
        if (selectedGroupId.value == groupId) {
            selectedGroupId.value = "all"
            filterConversationsByGroup()
        }
    }
    
    fun addContactToGroup(groupId: String, contactAddress: String) {
        val index = _groups.indexOfFirst { it.id == groupId }
        if (index >= 0) {
            val group = _groups[index]
            val updatedContacts = group.contacts.toMutableList()
            
            if (!updatedContacts.contains(contactAddress)) {
                updatedContacts.add(contactAddress)
                _groups[index] = group.copy(contacts = updatedContacts)
                
                // Refresh filtered list if we're currently viewing this group
                if (selectedGroupId.value == groupId) {
                    filterConversationsByGroup()
                }
            }
        }
    }
    
    fun removeContactFromGroup(groupId: String, contactAddress: String) {
        val index = _groups.indexOfFirst { it.id == groupId }
        if (index >= 0) {
            val group = _groups[index]
            val updatedContacts = group.contacts.toMutableList()
            
            if (updatedContacts.remove(contactAddress)) {
                _groups[index] = group.copy(contacts = updatedContacts)
                
                // Refresh filtered list if we're currently viewing this group
                if (selectedGroupId.value == groupId) {
                    filterConversationsByGroup()
                }
            }
        }
    }
    
    fun isContactInGroup(groupId: String, contactAddress: String): Boolean {
        return _groups.find { it.id == groupId }?.contacts?.contains(contactAddress) ?: false
    }
    
    fun selectConversation(contactAddress: String) {
        currentContact.value = contactAddress
        isInConversationList.value = false
        
        // Filter messages for this conversation
        _messages.clear()
        // Sort messages by date (oldest first) so newest messages appear at the bottom
        val filteredMessages = _allMessages.filter { it.address == contactAddress }
                                         .sortedBy { it.date }
        _messages.addAll(filteredMessages)
        
        // Pre-fill the recipient field
        currentRecipient.value = contactAddress
    }
    
    fun goToConversationList() {
        isInConversationList.value = true
        currentContact.value = ""
    }
    
    fun composeNewMessage() {
        isInConversationList.value = false
        currentContact.value = ""
        currentRecipient.value = ""
        currentMessage.value = ""
        _messages.clear()
    }
    
    fun sendMessage() {
        if (currentRecipient.value.isNotEmpty() && currentMessage.value.isNotEmpty()) {
            val recipient = currentRecipient.value
            val messageText = currentMessage.value
            
            SmsUtils.sendSms(recipient, messageText)
            
            // Look up contact name
            val contactName = SmsUtils.getContactName(getApplication(), recipient)
            
            // Create sent message object
            val sentMessage = SmsMessage(
                id = "",
                address = recipient,
                body = messageText,
                date = System.currentTimeMillis(),
                type = android.provider.Telephony.Sms.MESSAGE_TYPE_SENT,
                read = 1,
                threadId = 0,
                isRcs = false,
                contactName = contactName
            )
            
            // Add to current conversation (maintaining chronological order)
            _messages.add(sentMessage)
            
            // Add to all messages
            _allMessages.add(sentMessage)
            
            // Update conversations list
            updateFilteredConversations()
            
            // Select this conversation
            if (currentContact.value.isEmpty()) {
                currentContact.value = recipient
            }
            
            // Clear the message input
            currentMessage.value = ""
        }
    }
} 