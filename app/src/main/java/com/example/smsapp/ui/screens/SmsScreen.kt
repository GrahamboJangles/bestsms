package com.example.smsapp.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.example.smsapp.model.AttachmentType
import com.example.smsapp.model.SmsMessage
import com.example.smsapp.ui.components.MessageItem
import com.example.smsapp.viewmodel.SmsViewModel
import kotlinx.coroutines.launch
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.imePadding

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsScreen(viewModel: SmsViewModel, modifier: Modifier = Modifier) {
    val messages = viewModel.messages
    val currentRecipient by remember { viewModel.currentRecipient }
    val currentMessage by remember { viewModel.currentMessage }
    val permissionsGranted by remember { viewModel.permissionsGranted }
    val currentContact by remember { viewModel.currentContact }
    val currentAttachmentUri by remember { viewModel.currentAttachmentUri }
    val showAttachmentOptions by remember { viewModel.showAttachmentOptions }
    val showAttachmentDialog by remember { viewModel.showAttachmentDialog }
    val selectedAttachment by remember { viewModel.selectedAttachment }
    
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    
    // Activity result launchers for attachments
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addAttachment(it, AttachmentType.IMAGE, "image/*")
        }
    }
    
    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addAttachment(it, AttachmentType.VIDEO, "video/*")
        }
    }
    
    val audioPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addAttachment(it, AttachmentType.AUDIO, "audio/*")
        }
    }
    
    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let {
            viewModel.addAttachment(it, AttachmentType.DOCUMENT, "application/*")
        }
    }
    
    // Auto-scroll to bottom when messages change
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }
    
    // Attachment selection dialog
    if (showAttachmentOptions) {
        AttachmentOptionsDialog(
            onDismiss = { viewModel.showAttachmentOptions.value = false },
            onImageSelected = { imagePickerLauncher.launch("image/*") },
            onVideoSelected = { videoPickerLauncher.launch("video/*") },
            onAudioSelected = { audioPickerLauncher.launch("audio/*") },
            onDocumentSelected = { documentPickerLauncher.launch("*/*") }
        )
    }
    
    // Attachment viewing dialog
    if (showAttachmentDialog && selectedAttachment != null) {
        AttachmentViewDialog(
            message = selectedAttachment!!,
            onDismiss = { viewModel.closeAttachmentDialog() },
            onOpenExternal = {
                try {
                    val intent = Intent(Intent.ACTION_VIEW).apply {
                        data = Uri.parse(selectedAttachment!!.attachmentUri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    context.startActivity(intent)
                } catch (e: Exception) {
                    // Handle error opening attachment
                }
            }
        )
    }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (!viewModel.isInConversationList.value) {
                        if (viewModel.currentContact.value.isNotEmpty()) {
                            val contactName = viewModel.currentContactName.value
                            val contactNumber = viewModel.currentContact.value
                            val displayText = if (contactName.isNotEmpty()) {
                                "$contactName (${contactNumber})"
                            } else {
                                contactNumber
                            }
                            Text(displayText)
                        } else {
                            Text("New Message")
                        }
                    } else {
                        Text("Messages")
                    }
                },
                navigationIcon = {
                    if (!viewModel.isInConversationList.value) {
                        IconButton(onClick = {
                            viewModel.goToConversationList()
                        }) {
                            Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                        }
                    }
                }
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .navigationBarsPadding()
                    .background(MaterialTheme.colorScheme.surface)
            ) {
                // Show current attachment preview if any
                if (currentAttachmentUri != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val attachmentTypeText = viewModel.currentAttachmentType.value.name
                            .lowercase()
                            .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                        
                        Text(
                            text = "Attachment: $attachmentTypeText",
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        
                        IconButton(onClick = { viewModel.clearAttachment() }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Remove attachment"
                            )
                        }
                    }
                    
                    Divider()
                }
                
                // Message input area
                Row(
                    modifier = Modifier
                        .fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Only show recipient field for new conversations
                    if (currentContact.isEmpty()) {
                        OutlinedTextField(
                            value = currentRecipient,
                            onValueChange = { viewModel.currentRecipient.value = it },
                            label = { Text("Recipient") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 8.dp),
                            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
                        )
                    }
                    
                    // Message input field
                    OutlinedTextField(
                        value = currentMessage,
                        onValueChange = { viewModel.currentMessage.value = it },
                        label = { Text("Message") },
                        modifier = Modifier
                            .weight(1f)
                            .padding(end = 8.dp),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                        keyboardActions = KeyboardActions(
                            onSend = {
                                if (currentMessage.isNotBlank()) {
                                    viewModel.sendMessage()
                                    focusManager.clearFocus()
                                }
                            }
                        )
                    )
                    
                    // Attachment button
                    IconButton(onClick = { viewModel.showAttachmentOptions.value = true }) {
                        Icon(Icons.Default.AttachFile, contentDescription = "Attach file")
                    }
                    
                    // Send button
                    IconButton(
                        onClick = {
                            if (currentMessage.isNotBlank()) {
                                viewModel.sendMessage()
                                focusManager.clearFocus()
                            }
                        },
                        enabled = currentMessage.isNotBlank() || currentAttachmentUri != null
                    ) {
                        Icon(Icons.Default.Send, contentDescription = "Send message")
                    }
                }
            }
        }
    ) { paddingValues ->
        if (!permissionsGranted) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "SMS permissions are required to use this app",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { viewModel.checkPermissions() }
                    ) {
                        Text("Check Permissions")
                    }
                }
            }
        } else {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxSize(),
                    reverseLayout = false
                ) {
                    items(messages) { message ->
                        MessageItem(
                            message = message,
                            onAttachmentClick = { viewModel.viewAttachment(message) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun AttachmentOptionsDialog(
    onDismiss: () -> Unit,
    onImageSelected: () -> Unit,
    onVideoSelected: () -> Unit,
    onAudioSelected: () -> Unit,
    onDocumentSelected: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Attach") },
        text = {
            Column {
                AttachmentOption(
                    icon = Icons.Default.Image,
                    title = "Image",
                    onClick = {
                        onImageSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.Videocam,
                    title = "Video",
                    onClick = {
                        onVideoSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.AudioFile,
                    title = "Audio",
                    onClick = {
                        onAudioSelected()
                        onDismiss()
                    }
                )
                
                AttachmentOption(
                    icon = Icons.Default.Description,
                    title = "Document",
                    onClick = {
                        onDocumentSelected()
                        onDismiss()
                    }
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun AttachmentOption(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
            .padding(12.dp)
            .clickable { onClick() },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary
        )
        
        Spacer(modifier = Modifier.width(16.dp))
        
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
fun AttachmentViewDialog(
    message: SmsMessage,
    onDismiss: () -> Unit,
    onOpenExternal: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (message.attachmentType) {
                    AttachmentType.IMAGE -> {
                        AsyncImage(
                            model = ImageRequest.Builder(LocalContext.current)
                                .data(message.attachmentUri)
                                .crossfade(true)
                                .build(),
                            contentDescription = "Image attachment",
                            contentScale = ContentScale.FillWidth,
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp)
                        )
                    }
                    AttachmentType.VIDEO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(200.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Videocam,
                                contentDescription = "Video",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    AttachmentType.AUDIO -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AudioFile,
                                contentDescription = "Audio",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp)
                                .background(MaterialTheme.colorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = "Document",
                                modifier = Modifier.size(64.dp),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                val attachmentTypeText = message.attachmentType.name
                    .lowercase()
                    .replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
                
                Text(
                    text = "$attachmentTypeText Attachment",
                    style = MaterialTheme.typography.titleMedium
                )
                
                if (message.body.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = message.body,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("Close")
                    }
                    
                    Button(onClick = onOpenExternal) {
                        Text("Open")
                    }
                }
            }
        }
    }
} 