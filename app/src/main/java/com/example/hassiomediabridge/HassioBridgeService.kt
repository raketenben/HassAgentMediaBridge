package com.example.hassiomediabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL


class HassioBridgeService : Service(), PlayerStateUpdateCallback  {

    private lateinit var session : MediaSessionCompat
    private lateinit var notificationClient: MediaNotificationClient

    private lateinit var playerStateClient : PlayerStateClient

    override fun onCreate() {
        super.onCreate()

        YoutubeDL.getInstance().init(applicationContext)

        notificationClient = MediaNotificationClient(this)
        playerStateClient = PlayerStateClient(applicationContext,this)

        session = MediaSessionCompat(applicationContext,"HassioMediaBridge").apply {
            setCallback(mediaSessionCallback)
        }

        createNotificationChannel()
    }

    private val mediaSessionCallback: MediaSessionCompat.Callback =
        object : MediaSessionCompat.Callback() {
            override fun onPlay() {
                super.onPlay()
                playerStateClient.play()
            }

            override fun onPause() {
                super.onPause()
                playerStateClient.pause()
            }

            override fun onPlayFromMediaId(mediaId: String, extras: Bundle) {
                super.onPlayFromMediaId(mediaId, extras)
            }

            override fun onSkipToNext() {
                super.onSkipToNext()
                playerStateClient.skip()
            }

            override fun onSkipToPrevious() {
                super.onSkipToNext()
                playerStateClient.previous()
            }
        }


    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("service","Received Intent - Reloading Settings")
        playerStateClient.reloadSettings();
        notificationClient.reloadSettings()

        playerStateClient.recreateConnection()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? { return null }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_DEFAULT
        val channel = NotificationChannel("MEDIA", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun callback(report: PlayerStateReport) {
        notificationClient.setState(session,report,this)
    }
}