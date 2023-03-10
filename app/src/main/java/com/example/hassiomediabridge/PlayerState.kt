package com.example.hassiomediabridge

import android.content.SharedPreferences
import android.util.Log
import com.neovisionaries.ws.client.*
import org.json.JSONObject
import java.net.URL
import java.time.Duration
import java.time.Instant
import javax.net.ssl.SSLSocketFactory


class PlayerStateReport(
    val title : String,
    val artist : String,
    val volume : Double,
    val muted : Boolean,
    val media_type : String,
    val inactive_since : Long,
    val playing : Boolean,
    val media_duration : Double,
    val media_position : Double,
    val thumbnail : String,
    ) {
    init {}
}


interface PlayerStateUpdateCallback {
    fun callback(report: PlayerStateReport)
}

class PlayerStateClient(var sharedPreferences: SharedPreferences, var playerStateUpdateCallback: PlayerStateUpdateCallback) {

    private var endpoint : String =
        sharedPreferences.getString("endpoint", null) ?: throw Exception("Endpoint not set")

    private var token : String =
        sharedPreferences.getString("token", null) ?: throw Exception("Token not set");

    private var entity : String =
        sharedPreferences.getString("entity", null) ?: throw Exception("Entity not set");

    private var playerState : PlayerStateReport = PlayerStateReport(
        "",
        "",
        1.0,
        false,
        "music",
        10000,false,
        0.0,
        0.0,
        ""
    )

    private var messageId = 1;
    private lateinit var ws : WebSocket;

    private val websocketHandler : WebSocketAdapter = object : WebSocketAdapter() {
        override fun  onConnected(ws: WebSocket, headers: Map<String, List<String>>) {
            Log.d("websocket", "Connected")
        }

        override fun onTextMessage(ws: WebSocket, text: String) {
            var messageObject = JSONObject(text);
            var type = messageObject.getString("type");

            when (type) {
                "auth_required" -> {
                    //send our token
                    var responseObject = JSONObject()
                    responseObject.put("type","auth")
                    responseObject.put("access_token","$token")
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
                    triggerObject.put("entity_id","$entity")

                    responseObject.put("trigger",triggerObject)
                    ws.sendText(responseObject.toString())
                }
                "auth_invalid" -> {
                    Log.i("websocket","authentication failed")
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

        override fun onDisconnected(
            websocket: WebSocket?,
            serverCloseFrame: WebSocketFrame?,
            clientCloseFrame: WebSocketFrame?,
            closedByServer: Boolean
        ) {
            super.onDisconnected(websocket, serverCloseFrame, clientCloseFrame, closedByServer)
        }
    };

    init {
        var server_url = URL(endpoint)
        var server_name = server_url.host

        var factory : WebSocketFactory = WebSocketFactory()
        val sslSocketFactory = SSLSocketFactory.getDefault()
        factory.verifyHostname = true
        factory.sslSocketFactory = sslSocketFactory as SSLSocketFactory?
        factory.setServerName(server_name)

        ws = factory.createSocket("$endpoint/api/websocket")

        // Register a listener to receive WebSocket events.
        ws.addListener(websocketHandler)
        ws.connectAsynchronously()
    }

    fun updateSharedPreferences(){
        endpoint = sharedPreferences.getString("endpoint", null) ?: throw Exception("Endpoint not set");
        token = sharedPreferences.getString("token", null) ?: throw Exception("Token not set");
        entity = sharedPreferences.getString("entity", null) ?: throw Exception("Entity not set");
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
        var media_title = ""

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
            volume_level,
            volume_muted,
            media_type,
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
        targetObject.put("entity_id","$entity")

        responseObject.put("target",targetObject)

        return responseObject;
    }
}