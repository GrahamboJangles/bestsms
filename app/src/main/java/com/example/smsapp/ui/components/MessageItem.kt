package com.example.smsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smsapp.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageItem(message: SmsMessage) {
    val isIncoming = message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
    val alignment = if (isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    val backgroundColor = if (isIncoming) 
        MaterialTheme.colorScheme.secondaryContainer
    else 
        MaterialTheme.colorScheme.primaryContainer
    
    val timeString = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        .format(Date(message.date))
    
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(8.dp),
        contentAlignment = alignment
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth(0.8f)
                .clip(
                    RoundedCornerShape(
                        topStart = 16.dp,
                        topEnd = 16.dp,
                        bottomStart = if (isIncoming) 0.dp else 16.dp,
                        bottomEnd = if (isIncoming) 16.dp else 0.dp
                    )
                )
                .background(backgroundColor)
                .padding(12.dp)
        ) {
            if (isIncoming) {
                Text(
                    text = if (message.contactName.isNotEmpty()) message.contactName else message.address,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Text(
                text = message.body,
                style = MaterialTheme.typography.bodyLarge
            )
            
            Text(
                text = timeString,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier
                    .fillMaxWidth(),
                textAlign = TextAlign.End,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
} 