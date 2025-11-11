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
    companion object {
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        private const val REQUEST_CODE_PERMISSIONS = 1002
    }

    private var screenCaptureResultCode = 0
    private var screenCaptureData: Intent? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            startService(Intent(this, WebSocketService::class.java))
            Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStartRecord).setOnClickListener {
            checkPermissionsAndStartRecord()
        }

        findViewById<Button>(R.id.btnStopRecord).setOnClickListener {
            stopScreenRecord()
        }
    }

    private fun checkPermissionsAndStartRecord() {
        val permissions = mutableListOf<String>()
        
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
                != PackageManager.PERMISSION_GRANTED) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissions.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            requestScreenCapture()
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_CODE_SCREEN_CAPTURE)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_CODE_SCREEN_CAPTURE) {
            if (resultCode == Activity.RESULT_OK && data != null) {
                screenCaptureResultCode = resultCode
                screenCaptureData = data
                startScreenRecord()
            } else {
                Toast.makeText(this, "螢幕錄製權限被拒絕", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                requestScreenCapture()
            } else {
                Toast.makeText(this, "需要儲存權限才能錄製", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun startScreenRecord() {
        val intent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_START
            putExtra(ScreenRecordService.EXTRA_RESULT_CODE, screenCaptureResultCode)
            putExtra(ScreenRecordService.EXTRA_DATA, screenCaptureData)
        }
        
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        
        Toast.makeText(this, "開始錄製螢幕", Toast.LENGTH_SHORT).show()
    }

    private fun stopScreenRecord() {
        val intent = Intent(this, ScreenRecordService::class.java).apply {
            action = ScreenRecordService.ACTION_STOP
        }
        startService(intent)
        Toast.makeText(this, "停止錄製", Toast.LENGTH_SHORT).show()
    }
}