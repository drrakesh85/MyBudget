package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCloudBackup: () -> Unit = {},
    onNavigateToCategorySettings: () -> Unit = {},
    onNavigateToTagSettings: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToAccountManagement: () -> Unit = {},
    onNavigateToBackupRestore: () -> Unit = {},
    onNavigateToPcBrowserAccess: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportExcel: () -> Unit = {},
    onImportCsv: () -> Unit = {},
    onImportExcel: () -> Unit = {},
    onTestCsvImport: () -> Unit = {},
    expenseViewModel: ExpenseViewModel = viewModel()
) {
    val settingsItems = listOf(
        "PC Browser Access" to Color(0xFF00B0FF),
        "Cloud Backup" to Color(0xFF1976D2),
        "Account Management" to Color(0xFF00ACC1),
        "Category" to Color(0xFF4CAF50),
        "Tags" to Color(0xFFF44336),
        "Backup & Restore App Data" to Color(0xFF00BCD4),
        "Import from CSV" to Color(0xFFCDDC39),
        "Export to CSV" to Color(0xFF1976D2),
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
                            "PC Browser Access" -> onNavigateToPcBrowserAccess()
                            "Cloud Backup" -> onNavigateToCloudBackup()
                            "Account Management" -> onNavigateToAccountManagement()
                            "Category" -> onNavigateToCategorySettings()
                            "Tags" -> onNavigateToTagSettings()
                            "Audit & Integrity" -> onNavigateToAudit()
                            "Backup & Restore App Data" -> onNavigateToBackupRestore()
                            "Export to CSV" -> onExportCsv()
                            "Import from CSV" -> onImportCsv()
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
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(stripeColor)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
        }
        HorizontalDivider(color = Color(0xFFEEEEEE))
    }
}
