package com.hackerai.mybudget

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TagSettingsScreen(
    onBack: () -> Unit,
    viewModel: TagViewModel = viewModel()
) {
    val tags by viewModel.tags.collectAsState()

    var showAddDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogTargetTag by remember { mutableStateOf<String?>(null) }
    var dialogMode by remember { mutableStateOf("add") } // "add", "edit"

    if (showAddDialog) {
        var text by remember { mutableStateOf(dialogTargetTag ?: "") }
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(dialogTitle) },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter tag name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (text.isNotBlank()) {
                        when (dialogMode) {
                            "add" -> viewModel.addTag(text)
                            "edit" -> dialogTargetTag?.let { viewModel.renameTag(it, text) }
                        }
                    }
                    showAddDialog = false 
                }) {
                    Text("SAVE")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("CANCEL")
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Tags", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.White)
                    }
                    IconButton(onClick = { 
                        dialogTitle = "Add Tag"
                        dialogMode = "add"
                        dialogTargetTag = null
                        showAddDialog = true 
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00ACC1))
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(tags) { tag ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = tag,
                        modifier = Modifier.weight(1f),
                        fontSize = 16.sp
                    )
                    IconButton(onClick = {
                        dialogTitle = "Edit Tag"
                        dialogTargetTag = tag
                        dialogMode = "edit"
                        showAddDialog = true
                    }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit", tint = Color.Gray)
                    }
                }
                HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
            }
        }
    }
}
