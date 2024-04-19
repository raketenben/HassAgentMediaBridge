package com.example.hassiomediabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.os.*
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.media.VolumeProviderCompat
import androidx.mediarouter.media.MediaRouteDescriptor
import androidx.mediarouter.media.MediaRouteProvider
import androidx.mediarouter.media.MediaRouteProviderDescriptor
import androidx.mediarouter.media.MediaRouter
import androidx.mediarouter.media.MediaRouter.getInstance
import com.yausername.youtubedl_android.YoutubeDL


class HassioBridgeService : Service(), PlayerStateUpdateCallback  {

    private lateinit var session : MediaSessionCompat
    private lateinit var notificationClient: MediaNotificationClient

    private lateinit var playerStateClient : PlayerStateClient
    private lateinit var volumeProvider : VolumeProviderCompat

    private lateinit var mediaRouter : MediaRouter

    private lateinit var wakeLock: PowerManager.WakeLock

    override fun onCreate() {
        super.onCreate()

        YoutubeDL.getInstance().init(this)

        notificationClient = MediaNotificationClient(this)
        playerStateClient = PlayerStateClient(this,this)

        volumeProvider = HassVolumeProvider()

        session = MediaSessionCompat(this,"HassioMediaBridge").apply {
            setCallback(mediaSessionCallback)
            setPlaybackToRemote(volumeProvider)
            isActive = true
        }

        mediaRouter = getInstance(this)
        mediaRouter.setMediaSessionCompat(session)

        val routeProvider = HassRouterProvider(this)
        mediaRouter.addProvider(routeProvider)
        val route = mediaRouter.providers.last().routes.last()

        mediaRouter.selectRoute(route)

        Log.i("routes", mediaRouter.selectedRoute.toString())

        createNotificationChannel()

        /*wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "HassMediaBridge::playerSync").apply {
                acquire()
            }
        }*/
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

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
                playerStateClient.seekTo((pos/1000L).toDouble())
            }
        }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        this.startForeground(1, notificationClient.firstNotification());

        Log.i("service","Received Intent - Reloading Settings")
        playerStateClient.reloadSettings()
        notificationClient.reloadSettings()

        playerStateClient.recreateConnection()

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? { return null }

    private fun createNotificationChannel() {
        val name = getString(R.string.channel_name)
        val descriptionText = getString(R.string.channel_description)
        val importance = NotificationManager.IMPORTANCE_MIN
        val channel = NotificationChannel("MEDIA", name, importance).apply {
            description = descriptionText
        }
        // Register the channel with the system
        val notificationManager: NotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    override fun callback(report: PlayerStateReport) {
        /*if(report.playerStatus == "unavailable"){
            if (wakeLock.isHeld) wakeLock.release()
        }else{
            if (!wakeLock.isHeld) wakeLock.acquire()
        }*/

        notificationClient.setState(session,report)
    }
}

class HassRouterProvider(var _context: Context) : MediaRouteProvider(_context){
    init {
        val descriptor = MediaRouteDescriptor.Builder("HASS_PLAYER_ROUTE","HassioBridgeService")
            .setDescription("Output Device for HassioMediaBridge")
            .setPlaybackStream(AudioManager.STREAM_MUSIC)
            .setPlaybackType(MediaRouter.RouteInfo.PLAYBACK_TYPE_REMOTE)
            .setVolumeHandling(MediaRouter.RouteInfo.PLAYBACK_VOLUME_VARIABLE)
            .setVolume(100)
            .setVolumeMax(100)
            .build()

        val descriptionCompat = MediaRouteProviderDescriptor.Builder()
            .addRoute(descriptor)
            .build()

        setDescriptor(descriptionCompat)
    }

}

class HassVolumeProvider : VolumeProviderCompat(VOLUME_CONTROL_RELATIVE,100,100) {
    override fun onAdjustVolume(direction: Int) {
        super.onAdjustVolume(direction)
        Log.i("adjust event",direction.toString())
    }

    override fun onSetVolumeTo(volume: Int) {
        super.onSetVolumeTo(volume)
        Log.i("volume event",volume.toString())
    }
}