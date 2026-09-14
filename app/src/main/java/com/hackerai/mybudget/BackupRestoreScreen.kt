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

    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        uri?.let {
            viewModel.exportAppData(categoriesCheck, tagsCheck, payerCheck, payeeCheck) { csv ->
                try {
                    context.contentResolver.openOutputStream(it)?.use { stream ->
                        stream.write(csv.toByteArray())
                        stream.flush()
                    }
                    Toast.makeText(context, "Backup saved successfully", Toast.LENGTH_SHORT).show()
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
                    viewModel.importAppData(content, categoriesCheck, tagsCheck, payerCheck, payeeCheck) {
                        Toast.makeText(context, "Restore complete!", Toast.LENGTH_SHORT).show()
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
                    title = { Text("Backup & Restore App Data", color = Color.White) },
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
                text = if (selectedTabIndex == 0) "Select data to backup:" else "Select data to restore:",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            CheckboxItem(label = "Categories", checked = categoriesCheck, onCheckedChange = { categoriesCheck = it })
            CheckboxItem(label = "Tags", checked = tagsCheck, onCheckedChange = { tagsCheck = it })
            CheckboxItem(label = "Payer", checked = payerCheck, onCheckedChange = { payerCheck = it })
            CheckboxItem(label = "Payee", checked = payeeCheck, onCheckedChange = { payeeCheck = it })

            Spacer(modifier = Modifier.weight(1f))

            if (selectedTabIndex == 0) {
                Button(
                    onClick = { createDocumentLauncher.launch("app_metadata_backup.csv") },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BACKUP TO CSV")
                }
            } else {
                Button(
                    onClick = { 
                        // Support various CSV and text MIME types for better compatibility
                        openDocumentLauncher.launch(arrayOf(
                            "text/csv", 
                            "text/plain", 
                            "text/comma-separated-values", 
                            "application/csv", 
                            "application/vnd.ms-excel",
                            "*/*" // Fallback to allow any file if the above fail
                        )) 
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("BROWSE & RESTORE")
                }
            }
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
