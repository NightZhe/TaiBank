package com.example.remoteclient

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.*
import okhttp3.*
import org.json.JSONObject
import java.io.IOException

class WebSocketService : Service() {
    companion object {
        private const val TAG = "WebSocketService"
        // TODO: 改成你的 ws url
        private const val SERVER_WS_URL = "ws://YOUR_SERVER:8080/ws"
        private const val CHANNEL_ID = "remote_client_channel"
    }

    private val client = OkHttpClient()
    private var ws: WebSocket? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onCreate() {
        super.onCreate()
        startForegroundIfNeeded()
        connectWebSocket()
    }

    override fun onDestroy() {
        super.onDestroy()
        ws?.close(1000, "ServiceDestroyed")
        scope.cancel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun startForegroundIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(NotificationManager::class.java)
            val channel = NotificationChannel(CHANNEL_ID, "RemoteClient", NotificationManager.IMPORTANCE_LOW)
            nm.createNotificationChannel(channel)
        }
        val notif: Notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("RemoteClient")
            .setContentText("Connected")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .build()
        startForeground(1, notif)
    }

    private fun connectWebSocket() {
        val req = Request.Builder().url(SERVER_WS_URL).build()
        ws = client.newWebSocket(req, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.i(TAG, "ws open")
                sendDeviceInfo()
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                Log.i(TAG, "ws msg: $text")
                handleServerCommand(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                webSocket.close(1000, null)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.e(TAG, "ws fail: ${t.message}")
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        scope.launch {
            delay(5000)
            connectWebSocket()
        }
    }

    private fun sendDeviceInfo() {
        val json = JSONObject()
        json.put("device_id", android.os.Build.MODEL ?: "unknown")
        json.put("android_id", Utils.getAndroidId(this))
        json.put("ip", Utils.getLocalIpAddress(this))
        json.put("battery", Utils.getBatteryPercent(this))
        ws?.send(json.toString())
    }

    private fun handleServerCommand(msg: String) {
        try {
            val jo = JSONObject(msg)
            val action = jo.optString("action")
            when (action) {
                "launch" -> {
                    val pkg = jo.optString("package")
                    if (pkg.isNotEmpty()) {
                        launchApp(pkg)
                        sendAck(action, true, "launched $pkg")
                    } else sendAck(action, false, "no package")
                }
                "toast" -> {
                    val t = jo.optString("text")
                    Utils.showToast(this, t)
                    sendAck(action, true, "ok")
                }
                else -> sendAck(action, false, "unknown action")
            }
        } catch (e: Exception) {
            Log.e(TAG, "handle fail: ${e.message}")
        }
    }

    private fun sendAck(action: String, ok: Boolean, msg: String) {
        val jo = JSONObject()
        jo.put("type", "ack")
        jo.put("action", action)
        jo.put("ok", ok)
        jo.put("msg", msg)
        ws?.send(jo.toString())
    }

    private fun launchApp(pkg: String) {
        val pcm = packageManager
        val intent = pcm.getLaunchIntentForPackage(pkg)
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            // 若無法直接取得啟動 intent，嘗試開啟 package's main activity via am
            try {
                Runtime.getRuntime().exec(arrayOf("am", "start", "-n", pkg))
            } catch (e: Exception) {
                Log.e(TAG, "am start fail: ${e.message}")
            }
        }
    }
}
