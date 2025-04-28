package com.example.smsapp.util

import android.Manifest
import android.app.Activity
import android.content.ContentResolver
import android.content.Context
import android.content.pm.PackageManager
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.ContactsContract
import android.provider.Telephony
import android.telephony.PhoneNumberUtils
import android.telephony.SmsManager
import android.util.Log
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.smsapp.model.SmsMessage
import java.io.File
import java.io.FileOutputStream
import java.lang.ref.WeakReference
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

object SmsUtils {
    
    private const val TAG = "SmsUtils"
    private const val SMS_PERMISSION_REQUEST_CODE = 123
    
    // For diagnostic output
    private var _diagnosticInfo = ""
    val appDiagnosticInfo: String get() = _diagnosticInfo
    
    // Cache for contact lookups to improve performance
    private val contactNameCache = ConcurrentHashMap<String, String>()
    
    // Multiple URIs to try for retrieving messages (some might work better on different devices)
    private val INBOX_URIS = arrayOf(
        Telephony.Sms.Inbox.CONTENT_URI,
        Uri.parse("content://sms/inbox")
    )
    
    private val SENT_URIS = arrayOf(
        Telephony.Sms.Sent.CONTENT_URI,
        Uri.parse("content://sms/sent")
    )
    
    private val ALL_SMS_URIS = arrayOf(
        Telephony.Sms.CONTENT_URI,
        Uri.parse("content://sms/all")
    )
    
    // Standard message URIs to try in order
    private val STANDARD_MESSAGE_URIS = listOf(
        Uri.parse("content://sms/all"),
        Telephony.Sms.CONTENT_URI,
        Uri.parse("content://sms/inbox"),
        Telephony.Sms.Inbox.CONTENT_URI,
        Uri.parse("content://sms/sent"),
        Telephony.Sms.Sent.CONTENT_URI,
        Uri.parse("content://mms-sms/conversations"),
        Uri.parse("content://mms-sms/complete-conversations")
    )
    
    // Samsung RCS/Advanced Messaging URIs - expanded list with S24-specific providers
    private val SAMSUNG_ADVANCED_MESSAGE_URIS = arrayOf(
        // Standard Samsung RCS URIs
        Uri.parse("content://com.samsung.rcs.im/message"),
        Uri.parse("content://com.samsung.message/message"),
        Uri.parse("content://com.samsung.rcs/message"),
        Uri.parse("content://com.samsung.rcs.service/message"),
        Uri.parse("content://com.samsung.messaging/messages"),
        Uri.parse("content://com.samsung.provider.messagingprovider/message"),
        Uri.parse("content://com.samsung.cmessaging/message"),
        
        // S24-specific provider paths (based on newer Samsung content providers)
        Uri.parse("content://com.samsung.android.messaging/message"),
        Uri.parse("content://com.samsung.android.messaging.provider/message"),
        Uri.parse("content://com.samsung.android.messagingprovider/message"),
        Uri.parse("content://com.samsung.android.messaging/conversations/messages"),
        Uri.parse("content://com.samsung.android.messaging.provider/conversations/messages"),
        Uri.parse("content://com.samsung.android.messaging/threads"),
        Uri.parse("content://com.samsung.android.messaging/conversations"),
        
        // Deeper Samsung provider paths
        Uri.parse("content://com.samsung.android.providers.messagingprovider/message"),
        Uri.parse("content://com.samsung.message.provider/message"),
        Uri.parse("content://com.samsung.msg.provider/message"),
        
        // Samsung database direct access attempts
        Uri.parse("content://com.samsung.android.messaging.provider.im/messages"),
        Uri.parse("content://com.samsung.android.messaging.provider.im/conversations"),
        
        // OneUI 6.0 specific paths (S24 Ultra runs on OneUI 6.0)
        Uri.parse("content://com.samsung.onetui.messaging/message"),
        Uri.parse("content://com.samsung.onetui.messaging.provider/message")
    )
    
    // Conversation URIs that might contain RCS messages
    private val CONVERSATION_URIS = arrayOf(
        Uri.parse("content://mms-sms/conversations"),
        Uri.parse("content://mms-sms/complete-conversations"),
        Uri.parse("content://threads/"),
        Uri.parse("content://com.samsung.android.messaging/threads"),
        Uri.parse("content://com.samsung.android.messaging/conversations")
    )
    
    // Add these new Samsung S24 specific URIs
    private val SAMSUNG_S24_URIS = arrayOf(
        Uri.parse("content://com.samsung.rcs.im/rcsim"),
        Uri.parse("content://com.samsung.rcs/message"),
        Uri.parse("content://com.samsung.android.messaging.chattable/messages"),
        Uri.parse("content://com.samsung.android.messaging.provider/messages"),
        Uri.parse("content://com.samsung.message/messages"),
        Uri.parse("content://com.samsung.android.providers.messageprovider/messages"),
        Uri.parse("content://com.sec.mms.provider/messages"),
        Uri.parse("content://com.samsung.rcs.service/messages")
    )
    
    // Track statistics for diagnostic purposes
    private val inboxCount = AtomicInteger(0)
    private val sentCount = AtomicInteger(0)
    private val allSmsCount = AtomicInteger(0)
    private val rcsCount = AtomicInteger(0)
    private val retrievalErrors = mutableListOf<String>()
    
    // Debug output storage for diagnostic purposes
    private val debugOutput = StringBuilder()
    
    // Diagnostic tracking variables
    private var contentProviderAttempts = 0
    private var messagesFound = 0
    private var rcsMessagesFound = 0
    private var latestSuccessfulProvider = ""
    private var appContextRef: WeakReference<Context>? = null
    
    // Samsung Advanced Messaging URIs
    private val SAMSUNG_MESSAGING_URIS = listOf(
        "content://im/chat",
        "content://im/message",
        "content://mms-sms/complete-conversations",
        "content://com.samsung.android.messaging.provider/conversations",
        "content://com.samsung.android.messaging.provider/messages",
        "content://com.samsung.android.messagingapp.provider/messages",
        "content://com.samsung.android.messagingapp.provider/conversations",
        "content://com.samsung.rcs.im.provider/conversations",
        "content://com.samsung.rcs.im.provider/rcs_messages",
        "content://com.samsung.rcs.provider/messages",
        "content://com.samsung.rcs/messages",
        "content://com.samsung.im.provider/rcs_messages",
        "content://com.sec.mms/conversations",
        "content://com.sec.mms/messages",
        "content://com.sec.sms/conversations",
        "content://com.sec.sms/messages",
        // S24 Ultra specific providers
        "content://com.samsung.android.messaging/messages",
        "content://com.samsung.android.messaging/rcs_messages",
        "content://com.samsung.android.messaging.provider/rcs_messages",
        "content://mms-sms-v2/messages",
        "content://mms-sms-v2/conversations",
        "content://com.samsung.android.messaging.provider.im/messages",
        "content://com.samsung.android.messaging.provider.im/conversations",
        // More One UI 6 providers
        "content://com.samsung.android.messaging.provider/chat_history",
        "content://com.samsung.android.messaging.provider/chat_messages",
        "content://one.samsung.android.messaging/messages",
        "content://one.samsung.android.messaging/rcs",
        // Additional Samsung S24-specific URIs (One UI 6.1)
        "content://com.samsung.android.messaging.provider/rcs_chat",
        "content://com.samsung.android.messaging.provider/advanced_messaging",
        "content://com.samsung.android.providers.messaging/rcs_messages",
        "content://com.samsung.android.providers.messaging/chat",
        "content://com.samsung.android.messaging.provider/am_messages",
        "content://messages/rcs",
        "content://messages/advanced",
        "content://com.google.android.apps.messaging.provider/messages",
        "content://rcs.im/conversations",
        "content://rcs.im/messages",
        // Direct access to databases (requires proper permissions)
        "content://com.samsung.android.provider.messageprovider/rcs"
    )
    
