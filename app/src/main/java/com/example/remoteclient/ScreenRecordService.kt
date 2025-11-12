package com.example.remoteclient

import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class ScreenRecordService : Service() {
    companion object {
        private const val TAG = "ScreenRecordService"
        private const val CHANNEL_ID = "screen_record_channel"
        const val ACTION_START = "action_start"
        const val ACTION_STOP = "action_stop"
        const val EXTRA_RESULT_CODE = "result_code"
        const val EXTRA_DATA = "data"
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaRecorder: MediaRecorder? = null
    private var videoFile: File? = null
    private var videoUri: Uri? = null
    private var parcelFileDescriptor: ParcelFileDescriptor? = null

    private var screenWidth = 720
    private var screenHeight = 1280
    private var screenDensity = 320

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        getScreenMetrics()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                val resultCode = intent.getIntExtra(EXTRA_RESULT_CODE, Activity.RESULT_CANCELED)
                val data = intent.getParcelableExtra<Intent>(EXTRA_DATA)
                if (resultCode == Activity.RESULT_OK && data != null) {
                    startRecording(resultCode, data)
                }
            }
            ACTION_STOP -> {
                stopRecording()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Screen Recording",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun getScreenMetrics() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val bounds = windowManager.currentWindowMetrics.bounds
            screenWidth = bounds.width()
            screenHeight = bounds.height()
            metrics.densityDpi.let { screenDensity = it }
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.getMetrics(metrics)
            screenWidth = metrics.widthPixels
            screenHeight = metrics.heightPixels
            screenDensity = metrics.densityDpi
        }
        
        // 確保寬高是偶數（H264 編碼要求）
        if (screenWidth % 2 != 0) screenWidth -= 1
        if (screenHeight % 2 != 0) screenHeight -= 1
        
        Log.i(TAG, "Screen: ${screenWidth}x${screenHeight}, density: $screenDensity")
    }

    private fun startRecording(resultCode: Int, data: Intent) {
        try {
            val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "screen_record_$timestamp.mp4"

            // 初始化 MediaRecorder (必須在設定輸出檔案之前)
            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            // 根據 Android 版本選擇儲存方式並設定輸出
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                setupMediaRecorderForAndroid10Plus(fileName)
            } else {
                setupMediaRecorderForOlderAndroid(fileName)
            }

            // 啟動 MediaProjection
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)

            // 創建 VirtualDisplay
            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ScreenRecord",
                screenWidth,
                screenHeight,
                screenDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder!!.surface,
                null,
                null
            )

            mediaRecorder?.start()
            
            // 啟動前台服務
            val notification = createNotification("正在錄製螢幕...")
            startForeground(2, notification)
            
            Log.i(TAG, "Screen recording started: $fileName")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to start recording: ${e.message}")
            e.printStackTrace()
            stopRecording()
        }
    }

    private fun setupMediaRecorderForAndroid10Plus(fileName: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, fileName)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "DCIM/ScreenRecorder")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }

            videoUri = contentResolver.insert(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                contentValues
            )
            
            if (videoUri == null) {
                throw Exception("Failed to create MediaStore entry")
            }
            
            parcelFileDescriptor = contentResolver.openFileDescriptor(videoUri!!, "w")
            
            mediaRecorder?.apply {
                setVideoSource(MediaRecorder.VideoSource.SURFACE)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setOutputFile(parcelFileDescriptor!!.fileDescriptor)
                setVideoSize(screenWidth, screenHeight)
                setVideoEncoder(MediaRecorder.VideoEncoder.H264)
                setVideoEncodingBitRate(5 * 1024 * 1024) // 5 Mbps
                setVideoFrameRate(30)
                prepare()
            }
            
            Log.i(TAG, "Created video URI: $videoUri")
        }
    }

    private fun setupMediaRecorderForOlderAndroid(fileName: String) {
        val dcimDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
        val screenRecorderDir = File(dcimDir, "ScreenRecorder")
        
        if (!screenRecorderDir.exists()) {
            screenRecorderDir.mkdirs()
        }
        
        videoFile = File(screenRecorderDir, fileName)
        
        mediaRecorder?.apply {
            setVideoSource(MediaRecorder.VideoSource.SURFACE)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setOutputFile(videoFile!!.absolutePath)
            setVideoSize(screenWidth, screenHeight)
            setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            setVideoEncodingBitRate(5 * 1024 * 1024) // 5 Mbps
            setVideoFrameRate(30)
            prepare()
        }
        
        Log.i(TAG, "Created video file: ${videoFile?.absolutePath}")
    }

    private fun stopRecording() {
        try {
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: Exception) {
                    Log.e(TAG, "Error stopping recorder: ${e.message}")
                }
                reset()
                release()
            }
            mediaRecorder = null

            virtualDisplay?.release()
            virtualDisplay = null

            mediaProjection?.stop()
            mediaProjection = null

            // 關閉 ParcelFileDescriptor
            parcelFileDescriptor?.close()
            parcelFileDescriptor = null

            // 更新 MediaStore (Android 10+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && videoUri != null) {
                val contentValues = ContentValues().apply {
                    put(MediaStore.Video.Media.IS_PENDING, 0)
                }
                contentResolver.update(videoUri!!, contentValues, null, null)
                Log.i(TAG, "Screen recording stopped and saved: $videoUri")
            } else if (videoFile != null) {
                // Android 9 及以下，手動掃描檔案到媒體庫
                val scanIntent = Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE)
                scanIntent.data = Uri.fromFile(videoFile)
                sendBroadcast(scanIntent)
                Log.i(TAG, "Screen recording stopped and saved: ${videoFile?.absolutePath}")
            }
            
            // 通知錄製完成
            showCompletionNotification()

        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop recording: ${e.message}")
            e.printStackTrace()
        }
    }

    private fun createNotification(message: String): Notification {
        val stopIntent = Intent(this, ScreenRecordService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this, 0, stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("螢幕錄製")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .addAction(android.R.drawable.ic_delete, "停止", stopPendingIntent)
            .build()
    }

    private fun showCompletionNotification() {
        val fileName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            videoUri?.lastPathSegment ?: "未知檔案"
        } else {
            videoFile?.name ?: "未知檔案"
        }
        
        val notification = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("錄製完成")
            .setContentText("影片已儲存至 DCIM/ScreenRecorder")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(3, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        stopRecording()
    }
}