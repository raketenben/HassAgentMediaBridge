package com.example.hassiomediabridge

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors


class MediaNotificationClient(var context: Context) {

    private var endpoint = "";
    private var externalThumbnail = false;

    fun reloadSettings(){
        var sharedPreferences = context.getSharedPreferences(context.getString(R.string.preference_file_key),Context.MODE_PRIVATE)
        endpoint = sharedPreferences.getString("endpoint","") ?: ""
        externalThumbnail = sharedPreferences.getBoolean("externalThumbnail",false)
    }

    private var lastTitle = ""

    private var title = ""
    private var artist = ""
    private var thumbnail : Bitmap? = null
    private var playing = false
    private var inactiveSince = 0L
    private var duration = 0L
    private var position = 0L

    fun setState(session: MediaSessionCompat, playerState: PlayerStateReport,applicationContext: Context){
        title = playerState.title
        artist = playerState.artist
        playing = playerState.playing
        inactiveSince = playerState.inactive_since
        duration = (playerState.media_duration*1000).toLong()
        position = (playerState.media_position*1000).toLong()


        val thumbnailPath = playerState.thumbnail
        val localThumbnailUrl = "$endpoint$thumbnailPath"

        if(lastTitle != title) {
            lastTitle = title

            val executor = Executors.newSingleThreadExecutor()

            Log.i("thumbnail source",externalThumbnail.toString())
            executor.execute {
                //thumbnail source
                val thumbnailUrl = if(externalThumbnail) {
                    val request = YoutubeDLRequest("$artist - $title")
                    request.addOption("--default-search",  "ytsearch1")
                    val response = YoutubeDL.getInstance().getInfo(request)
                    response.thumbnail
                }else{
                    localThumbnailUrl
                }
                Log.i("thumbnail source",thumbnailUrl.toString())

                thumbnail = getBitmapFromURL(thumbnailUrl)
                update(session,applicationContext)
            }
        }

        update(session,applicationContext)
    }

    fun clear(applicationContext: Context){
        with(NotificationManagerCompat.from(applicationContext)){
            cancel(1)
        }
    }

    private fun update(session: MediaSessionCompat, applicationContext: Context) {

        val actions = (
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_FAST_FORWARD or
            PlaybackStateCompat.ACTION_REWIND
        )


        val metadataBuilder = MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        }
        if(thumbnail != null) metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,thumbnail)


        session.setMetadata(metadataBuilder.build())

        session.isActive = playing
        val playState = if (playing) {
            PlaybackStateCompat.STATE_PLAYING
        }else{
            PlaybackStateCompat.STATE_PAUSED
        }

        val state = PlaybackStateCompat.Builder()
            .setActions(actions)
            .apply {
                setState(playState,position,1.0f)
            }
        session.setPlaybackState(state.build())
        //
        if((inactiveSince > 15 && !playing) ||title == ""){
            with(NotificationManagerCompat.from(applicationContext)){
                cancel(1)
            }
        }else{
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle().setMediaSession(
                session.sessionToken)
            val notificationBuilder = NotificationCompat.Builder(applicationContext, "23")
                .setSmallIcon(R.drawable.ic_launcher_foreground)
                .setStyle(mediaStyle)
                .setChannelId("MEDIA")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)


            with(NotificationManagerCompat.from(applicationContext)) {
                // notificationId is a unique int for each notification that you must define
                notify(1, notificationBuilder.build())
            }
        }
    }

    private fun getBitmapFromURL(src: String?): Bitmap? {
        return try {
            val url = URL(src)
            val connection: HttpURLConnection = url.openConnection() as HttpURLConnection
            connection.doInput = true
            connection.connect()
            val input: InputStream = connection.inputStream
            BitmapFactory.decodeStream(input)
        } catch (e: IOException) {
            // Log exception
            null
        }
    }
}