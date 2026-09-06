package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuditScreen(
    onBack: () -> Unit,
    viewModel: AuditViewModel = viewModel(),
    accountViewModel: AccountViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val accounts by accountViewModel.accounts.collectAsState()
    
    var showFixDialog by remember { mutableStateOf<AuditIssue?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Audit & Integrity", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.runAudit() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF607D8B))
            )
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding).fillMaxSize()) {
            when (val state = uiState) {
                is AuditUiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                is AuditUiState.Success -> {
                    if (state.issues.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF4CAF50), modifier = Modifier.size(64.dp))
                            Spacer(modifier = Modifier.height(16.dp))
                            Text("Database Integrity is Perfect!", fontWeight = FontWeight.Bold)
                            Text("No orphaned or missing accounts found.", color = Color.Gray)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            item {
                                Text(
                                    "Found ${state.issues.size} integrity issues that need attention.",
                                    modifier = Modifier.padding(16.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.Red
                                )
                            }
                            items(state.issues) { issue ->
                                AuditIssueItem(issue) {
                                    showFixDialog = issue
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showFixDialog != null) {
            val issue = showFixDialog!!
            var selectedAccount by remember { mutableStateOf("") }
            
            AlertDialog(
                onDismissRequest = { showFixDialog = null },
                title = { Text(issue.title) },
                text = {
                    Column {
                        Text(issue.description)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Move affected transactions to:", fontWeight = FontWeight.Bold)
                        
                        accounts.forEach { acc ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.fillMaxWidth().clickable { selectedAccount = acc.nickName }.padding(vertical = 8.dp)
                            ) {
                                RadioButton(selected = selectedAccount == acc.nickName, onClick = { selectedAccount = acc.nickName })
                                Text(acc.nickName, modifier = Modifier.padding(start = 8.dp))
                            }
                        }
                    }
                },
                confirmButton = {
                    Button(
                        enabled = selectedAccount.isNotEmpty(),
                        onClick = {
                            when (issue.type) {
                                IssueType.ORPHANED_TRANSACTIONS -> {
                                    val orphans = issue.data as? List<String> ?: emptyList()
                                    orphans.forEach { viewModel.fixOrphaned(it, selectedAccount) }
                                }
                                IssueType.EMPTY_ACCOUNT_NAME -> {
                                    viewModel.fixBlank(selectedAccount)
                                }
                                else -> {}
                            }
                            showFixDialog = null
                        }
                    ) {
                        Text("FIX ALL")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showFixDialog = null }) { Text("CANCEL") }
                }
            )
        }
    }
}

@Composable
fun AuditIssueItem(issue: AuditIssue, onFix: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0))
    ) {
        Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Error, contentDescription = null, tint = Color(0xFFFF9800))
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(issue.title, fontWeight = FontWeight.Bold, color = Color(0xFFE65100))
                Text("${issue.affectedCount} transactions affected", fontSize = 12.sp)
            }
            Button(onClick = onFix, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100))) {
                Text("FIX")
            }
        }
    }
}
