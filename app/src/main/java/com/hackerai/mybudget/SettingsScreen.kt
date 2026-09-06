package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCategorySettings: () -> Unit = {},
    onNavigateToTagSettings: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToAccountManagement: () -> Unit = {}
) {
    val settingsItems = listOf(
        "PIN setup" to Color(0xFF4CAF50),
        "Backup" to Color(0xFF00BCD4),
        "Sync with Dropbox" to Color(0xFFFF9800),
        "Google Drive" to Color(0xFFF44336),
        "Export to Excel" to Color(0xFF2E7D32),
        "Export to CSV" to Color(0xFF1976D2),
        "Date" to Color(0xFF9E9E9E),
        "Display" to Color(0xFF2196F3),
        "Currency" to Color(0xFFE91E63),
        "Account Management" to Color(0xFF00ACC1),
        "Category" to Color(0xFF4CAF50),
        "Payee" to Color(0xFF673AB7),
        "Payer" to Color(0xFF9C27B0),
        "Tags" to Color(0xFFF44336),
        "Audit & Integrity" to Color(0xFF607D8B)
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(settingsItems) { item ->
                SettingsItem(
                    label = item.first,
                    stripeColor = item.second,
                    onClick = {
                        when (item.first) {
                            "Account Management" -> onNavigateToAccountManagement()
                            "Category" -> onNavigateToCategorySettings()
                            "Tags" -> onNavigateToTagSettings()
                            "Audit & Integrity" -> onNavigateToAudit()
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun SettingsItem(label: String, stripeColor: Color, onClick: () -> Unit = {}) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onClick() }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Color stripe on the left as per design
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(stripeColor)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = label,
                fontSize = 16.sp,
                color = Color.DarkGray
            )
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}
