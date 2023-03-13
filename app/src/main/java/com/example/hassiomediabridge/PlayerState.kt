package com.example.hassiomediabridge

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.util.Log
import com.neovisionaries.ws.client.*
import org.json.JSONObject
import java.net.URL
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLSocketFactory


class PlayerStateReport(
    val mediaTitle: String,
    val mediaArtist: String,
    val inactiveSince: Long,
    val playerStatus: String,
    val mediaDuration: Double,
    val mediaPosition: Double,
    val thumbnailUrl: String,
    val deviceName : String
    ) {}


interface PlayerStateUpdateCallback {
    fun callback(report: PlayerStateReport)
}

class PlayerStateClient(
    private val context: Context,
    private var playerStateUpdateCallback: PlayerStateUpdateCallback,
) {
    private var endpoint = ""
    private var token = ""
    private var entity = ""

    private var messageId = 1
    private var ws : WebSocket

    private var connectivityManager : ConnectivityManager

    private val websocketHandler : WebSocketAdapter = object : WebSocketAdapter() {
        override fun  onConnected(ws: WebSocket, headers: Map<String, List<String>>) {
            Log.i("websocket", "Connected")
        }

        override fun onTextMessage(ws: WebSocket, text: String) {
            val messageObject = JSONObject(text)

            when (messageObject.getString("type")) {
                "auth_required" -> {
                    //send our token
                    val responseObject = JSONObject()
                    responseObject.put("type","auth")
                    responseObject.put("access_token", token)
                    ws.sendText(responseObject.toString())
                }
                "auth_ok" -> {

                    messageId += 1
                    Log.i("websocket","authentication ok")
                    //subscribe to media player
                    val responseObject = JSONObject()
                    responseObject.put("id",messageId)
                    responseObject.put("type","subscribe_trigger")
                    val triggerObject = JSONObject()
                    triggerObject.put("platform","state")
                    triggerObject.put("entity_id",entity)

                    responseObject.put("trigger",triggerObject)
                    ws.sendText(responseObject.toString())
                }
                "auth_invalid" -> {
                    Log.w("websocket","authentication failed")
                }
                "event" -> {
                    val eventObject = messageObject.getJSONObject("event")
                    val variableObject = eventObject.getJSONObject("variables")
                    val triggerObject = variableObject.getJSONObject("trigger")

                    //var fromStateObject = triggerObject.getJSONObject("from_state")
                    val toStateObject = triggerObject.getJSONObject("to_state")

                    updateState(toStateObject)
                }
            }
        }

        override fun onError(websocket: WebSocket?, cause: WebSocketException?) {
            Log.e("websocket",cause.toString())
            super.onError(websocket, cause)
        }

        override fun onConnectError(websocket: WebSocket?, exception: WebSocketException?) {
            super.onConnectError(websocket, exception)
            Log.w("websocket", "Unable to connect!")
        }

        override fun onDisconnected(
            websocket: WebSocket?,
            serverCloseFrame: WebSocketFrame?,
            clientCloseFrame: WebSocketFrame?,
            closedByServer: Boolean
        ) {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
            Log.i("websocket","Disconnected")
        }
    }

    private val connectivityCallback = object : ConnectivityManager.NetworkCallback () {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i("network", "Network Available")
            recreateConnection()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.i("network", "Connection lost")
            val emptyState = PlayerStateReport("","",10000,"unavailable",0.0,0.0,"","")
            playerStateUpdateCallback.callback(emptyState)
        }
    }

    init {
        reloadSettings()

        val wsFactory = WebSocketFactory()
        val sslSocketFactory = SSLSocketFactory.getDefault()

        wsFactory.verifyHostname = true
        wsFactory.sslSocketFactory = sslSocketFactory as SSLSocketFactory?

        wsFactory.setServerName(URL(endpoint).host)

        ws = wsFactory.createSocket("$endpoint/api/websocket")
        // Register a listener to receive WebSocket events.
        ws.addListener(websocketHandler)
        //register network change listener
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        connectivityManager.registerDefaultNetworkCallback(connectivityCallback)
    }


    private fun closeConnection(){
        ws.disconnect()
    }

    fun recreateConnection(){
        ws.disconnect()
        ws = ws.recreate()
        ws.connectAsynchronously()
    }

    fun reloadSettings(){
        val sharedPreferences = context.getSharedPreferences(context.getString(R.string.preference_file_key),Context.MODE_PRIVATE)

        endpoint = sharedPreferences.getString("endpoint","") ?: ""
        token = sharedPreferences.getString("token","") ?: ""
        entity = sharedPreferences.getString("entity","") ?: ""
    }

    fun updateState(stateObject : JSONObject){
        val state = stateObject.getString("state")

        val attributes = stateObject.getJSONObject("attributes")
        //val volumeLevel = attributes.getDouble("volume_level")
        //val volumeMuted = attributes.getBoolean("is_volume_muted")
        //val mediaType = attributes.getString("media_content_type")
        val mediaArtist = try {attributes.getString("media_artist")} catch (e : Exception){"none"}
        val mediaTitle = try {attributes.getString("media_title")} catch (e : Exception){""}
        val mediaDuration =  try {attributes.getDouble("media_duration")} catch (e : Exception){0.0}
        val mediaPosition =  try {attributes.getDouble("media_position")} catch (e : Exception){0.0}
        val mediaThumbnailUrl = try {attributes.getString("entity_picture")} catch (e : Exception){""}
        val deviceName = try {attributes.getString("friendly_name")} catch (e : Exception){""}

        val minutesPast = try {
            val lastChanged = stateObject.getString("last_changed")
            val lastChange = Instant.parse(lastChanged.split("+")[0] + "Z")
            val now = Instant.now()
            val difference = Duration.between(lastChange, now)
            difference.toMinutes()
        }  catch(e : Exception) {
            0
        }

        playerStateUpdateCallback.callback(PlayerStateReport(
            mediaTitle,
            mediaArtist,
            minutesPast,
            state,
            mediaDuration,
            mediaPosition,
            mediaThumbnailUrl,
            deviceName
            )
        )
    }

    fun seekTo(pos : Double){
        val event = constructEvent("media_seek")
        val data = JSONObject().put("seek_position",pos)
        event.put("service_data",data)
        ws.sendText(event.toString())
    }

    fun play(){
        callBasicService("media_play")
    }

    fun pause() {
        callBasicService("media_pause")
    }

    fun skip() {
        callBasicService("media_next_track")
    }

    fun previous() {
        callBasicService("media_previous_track")
    }

    private fun callBasicService(service : String) {
        val event = constructEvent(service)
        ws.sendText(event.toString())
    }

    private fun constructEvent(service : String): JSONObject {

        val responseObject = JSONObject()
        messageId += 1
        responseObject.put("id",messageId)
        responseObject.put("type","call_service")
        responseObject.put("domain","media_player")
        responseObject.put("service",service)

        val targetObject = JSONObject()
        targetObject.put("entity_id", entity)

        responseObject.put("target",targetObject)

        return responseObject
    }
}