package com.example.smsapp.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.example.smsapp.ui.components.AddGroupDialog
import com.example.smsapp.ui.components.AddToGroupDialog
import com.example.smsapp.ui.components.ConversationItem
import com.example.smsapp.ui.components.GroupTab
import com.example.smsapp.viewmodel.SmsViewModel
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConversationListScreen(viewModel: SmsViewModel) {
    // State for dialogs
    var showAddToGroupDialog by remember { mutableStateOf(false) }
    var selectedContactForGroup by remember { mutableStateOf("") }
    var showDebugDialog by remember { mutableStateOf(false) }
    val showGroupDialog by remember { viewModel.showGroupDialog }
    val newGroupName by remember { viewModel.newGroupName }
    val selectedGroupId by remember { viewModel.selectedGroupId }
    val diagnosticInfo by remember { viewModel.diagnosticInfo }
    
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Messages") },
                actions = {
                    // Debug info button
                    IconButton(onClick = { showDebugDialog = true }) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Debug Info"
                        )
                    }
                    // Add new group button
                    IconButton(onClick = { viewModel.showGroupDialog.value = true }) {
                        Icon(
                            imageVector = Icons.Default.AddCircle,
                            contentDescription = "Add Group"
                        )
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.composeNewMessage() }
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "New Message"
                )
            }
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Group tabs
            LazyRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp, horizontal = 4.dp)
            ) {
                // All tab
                item {
                    GroupTab(
                        name = "All",
                        isSelected = selectedGroupId == "all",
                        onClick = { viewModel.selectGroup("all") }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
                
                // Custom group tabs
                items(viewModel.groups) { group ->
                    GroupTab(
                        name = group.name,
                        isSelected = selectedGroupId == group.id,
                        onClick = { viewModel.selectGroup(group.id) }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                }
            }
            
            // Conversation list
            if (viewModel.conversations.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    if (selectedGroupId == "all") {
                        Text(
                            text = "No conversations yet\nTap + to start a new conversation",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    } else {
                        Text(
                            text = "No conversations in this group\nLong-press on a conversation to add it to this group",
                            textAlign = TextAlign.Center,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    items(viewModel.conversations) { conversation ->
                        ConversationItem(
                            conversation = conversation,
                            onClick = {
                                viewModel.selectConversation(conversation.address)
                            },
                            onLongClick = {
                                selectedContactForGroup = conversation.address
                                showAddToGroupDialog = true
                            }
                        )
                    }
                }
            }
        }
        
        // Group management dialogs
        if (showAddToGroupDialog) {
            AddToGroupDialog(
                contactAddress = selectedContactForGroup,
                groups = viewModel.groups,
                isInGroup = viewModel::isContactInGroup,
                onAddToGroup = viewModel::addContactToGroup,
                onRemoveFromGroup = viewModel::removeContactFromGroup,
                onDismiss = { showAddToGroupDialog = false }
            )
        }
        
        if (showGroupDialog) {
            AddGroupDialog(
                groupName = newGroupName,
                onGroupNameChange = { viewModel.newGroupName.value = it },
                onAddGroup = { viewModel.addGroup(newGroupName) },
                onDismiss = { viewModel.showGroupDialog.value = false }
            )
        }

        // Debug info dialog
        if (showDebugDialog) {
            AlertDialog(
                onDismissRequest = { showDebugDialog = false },
                title = { Text("Diagnostic Information") },
                text = { 
                    Column(
                        modifier = Modifier
                            .verticalScroll(rememberScrollState())
                            .padding(8.dp)
                    ) {
                        Text(
                            text = diagnosticInfo,
                            style = MaterialTheme.typography.bodySmall
                        )
                        
                        Spacer(modifier = Modifier.height(8.dp))
                        
                        // Add RCS stats
                        val rcsCount = viewModel.messages.count { it.isRcs }
                        Text(
                            text = "RCS Messages in Current Conversation: $rcsCount",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                confirmButton = {
                    androidx.compose.material3.Button(
                        onClick = { 
                            showDebugDialog = false
                            // Reload messages to retry
                            viewModel.loadMessages()
                        }
                    ) {
                        Text("Reload Messages")
                    }
                },
                dismissButton = {
                    androidx.compose.material3.TextButton(
                        onClick = { showDebugDialog = false }
                    ) {
                        Text("Close")
                    }
                }
            )
        }
    }
} 