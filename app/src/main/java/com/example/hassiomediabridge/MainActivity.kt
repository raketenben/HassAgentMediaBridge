package com.example.hassiomediabridge

import android.content.*
import android.os.*
import android.util.Log
import android.widget.Button
import android.widget.EditText
import android.widget.Switch
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.neovisionaries.ws.client.*
import org.json.JSONObject
import java.net.URL
import java.util.concurrent.Executors
import javax.net.ssl.SSLSocketFactory


class MainActivity : AppCompatActivity() {
    private lateinit var sharedPreferences : SharedPreferences;

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        sharedPreferences = applicationContext.getSharedPreferences(getString(R.string.preference_file_key),Context.MODE_PRIVATE);
        loadSettings()

        findViewById<Button>(R.id.saveAndTest).setOnClickListener { onSaveAndTest() }

        val thumbnailSwitch = findViewById<Switch>(R.id.externalThumbnail);
        thumbnailSwitch.setOnClickListener {
            with (sharedPreferences.edit()) {
                putBoolean("externalThumbnail",thumbnailSwitch.isChecked);
                apply()
            }
            thumbnailSwitch.text = if (thumbnailSwitch.isChecked) {
                "YouTube"
            }else{
                "HomeAssistant"
            }
        }


        //start
        startForegroundService(Intent(this, HassioBridgeService::class.java))
    }

    private fun loadSettings(){
        findViewById<EditText>(R.id.endpointURL).setText(sharedPreferences.getString("endpoint",""))
        findViewById<EditText>(R.id.endpointToken).setText(sharedPreferences.getString("token",""));
        findViewById<EditText>(R.id.mediaplayerEntity).setText(sharedPreferences.getString("entity",""));
        findViewById<Switch>(R.id.externalThumbnail).setChecked(sharedPreferences.getBoolean("externalThumbnail",false));

        if(findViewById<Switch>(R.id.externalThumbnail).isChecked){
            findViewById<Switch>(R.id.externalThumbnail).text = "YouTube"
        }else{
            findViewById<Switch>(R.id.externalThumbnail).text = "HomeAssistant"
        }
    }

    //fopr connection test
    var messageId = 0;
    var endpoint = "";
    var token = "";
    var entity = "";

    private val websocketHandler : WebSocketAdapter = object : WebSocketAdapter() {
        override fun  onConnected(ws: WebSocket, headers: Map<String, List<String>>) {
            Log.i("validation", "Connected")
            findViewById<TextView>(R.id.connectionStatus).text = "Connected!"
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
                    findViewById<TextView>(R.id.connectionStatus).text = "Trying credentials!"
                }
                "auth_ok" -> {
                    messageId += 1;
                    Log.i("validation","authentication ok")
                    //subscribe to media player
                    findViewById<TextView>(R.id.connectionStatus).text = "Connection and Auth is working!";
                    Log.i("validation","closing connection")
                    ws.disconnect()
                }
                "auth_invalid" -> {
                    Log.i("validation","authentication failed")
                    findViewById<TextView>(R.id.connectionStatus).text = "Authentication is invalid!"
                }
            }
        }

        override fun onError(websocket: WebSocket?, cause: WebSocketException?) {
            Log.e("validation",cause.toString())
            super.onError(websocket, cause)
            findViewById<TextView>(R.id.connectionStatus).text = "Connection Failed!"
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


    private fun onSaveAndTest(){
        //save settings
        with (sharedPreferences.edit()) {
            putString("endpoint",findViewById<EditText>(R.id.endpointURL).text.toString())
            putString("token",findViewById<EditText>(R.id.endpointToken).text.toString());
            putString("entity",findViewById<EditText>(R.id.mediaplayerEntity).text.toString());
            commit()
        }

        findViewById<TextView>(R.id.connectionStatus).text = "Checking connection...";

        val executor = Executors.newSingleThreadExecutor()
        val handler = Handler(Looper.getMainLooper())

        endpoint = findViewById<EditText>(R.id.endpointURL).text.toString()
        token = findViewById<EditText>(R.id.endpointToken).text.toString()
        entity = findViewById<EditText>(R.id.endpointURL).text.toString()

        var server_url = URL(endpoint)
        var server_name = server_url.host

        executor.execute {

            var factory : WebSocketFactory = WebSocketFactory()
            val sslSocketFactory = SSLSocketFactory.getDefault()
            factory.verifyHostname = true
            factory.sslSocketFactory = sslSocketFactory as SSLSocketFactory?
            factory.setServerName(server_name)

            val ws = factory.createSocket("$endpoint/api/websocket")

            // Register a listener to receive WebSocket events.
            ws.addListener(websocketHandler)

            ws.connect();
        }

        startForegroundService(Intent(applicationContext, HassioBridgeService::class.java))
    }
}