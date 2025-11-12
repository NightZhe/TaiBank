package com.example.remoteclient

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.text.TextUtils
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class MainActivity : AppCompatActivity() {
    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE_SCREEN_CAPTURE = 1001
        private const val REQUEST_CODE_PERMISSIONS = 1002
        private const val REQUEST_CODE_ACCESSIBILITY = 1003
    }

    private var screenCaptureResultCode = 0
    private var screenCaptureData: Intent? = null
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)

        // ========== 設定 K PLUS 按鈕的絕對座標 ==========
        val btnLaunchKPlus = findViewById<Button>(R.id.btnLaunchKPlus)
        btnLaunchKPlus.post {
            // 計算需要偏移多少才能到達目標位置
            val location = IntArray(2)
            btnLaunchKPlus.getLocationOnScreen(location)
            
            val currentX = location[0].toFloat()
            val currentY = location[1].toFloat()
            
            val targetX = 500f  // 目標 X 座標 (像素)
            val targetY = 1000f // 目標 Y 座標 (像素)
            
            Log.d(TAG, "=== K PLUS 按鈕位置資訊 ===")
            Log.d(TAG, "原始位置: X=$currentX, Y=$currentY")
            Log.d(TAG, "目標位置: X=$targetX, Y=$targetY")
            Log.d(TAG, "偏移量: X=${targetX - currentX}, Y=${targetY - currentY}")
            
            btnLaunchKPlus.translationX = targetX - currentX
            btnLaunchKPlus.translationY = targetY - currentY
            
            // 再次確認移動後的位置
            btnLaunchKPlus.postDelayed({
                val newLocation = IntArray(2)
                btnLaunchKPlus.getLocationOnScreen(newLocation)
                Log.d(TAG, "移動後位置: X=${newLocation[0]}, Y=${newLocation[1]}")
                Log.d(TAG, "按鈕寬度: ${btnLaunchKPlus.width}, 高度: ${btnLaunchKPlus.height}")
                Log.d(TAG, "點擊範圍: X=${newLocation[0]} ~ ${newLocation[0] + btnLaunchKPlus.width}")
                Log.d(TAG, "點擊範圍: Y=${newLocation[1]} ~ ${newLocation[1] + btnLaunchKPlus.height}")
                Log.d(TAG, "建議點擊中心點: X=${newLocation[0] + btnLaunchKPlus.width/2}, Y=${newLocation[1] + btnLaunchKPlus.height/2}")
                Log.d(TAG, "========================")
            }, 500)
        }

        // WebSocket Service
        findViewById<Button>(R.id.btnStartService).setOnClickListener {
            Log.d(TAG, "Start WebSocket Service clicked")
            startService(Intent(this, WebSocketService::class.java))
            tvStatus.text = "WebSocket Service Started"
            Toast.makeText(this, "Service Started", Toast.LENGTH_SHORT).show()
        }

        findViewById<Button>(R.id.btnStopService).setOnClickListener {
            Log.d(TAG, "Stop WebSocket Service clicked")
            stopService(Intent(this, WebSocketService::class.java))
            tvStatus.text = "WebSocket Service Stopped"
            Toast.makeText(this, "Service Stopped", Toast.LENGTH_SHORT).show()
        }

        // K PLUS 啟動
        btnLaunchKPlus.setOnClickListener {
            Log.d(TAG, "K PLUS button clicked")
            val intent = packageManager.getLaunchIntentForPackage("com.kasikorn.retail.mbanking.wap")
            if (intent != null) {
                startActivity(intent)
                Toast.makeText(this, "啟動 K PLUS", Toast.LENGTH_SHORT).show()
                Log.i(TAG, "K PLUS launched successfully")
            } else {
                Toast.makeText(this, "找不到 K PLUS 應用程式", Toast.LENGTH_SHORT).show()
                Log.w(TAG, "K PLUS app not found")
            }
        }

        // 自動點擊
        findViewById<Button>(R.id.btnEnableAutoClick).setOnClickListener {
            Log.d(TAG, "Enable AutoClick clicked")
            checkAccessibilityPermission()
        }

        findViewById<Button>(R.id.btnTestClick).setOnClickListener {
            Log.d(TAG, "Test Click button clicked")
            showClickInputDialog()
        }
        
        findViewById<Button>(R.id.btnTestSequence).setOnClickListener {
            Log.d(TAG, "Test Sequence clicked")
            showSequenceDialog()
        }
    }

    private fun checkAccessibilityPermission() {
        val isEnabled = isAccessibilityServiceEnabled()
        Log.d(TAG, "Accessibility service enabled: $isEnabled")
        
        if (isEnabled) {
            val instance = AutoClickService.getInstance()
            Log.d(TAG, "AutoClickService instance: $instance")
            Toast.makeText(this, "自動點擊功能已啟用", Toast.LENGTH_SHORT).show()
        } else {
            AlertDialog.Builder(this)
                .setTitle("啟用無障礙服務")
                .setMessage("需要啟用無障礙服務才能使用自動點擊功能")
                .setPositiveButton("前往設定") { _, _ ->
                    Log.d(TAG, "Opening accessibility settings")
                    val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    startActivityForResult(intent, REQUEST_CODE_ACCESSIBILITY)
                }
                .setNegativeButton("取消", null)
                .show()
        }
    }

    private fun isAccessibilityServiceEnabled(): Boolean {
        val service = "${packageName}/${AutoClickService::class.java.canonicalName}"
        Log.d(TAG, "Checking service: $service")
        
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        )
        
        Log.d(TAG, "Enabled services: $enabledServices")
        return enabledServices?.contains(service) == true
    }

    private fun showClickInputDialog() {
        Log.d(TAG, "showClickInputDialog()")
        
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Accessibility service not enabled")
            Toast.makeText(this, "請先啟用無障礙服務", Toast.LENGTH_SHORT).show()
            return
        }

        val dialogView = layoutInflater.inflate(R.layout.dialog_click_input, null)
        val etX = dialogView.findViewById<EditText>(R.id.etX)
        val etY = dialogView.findViewById<EditText>(R.id.etY)
        
        // 預先填入 K PLUS 按鈕的中心點座標
        val btnLaunchKPlus = findViewById<Button>(R.id.btnLaunchKPlus)
        val location = IntArray(2)
        btnLaunchKPlus.getLocationOnScreen(location)
        val centerX = location[0] + btnLaunchKPlus.width / 2
        val centerY = location[1] + btnLaunchKPlus.height / 2
        
        etX.hint = "建議: $centerX (K PLUS 按鈕中心)"
        etY.hint = "建議: $centerY (K PLUS 按鈕中心)"

        AlertDialog.Builder(this)
            .setTitle("輸入點擊座標")
            .setView(dialogView)
            .setPositiveButton("點擊") { _, _ ->
                val x = etX.text.toString().toFloatOrNull()
                val y = etY.text.toString().toFloatOrNull()
                
                Log.d(TAG, "User input: x=$x, y=$y")
                
                if (x != null && y != null) {
                    Log.i(TAG, "=== 執行點擊 ===")
                    Log.i(TAG, "點擊座標: ($x, $y)")
                    Log.i(TAG, "K PLUS 按鈕位置: (${location[0]}, ${location[1]})")
                    Log.i(TAG, "K PLUS 按鈕大小: ${btnLaunchKPlus.width} x ${btnLaunchKPlus.height}")
                    
                    // 檢查點擊座標是否在按鈕範圍內
                    val inRangeX = x >= location[0] && x <= location[0] + btnLaunchKPlus.width
                    val inRangeY = y >= location[1] && y <= location[1] + btnLaunchKPlus.height
                    
                    if (inRangeX && inRangeY) {
                        Log.i(TAG, "✅ 座標在按鈕範圍內")
                    } else {
                        Log.w(TAG, "⚠️ 座標不在按鈕範圍內! inRangeX=$inRangeX, inRangeY=$inRangeY")
                    }
                    
                    AutoClickService.performClick(x, y)
                    Toast.makeText(this, "執行點擊 ($x, $y)", Toast.LENGTH_SHORT).show()
                } else {
                    Log.w(TAG, "Invalid coordinates entered")
                    Toast.makeText(this, "請輸入有效座標", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("取消") { _, _ ->
                Log.d(TAG, "Click dialog cancelled")
            }
            .show()
    }

    private fun showSequenceDialog() {
        Log.d(TAG, "showSequenceDialog()")
        
        if (!isAccessibilityServiceEnabled()) {
            Log.w(TAG, "Accessibility service not enabled")
            Toast.makeText(this, "請先啟用無障礙服務", Toast.LENGTH_SHORT).show()
            return
        }

        // 取得 K PLUS 按鈕的實際位置
        val btnLaunchKPlus = findViewById<Button>(R.id.btnLaunchKPlus)
        val location = IntArray(2)
        btnLaunchKPlus.getLocationOnScreen(location)
        val centerX = (location[0] + btnLaunchKPlus.width / 2).toFloat()
        val centerY = (location[1] + btnLaunchKPlus.height / 2).toFloat()

        // 示範：點擊序列
        val clicks = listOf(
            AutoClickService.ClickAction(centerX, centerY, 0),      // 點擊 K PLUS
            AutoClickService.ClickAction(centerX, centerY, 1000),   // 等待1秒再點一次
            AutoClickService.ClickAction(centerX, centerY, 1000)    // 再等待1秒再點一次
        )
        
        Log.d(TAG, "Sequence clicks: $clicks")

        AlertDialog.Builder(this)
            .setTitle("執行點擊序列")
            .setMessage("將依序點擊 K PLUS 按鈕:\n($centerX, $centerY) x 3 次\n每次間隔 1 秒")
            .setPositiveButton("執行") { _, _ ->
                Log.i(TAG, "Calling AutoClickService.performClickSequence() with ${clicks.size} clicks")
                AutoClickService.performClickSequence(clicks)
                Toast.makeText(this, "開始執行點擊序列", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消") { _, _ ->
                Log.d(TAG, "Sequence dialog cancelled")
            }
            .show()
    }
}