    private val MESSAGE_DEDUPLICATION_KEYS = listOf(
        "body", "date", "address"
    )
    
    fun requestSmsPermission(activity: Activity) {
        val permissions = arrayOf(
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.READ_CONTACTS
        )
        
        ActivityCompat.requestPermissions(activity, permissions, SMS_PERMISSION_REQUEST_CODE)
    }
    
    fun hasPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.SEND_SMS) == 
                PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == 
                PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.RECEIVE_SMS) == 
                PackageManager.PERMISSION_GRANTED &&
               ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CONTACTS) == 
                PackageManager.PERMISSION_GRANTED
    }
    
    fun sendSms(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(phoneNumber, null, message, null, null)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending SMS: ${e.message}", e)
        }
    }
    
    fun getInboxMessages(context: Context): List<SmsMessage> {
        return retrieveAllMessages(context)
    }
    
    /**
     * Log installed content providers for debugging purposes
     */
    private fun logInstalledContentProviders(context: Context) {
        try {
            val packageManager = context.packageManager
            val providerList = packageManager.queryContentProviders(null, 0, 0)
            
            logDebug("Found ${providerList.size} content providers on device:")
            val samsungProviders = mutableListOf<String>()
            val messagingProviders = mutableListOf<String>()
            
            providerList.forEach { providerInfo ->
                val authority = providerInfo.authority ?: return@forEach
                
                if (authority.contains("samsung", ignoreCase = true) && 
                    (authority.contains("messag", ignoreCase = true) || 
                     authority.contains("sms", ignoreCase = true) || 
                     authority.contains("mms", ignoreCase = true) ||
                     authority.contains("rcs", ignoreCase = true))) {
                    samsungProviders.add(authority)
                } else if (authority.contains("messag", ignoreCase = true) || 
                           authority.contains("sms", ignoreCase = true) || 
                           authority.contains("mms", ignoreCase = true) ||
                           authority.contains("chat", ignoreCase = true) ||
                           authority.contains("rcs", ignoreCase = true)) {
                    messagingProviders.add(authority)
                }
            }
            
            logDebug("Samsung messaging providers: $samsungProviders")
            logDebug("Other messaging providers: $messagingProviders")
            
            // Try accessing these discovered providers
            for (provider in samsungProviders) {
                tryScanProvider(context, provider)
            }
        } catch (e: Exception) {
            logDebug("Error enumerating content providers: ${e.message}")
        }
    }
    
    /**
     * Try scanning a discovered provider
     */
    private fun tryScanProvider(context: Context, provider: String) {
        try {
            val uri = Uri.parse("content://$provider")
            
            // Try to query this provider
            context.contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )?.use { cursor ->
                logDebug("Successfully accessed provider: $provider with ${cursor.count} records")
                
                // If it has records, try to process them
                if (cursor.count > 0) {
                    // Dump first record column names for debug
                    val columns = cursor.columnNames.joinToString()
                    logDebug("Provider $provider columns: $columns")
                    
                    // If it has message-like columns, try to process as messages
                    if (columns.contains("body") || columns.contains("text") || columns.contains("content")) {
                        logDebug("Provider appears to contain messages, attempting to process...")
                        // TODO: Implement processing if needed
                    }
                }
            }
        } catch (e: Exception) {
            // Log but continue - this is just exploratory
            logDebug("Cannot access provider $provider: ${e.message}")
        }
    }
    
    /**
     * Scan all conversation threads for messages
     */
    private fun retrieveMessagesFromConversations(
        context: Context,
        contentResolver: ContentResolver,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        for (uri in CONVERSATION_URIS) {
            try {
                logDebug("Attempting to access conversation URI: $uri")
                contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    logDebug("Found ${cursor.count} conversations in $uri")
                    
                    // Process each conversation to find its messages
                    if (cursor.count > 0) {
                        val columnNames = cursor.columnNames
                        val threadIdIndex = findColumnIndex(columnNames, arrayOf(
                            "thread_id", "_id", "id", "tid", "conversation_id"
                        ))
                        
                        if (threadIdIndex != -1) {
                            while (cursor.moveToNext()) {
                                val threadId = cursor.getLong(threadIdIndex)
                                scanConversationMessages(context, contentResolver, threadId, messageList, processedIds)
                            }
                        } else {
                            logDebug("Could not find thread ID column in $uri")
                        }
                    }
                }
            } catch (e: Exception) {
                logDebug("Error accessing conversation URI $uri: ${e.message}")
            }
        }
    }
    
    /**
     * Scan messages in a specific conversation thread
     */
    private fun scanConversationMessages(
        context: Context,
        contentResolver: ContentResolver,
        threadId: Long,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        // Try multiple URIs that might contain this thread's messages
        val threadUris = arrayOf(
            Uri.parse("content://sms/conversations/$threadId"),
            Uri.parse("content://mms-sms/conversations/$threadId"),
            Uri.parse("content://com.samsung.android.messaging/conversations/$threadId/messages"),
            Uri.parse("content://com.samsung.android.messaging.provider/conversations/$threadId/messages")
        )
        
        for (uri in threadUris) {
            try {
                contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.count > 0) {
                        logDebug("Found ${cursor.count} messages in conversation thread $threadId at $uri")
                        val bodyDateMap = HashSet<String>()
                        processAdvancedMessages(context, cursor, messageList, processedIds, bodyDateMap)
                    }
                }
            } catch (e: Exception) {
                // Log but continue trying other URIs
                logDebug("Cannot access thread $threadId at $uri: ${e.message}")
            }
        }
    }
    
    /**
     * Retrieve Samsung Advanced Messaging (RCS) messages
     */
    private fun retrieveSamsungAdvancedMessages(
        context: Context,
        contentResolver: ContentResolver,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        // Samsung uses multiple possible URIs for RCS messages
        for (uri in SAMSUNG_ADVANCED_MESSAGE_URIS) {
            logDebug("Attempting to access Samsung Advanced Messaging URI: $uri")
            try {
                // Test if the URI is accessible
                val testCursor = contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )
                
                if (testCursor == null) {
                    logDebug("URI not accessible: $uri")
                    continue
                }
                
                // Found accessible URI - check columns and count
                val columnNames = testCursor.columnNames
                logDebug("Found ${testCursor.count} messages in $uri with columns: ${columnNames.joinToString()}")
                testCursor.close()
                
                // Now do the real query with all columns to handle any schema
                contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    null
                )?.use { cursor ->
                    val bodyDateMap = HashSet<String>()
                    processAdvancedMessages(context, cursor, messageList, processedIds, bodyDateMap)
                }
            } catch (e: Exception) {
                logDebug("Error accessing Samsung Advanced Messaging URI $uri: ${e.message}")
            }
        }
    }
    
    /**
     * Retrieve messages from a specific URI with standard method
     */
    private fun retrieveMessages(context: Context, uri: Uri): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val contentResolver = context.contentResolver
        
        try {
            contentResolver.query(
                uri,
                null,
                null,
                null,
                "date DESC"
            )?.use { cursor ->
                val columnNames = cursor.columnNames
                
                // Map column indices - handle different provider schemas
                val idIndex = columnNames.indexOf("_id").takeIf { it >= 0 } 
                    ?: columnNames.indexOf("id").takeIf { it >= 0 } 
                    ?: columnNames.indexOf("message_id").takeIf { it >= 0 } 
                    ?: -1
                
                val addressIndex = columnNames.indexOf("address").takeIf { it >= 0 } 
                    ?: columnNames.indexOf("phone").takeIf { it >= 0 }
                    ?: columnNames.indexOf("sender").takeIf { it >= 0 }
                    ?: columnNames.indexOf("recipient").takeIf { it >= 0 }
                    ?: columnNames.indexOf("number").takeIf { it >= 0 }
                    ?: -1
                
                val bodyIndex = columnNames.indexOf("body").takeIf { it >= 0 }
                    ?: columnNames.indexOf("text").takeIf { it >= 0 }
                    ?: columnNames.indexOf("message_body").takeIf { it >= 0 }
                    ?: columnNames.indexOf("content").takeIf { it >= 0 }
                    ?: -1
                
                val dateIndex = columnNames.indexOf("date").takeIf { it >= 0 }
                    ?: columnNames.indexOf("timestamp").takeIf { it >= 0 }
                    ?: columnNames.indexOf("time").takeIf { it >= 0 }
                    ?: columnNames.indexOf("date_sent").takeIf { it >= 0 }
                    ?: -1
                
                val typeIndex = columnNames.indexOf("type").takeIf { it >= 0 }
                    ?: columnNames.indexOf("message_type").takeIf { it >= 0 }
                    ?: columnNames.indexOf("msg_type").takeIf { it >= 0 }
                    ?: -1
                
                val threadIdIndex = columnNames.indexOf("thread_id").takeIf { it >= 0 }
                    ?: columnNames.indexOf("conversation_id").takeIf { it >= 0 }
                    ?: columnNames.indexOf("tid").takeIf { it >= 0 }
                    ?: -1
                
                // Read unread indicator
                val readIndex = columnNames.indexOf("read").takeIf { it >= 0 }
                    ?: columnNames.indexOf("status").takeIf { it >= 0 }
                    ?: -1
                
                // Look for RCS indicator columns
                val rcsIndex = columnNames.indexOf("is_rcs").takeIf { it >= 0 }
                    ?: columnNames.indexOf("rcs").takeIf { it >= 0 }
                    ?: columnNames.indexOf("message_type").takeIf { it >= 0 }
                    ?: columnNames.indexOf("protocol").takeIf { it >= 0 }
                    ?: -1
                
                val isRcsProvider = uri.toString().contains("rcs", ignoreCase = true) ||
                                  uri.toString().contains("samsung", ignoreCase = true)
                
                // Process each message
                while (cursor.moveToNext()) {
                    try {
                        val id = if (idIndex >= 0) cursor.getString(idIndex) ?: "" else ""
                        val address = if (addressIndex >= 0) cursor.getString(addressIndex) ?: "" else ""
                        val body = if (bodyIndex >= 0) cursor.getString(bodyIndex) ?: "" else ""
                        val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else System.currentTimeMillis()
                        val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else 1 // Default to inbox
                        val threadId = if (threadIdIndex >= 0) cursor.getLong(threadIdIndex) else 0L
                        val read = if (readIndex >= 0) cursor.getInt(readIndex) else 1 // Default to read
                        
                        // Determine if it's an RCS message based on provider or column
                        val isRcs = isRcsProvider || 
                                (rcsIndex >= 0 && cursor.getInt(rcsIndex) > 0) ||
                                uri.toString().contains("advanced_messaging", ignoreCase = true) ||
                                uri.toString().contains("chat", ignoreCase = true)
                        
                        // Skip if missing critical data
                        if (address.isBlank() || body.isBlank()) continue
                        
                        // Get contact name
                        val contactName = getContactName(context, address)
                        
                        // Create message object
                        val message = SmsMessage(
                            id = id,
                            address = cleanPhoneNumber(address),
                            body = body,
                            date = date,
                            type = type,
                            read = read,
                            threadId = threadId,
                            isRcs = isRcs,
                            contactName = contactName
                        )
                        
                        messages.add(message)
                        
                        // If it's an RCS message, increment counter
                        if (isRcs) {
                            rcsMessagesFound++
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing message from $uri", e)
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error retrieving messages from $uri", e)
        }
        
        return messages
    }
    
    /**
     * Lookup contact name by phone number
     */
    fun getContactName(context: Context, phoneNumber: String): String {
        // Guard against null or empty phone numbers
        if (phoneNumber.isNullOrBlank()) return ""
        
        // Check the cache first
        if (contactNameCache.containsKey(phoneNumber)) {
            return contactNameCache[phoneNumber] ?: phoneNumber
        }
        
        var contactName = phoneNumber
        
        // Clean the phone number for matching
        val normalizedNumber = try {
            if (phoneNumber.startsWith("+")) {
                phoneNumber
            } else {
                // For numbers without country code, try to normalize them
                PhoneNumberUtils.normalizeNumber(phoneNumber)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error normalizing phone number: ${e.message}", e)
            phoneNumber // Fallback to original number
        }
        
        try {
            val contentResolver = context.contentResolver
            val uri = Uri.withAppendedPath(
                ContactsContract.PhoneLookup.CONTENT_FILTER_URI,
                Uri.encode(normalizedNumber)
            )
            
            val projection = arrayOf(
                ContactsContract.PhoneLookup.DISPLAY_NAME
            )
            
            contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(ContactsContract.PhoneLookup.DISPLAY_NAME)
                    if (nameIndex != -1) {
                        val name = cursor.getString(nameIndex)
                        if (!name.isNullOrBlank()) {
                            contactName = name
                        }
                    }
                }
            }
            
            // If first lookup failed, try alternate lookup method for some devices
            if (contactName == phoneNumber) {
                contactName = lookupContactNameAlternate(context, phoneNumber)
            }
            
            // Cache the result for future lookups
            contactNameCache[phoneNumber] = contactName
            
            return contactName
        } catch (e: Exception) {
            Log.e(TAG, "Error looking up contact name: ${e.message}", e)
            return phoneNumber
        }
    }
    
    /**
     * Alternative contact lookup for devices where the standard method fails
     */
    private fun lookupContactNameAlternate(context: Context, phoneNumber: String): String {
        try {
            val uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI
            val projection = arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER
            )
            
            val selection = "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
            // Use wildcards before and after for partial matching
            val selectionArgs = arrayOf("%${phoneNumber.takeLast(8)}%") 
            
            context.contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
                    val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
                    
                    do {
                        val name = cursor.getString(nameColumn)
                        val number = cursor.getString(numberColumn)
                        
                        if (PhoneNumberUtils.compare(context, phoneNumber, number)) {
                            return name
                        }
                    } while (cursor.moveToNext())
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in alternate contact lookup: ${e.message}", e)
        }
        
        return phoneNumber
    }
    
    /**
     * Get device information for debugging
     */
    private fun getDeviceInfo(): String {
        return "Manufacturer: ${Build.MANUFACTURER}, " +
               "Model: ${Build.MODEL}, " +
               "Android version: ${Build.VERSION.RELEASE}, " +
               "SDK: ${Build.VERSION.SDK_INT}"
    }
    
    /**
     * Detect Samsung Messaging app
     */
    private fun detectSamsungMessagingApp(context: Context): String {
        val sb = StringBuilder("Samsung App Detection:\n")
        val packageManager = context.packageManager
        
        val samsungPackages = listOf(
            "com.samsung.android.messaging",
            "com.samsung.android.messagingapp",
            "com.sec.smsapp",
            "com.samsung.rcs",
            "com.samsung.rcs.im",
            "com.sec.android.message",
            "com.samsung.sms",
            "com.samsung.message"
        )
        
        var found = false
        for (pkg in samsungPackages) {
            try {
                val info = packageManager.getPackageInfo(pkg, 0)
                sb.append("* Found: $pkg (version ${info.versionName})\n")
                found = true
            } catch (e: PackageManager.NameNotFoundException) {
                sb.append("* Not found: $pkg\n")
            }
        }
        
        if (!found) {
            sb.append("No Samsung messaging apps detected on this device\n")
        }
        
        return sb.toString()
    }

    /**
     * Get diagnostic information for debugging
     */
    fun createDiagnosticInfo(): String {
        val info = StringBuilder()
        
        info.append("DEVICE INFO:\n")
        info.append("${getDeviceInfo()}\n\n")
        
        // Message stats
        info.append("MESSAGE STATS:\n")
        info.append("Total messages found: $messagesFound\n")
        info.append("RCS messages found: $rcsMessagesFound\n")
        info.append("Latest successful provider: $latestSuccessfulProvider\n\n")
        
        // Content provider attempt info
        info.append("CONTENT PROVIDER ATTEMPTS: $contentProviderAttempts total\n\n")
        
        // Errors
        info.append("ERRORS (${retrievalErrors.size}):\n")
        if (retrievalErrors.isEmpty()) {
            info.append("No errors encountered\n")
        } else {
            var index = 1
            for (error in retrievalErrors) {
                info.append("${index}. $error\n")
                index++
            }
        }
        
        return info.toString()
    }

    /**
     * Specifically target recent sent messages with multiple methods
     */
    private fun retrieveRecentSentMessages(
        context: Context,
        contentResolver: ContentResolver,
        sinceTimestamp: Long,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        // First try the standard sent folder with date filter
        for (uri in SENT_URIS) {
            retrieveSentMessagesWithDateFilter(context, contentResolver, uri, sinceTimestamp, messageList, processedIds)
        }
        
        // Then try the all-messages provider with type and date filter
        for (uri in ALL_SMS_URIS) {
            retrieveSentMessagesWithDateFilter(context, contentResolver, uri, sinceTimestamp, messageList, processedIds)
        }
        
        // Try carrier-specific URIs that might contain sent messages
        val carrierUris = arrayOf(
            Uri.parse("content://mms-sms/conversations"),
            Uri.parse("content://telephony/complete_conversations"),
            Uri.parse("content://mms-sms/complete-conversations")
        )
        
        for (uri in carrierUris) {
            try {
                retrieveSentMessagesFromCarrierUri(context, contentResolver, uri, sinceTimestamp, messageList, processedIds)
            } catch (e: Exception) {
                // Log but continue - carrier URI might not be accessible
                logDebug("Could not access carrier URI ${uri}: ${e.message}")
            }
        }
    }
    
    private fun retrieveSentMessagesWithDateFilter(
        context: Context,
        contentResolver: ContentResolver,
        uri: Uri,
        sinceTimestamp: Long,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        val projection = arrayOf(
            Telephony.Sms._ID,
            Telephony.Sms.ADDRESS,
            Telephony.Sms.BODY,
            Telephony.Sms.DATE,
            Telephony.Sms.TYPE
        )
        
        try {
            // Query with both TYPE and DATE filters
            val selection: String
            val selectionArgs: Array<String>
            
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                // More precise on newer Android versions
                selection = "${Telephony.Sms.TYPE} = ? AND ${Telephony.Sms.DATE} >= ?"
                selectionArgs = arrayOf(
                    Telephony.Sms.MESSAGE_TYPE_SENT.toString(),
                    sinceTimestamp.toString()
                )
            } else {
                // On older versions, be less restrictive to ensure we get messages
                selection = "${Telephony.Sms.DATE} >= ?"
                selectionArgs = arrayOf(sinceTimestamp.toString())
            }
            
            contentResolver.query(
                uri,
                projection,
                selection,
                selectionArgs,
                "${Telephony.Sms.DATE} DESC" // Most recent first
            )?.use { cursor ->
                val idIndex = cursor.getColumnIndex(Telephony.Sms._ID)
                val addressIndex = cursor.getColumnIndex(Telephony.Sms.ADDRESS)
                val bodyIndex = cursor.getColumnIndex(Telephony.Sms.BODY)
                val dateIndex = cursor.getColumnIndex(Telephony.Sms.DATE)
                val typeIndex = cursor.getColumnIndex(Telephony.Sms.TYPE)
                
                logDebug("Found ${cursor.count} recent messages in $uri with date filter")
                
                var processedCount = 0
                while (cursor.moveToNext()) {
                    try {
                        // For sent messages with date filter
                        val type = if (typeIndex != -1) {
                            cursor.getInt(typeIndex)
                        } else {
                            Telephony.Sms.MESSAGE_TYPE_SENT // Default to sent
                        }
                        
                        // Only consider outgoing messages in this special retrieval function
                        if (type != Telephony.Sms.MESSAGE_TYPE_SENT) {
                            continue
                        }
                        
                        val id = if (idIndex != -1) cursor.getLong(idIndex).toString() else ""
                        val address = if (addressIndex != -1) cursor.getString(addressIndex) ?: "" else ""
                        val body = if (bodyIndex != -1) cursor.getString(bodyIndex) ?: "" else ""
                        val date = if (dateIndex != -1) cursor.getLong(dateIndex) else System.currentTimeMillis()
                        
                        // Skip if missing critical data
                        if (address.isBlank() || body.isBlank()) continue
                        
                        // Create a unique ID for deduplication
                        val uniqueId = "$address:$body:$date"
                        if (processedIds.contains(uniqueId)) continue
                        
                        // Get contact name for this phone number
                        val contactName = getContactName(context, address)
                        
                        // Create the message object
                        val message = SmsMessage(
                            id = id,
                            address = address,
                            body = body,
                            date = date,
                            type = type,
                            read = 1, // Assume read
                            threadId = 0,
                            isRcs = false,
                            contactName = contactName
                        )
                        
                        // Add to the list
                        messageList.add(message)
                        processedIds.add(uniqueId)
                        processedCount++
                        sentCount.incrementAndGet()
                    } catch (e: Exception) {
                        Log.e(TAG, "Error processing recent sent message: ${e.message}", e)
                    }
                }
                
                logDebug("Added $processedCount recent sent messages from $uri")
            }
        } catch (e: Exception) {
            logDebug("Error querying recent sent messages from $uri: ${e.message}")
        }
    }
    
    /**
     * Try to extract sent messages from carrier-specific conversation URIs
     */
    private fun retrieveSentMessagesFromCarrierUri(
        context: Context,
        contentResolver: ContentResolver,
        uri: Uri,
        sinceTimestamp: Long,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>
    ) {
        try {
            // First check if this URI is accessible
            val testCursor = contentResolver.query(
                uri,
                null,
                null,
                null,
                null
            )
            
            if (testCursor == null) {
                logDebug("Carrier URI $uri not accessible")
                return
            }
            testCursor.close()
            
            // Now do the real query
            contentResolver.query(
                uri,
                null, // Get all columns so we can inspect them
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.count > 0) {
                    logDebug("Found ${cursor.count} messages in carrier URI $uri")
                    
                    // Find column indices dynamically since carrier URIs vary
                    val columnNames = cursor.columnNames
                    val addressIndex = findColumnIndex(columnNames, arrayOf("address", "recipient_ids", "recipient"))
                    val bodyIndex = findColumnIndex(columnNames, arrayOf("body", "snippet", "content", "text"))
                    val dateIndex = findColumnIndex(columnNames, arrayOf("date", "timestamp", "date_sent"))
                    val typeIndex = findColumnIndex(columnNames, arrayOf("type", "message_type", "msg_type"))
                    val threadIdIndex = findColumnIndex(columnNames, arrayOf("thread_id", "conversation_id"))
                    
                    // Process if we have the minimum required columns
                    if (addressIndex != -1 && bodyIndex != -1) {
                        var count = 0
                        while (cursor.moveToNext()) {
                            try {
                                val body = cursor.getString(bodyIndex) ?: continue
                                if (body.isBlank()) continue
                                
                                val address = cursor.getString(addressIndex) ?: continue
                                if (address.isBlank()) continue
                                
                                // Get date if available, otherwise use current time
                                val date = if (dateIndex != -1) cursor.getLong(dateIndex) else System.currentTimeMillis()
                                
                                // Skip if older than our cutoff
                                if (date < sinceTimestamp) continue
                                
                                // Determine if message is sent based on type
                                val type = if (typeIndex != -1) {
                                    val rawType = cursor.getInt(typeIndex)
                                    // Different providers use different values for sent messages
                                    if (rawType == 2 || rawType == 4 || rawType == Telephony.Sms.MESSAGE_TYPE_SENT) {
                                        Telephony.Sms.MESSAGE_TYPE_SENT
                                    } else {
                                        Telephony.Sms.MESSAGE_TYPE_INBOX
                                    }
                                } else {
                                    Telephony.Sms.MESSAGE_TYPE_SENT // Default to sent for this method
                                }
                                
                                // Skip if not a sent message
                                if (type != Telephony.Sms.MESSAGE_TYPE_SENT) continue
                                
                                // Generate a unique ID
                                val uniqueId = "$address:$body:$date"
                                if (processedIds.contains(uniqueId)) continue
                                
                                // Get thread ID if available
                                val threadId = if (threadIdIndex != -1) cursor.getLong(threadIdIndex) else 0
                                
                                // Create and add the message
                                val contactName = getContactName(context, address)
                                messageList.add(
                                    SmsMessage(
                                        id = "", // We don't have a reliable ID from these providers
                                        address = address,
                                        body = body,
                                        date = date,
                                        type = type,
                                        read = 1,
                                        threadId = threadId,
                                        isRcs = false,
                                        contactName = contactName
                                    )
                                )
                                
                                processedIds.add(uniqueId)
                                messagesFound++
                                sentCount.incrementAndGet()
                                count++
                            } catch (e: Exception) {
                                Log.e(TAG, "Error processing carrier message: ${e.message}", e)
                            }
                        }
                        
                        logDebug("Added $count sent messages from carrier URI $uri")
                    }
                }
            }
        } catch (e: Exception) {
            logDebug("Carrier URI $uri exception: ${e.message}")
        }
    }
    
    /**
     * Helper to find a column by multiple possible names
     */
    private fun findColumnIndex(columnNames: Array<String>, possibleNames: Array<String>): Int {
        for (name in possibleNames) {
            val index = columnNames.indexOf(name)
            if (index != -1) return index
        }
        return -1
    }
    
    /**
     * Process Samsung Advanced Messages from cursor
     */
    private fun processAdvancedMessages(
        context: Context,
        cursor: Cursor,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>,
        bodyDateMap: HashSet<String>
    ) {
        val indexMap = mutableMapOf<String, Int>()
        
        // Map all column names to their indices
        for (i in 0 until cursor.columnCount) {
            val columnName = cursor.getColumnName(i)
            indexMap[columnName] = i
            indexMap[columnName.lowercase()] = i // Also add lowercase version for case-insensitive lookup
        }
        
        // Log available columns for debugging
        logDebug("Advanced message columns: ${indexMap.keys.joinToString(", ")}")
        
        while (cursor.moveToNext()) {
            try {
                // Try to extract message ID using various possible column names
                val idColumnNames = listOf("_id", "id", "message_id", "msg_id")
                val messageId = extractStringFromCursor(cursor, indexMap, idColumnNames) ?: "unknown_${System.currentTimeMillis()}_${Random().nextInt(10000)}"
                
                // Extract message body from various possible column names
                val bodyColumnNames = listOf("body", "text", "content", "message", "msg_content", "message_content", "text_content")
                val body = extractStringFromCursor(cursor, indexMap, bodyColumnNames) ?: ""
                
                // Extract address (phone number) from various possible column names
                val addressColumnNames = listOf("address", "phone", "phone_number", "sender", "recipient", "address_normalized", "from_address")
                val address = extractStringFromCursor(cursor, indexMap, addressColumnNames) ?: ""
                
                // Extract date from various possible column names
                val dateColumnNames = listOf("date", "timestamp", "time", "date_sent")
                val date = extractLongFromCursor(cursor, indexMap, dateColumnNames) ?: System.currentTimeMillis()
                
                // More aggressive deduplication using body+date
                val bodyDateKey = "$body:$date"
                if (bodyDateMap.contains(bodyDateKey)) continue
                
                // Skip if we've already processed this message
                val uniqueId = "$address:$body:$date"
                if (processedIds.contains(uniqueId)) {
                    continue
                }
                
                // Add to processed sets
                processedIds.add(uniqueId)
                bodyDateMap.add(bodyDateKey)
                
                // Check if this is an RCS message
                val isRcs = isRcsMessage(cursor, indexMap)
                
                // Determine message type and direction
                val isIncoming = determineIfIncoming(cursor, indexMap, address)
                val type = if (isIncoming) Telephony.Sms.MESSAGE_TYPE_INBOX else Telephony.Sms.MESSAGE_TYPE_SENT
                
                // Skip if missing critical data
                if (address.isBlank() || body.isBlank()) continue
                
                // Create SMS message
                val message = SmsMessage(
                    id = messageId,
                    address = cleanPhoneNumber(address),
                    body = body,
                    date = date,
                    type = type,
                    read = 1, // Assume read for advanced messages
                    threadId = extractLongFromCursor(cursor, indexMap, listOf("thread_id")) ?: 0,
                    isRcs = isRcs,
                    contactName = getContactName(context, address)
                )
                
                messageList.add(message)
                messagesFound++
                
                if (isRcs) {
                    rcsMessagesFound++
                    logDebug("RCS message found: ${message.body.take(20)}...")
                }
                
            } catch (e: Exception) {
                logDebug("Error processing advanced message: ${e.message}")
            }
        }
    }
    
    /**
     * Determine if a message is an RCS message based on available columns
     */
    private fun isRcsMessage(cursor: Cursor, indexMap: Map<String, Int>): Boolean {
        // First check message body for RCS indicators
        val bodyColumnNames = listOf("body", "text", "content", "message")
        val body = extractStringFromCursor(cursor, indexMap, bodyColumnNames) ?: ""
        
        // Check if the message contains known RCS indicators in the content
        if (body.contains("<rcstext>") || 
            body.contains("<rcsmedia>") || 
            body.contains("RCS:") || 
            body.contains("[RCS]")) {
            return true
        }

        // Check for RCS specific columns or flags
        val rcsColumnNames = listOf(
            "rcs", "is_rcs", "rcs_message", "message_type", "chat_type",
            "rcs_chat_type", "message_tag", "im_type", "chat_session_id",
            "feature_tag", "conversation_type"
        )
        
        for (columnName in rcsColumnNames) {
            if (indexMap.containsKey(columnName)) {
                val index = indexMap[columnName] ?: continue
                try {
                    // Check for string values
                    val stringValue = cursor.getString(index)
                    if (stringValue != null && 
                       (stringValue.equals("rcs", true) || 
                        stringValue.equals("true", true) || 
                        stringValue.equals("1", true) ||
                        stringValue.contains("rcs", true) ||
                        stringValue.contains("chat", true) ||
                        stringValue.contains("advanced", true))) {
                        return true
                    }
                    
                    // Check for integer values (1 = true)
                    val intValue = cursor.getInt(index)
                    if (intValue == 1) {
                        return true
                    }
                } catch (e: Exception) {
                    // Column exists but couldn't be read, continue to next column
                }
            }
        }
        
        // Check for Samsung-specific RCS indicators in the message body or metadata
        val messageTypeIndex = indexMap["message_type"] ?: indexMap["msg_type"] ?: -1
        if (messageTypeIndex >= 0) {
            try {
                val messageType = cursor.getInt(messageTypeIndex)
                // Samsung often uses specific type codes for RCS messages
                if (messageType >= 128 || messageType == 5 || messageType == 6 || 
                    messageType == 20 || messageType == 21 || messageType == 22) {
                    return true
                }
            } catch (e: Exception) {
                // Ignore and continue
            }
        }
        
        // Check for MIME type column which might indicate RCS media
        val mimeTypeColumnNames = listOf("mime_type", "content_type", "message_content_type")
        for (columnName in mimeTypeColumnNames) {
            if (indexMap.containsKey(columnName)) {
                val index = indexMap[columnName] ?: continue
                try {
                    val mimeType = cursor.getString(index)
                    if (mimeType != null && 
                        (mimeType.contains("rcs") || 
                         mimeType.contains("application/vnd.gsma.rcs") ||
                         mimeType.contains("chat"))) {
                        return true
                    }
                } catch (e: Exception) {
                    // Ignore and continue
                }
            }
        }
        
        return false
    }
    
    /**
     * Determine if a message is incoming based on available columns
     */
    private fun determineIfIncoming(cursor: Cursor, indexMap: Map<String, Int>, address: String): Boolean {
        // Check direct indicators first
        val directionColumnNames = listOf("type", "message_type", "direction", "incoming", "status")
        
        for (columnName in directionColumnNames) {
            if (indexMap.containsKey(columnName)) {
                val index = indexMap[columnName] ?: continue
                try {
                    // Check for string values
                    val stringValue = cursor.getString(index)
                    if (stringValue != null) {
                        if (stringValue.equals("inbox", true) || 
                            stringValue.equals("incoming", true) || 
                            stringValue.equals("received", true) ||
                            stringValue.equals("1", true)) {
                            return true
                        }
                        if (stringValue.equals("sent", true) || 
                            stringValue.equals("outgoing", true) || 
                            stringValue.equals("outbox", true) ||
                            stringValue.equals("2", true)) {
                            return false
                        }
                    }
                    
                    // Check for integer values
                    val intValue = cursor.getInt(index)
                    if (intValue == Telephony.Sms.MESSAGE_TYPE_INBOX || intValue == 1) {
                        return true
                    }
                    if (intValue == Telephony.Sms.MESSAGE_TYPE_SENT || intValue == 2) {
                        return false
                    }
                    
                    // Samsung sometimes uses these values
                    if (intValue == 20 || intValue == 137) { // Outgoing
                        return false
                    }
                    if (intValue == 10 || intValue == 135) { // Incoming
                        return true
                    }
                } catch (e: Exception) {
                    // Column exists but couldn't be read, continue to next column
                }
            }
        }
        
        // Fallback: assume it's incoming if the address doesn't look like a name
        // (For sent messages, Samsung often stores the recipient name rather than phone number)
        return !address.matches(Regex("[a-zA-Z\\s]+"))
    }
    
    /**
     * Extract a string value from cursor trying multiple possible column names
     */
    private fun extractStringFromCursor(cursor: Cursor, indexMap: Map<String, Int>, columnNames: List<String>): String? {
        for (columnName in columnNames) {
            val index = indexMap[columnName] ?: indexMap[columnName.lowercase()] ?: continue
            try {
                return cursor.getString(index)
            } catch (e: Exception) {
                // Try next column name
            }
        }
        return null
    }
    
    /**
     * Extract a long value from cursor trying multiple possible column names
     */
    private fun extractLongFromCursor(cursor: Cursor, indexMap: Map<String, Int>, columnNames: List<String>): Long? {
        for (columnName in columnNames) {
            val index = indexMap[columnName] ?: indexMap[columnName.lowercase()] ?: continue
            try {
                return cursor.getLong(index)
            } catch (e: Exception) {
                // Try next column name
            }
        }
        return null
    }
    
    /**
     * Clean phone number to standard format
     */
    private fun cleanPhoneNumber(address: String): String {
        return try {
            // Samsung sometimes stores contact names instead of phone numbers for sent messages
            if (address.matches(Regex("[a-zA-Z\\s]+"))) {
                address
            } else {
                PhoneNumberUtils.normalizeNumber(address)
            }
        } catch (e: Exception) {
            address
        }
    }

    /**
     * Detect the default SMS app on the device
     */
    fun detectDefaultSmsApp(context: Context): String {
        val defaultSmsPackage = Telephony.Sms.getDefaultSmsPackage(context)
        logDebug("Default SMS package: $defaultSmsPackage")
        
        // Get app name from package
        val packageManager = context.packageManager
        var appName = defaultSmsPackage ?: "None"
        
        try {
            if (defaultSmsPackage != null) {
                val appInfo = packageManager.getApplicationInfo(defaultSmsPackage, 0)
                appName = packageManager.getApplicationLabel(appInfo).toString()
            }
        } catch (e: Exception) {
            logDebug("Error getting app name: ${e.message}")
        }
        
        // Check for Samsung messaging apps
        val isSamsungMessagesDefault = defaultSmsPackage?.contains("samsung") == true || 
                                      defaultSmsPackage?.contains("msg") == true
        
        return "Default SMS app: $appName ($defaultSmsPackage)" +
               if (isSamsungMessagesDefault) " - Samsung Messages detected as default" else ""
    }

    // Main message retrieval function
    fun retrieveAllMessages(context: Context): List<SmsMessage> {
        val allMessages = mutableListOf<SmsMessage>()
        val seenMessages = mutableSetOf<String>()
        var logInfo = "Message retrieval started\n"

        // Try standard SMS/MMS providers
        for (uri in STANDARD_MESSAGE_URIS) {
            try {
                logInfo += "Trying URI: $uri\n"
                val messages = retrieveMessages(context, uri)
                logInfo += "  Found ${messages.size} messages\n"
                
                // Add only unique messages
                for (message in messages) {
                    val dedupeKey = createDeduplicationKey(message)
                    if (!seenMessages.contains(dedupeKey)) {
                        seenMessages.add(dedupeKey)
                        allMessages.add(message)
                    }
                }
            } catch (e: Exception) {
                logInfo += "  Error: ${e.message}\n"
            }
        }

        // Try Samsung-specific RCS providers
        val samsungMessages = tryAccessSamsungAdvancedMessages(context)
        logInfo += "Samsung RCS messages found: ${samsungMessages.size}\n"
        
        // Add only unique Samsung RCS messages
        for (message in samsungMessages) {
            val dedupeKey = createDeduplicationKey(message)
            if (!seenMessages.contains(dedupeKey)) {
                seenMessages.add(dedupeKey)
                allMessages.add(message)
            }
        }

        // Log Samsung app detection results
        logInfo += detectSamsungMessagingApp(context)

        // Sort by date (newest first)
        val sortedMessages = allMessages.sortedByDescending { it.date }
        
        // Update diagnostic info
        _diagnosticInfo = logInfo + "\nTotal unique messages: ${sortedMessages.size}\n"
        
        return sortedMessages
    }

    private fun createDeduplicationKey(message: SmsMessage): String {
        // Create a unique key based on message content and metadata
        return MESSAGE_DEDUPLICATION_KEYS.joinToString("|") { key ->
            when (key) {
                "body" -> message.body
                "date" -> message.date.toString()
                "address" -> message.address
                else -> ""
            }
        }
    }

    /**
     * Helper logger for debugging
     */
    private fun logDebug(message: String) {
        Log.d(TAG, message)
    }

    /**
     * Try to access all standard content providers for SMS messages
     */
    private fun tryAccessContentProviders(
        context: Context,
        messageList: MutableList<SmsMessage>,
        processedIds: HashSet<String>,
        bodyDateMap: HashSet<String>
    ) {
        val contentResolver: ContentResolver = context.contentResolver
        
        // Try standard SMS inbox URI
        for (uri in INBOX_URIS) {
            try {
                logDebug("Trying standard SMS inbox URI: $uri")
                contentProviderAttempts++
                
                contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    "date DESC"
                )?.use { cursor ->
                    val count = cursor.count
                    logDebug("Found $count messages in $uri")
                    
                    if (count > 0) {
                        // Map column indices
                        val columnNames = cursor.columnNames
                        val idIndex = columnNames.indexOf("_id")
                        val addressIndex = columnNames.indexOf("address")
                        val bodyIndex = columnNames.indexOf("body")
                        val dateIndex = columnNames.indexOf("date")
                        val typeIndex = columnNames.indexOf("type")
                        
                        while (cursor.moveToNext()) {
                            try {
                                val id = if (idIndex >= 0) cursor.getLong(idIndex).toString() else ""
                                val address = if (addressIndex >= 0) cursor.getString(addressIndex) ?: "" else ""
                                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex) ?: "" else ""
                                val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else System.currentTimeMillis()
                                val type = if (typeIndex >= 0) cursor.getInt(typeIndex) else Telephony.Sms.MESSAGE_TYPE_INBOX
                                
                                // More aggressive deduplication using body+date
                                val bodyDateKey = "$body:$date"
                                if (bodyDateMap.contains(bodyDateKey)) continue
                                
                                // Skip if we already have this message
                                val uniqueId = "$address:$body:$date"
                                if (processedIds.contains(uniqueId)) continue
                                
                                // Skip if missing critical data
                                if (address.isBlank() || body.isBlank()) continue
                                
                                // Add the message
                                val message = SmsMessage(
                                    id = id,
                                    address = cleanPhoneNumber(address),
                                    body = body,
                                    date = date,
                                    type = type,
                                    read = 1, // Assume read
                                    threadId = 0,
                                    isRcs = false,
                                    contactName = getContactName(context, address)
                                )
                                
                                messageList.add(message)
                                processedIds.add(uniqueId)
                                bodyDateMap.add(bodyDateKey)
                                messagesFound++
                                
                                if (type == Telephony.Sms.MESSAGE_TYPE_INBOX) {
                                    inboxCount.incrementAndGet()
                                } else if (type == Telephony.Sms.MESSAGE_TYPE_SENT) {
                                    sentCount.incrementAndGet()
                                }
                            } catch (e: Exception) {
                                logDebug("Error processing message: ${e.message}")
                            }
                        }
                        
                        latestSuccessfulProvider = uri.toString()
                    }
                }
            } catch (e: Exception) {
                logDebug("Error accessing SMS URI $uri: ${e.message}")
                retrievalErrors.add("Standard URI $uri: ${e.message ?: "Unknown error"}")
            }
        }
        
        // Try standard SMS sent URI
        for (uri in SENT_URIS) {
            try {
                logDebug("Trying standard SMS sent URI: $uri")
                contentProviderAttempts++
                
                contentResolver.query(
                    uri,
                    null,
                    null,
                    null,
                    "date DESC"
                )?.use { cursor ->
                    val count = cursor.count
                    logDebug("Found $count sent messages in $uri")
                    
                    if (count > 0) {
                        // Process similar to inbox messages
                        // Map column indices
                        val columnNames = cursor.columnNames
                        val idIndex = columnNames.indexOf("_id")
                        val addressIndex = columnNames.indexOf("address")
                        val bodyIndex = columnNames.indexOf("body")
                        val dateIndex = columnNames.indexOf("date")
                        
                        while (cursor.moveToNext()) {
                            try {
                                val id = if (idIndex >= 0) cursor.getLong(idIndex).toString() else ""
                                val address = if (addressIndex >= 0) cursor.getString(addressIndex) ?: "" else ""
                                val body = if (bodyIndex >= 0) cursor.getString(bodyIndex) ?: "" else ""
                                val date = if (dateIndex >= 0) cursor.getLong(dateIndex) else System.currentTimeMillis()
                                
                                // Skip if we already have this message
                                val uniqueId = "$address:$body:$date"
                                if (processedIds.contains(uniqueId)) continue
                                
                                // Skip if missing critical data
                                if (address.isBlank() || body.isBlank()) continue
                                
                                // Add the message
                                val message = SmsMessage(
                                    id = id,
                                    address = cleanPhoneNumber(address),
                                    body = body,
                                    date = date,
                                    type = Telephony.Sms.MESSAGE_TYPE_SENT,
                                    read = 1, // Assume read
                                    threadId = 0,
                                    isRcs = false,
                                    contactName = getContactName(context, address)
                                )
                                
                                messageList.add(message)
                                processedIds.add(uniqueId)
                                messagesFound++
                            } catch (e: Exception) {
                                logDebug("Error processing sent message: ${e.message}")
                            }
                        }
                        
                        latestSuccessfulProvider = uri.toString()
                    }
                }
            } catch (e: Exception) {
                logDebug("Error accessing sent SMS URI $uri: ${e.message}")
                retrievalErrors.add("Sent URI $uri: ${e.message}")
            }
        }
    }

    /**
     * Try to access Samsung advanced messages
     */
    fun tryAccessSamsungAdvancedMessages(context: Context): List<SmsMessage> {
        val rcsMessages = mutableListOf<SmsMessage>()
        var logInfo = "Checking Samsung RCS providers:\n"

        for (uri in SAMSUNG_MESSAGING_URIS) {
            try {
                logInfo += "Trying Samsung URI: $uri\n"
                
                val cursor = context.contentResolver.query(
                    Uri.parse(uri),
                    null, null, null, null
                )
                
                if (cursor != null) {
                    logInfo += "  Access granted to: $uri\n"
                    logInfo += "  Columns: ${cursor.columnNames.joinToString(", ")}\n"
                    
                    // Process the cursor based on available columns
                    val messages = processSamsungMessageCursor(cursor, uri)
                    logInfo += "  Found ${messages.size} RCS messages\n"
                    rcsMessages.addAll(messages)
                    
                    cursor.close()
                } else {
                    logInfo += "  No cursor returned for: $uri\n"
                }
            } catch (e: Exception) {
                logInfo += "  Error accessing $uri: ${e.message}\n"
            }
        }
        
        logDebug(logInfo)
        return rcsMessages
    }

    private fun processSamsungMessageCursor(cursor: Cursor, uri: String): List<SmsMessage> {
        val messages = mutableListOf<SmsMessage>()
        val columnMap = mapSamsungColumns(cursor)
        
        if (cursor.moveToFirst()) {
            do {
                try {
                    // Extract data using the mapped columns
                    val message = extractSamsungMessage(cursor, columnMap, uri)
                    messages.add(message)
                } catch (e: Exception) {
                    // Log but continue processing
                    Log.e(TAG, "Error processing Samsung message: ${e.message}")
                }
            } while (cursor.moveToNext())
        }
        
        return messages
    }

    private fun mapSamsungColumns(cursor: Cursor): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        val columnNames = cursor.columnNames
        
        // Map standard column names to their indices
        val possibleColumns = mapOf(
            "body" to listOf("body", "message_body", "text", "content", "msg_body", "message_content"),
            "address" to listOf("address", "phone", "sender", "recipient", "from_address", "number"),
            "date" to listOf("date", "timestamp", "time", "created_at", "date_sent"),
            "type" to listOf("type", "message_type", "msg_type", "message_box"),
            "id" to listOf("_id", "id", "message_id"),
            "thread_id" to listOf("thread_id", "conversation_id", "chat_id")
        )
        
        // For each field we need, find a matching column
        for ((field, possibleNames) in possibleColumns) {
            for (colName in possibleNames) {
                val index = columnNames.indexOf(colName)
                if (index >= 0) {
                    map[field] = index
                    break
                }
            }
        }
        
        return map
    }

    private fun extractSamsungMessage(cursor: Cursor, columnMap: Map<String, Int>, uri: String): SmsMessage {
        // Get values using the column map, with fallbacks
        val id = columnMap["id"]?.let { if (it >= 0) cursor.getString(it) else "" } ?: ""
        
        val address = columnMap["address"]?.let { 
            if (it >= 0) cursor.getString(it) ?: "" else "" 
        } ?: ""
        
        val body = columnMap["body"]?.let { 
            if (it >= 0) cursor.getString(it) ?: "" else "" 
        } ?: ""
        
        val date = columnMap["date"]?.let { 
            if (it >= 0) cursor.getLong(it) else System.currentTimeMillis() 
        } ?: System.currentTimeMillis()
        
        val type = columnMap["type"]?.let { 
            if (it >= 0) cursor.getInt(it) else 1 
        } ?: 1
        
        val threadId = columnMap["thread_id"]?.let { 
            if (it >= 0) cursor.getLong(it) else 0 
        } ?: 0
        
        // We don't have contact context here, so return empty string for contactName
        // The caller will need to look up the contact name separately
        
        return SmsMessage(
            id = id,
            address = address,
            body = body,
            date = date,
            type = type,
            read = 1,  // Assume read for Samsung messages as we may not have this info
            threadId = threadId,
            isRcs = true,  // Mark as RCS since it came from Samsung provider
            contactName = ""
        )
    }
} 