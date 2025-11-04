package com.example.remoteclient

import android.annotation.SuppressLint
import android.content.Context
import android.net.wifi.WifiManager
import android.os.BatteryManager
import android.provider.Settings
import android.widget.Toast

object Utils {
    @SuppressLint("HardwareIds")
    fun getAndroidId(ctx: Context): String {
        return Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ANDROID_ID) ?: ""
    }

    fun getLocalIpAddress(ctx: Context): String {
        return try {
            val wm = ctx.applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
            val ip = wm.connectionInfo.ipAddress
            String.format("%d.%d.%d.%d", ip and 0xff, ip shr 8 and 0xff, ip shr 16 and 0xff, ip shr 24 and 0xff)
        } catch (e: Exception) {
            ""
        }
    }

    fun getBatteryPercent(ctx: Context): Int {
        val bm = ctx.getSystemService(Context.BATTERY_SERVICE) as BatteryManager
        val perc = bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
        return perc
    }

    fun showToast(ctx: Context, text: String) {
        android.os.Handler(ctx.mainLooper).post {
            Toast.makeText(ctx, text, Toast.LENGTH_SHORT).show()
        }
    }
}
