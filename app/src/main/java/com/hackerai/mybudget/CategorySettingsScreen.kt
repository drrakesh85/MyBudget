package com.hackerai.mybudget

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
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
fun CategorySettingsScreen(
    onBack: () -> Unit,
    viewModel: CategoryViewModel = viewModel()
) {
    val categoriesMap by viewModel.categoriesMap.collectAsState()
    var selectedTab by remember { mutableIntStateOf(0) } // 0 for Expense, 1 for Income
    val tabs = listOf("EXPENSE", "INCOME")

    var showAddDialog by remember { mutableStateOf(false) }
    var dialogTitle by remember { mutableStateOf("") }
    var dialogTargetCategory by remember { mutableStateOf<String?>(null) }
    var dialogTargetSubcategory by remember { mutableStateOf<String?>(null) }
    var dialogMode by remember { mutableStateOf("add_category") } // "add_category", "add_subcategory", "edit_category", "edit_subcategory"

    if (showAddDialog) {
        var text by remember { mutableStateOf("") }
        val type = if (selectedTab == 0) "Expense" else "Income"
        
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            title = { Text(dialogTitle) },
            text = {
                TextField(
                    value = text,
                    onValueChange = { text = it },
                    placeholder = { Text("Enter name") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = { 
                    if (text.isNotBlank()) {
                        when (dialogMode) {
                            "add_category" -> viewModel.addCategory(text, type)
                            "add_subcategory" -> dialogTargetCategory?.let { viewModel.addSubcategory(it, text, type) }
                            "edit_category" -> dialogTargetCategory?.let { viewModel.renameCategory(it, text, type) }
                            "edit_subcategory" -> {
                                dialogTargetCategory?.let { cat ->
                                    dialogTargetSubcategory?.let { sub ->
                                        viewModel.renameSubcategory(cat, sub, text, type)
                                    }
                                }
                            }
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
            Column {
                TopAppBar(
                    title = { Text("Category:Subcategory", color = Color.White) },
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
                            dialogTitle = "Add Category"
                            dialogMode = "add_category"
                            showAddDialog = true 
                        }) {
                            Icon(Icons.Default.Add, contentDescription = "Add", tint = Color.White)
                        }
                        IconButton(onClick = {}) {
                            Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00ACC1))
                )
                TabRow(
                    selectedTabIndex = selectedTab,
                    containerColor = Color(0xFF00ACC1),
                    contentColor = Color.White,
                    indicator = { tabPositions ->
                        TabRowDefaults.SecondaryIndicator(
                            Modifier.tabIndicatorOffset(tabPositions[selectedTab]),
                            color = Color.White
                        )
                    }
                ) {
                    tabs.forEachIndexed { index, title ->
                        Tab(
                            selected = selectedTab == index,
                            onClick = { selectedTab = index },
                            text = { Text(title, color = Color.White) }
                        )
                    }
                }
            }
        }
    ) { padding ->
        val type = if (selectedTab == 0) "Expense" else "Income"
        val currentCategories = categoriesMap[type] ?: emptyMap()

        LazyColumn(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(Color.White)
        ) {
            items(currentCategories.keys.sorted().toList()) { category ->
                CategoryExpandableItem(
                    category = category,
                    subcategories = currentCategories[category] ?: emptyList(),
                    onAddSubcategory = {
                        dialogTitle = "Add Subcategory to $category"
                        dialogTargetCategory = category
                        dialogMode = "add_subcategory"
                        showAddDialog = true
                    },
                    onEditCategory = {
                        dialogTitle = "Edit Category: $category"
                        dialogTargetCategory = category
                        dialogMode = "edit_category"
                        showAddDialog = true
                    },
                    onEditSubcategory = { sub ->
                        dialogTitle = "Edit Subcategory: $sub"
                        dialogTargetCategory = category
                        dialogTargetSubcategory = sub
                        dialogMode = "edit_subcategory"
                        showAddDialog = true
                    }
                )
            }
        }
    }
}

@Composable
fun CategoryExpandableItem(
    category: String, 
    subcategories: List<String>,
    onAddSubcategory: () -> Unit = {},
    onEditCategory: () -> Unit = {},
    onEditSubcategory: (String) -> Unit = {}
) {
    var expanded by remember { mutableStateOf(false) }
    val initial = if (category.isNotEmpty()) category[0].uppercase() else "?"
    val color = remember(category) {
        val colors = listOf(
            Color(0xFF8BC34A), Color(0xFF00BCD4), Color(0xFFFF9800),
            Color(0xFFF44336), Color(0xFF9C27B0), Color(0xFF3F51B5)
        )
        colors[category.hashCode().coerceAtLeast(0) % colors.size]
    }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = color,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(initial, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                }
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = category,
                modifier = Modifier.weight(1f),
                fontSize = 16.sp,
                color = Color(0xFF00897B)
            )
            IconButton(onClick = onEditCategory) {
                Icon(Icons.Default.Edit, contentDescription = "Edit Category", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
            IconButton(onClick = onAddSubcategory) {
                Icon(Icons.Default.Add, contentDescription = "Add Subcategory", tint = Color.Gray, modifier = Modifier.size(20.dp))
            }
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                tint = Color.LightGray
            )
        }
        
        AnimatedVisibility(visible = expanded) {
            Column(modifier = Modifier.padding(start = 72.dp)) {
                subcategories.forEach { sub ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = sub,
                            modifier = Modifier.weight(1f),
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                        IconButton(onClick = { onEditSubcategory(sub) }) {
                            Icon(Icons.Default.Edit, contentDescription = "Edit Subcategory", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }
                }
                if (subcategories.isEmpty()) {
                    Text(
                        text = "No subcategories",
                        modifier = Modifier.padding(vertical = 12.dp),
                        fontSize = 14.sp,
                        color = Color.Gray
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}
