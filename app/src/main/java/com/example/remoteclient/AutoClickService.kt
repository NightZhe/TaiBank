package com.example.remoteclient

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.util.Log
import android.view.accessibility.AccessibilityEvent

class AutoClickService : AccessibilityService() {
    companion object {
        private const val TAG = "AutoClickService"
        private var instance: AutoClickService? = null
        
        fun getInstance(): AutoClickService? {
            Log.d(TAG, "getInstance() called, instance = $instance")
            return instance
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
        val delay: Long = 0 // 點擊前等待時間（毫秒）
    )
    
    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Log.i(TAG, "✅ AutoClickService connected successfully")
        Log.i(TAG, "Service info: ${serviceInfo?.id}")
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
        
        try {
            if (delay > 0) {
                Log.d(TAG, "Waiting for ${delay}ms before click...")
                Thread.sleep(delay)
            }
            
            Log.d(TAG, "Creating gesture path for ($x, $y)")
            val path = Path()
            path.moveTo(x, y)
            
            val gestureBuilder = GestureDescription.Builder()
            val strokeDescription = GestureDescription.StrokeDescription(path, 0, 100)
            gestureBuilder.addStroke(strokeDescription)
            
            val gesture = gestureBuilder.build()
            Log.d(TAG, "Gesture created, dispatching...")
            
            val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    Log.i(TAG, "✅ Click completed at ($x, $y)")
                }
                
                override fun onCancelled(gestureDescription: GestureDescription?) {
                    Log.e(TAG, "❌ Click cancelled at ($x, $y)")
                }
            }, null)
            
            if (dispatched) {
                Log.d(TAG, "Gesture dispatched successfully")
            } else {
                Log.e(TAG, "Failed to dispatch gesture!")
            }
            
        } catch (e: Exception) {
            Log.e(TAG, "Exception in executeClick: ${e.message}", e)
            e.printStackTrace()
        }
        
        Log.d(TAG, "executeClick() END")
    }
    
    private fun executeClickSequence(clicks: List<ClickAction>) {
        Log.i(TAG, "executeClickSequence() START with ${clicks.size} clicks")
        
        Thread {
            try {
                clicks.forEachIndexed { index, click ->
                    Log.d(TAG, "Executing click ${index + 1}/${clicks.size}: (${click.x}, ${click.y})")
                    executeClick(click.x, click.y, click.delay)
                    Thread.sleep(200) // 每次點擊間隔 200ms
                }
                Log.i(TAG, "✅ Click sequence completed")
            } catch (e: Exception) {
                Log.e(TAG, "❌ Exception in click sequence: ${e.message}", e)
                e.printStackTrace()
            }
        }.start()
    }
}