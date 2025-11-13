package com.example.remoteclient

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.os.Handler
import android.os.Looper

class AutoClickService : AccessibilityService() {
    companion object {
        private const val TAG = "AutoClickService"
        private var instance: AutoClickService? = null
        
        fun getInstance(): AutoClickService? {
            Log.d(TAG, "getInstance() called, instance = $instance")
            return instance
        }
        
        fun isServiceRunning(): Boolean {
            return instance != null
        }
        
        // 執行點擊序列
        fun performClickSequence(clicks: List<ClickAction>) {
            Log.i(TAG, "performClickSequence() called with ${clicks.size} clicks")
            if (instance == null) {
                Log.e(TAG, "❌ Service instance is null! Cannot perform click sequence.")
                return
            }
            instance?.executeClickSequence(clicks)
        }
        
        // 執行單次點擊
        fun performClick(x: Float, y: Float, delay: Long = 0) {
            Log.i(TAG, "performClick() called: x=$x, y=$y, delay=$delay")
            if (instance == null) {
                Log.e(TAG, "❌ Service instance is null! Cannot perform click.")
                return
            }
            instance?.executeClick(x, y, delay)
        }
    }
    
    data class ClickAction(
        val x: Float,
        val y: Float,
        val delay: Long = 0
    )
    
    private val handler = Handler(Looper.getMainLooper())
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "✅ AutoClickService connected successfully")
        Log.i(TAG, "Service info: ${serviceInfo?.id}")
        Log.i(TAG, "Can perform gestures: ${serviceInfo?.flags?.and(android.accessibilityservice.AccessibilityServiceInfo.FLAG_REQUEST_TOUCH_EXPLORATION_MODE)}")
    }
    
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // 不需要處理事件
    }
    
    override fun onInterrupt() {
        Log.w(TAG, "⚠️ AutoClickService interrupted")
    }
    
    override fun onDestroy() {
        super.onDestroy()
        instance = null
        Log.i(TAG, "❌ AutoClickService destroyed")
    }
    
    private fun executeClick(x: Float, y: Float, delay: Long) {
        Log.d(TAG, "executeClick() START: x=$x, y=$y, delay=$delay")
        
        // 在主執行緒中執行 (重要!)
        handler.postDelayed({
            try {
                Log.d(TAG, "Creating gesture path for ($x, $y)")
                
                // 創建點擊路徑
                val path = Path()
                path.moveTo(x, y)
                
                // 創建手勢描述 - 調整參數
                val gestureBuilder = GestureDescription.Builder()
                val strokeDescription = GestureDescription.StrokeDescription(
                    path,
                    0,        // 開始時間 (ms)
                    50,       // 持續時間 (ms) - 改短一點更像真實點擊
                    false     // 不繼續
                )
                gestureBuilder.addStroke(strokeDescription)
                
                val gesture = gestureBuilder.build()
                Log.d(TAG, "Gesture created, stroke count: ${gesture.strokeCount}")
                Log.d(TAG, "Gesture duration: ${gesture.getStroke(0).duration}ms")
                
                // 發送手勢
                val success = dispatchGesture(gesture, object : GestureResultCallback() {
                    override fun onCompleted(gestureDescription: GestureDescription?) {
                        Log.i(TAG, "✅ Click COMPLETED at ($x, $y)")
                    }
                    
                    override fun onCancelled(gestureDescription: GestureDescription?) {
                        Log.e(TAG, "❌ Click CANCELLED at ($x, $y)")
                        Log.e(TAG, "Possible reasons: permission issue, gesture conflict, or invalid coordinates")
                    }
                }, null)
                
                if (success) {
                    Log.i(TAG, "✅ Gesture dispatched successfully")
                } else {
                    Log.e(TAG, "❌ Failed to dispatch gesture! Check accessibility service permissions.")
                }
                
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in executeClick: ${e.message}", e)
                e.printStackTrace()
            }
            
            Log.d(TAG, "executeClick() END")
        }, delay)
    }
    
    private fun executeClickSequence(clicks: List<ClickAction>) {
        Log.i(TAG, "executeClickSequence() START with ${clicks.size} clicks")
        
        var cumulativeDelay = 0L
        
        clicks.forEachIndexed { index, click ->
            cumulativeDelay += click.delay
            
            handler.postDelayed({
                Log.d(TAG, "Executing click ${index + 1}/${clicks.size}: (${click.x}, ${click.y})")
                executeClick(click.x, click.y, 0) // delay 已經在這裡處理了
                
                if (index == clicks.size - 1) {
                    Log.i(TAG, "✅ Click sequence completed")
                }
            }, cumulativeDelay)
            
            cumulativeDelay += 300 // 每次點擊間隔 300ms
        }
    }
}