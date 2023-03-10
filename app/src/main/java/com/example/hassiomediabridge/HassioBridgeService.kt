package com.example.hassiomediabridge

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import android.support.v4.media.session.MediaSessionCompat
import android.util.Log
import androidx.media.VolumeProviderCompat
import com.yausername.youtubedl_android.YoutubeDL
import java.util.*


class HassioBridgeService : Service(), PlayerStateUpdateCallback  {

    private lateinit var session : MediaSessionCompat;
    private lateinit var playerStateClient : PlayerStateClient;
    private lateinit var sharedPreferences : SharedPreferences;
    private var notificationClient: MediaNotificationClient? = null;
    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        init();
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

            override fun onSeekTo(pos: Long) {
                super.onSeekTo(pos)
            }

            override fun onSkipToNext() {
                super.onSkipToNext();
                playerStateClient.skip();
            }

            override fun onSkipToPrevious() {
                super.onSkipToNext();
                playerStateClient.previous()
            }
        }

    fun init(){
        notificationClient?.clear(applicationContext);

        YoutubeDL.getInstance().init(applicationContext);

        sharedPreferences = applicationContext.getSharedPreferences(getString(R.string.preference_file_key),Context.MODE_MULTI_PROCESS) ?: return;

        session = MediaSessionCompat(this,"HassioMediaBridge").apply {
            setCallback(mediaSessionCallback)
        };
        playerStateClient = PlayerStateClient(sharedPreferences,this);

        val volumeProvider = MyVolumeProvider(playerStateClient);
        session.setPlaybackToRemote(volumeProvider);

        notificationClient = MediaNotificationClient(sharedPreferences);

        createNotificationChannel();
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.i("service","Start command")

        init();

        return START_STICKY
    }

    fun onStop(){

    }

    private fun createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
    }

    override fun callback(report: PlayerStateReport) {
        notificationClient?.setState(session,report,applicationContext)
    }
}

//TODO: implement volume controls
class MyVolumeProvider(val playerStateClient: PlayerStateClient) : VolumeProviderCompat(VolumeProviderCompat.VOLUME_CONTROL_ABSOLUTE,100,100) {
    init {}

    /*override fun onAdjustVolume(direction: Double) {
        Log.i("volume", direction.toString());
        val newVolume = Math.max(Math.min(100, playerStateClient.playerState?.volume ?: 0 + direction*5),0)

        playerStateClient.setVolume(newVolume)
            /*
            -1 -- volume down
            1 -- volume up
            0 -- volume button released
             */
    }*/


}