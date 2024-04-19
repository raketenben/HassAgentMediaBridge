package com.example.hassiomediabridge

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.support.v4.media.MediaMetadataCompat
import android.support.v4.media.session.MediaSessionCompat
import android.support.v4.media.session.PlaybackStateCompat
import android.util.Log
import androidx.core.app.BundleCompat
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.IOException
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.sql.Timestamp
import java.util.concurrent.Executors


class MediaNotificationClient(var context: Context) {

    private var endpoint = ""
    private var externalThumbnail = false

    fun reloadSettings(){
        val sharedPreferences = context.getSharedPreferences(context.getString(R.string.preference_file_key),Context.MODE_PRIVATE)
        endpoint = sharedPreferences.getString("endpoint","") ?: ""
        externalThumbnail = sharedPreferences.getBoolean("externalThumbnail",false)
    }

    init {
        reloadSettings()
    }

    private var lastTitle = ""

    private var title = ""
    private var artist = ""
    private var thumbnail : Bitmap? = null
    private var playerStatus = "unavailable"
    private var inactiveSince = 0L
    private var duration = 0L
    private var position = 0L

    //no timepass fix
    private var lastPosition = 0L
    private var mediaStartTime = Timestamp(System.currentTimeMillis())

    fun setState(session: MediaSessionCompat, playerState: PlayerStateReport){

        title = playerState.mediaTitle
        artist = playerState.mediaArtist
        playerStatus = playerState.playerStatus
        inactiveSince = playerState.inactiveSince
        duration = (playerState.mediaDuration*1000).toLong()
        val timePosition = (playerState.mediaPosition*1000).toLong()

        val thumbnailPath = playerState.thumbnailUrl
        val localThumbnailUrl = "$endpoint$thumbnailPath"

        if(lastTitle != title) {
            lastTitle = title

            mediaStartTime = Timestamp(System.currentTimeMillis())

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
                update(session)
            }
        }

        //no timepass fix
        position = if(lastPosition != timePosition){
            lastPosition = timePosition
            mediaStartTime = Timestamp(System.currentTimeMillis())
            timePosition
        }else{
            if(playerStatus == "playing") {
                Timestamp(System.currentTimeMillis()).time - mediaStartTime.time + lastPosition
            }else{
                position
            }
        }

        update(session)

    }

    private fun clear(){
        with(NotificationManagerCompat.from(context)){
            cancel(1)
        }
    }

    private fun update(session: MediaSessionCompat) {

        val actions = (
            PlaybackStateCompat.ACTION_PLAY_PAUSE or
            PlaybackStateCompat.ACTION_PAUSE or
            PlaybackStateCompat.ACTION_PLAY or
            PlaybackStateCompat.ACTION_SKIP_TO_NEXT or
            PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS or
            PlaybackStateCompat.ACTION_SEEK_TO
        )


        val metadataBuilder = MediaMetadataCompat.Builder().apply {
            putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, title)
            putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
            putString(MediaMetadataCompat.METADATA_KEY_ARTIST, artist)
            //putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_SUBTITLE, artist)
            putLong(MediaMetadataCompat.METADATA_KEY_DURATION, duration)

        }
        if(thumbnail != null) metadataBuilder.putBitmap(MediaMetadataCompat.METADATA_KEY_ALBUM_ART,thumbnail)


        session.setMetadata(metadataBuilder.build())

        session.isActive = playerStatus != "unavailable"
        val playState = if (playerStatus == "playing") {
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
        if((inactiveSince > 15 && playerStatus == "paused") || playerStatus == "unavailable"){
            clear()
        }else{
            val mediaStyle = androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(session.sessionToken)

            val notificationBuilder = NotificationCompat.Builder(context, "23")
                .setSmallIcon(R.mipmap.ic_launcher_monochrome)
                .setStyle(mediaStyle)
                .setChannelId("MEDIA")
                .setPriority(NotificationCompat.PRIORITY_MIN)


            with(NotificationManagerCompat.from(context)) {
                // notificationId is a unique int for each notification that you must define
                notify(1, notificationBuilder.build())
            }
        }
    }

    fun firstNotification(): Notification {

        val notificationBuilder = NotificationCompat.Builder(context, "23")
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setChannelId("MEDIA")
            .setPriority(NotificationCompat.PRIORITY_MIN)

        var not = notificationBuilder.build()

        with(NotificationManagerCompat.from(context)) {
            // notificationId is a unique int for each notification that you must define
            notify(1, not)
        }

        return not
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