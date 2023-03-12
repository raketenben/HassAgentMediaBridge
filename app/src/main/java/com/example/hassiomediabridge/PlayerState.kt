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
    val title: String,
    val artist: String,
    val inactive_since: Long,
    val playing: Boolean,
    val media_duration: Double,
    val media_position: Double,
    val thumbnail: String,
    ) {
    init {}
}


interface PlayerStateUpdateCallback {
    fun callback(report: PlayerStateReport)
}

class PlayerStateClient(
    private val context: Context,
    private var playerStateUpdateCallback: PlayerStateUpdateCallback,
) {
    private var endpoint = "";
    private var token = "";
    private var entity = "";

    private var playerState : PlayerStateReport = PlayerStateReport(
        "",
        "",
        10000,
        false,
        0.0,
        0.0, ""
    )

    private var messageId = 1;
    private var ws : WebSocket;

    private var connectivityManager : ConnectivityManager;

    private var reconnectingState = false;

    private val websocketHandler : WebSocketAdapter = object : WebSocketAdapter() {
        override fun  onConnected(ws: WebSocket, headers: Map<String, List<String>>) {
            Log.i("websocket", "Connected")
        }

        override fun onTextMessage(ws: WebSocket, text: String) {
            val messageObject = JSONObject(text);
            var type = messageObject.getString("type");

            when (type) {
                "auth_required" -> {
                    //send our token
                    var responseObject = JSONObject()
                    responseObject.put("type","auth")
                    responseObject.put("access_token", token)
                    ws.sendText(responseObject.toString())
                }
                "auth_ok" -> {

                    messageId += 1;
                    Log.i("websocket","authentication ok")
                    //subscribe to media player
                    var responseObject = JSONObject()
                    responseObject.put("id",messageId)
                    responseObject.put("type","subscribe_trigger")
                    var triggerObject = JSONObject()
                    triggerObject.put("platform","state")
                    triggerObject.put("entity_id",entity)

                    responseObject.put("trigger",triggerObject)
                    ws.sendText(responseObject.toString())
                }
                "auth_invalid" -> {
                    Log.w("websocket","authentication failed")
                }
                "event" -> {
                    var eventObject = messageObject.getJSONObject("event")
                    var variableObject = eventObject.getJSONObject("variables")
                    var triggerObject = variableObject.getJSONObject("trigger")

                    //var fromStateObject = triggerObject.getJSONObject("from_state")
                    var toStateObject = triggerObject.getJSONObject("to_state")

                    updateState(toStateObject);
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
    };

    private val conectivityCallback = object : ConnectivityManager.NetworkCallback () {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            Log.i("network", "Network Available")
            recreateConnection();
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            Log.i("network", "Connection lost")
            var emptyState = PlayerStateReport("","",10000,false,0.0,0.0,"")
            playerStateUpdateCallback.callback(emptyState);
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
        connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager;
        connectivityManager.registerDefaultNetworkCallback(conectivityCallback)
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
        var sharedPreferences = context.getSharedPreferences(context.getString(R.string.preference_file_key),Context.MODE_PRIVATE)

        endpoint = sharedPreferences.getString("endpoint","") ?: ""
        token = sharedPreferences.getString("token","") ?: ""
        entity = sharedPreferences.getString("entity","") ?: ""
    }

    fun updateState(stateObject : JSONObject){

        val state = stateObject.getString("state")
        val attributes = stateObject.getJSONObject("attributes");
        val volume_level = attributes.getDouble("volume_level");
        val volume_muted = attributes.getBoolean("is_volume_muted");
        val media_type = attributes.getString("media_content_type");
        var media_string = attributes.getString("media_title");
        var media_duration =  attributes.getDouble("media_duration");
        var media_position =  attributes.getDouble("media_position");
        var media_thumbnail =  attributes.getString("entity_picture");


        val media_split = media_string.split("-");

        var media_artist = ""
        var media_title: String

        when(media_split.count()){
            1 -> media_title = media_string
            2 -> {
                media_artist = media_split[0]
                media_title = media_split[1]
            }
            3 -> {
                media_artist = media_split[1]
                media_title = media_split[2]
            }
            4 -> {
                media_artist = media_split[1]
                media_title = media_split[2]
            }
            else -> {
                media_artist = media_split[1]
                media_title = media_split[2]
            }
        }

        val last_changed = stateObject.getString("last_changed");
        val last_change = Instant.parse(last_changed.split("+")[0]+"Z")
        val now = Instant.now();
        val differenz = Duration.between(last_change,now);
        val past_minutes = differenz.toMinutes();

        val report = PlayerStateReport(
            media_title,
            media_artist,
            past_minutes,
            state == "playing",
            media_duration,
            media_position,
            media_thumbnail
        );

        playerState = report;

        playerStateUpdateCallback.callback(playerState);
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
        ws.sendText(event.toString());
    }

    private fun constructEvent(service : String): JSONObject {

        var responseObject = JSONObject()
        messageId += 1;
        responseObject.put("id",messageId)
        responseObject.put("type","call_service")
        responseObject.put("domain","media_player")
        responseObject.put("service",service)

        var targetObject = JSONObject()
        targetObject.put("entity_id", entity)

        responseObject.put("target",targetObject)

        return responseObject;
    }
}