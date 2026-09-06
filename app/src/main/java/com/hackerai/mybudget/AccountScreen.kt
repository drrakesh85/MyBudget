package com.hackerai.mybudget

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.hackerai.mybudget.data.AccountType
import java.text.NumberFormat
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onManageAccounts: () -> Unit,
    onNavigateToAccountSummary: (String) -> Unit,
    viewModel: AccountViewModel = viewModel()
) {
    val accountBalances by viewModel.accountBalances.collectAsState()
    var isEditMode by remember { mutableStateOf(false) }
    
    // State for drag and drop
    var draggedItemIndex by remember { mutableStateOf<Int?>(null) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    val lazyListState = rememberLazyListState()

    // Create a mutable copy of the list for local reordering during drag
    val currentList = remember(accountBalances) { accountBalances.toMutableStateList() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Account", color = Color.White) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.ViewModule, contentDescription = "Grid View", tint = Color.White)
                    }
                    IconButton(onClick = { isEditMode = !isEditMode }) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit Order", tint = if (isEditMode) Color.Yellow else Color.White)
                    }
                    IconButton(onClick = {}) {
                        Icon(Icons.Default.MoreVert, contentDescription = "More", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF00ACC1))
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onManageAccounts,
                containerColor = Color(0xFF00ACC1),
                contentColor = Color.White
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add Account")
            }
        },
        bottomBar = {
            val totalBalance = accountBalances.sumOf { it.balance }
            Surface(
                color = Color(0xFFE8F5E9),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(8.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "Total Accounts Balance: ${formatAmount(totalBalance)} INR",
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) { paddingValues ->
        LazyColumn(
            state = lazyListState,
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .background(Color.White)
                .pointerInput(isEditMode) {
                    if (!isEditMode) return@pointerInput
                    detectDragGesturesAfterLongPress(
                        onDragStart = { offset ->
                            lazyListState.layoutInfo.visibleItemsInfo
                                .firstOrNull { item -> 
                                    offset.y.toInt() in item.offset..(item.offset + item.size)
                                }?.let { item ->
                                    draggedItemIndex = item.index
                                }
                        },
                        onDrag = { change, dragAmount ->
                            change.consume()
                            draggedItemIndex?.let { index ->
                                dragOffset += dragAmount.y
                                
                                // Basic reordering logic
                                val targetIndex = when {
                                    dragOffset > 50f && index < currentList.size - 1 -> index + 1
                                    dragOffset < -50f && index > 0 -> index - 1
                                    else -> null
                                }
                                
                                if (targetIndex != null) {
                                    val item = currentList.removeAt(index)
                                    currentList.add(targetIndex, item)
                                    viewModel.moveAccount(index, targetIndex)
                                    draggedItemIndex = targetIndex
                                    dragOffset = 0f
                                }
                            }
                        },
                        onDragEnd = {
                            draggedItemIndex = null
                            dragOffset = 0f
                        },
                        onDragCancel = {
                            draggedItemIndex = null
                            dragOffset = 0f
                        }
                    )
                }
        ) {
            itemsIndexed(currentList, key = { _, item -> item.account.id }) { index, item ->
                val isDragging = draggedItemIndex == index
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "")
                
                AccountBalanceItem(
                    accountBalance = item,
                    isEditMode = isEditMode,
                    modifier = Modifier
                        .shadow(elevation)
                        .background(if (isDragging) Color(0xFFF0F0F0) else Color.White),
                    onClick = {
                        if (!isEditMode) {
                            onNavigateToAccountSummary(item.account.nickName)
                        }
                    },
                    onMoveUp = {
                        if (index > 0) {
                            viewModel.moveAccount(index, index - 1)
                        }
                    },
                    onMoveDown = {
                        if (index < currentList.size - 1) {
                            viewModel.moveAccount(index, index + 1)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun AccountBalanceItem(
    accountBalance: AccountViewModel.AccountBalance,
    isEditMode: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit
) {
    val account = accountBalance.account
    val color = when (account.type) {
        AccountType.SAVING -> Color(0xFF00BCD4)
        AccountType.CREDIT_CARD -> Color(0xFFE91E63)
        AccountType.CASH -> Color(0xFF4CAF50)
        AccountType.LOAN -> Color(0xFFFF9800)
    }

    Column(modifier = modifier.clickable { onClick() }) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Colored stripe
            Box(
                modifier = Modifier
                    .width(6.dp)
                    .height(40.dp)
                    .background(color)
            )
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = account.nickName,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp,
                    color = Color.DarkGray
                )
                Text(
                    text = account.nickName, // Mocking subtitle from screenshot
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "Account Balance INR",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
            if (isEditMode) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onMoveUp) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Move Up")
                    }
                    IconButton(onClick = onMoveDown) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Move Down")
                    }
                    Icon(
                        Icons.Default.DragHandle,
                        contentDescription = "Long press to drag",
                        tint = Color.Gray
                    )
                }
            } else {
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = formatAmount(accountBalance.income),
                        color = Color(0xFF2E7D32),
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatAmount(accountBalance.expense),
                        color = Color.Red,
                        fontSize = 14.sp
                    )
                    Text(
                        text = formatAmount(accountBalance.balance),
                        color = Color(0xFF2E7D32),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
        HorizontalDivider(thickness = 0.5.dp, color = Color(0xFFEEEEEE))
    }
}

private fun formatAmount(amount: Double): String {
    val formatter = NumberFormat.getNumberInstance(Locale.getDefault())
    formatter.minimumFractionDigits = 2
    formatter.maximumFractionDigits = 2
    return formatter.format(amount)
}
