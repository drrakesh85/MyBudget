package com.hackerai.mybudget

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudBackupScreen(
    onBack: () -> Unit,
    onGoogleDriveSync: () -> Unit,
    onDropboxSync: () -> Unit,
    isGoogleDriveConnected: Boolean,
    isDropboxConnected: Boolean,
    expenseViewModel: ExpenseViewModel = viewModel()
) {
    val context = LocalContext.current

    val googleDriveAutoEnabled by expenseViewModel.googleDriveAutoBackupEnabled.collectAsState()
    val googleDriveInterval by expenseViewModel.googleDriveBackupInterval.collectAsState()
    val googleDriveLastBackup by expenseViewModel.googleDriveLastBackupTimestamp.collectAsState()

    val dropboxAutoEnabled by expenseViewModel.dropboxAutoBackupEnabled.collectAsState()
    val dropboxInterval by expenseViewModel.dropboxBackupInterval.collectAsState()
    val dropboxLastBackup by expenseViewModel.dropboxLastBackupTimestamp.collectAsState()

    var showUploadConfirmDialog by remember { mutableStateOf<String?>(null) }
    var showDownloadConfirmDialog by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cloud Backup Settings", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.primary)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color(0xFFF5F5F5))
                .verticalScroll(rememberScrollState())
                .padding(12.dp)
        ) {
            // Option 1: Auto Cloud Backup
            AutomaticCloudBackupCard(
                isGoogleConnected = isGoogleDriveConnected,
                googleAutoEnabled = googleDriveAutoEnabled,
                googleIntervalMinutes = googleDriveInterval,
                googleLastBackupTimestamp = googleDriveLastBackup,
                onGoogleAutoEnabledChange = { expenseViewModel.setGoogleDriveAutoBackupEnabled(it) },
                onGoogleIntervalChange = { expenseViewModel.setGoogleDriveBackupInterval(it) },
                onGoogleBackupNow = {
                    val account = GoogleSignIn.getLastSignedInAccount(context)
                    if (account != null) {
                        expenseViewModel.backupToGoogleDrive(account) { msg ->
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(context, "Connect Google Drive first", Toast.LENGTH_SHORT).show()
                    }
                },
                isDropboxConnected = isDropboxConnected,
                dropboxAutoEnabled = dropboxAutoEnabled,
                dropboxIntervalMinutes = dropboxInterval,
                dropboxLastBackupTimestamp = dropboxLastBackup,
                onDropboxAutoEnabledChange = { expenseViewModel.setDropboxAutoBackupEnabled(it) },
                onDropboxIntervalChange = { expenseViewModel.setDropboxBackupInterval(it) },
                onDropboxBackupNow = {
                    expenseViewModel.backupToDropbox { msg ->
                        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Option 2: Manual Cloud Backup & Restore
            ManualCloudBackupCard(
                isGoogleConnected = isGoogleDriveConnected,
                isDropboxConnected = isDropboxConnected,
                onConnectGoogle = onGoogleDriveSync,
                onConnectDropbox = onDropboxSync,
                onDisconnectDropbox = { expenseViewModel.disconnectDropbox() },
                onUploadGoogleClick = { showUploadConfirmDialog = "GOOGLE" },
                onDownloadGoogleClick = { showDownloadConfirmDialog = "GOOGLE" },
                onUploadDropboxClick = { showUploadConfirmDialog = "DROPBOX" },
                onDownloadDropboxClick = { showDownloadConfirmDialog = "DROPBOX" }
            )
        }
    }

    // Confirmation Dialog for UPLOAD
    showUploadConfirmDialog?.let { provider ->
        AlertDialog(
            onDismissRequest = { showUploadConfirmDialog = null },
            title = { Text("UPLOAD LOCAL DATA") },
            text = { Text("This will replace the existing $provider backup with your current MyBudget data.") },
            confirmButton = {
                Button(
                    onClick = {
                        val target = showUploadConfirmDialog
                        showUploadConfirmDialog = null
                        if (target == "GOOGLE") {
                            val account = GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) {
                                expenseViewModel.backupToGoogleDrive(account) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Connect Google Drive first", Toast.LENGTH_SHORT).show()
                            }
                        } else if (target == "DROPBOX") {
                            expenseViewModel.backupToDropbox { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("UPLOAD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showUploadConfirmDialog = null }) {
                    Text("CANCEL")
                }
            }
        )
    }

    // Confirmation Dialog for DOWNLOAD
    showDownloadConfirmDialog?.let { provider ->
        AlertDialog(
            onDismissRequest = { showDownloadConfirmDialog = null },
            title = { Text("DOWNLOAD AND RESTORE") },
            text = { Text("This will replace your current local MyBudget transactions and accounts with the selected $provider backup. Your current local data will be backed up before restoration.") },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                    onClick = {
                        val target = showDownloadConfirmDialog
                        showDownloadConfirmDialog = null
                        if (target == "GOOGLE") {
                            val account = GoogleSignIn.getLastSignedInAccount(context)
                            if (account != null) {
                                expenseViewModel.restoreFromGoogleDrive(account) { msg ->
                                    Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Connect Google Drive first", Toast.LENGTH_SHORT).show()
                            }
                        } else if (target == "DROPBOX") {
                            expenseViewModel.restoreFromDropbox { msg ->
                                Toast.makeText(context, msg, Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                ) {
                    Text("DOWNLOAD")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDownloadConfirmDialog = null }) {
                    Text("CANCEL")
                }
            }
        )
    }
}

@Composable
fun AutomaticCloudBackupCard(
    isGoogleConnected: Boolean,
    googleAutoEnabled: Boolean,
    googleIntervalMinutes: Int,
    googleLastBackupTimestamp: Long,
    onGoogleAutoEnabledChange: (Boolean) -> Unit,
    onGoogleIntervalChange: (Int) -> Unit,
    onGoogleBackupNow: () -> Unit,
    isDropboxConnected: Boolean,
    dropboxAutoEnabled: Boolean,
    dropboxIntervalMinutes: Int,
    dropboxLastBackupTimestamp: Long,
    onDropboxAutoEnabledChange: (Boolean) -> Unit,
    onDropboxIntervalChange: (Int) -> Unit,
    onDropboxBackupNow: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("1. AUTOMATIC CLOUD BACKUP", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text("Automatically upload backups at set intervals when connected.", fontSize = 12.sp, color = Color.Gray)

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = googleAutoEnabled, onCheckedChange = onGoogleAutoEnabledChange, enabled = isGoogleConnected)
                Text("Enable Google Drive Auto Backup", fontSize = 13.sp)
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = dropboxAutoEnabled, onCheckedChange = onDropboxAutoEnabledChange, enabled = isDropboxConnected)
                Text("Enable Dropbox Auto Backup", fontSize = 13.sp)
            }
        }
    }
}

@Composable
fun ManualCloudBackupCard(
    isGoogleConnected: Boolean,
    isDropboxConnected: Boolean,
    onConnectGoogle: () -> Unit,
    onConnectDropbox: () -> Unit,
    onDisconnectDropbox: () -> Unit,
    onUploadGoogleClick: () -> Unit,
    onDownloadGoogleClick: () -> Unit,
    onUploadDropboxClick: () -> Unit,
    onDownloadDropboxClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("2. MANUAL CLOUD BACKUP & RESTORE", fontWeight = FontWeight.Bold, fontSize = 15.sp, color = MaterialTheme.colorScheme.primary)
            Text("Manually upload local data to cloud or download cloud backup to restore.", fontSize = 12.sp, color = Color.Gray)

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isGoogleConnected) {
                    Button(onClick = onConnectGoogle) { Text("CONNECT GOOGLE") }
                } else {
                    OutlinedButton(onClick = onUploadGoogleClick) { Text("UPLOAD GOOGLE") }
                    OutlinedButton(onClick = onDownloadGoogleClick) { Text("DOWNLOAD GOOGLE") }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (!isDropboxConnected) {
                    Button(onClick = onConnectDropbox) { Text("CONNECT DROPBOX") }
                } else {
                    OutlinedButton(onClick = onUploadDropboxClick) { Text("UPLOAD DROPBOX") }
                    OutlinedButton(onClick = onDownloadDropboxClick) { Text("DOWNLOAD DROPBOX") }
                }
            }
        }
    }
}
