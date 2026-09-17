package com.hackerai.mybudget

import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupRestoreScreen(
    onBack: () -> Unit,
    viewModel: ExpenseViewModel = viewModel()
) {
    var selectedTabIndex by remember { mutableIntStateOf(0) }
    val tabs = listOf("BACKUP", "RESTORE")
    val context = LocalContext.current

    var categoriesCheck by remember { mutableStateOf(true) }
    var tagsCheck by remember { mutableStateOf(true) }
    var payerCheck by remember { mutableStateOf(true) }
    var payeeCheck by remember { mutableStateOf(true) }

    var showForceReplaceDialog by remember { mutableStateOf(false) }

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportFullBackup { json ->
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(json.toByteArray())
                        stream.flush()
                    }
                    Toast.makeText(context, "Full backup saved successfully", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "Failed to save backup: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            try {
                context.contentResolver.openInputStream(it)?.use { stream ->
                    val content = stream.bufferedReader().readText()
                    if (content.isBlank()) {
                        Toast.makeText(context, "The selected file is empty", Toast.LENGTH_LONG).show()
                        return@use
                    }
                    
                    if (content.trim().startsWith("{")) {
                        // JSON Full Backup
                        viewModel.importFullBackup(content) {
                            Toast.makeText(context, "Full Restore complete!", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        // CSV Metadata Restore
                        viewModel.importAppData(content, categoriesCheck, tagsCheck, payerCheck, payeeCheck) {
                            Toast.makeText(context, "Restore complete!", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Restore failed: ${e.message}", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            Column(modifier = Modifier.background(MaterialTheme.colorScheme.primary)) {
                TopAppBar(
                    title = { Text("Backup & Restore", color = Color.White) },
                    navigationIcon = {
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                )

                TabRow(
                    selectedTabIndex = selectedTabIndex,
                    containerColor = Color.Transparent,
                    contentColor = Color.White
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTabIndex == index,
                            onClick = { selectedTabIndex = index },
                            text = { Text(title, color = Color.White) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
                .padding(16.dp)
        ) {
            Text(
                text = if (selectedTabIndex == 0) "Complete Data Backup" else "Data Restore",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Text(
                text = if (selectedTabIndex == 0) 
                    "Saves all transactions, accounts, and settings to a JSON file." 
                    else "Restore your data from a previously saved JSON or legacy CSV file.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            if (selectedTabIndex == 0) {
                Button(
                    onClick = { createDocumentLauncher.launch("my_budget_full_backup.json") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("BACKUP ALL DATA (JSON)")
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                
                OutlinedButton(
                    onClick = { 
                        // Keep legacy CSV backup for categories only if needed
                        viewModel.exportAppData(categoriesCheck, tagsCheck, payerCheck, payeeCheck) { csv ->
                             // Using a different mechanism for this would be better but keeping it simple
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = false // Disabled in favor of full JSON backup
                ) {
                    Text("Backup Metadata only (CSV)")
                }
            } else {
                Button(
                    onClick = { 
                        openDocumentLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*")) 
                    },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Text("BROWSE & RESTORE FILE")
                }
            }
            
            if (selectedTabIndex == 1) {
                Spacer(modifier = Modifier.height(24.dp))
                Text("Metadata Restore Options (CSV only):", style = MaterialTheme.typography.labelLarge)
                CheckboxItem(label = "Categories", checked = categoriesCheck, onCheckedChange = { categoriesCheck = it })
                CheckboxItem(label = "Tags", checked = tagsCheck, onCheckedChange = { tagsCheck = it })
                CheckboxItem(label = "Payer", checked = payerCheck, onCheckedChange = { payerCheck = it })
                CheckboxItem(label = "Payee", checked = payeeCheck, onCheckedChange = { payeeCheck = it })
            }

            if (selectedTabIndex == 1) {
                Spacer(modifier = Modifier.height(32.dp))
                HorizontalDivider(color = Color.Red.copy(alpha = 0.2f))
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = "Dangerous Zone",
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.Red,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.height(8.dp))
                
                OutlinedButton(
                    onClick = { showForceReplaceDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red)
                ) {
                    Text("FORCE REPLACE GOOGLE DRIVE FROM LOCAL")
                }
            }
        }

        if (showForceReplaceDialog) {
            AlertDialog(
                onDismissRequest = { showForceReplaceDialog = false },
                title = { Text("WARNING") },
                text = {
                    Text("This will replace the Google Drive sync_data.json with the current local database.\n\nExisting Google Drive data will NOT be merged.\n\nMake sure the local data has been verified before continuing.")
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showForceReplaceDialog = false
                            val account = viewModel.getLastSignedInGoogleAccount()
                            if (account != null) {
                                viewModel.forceReplaceGoogleDrive(account) { result ->
                                    if (result == "SUCCESS") {
                                        Toast.makeText(context, "Google Drive data replaced and verified.", Toast.LENGTH_LONG).show()
                                    } else {
                                        Toast.makeText(context, "Operation failed: $result", Toast.LENGTH_LONG).show()
                                    }
                                }
                            } else {
                                Toast.makeText(context, "Please sign in to Google Drive first.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                    ) {
                        Text("BACK UP & REPLACE")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showForceReplaceDialog = false }) {
                        Text("CANCEL")
                    }
                }
            )
        }
    }
}

@Composable
fun CheckboxItem(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onCheckedChange(!checked) }
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Spacer(modifier = Modifier.width(12.dp))
        Text(text = label, fontSize = 16.sp)
    }
}
