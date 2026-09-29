package com.hackerai.mybudget

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Computer
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hackerai.mybudget.web.WebServerService
import com.hackerai.mybudget.web.WebServerStatus

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PcBrowserAccessScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val serverInfo by WebServerService.serverInfo.collectAsState()
    val isRunning = serverInfo.status == WebServerStatus.RUNNING

    fun copyToClipboard(label: String, text: String) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText(label, text)
        clipboard.setPrimaryClip(clip)
        Toast.makeText(context, "$label copied to clipboard", Toast.LENGTH_SHORT).show()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PC Browser Access", color = Color.White) },
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
                .background(Color(0xFFF8F9FA))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Security Note Card
            Surface(
                color = Color(0xFFE3F2FD),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = "Info", tint = Color(0xFF0288D1))
                    Text(
                        "PC Browser Access works without a PIN. Only use it on a trusted local network.",
                        fontSize = 13.sp,
                        color = Color(0xFF01579B),
                        fontWeight = FontWeight.Medium
                    )
                }
            }

            // Server Control Card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (isRunning) Color(0xFFE8F5E9) else Color.White
                ),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Computer,
                            contentDescription = "PC",
                            tint = if (isRunning) Color(0xFF2E7D32) else Color.Gray,
                            modifier = Modifier.size(32.dp)
                        )
                        Column {
                            Text(
                                text = if (isRunning) "Server Active" else "Server Stopped",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                color = if (isRunning) Color(0xFF2E7D32) else Color(0xFF37474F)
                            )
                            Text(
                                text = if (isRunning) "LAN Connection Ready" else "Press START to enable",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    Button(
                        onClick = {
                            val serviceIntent = Intent(context, WebServerService::class.java)
                            if (isRunning) {
                                serviceIntent.action = WebServerService.ACTION_STOP
                                context.startService(serviceIntent)
                            } else {
                                serviceIntent.action = WebServerService.ACTION_START
                                androidx.core.content.ContextCompat.startForegroundService(context, serviceIntent)
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isRunning) Color(0xFFC62828) else Color(0xFF00B0FF)
                        )
                    ) {
                        Text(if (isRunning) "STOP" else "START", fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (serverInfo.status == WebServerStatus.ERROR) {
                Surface(
                    color = Color(0xFFFFEBEE),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = serverInfo.errorMessage ?: "Failed to start web server",
                        color = Color(0xFFC62828),
                        fontSize = 13.sp,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            if (isRunning && serverInfo.url != null) {
                // Connection Info Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Text("Connection Details", fontWeight = FontWeight.Bold, fontSize = 16.sp, color = Color(0xFF263238))

                        // URL Field
                        Column {
                            Text("1. Open PC Browser URL:", fontSize = 12.sp, color = Color.Gray)
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 4.dp)
                                    .background(Color(0xFFF1F5F9), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(
                                    text = serverInfo.url!!,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    color = Color(0xFF0288D1)
                                )
                                IconButton(onClick = { copyToClipboard("URL", serverInfo.url!!) }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy URL", tint = Color(0xFF0288D1))
                                }
                            }
                        }
                    }
                }

                // Instructions Card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = Color.White),
                    elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("Instructions", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFF263238))
                        Text("• Connect your PC and Phone to the same Wi-Fi network or Hotspot.", fontSize = 12.sp, color = Color(0xFF546E7A))
                        Text("• Open Chrome/Edge/Firefox on your PC and navigate to the URL above.", fontSize = 12.sp, color = Color(0xFF546E7A))
                        Text("• MyBudget will open directly on your PC.", fontSize = 12.sp, color = Color(0xFF546E7A))
                    }
                }
            }
        }
    }
}
