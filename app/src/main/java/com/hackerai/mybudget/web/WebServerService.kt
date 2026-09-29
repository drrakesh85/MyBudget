package com.hackerai.mybudget.web

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.hackerai.mybudget.MainActivity
import com.hackerai.mybudget.R
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class WebServerStatus {
    STOPPED,
    STARTING,
    RUNNING,
    ERROR
}

data class WebServerInfo(
    val status: WebServerStatus = WebServerStatus.STOPPED,
    val ipAddress: String? = null,
    val port: Int = 8080,
    val errorMessage: String? = null
) {
    val url: String?
        get() = if (ipAddress != null && status == WebServerStatus.RUNNING) "http://$ipAddress:$port" else null
}

class WebServerService : Service() {

    private val binder = LocalBinder()
    private var httpServer: MyBudgetHttpServer? = null

    inner class LocalBinder : Binder() {
        fun getService(): WebServerService = this@WebServerService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        when (action) {
            ACTION_START -> startServer()
            ACTION_STOP -> stopServer()
        }
        return START_NOT_STICKY
    }

    fun startServer() {
        if (_serverInfo.value.status == WebServerStatus.RUNNING) return

        _serverInfo.value = WebServerInfo(status = WebServerStatus.STARTING)

        // 1. Immediately post foreground notification to satisfy Android 14+ FGS startup window
        startForegroundNotification("Starting...", 8080)

        // 2. Discover LAN IP Address
        val ip = NetworkUtils.getLocalIpAddress()
        if (ip == null) {
            _serverInfo.value = WebServerInfo(
                status = WebServerStatus.ERROR,
                errorMessage = "No active LAN Wi-Fi or hotspot connection found."
            )
            stopForegroundNotification()
            stopSelf()
            return
        }

        // 3. Bind to port with fallbacks
        val possiblePorts = listOf(8080, 8081, 8082, 8888, 9090)
        var boundPort = 8080
        var startedServer: MyBudgetHttpServer? = null
        var lastError: Exception? = null

        for (p in possiblePorts) {
            try {
                val server = MyBudgetHttpServer(this, port = p)
                server.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false)
                startedServer = server
                boundPort = p
                break
            } catch (e: Exception) {
                lastError = e
            }
        }

        if (startedServer == null) {
            Log.e(TAG, "Failed to bind server to any port", lastError)
            _serverInfo.value = WebServerInfo(
                status = WebServerStatus.ERROR,
                errorMessage = "Could not bind server to ports: ${lastError?.message}"
            )
            stopForegroundNotification()
            stopSelf()
            return
        }

        httpServer = startedServer
        activeServerInstance = startedServer

        _serverInfo.value = WebServerInfo(
            status = WebServerStatus.RUNNING,
            ipAddress = ip,
            port = boundPort
        )

        // 4. Update notification with bound IP and Port
        startForegroundNotification(ip, boundPort)
        Log.i(TAG, "MyBudget Web Server started at http://$ip:$boundPort")
    }

    fun stopServer() {
        try {
            httpServer?.stop()
            httpServer = null
            activeServerInstance = null
        } catch (e: Exception) {
            Log.e(TAG, "Error stopping server", e)
        }

        stopForegroundNotification()
        _serverInfo.value = WebServerInfo(status = WebServerStatus.STOPPED)
        stopSelf()
        Log.i(TAG, "MyBudget Web Server stopped.")
    }

    private fun stopForegroundNotification() {
        try {
            stopForeground(STOP_FOREGROUND_REMOVE)
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping foreground notification", e)
        }
    }

    private fun startForegroundNotification(ip: String, port: Int) {
        createNotificationChannel()

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            Intent(this, WebServerService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MyBudget PC Access Active")
            .setContentText("http://$ip:$port — Local Network Access")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .addAction(R.mipmap.ic_launcher, "STOP", stopIntent)
            .build()

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE)
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed startForeground with CONNECTED_DEVICE, falling back to basic notification", e)
            try {
                startForeground(NOTIFICATION_ID, notification)
            } catch (ex: Exception) {
                Log.e(TAG, "Failed startForeground fallback", ex)
            }
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "PC Browser Access Server",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notification for active MyBudget LAN PC Web Server"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        stopServer()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "WebServerService"
        private const val CHANNEL_ID = "mybudget_web_server_channel"
        private const val NOTIFICATION_ID = 8080

        const val ACTION_START = "com.hackerai.mybudget.web.ACTION_START"
        const val ACTION_STOP = "com.hackerai.mybudget.web.ACTION_STOP"

        var activeServerInstance: MyBudgetHttpServer? = null

        private val _serverInfo = MutableStateFlow(WebServerInfo())
        val serverInfo: StateFlow<WebServerInfo> = _serverInfo.asStateFlow()
    }
}
