package com.example.smsapp.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smsapp.model.AttachmentType
import com.example.smsapp.model.SmsMessage
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageItem(
    message: SmsMessage,
    onAttachmentClick: ((SmsMessage) -> Unit)? = null
) {
    val isIncoming = message.type == android.provider.Telephony.Sms.MESSAGE_TYPE_INBOX
    val alignment = if (isIncoming) Alignment.CenterStart else Alignment.CenterEnd
    val backgroundColor = if (isIncoming) 
        MaterialTheme.colorScheme.secondaryContainer
    else 
        MaterialTheme.colorScheme.primaryContainer
    
    val timeString = SimpleDateFormat("MMM dd, hh:mm a", Locale.getDefault())
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
            
            // Display RCS indicator if applicable
            if (message.isRcs) {
                Text(
                    text = "RCS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
            
            // Display media attachment if present
            if (message.hasAttachment) {
                AttachmentContent(
                    message = message,
                    onAttachmentClick = { onAttachmentClick?.invoke(message) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp)
                )
            }
            
            // Show message body if it's not empty
            if (message.body.isNotEmpty()) {
                Text(
                    text = message.body,
                    style = MaterialTheme.typography.bodyLarge
                )
            }
            
            Spacer(modifier = Modifier.height(4.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (message.hasAttachment) {
                    val attachmentInfo = when (message.attachmentType) {
                        AttachmentType.IMAGE -> "Image"
                        AttachmentType.VIDEO -> "Video"
                        AttachmentType.AUDIO -> "Audio"
                        AttachmentType.DOCUMENT -> "Document"
                        AttachmentType.LOCATION -> "Location"
                        AttachmentType.CONTACT -> "Contact"
                        else -> ""
                    }
                    
                    if (attachmentInfo.isNotEmpty()) {
                        Text(
                            text = attachmentInfo,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
                
                Text(
                    text = timeString,
                    style = MaterialTheme.typography.bodySmall,
                    textAlign = TextAlign.End,
                    color = MaterialTheme.colorScheme.outline
                )
            }
        }
    }
}

@Composable
fun AttachmentContent(
    message: SmsMessage,
    onAttachmentClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clickable { onAttachmentClick() }
    ) {
        when (message.attachmentType) {
            AttachmentType.IMAGE -> {
                AsyncImage(
                    model = ImageRequest.Builder(LocalContext.current)
                        .data(message.attachmentUri)
                        .crossfade(true)
                        .build(),
                    contentDescription = "Image attachment",
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                )
            }
            AttachmentType.VIDEO -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.Black.copy(alpha = 0.7f)),
                    contentAlignment = Alignment.Center
                ) {
                    // Video thumbnail could be loaded here with Coil
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Play video",
                        modifier = Modifier.size(48.dp),
                        tint = Color.White
                    )
                }
            }
            else -> {
                val (icon, label) = when (message.attachmentType) {
                    AttachmentType.AUDIO -> Pair(Icons.Default.AudioFile, "Audio")
                    AttachmentType.DOCUMENT -> Pair(Icons.Default.Description, "Document")
                    AttachmentType.LOCATION -> Pair(Icons.Default.LocationOn, "Location")
                    AttachmentType.CONTACT -> Pair(Icons.Default.Person, "Contact")
                    else -> Pair(Icons.Default.Description, "Attachment")
                }
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = label,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
        }
    }
} 