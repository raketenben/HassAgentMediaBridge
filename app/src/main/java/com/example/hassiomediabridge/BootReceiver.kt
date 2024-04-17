package com.example.hassiomediabridge

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.example.hassiomediabridge.HassioBridgeService

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (Intent.ACTION_BOOT_COMPLETED == intent.action) {
            val serviceIntent = Intent(context.applicationContext, HassioBridgeService::class.java)
            context.applicationContext.startForegroundService(serviceIntent)
        }
    }
}