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
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToCategorySettings: () -> Unit = {},
    onNavigateToTagSettings: () -> Unit = {},
    onNavigateToAudit: () -> Unit = {},
    onNavigateToAccountManagement: () -> Unit = {},
    onRestoreBackup: () -> Unit = {},
    onGoogleDriveSync: () -> Unit = {},
    onDropboxSync: () -> Unit = {},
    onExportCsv: () -> Unit = {},
    onExportExcel: () -> Unit = {},
    expenseViewModel: ExpenseViewModel = viewModel()
) {
    val isDropboxConnected by expenseViewModel.isDropboxConnected.collectAsState()
    val dropboxSyncState by expenseViewModel.dropboxSyncState.collectAsState()
    val isGoogleDriveConnected by expenseViewModel.isGoogleDriveConnected.collectAsState()
    val googleDriveSyncState by expenseViewModel.googleDriveSyncState.collectAsState()
    val googleDriveLastSuccessfulSyncMillis by expenseViewModel.googleDriveLastSuccessfulSyncMillis.collectAsState()

    LaunchedEffect(Unit) {
        expenseViewModel.refreshGoogleDriveConnection()
    }

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
                if (item.first == "Sync with Dropbox") {
                    DropboxSettingsItem(
                        isConnected = isDropboxConnected,
                        syncState = dropboxSyncState,
                        onConnect = onDropboxSync,
                        onSyncNow = { expenseViewModel.syncWithDropbox() },
                        onDisconnect = { expenseViewModel.disconnectDropbox() }
                    )
                } else if (item.first == "Google Drive") {
                    GoogleDriveSettingsItem(
                        isConnected = isGoogleDriveConnected,
                        syncState = googleDriveSyncState,
                        lastSuccessfulSyncMillis = googleDriveLastSuccessfulSyncMillis,
                        onConnectOrSync = onGoogleDriveSync
                    )
                } else {
                    SettingsItem(
                        label = item.first,
                        stripeColor = item.second,
                        onClick = {
                            when (item.first) {
                                "Account Management" -> onNavigateToAccountManagement()
                                "Category" -> onNavigateToCategorySettings()
                                "Tags" -> onNavigateToTagSettings()
                                "Audit & Integrity" -> onNavigateToAudit()
                                "Backup" -> onRestoreBackup()
                                "Export to CSV" -> onExportCsv()
                                "Export to Excel" -> onExportExcel()
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun DropboxSettingsItem(
    isConnected: Boolean,
    syncState: SyncState,
    onConnect: () -> Unit,
    onSyncNow: () -> Unit,
    onDisconnect: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(Color(0xFFFF9800))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Sync with Dropbox",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                val statusText = when (syncState) {
                    is SyncState.Loading -> "Syncing..."
                    is SyncState.Success -> syncState.message
                    is SyncState.Error -> syncState.message
                    is SyncState.Idle -> if (isConnected) "Connected" else "Not connected"
                }
                Text(text = statusText, fontSize = 12.sp, color = Color.Gray)
            }
            if (!isConnected) {
                TextButton(onClick = onConnect) {
                    Text("CONNECT")
                }
            } else {
                Row {
                    TextButton(onClick = onSyncNow, enabled = syncState !is SyncState.Loading) {
                        Text("SYNC NOW")
                    }
                    TextButton(onClick = onDisconnect) {
                        Text("DISCONNECT", color = Color.Red)
                    }
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

@Composable
fun GoogleDriveSettingsItem(
    isConnected: Boolean,
    syncState: SyncState,
    lastSuccessfulSyncMillis: Long?,
    onConnectOrSync: () -> Unit
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = syncState !is SyncState.Loading) { onConnectOrSync() }
                .padding(vertical = 12.dp, horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .width(4.dp)
                    .height(24.dp)
                    .background(Color(0xFFF44336))
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Google Drive",
                    fontSize = 16.sp,
                    color = Color.DarkGray
                )
                val statusText = when (syncState) {
                    is SyncState.Loading -> "Syncing..."
                    is SyncState.Error -> "Sync failed"
                    is SyncState.Success -> formatGoogleDriveLastSynced(lastSuccessfulSyncMillis)
                    is SyncState.Idle -> when {
                        lastSuccessfulSyncMillis != null -> formatGoogleDriveLastSynced(lastSuccessfulSyncMillis)
                        isConnected -> "Connected"
                        else -> "Not connected"
                    }
                }
                Text(text = statusText, fontSize = 12.sp, color = Color.Gray)
            }
            if (!isConnected) {
                TextButton(
                    onClick = onConnectOrSync,
                    enabled = syncState !is SyncState.Loading
                ) {
                    Text("CONNECT")
                }
            } else {
                TextButton(
                    onClick = onConnectOrSync,
                    enabled = syncState !is SyncState.Loading
                ) {
                    Text("SYNC NOW")
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

private fun formatGoogleDriveLastSynced(lastSuccessfulSyncMillis: Long?): String {
    if (lastSuccessfulSyncMillis == null) return "Connected"
    val formatted = DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT)
        .format(Date(lastSuccessfulSyncMillis))
    return "Last synced: $formatted"
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
