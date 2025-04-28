package com.example.smsapp

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.example.smsapp.ui.screens.ConversationListScreen
import com.example.smsapp.ui.screens.SmsScreen
import com.example.smsapp.ui.theme.SMSappTheme
import com.example.smsapp.util.SmsUtils
import com.example.smsapp.viewmodel.SmsViewModel

class MainActivity : ComponentActivity() {
    
    private val viewModel: SmsViewModel by viewModels()
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        // Request permissions
        if (!SmsUtils.hasPermissions(this)) {
            SmsUtils.requestSmsPermission(this)
        }
        
        setContent {
            val isInConversationList by remember { viewModel.isInConversationList }
            
            SMSappTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (isInConversationList) {
                        ConversationListScreen(viewModel = viewModel)
                    } else {
                        SmsScreen(viewModel = viewModel)
                    }
                }
            }
        }
    }
    
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (!viewModel.isInConversationList.value) {
            viewModel.goToConversationList()
        } else {
            super.onBackPressed()
        }
    }
    
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        // Check if permissions were granted
        viewModel.checkPermissions()
    }
}