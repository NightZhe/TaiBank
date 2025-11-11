package com.example.remoteclient

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.widget.Button
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat


class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val tv = findViewById<TextView>(R.id.status)
        val btnStart = findViewById<Button>(R.id.btnStart)
        val btnStop = findViewById<Button>(R.id.btnStop)
        val btnLaunchKPlus = findViewById<Button>(R.id.btnLaunchKPlus)

        btnStart.setOnClickListener {
            val intent = Intent(this, WebSocketService::class.java)
            startForegroundService(intent)
            tv.text = "Service started"
        }

        btnStop.setOnClickListener {
            val intent = Intent(this, WebSocketService::class.java)
            stopService(intent)
            tv.text = "Service stopped"
        }

        btnLaunchKPlus.setOnClickListener {
            launchKPlus()
        }
    }

    private fun launchKPlus() {
        val kplusPackage = "com.kasikorn.retail.mbanking.wap"
        try {
            val intent = packageManager.getLaunchIntentForPackage(kplusPackage)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                startActivity(intent)
                Toast.makeText(this, "啟動 K PLUS", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "找不到 K PLUS 應用程式\n請確認已安裝", Toast.LENGTH_LONG).show()
            }
        } catch (e: Exception) {
            Toast.makeText(this, "啟動失敗: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}