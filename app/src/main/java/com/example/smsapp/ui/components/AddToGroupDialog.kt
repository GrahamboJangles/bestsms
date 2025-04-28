package com.example.smsapp.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.smsapp.model.ContactGroup

@Composable
fun AddToGroupDialog(
    contactAddress: String,
    groups: List<ContactGroup>,
    isInGroup: (String, String) -> Boolean,
    onAddToGroup: (String, String) -> Unit,
    onRemoveFromGroup: (String, String) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add to Groups") },
        text = {
            Column {
                Text(
                    text = "Select groups for $contactAddress",
                    style = MaterialTheme.typography.bodyMedium
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                LazyColumn {
                    items(groups) { group ->
                        val isChecked = isInGroup(group.id, contactAddress)
                        
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    // Toggle the group membership when the row is clicked
                                    if (isChecked) {
                                        onRemoveFromGroup(group.id, contactAddress)
                                    } else {
                                        onAddToGroup(group.id, contactAddress)
                                    }
                                }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Checkbox(
                                checked = isChecked,
                                onCheckedChange = { checked ->
                                    if (checked) {
                                        onAddToGroup(group.id, contactAddress)
                                    } else {
                                        onRemoveFromGroup(group.id, contactAddress)
                                    }
                                }
                            )
                            
                            Text(
                                text = group.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.padding(start = 8.dp)
                            )
                        }
                        
                        Divider()
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
} 