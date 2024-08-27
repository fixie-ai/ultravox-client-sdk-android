package ai.ultravox

import android.content.Context
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

@Suppress("unused")
class UltravoxSession(ctx: Context, private val coroScope: CoroutineScope, private val client: OkHttpClient = OkHttpClient()) {
    private val state = UltravoxSessionState()
    private var socket: WebSocket? = null
    private var room: Room = LiveKit.create(ctx)

    fun joinCall(joinUrl: String): UltravoxSessionState {
        if (state.status != UltravoxSessionStatus.DISCONNECTED) {
            throw RuntimeException("Cannot join a new call while already in a call")
        }
        state.status = UltravoxSessionStatus.CONNECTING
        val httpUrl = if (joinUrl.startsWith("wss://") or joinUrl.startsWith("ws://")) {
            // This is the expected case, but OkHttp expects http(s) protocol even
            // for WebSocket requests for some reason.
            joinUrl.replaceFirst("ws", "http")
        } else {
            joinUrl
        }
        val req = Request.Builder().url(httpUrl.toHttpUrl()).build()
        socket = client.newWebSocket(req, object: WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = JSONObject(text)
                if (message["type"] == "room_info") {
                    coroScope.launch {
                        launch {
                            room.events.collect { event ->
                                when (event) {
                                    is RoomEvent.DataReceived -> onDataReceived(event)
                                    else -> {}
                                }
                            }
                        }
                        room.connect(message["roomUrl"] as String, message["token"] as String)
                        room.localParticipant.setMicrophoneEnabled(true)
                        state.status = UltravoxSessionStatus.IDLE
                    }
                }
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                disconnect()
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                disconnect()
            }
        })

        return state
    }

    fun leaveCall() {
        disconnect()
    }

    fun sendText(text: String) {
        if (!state.status.live) {
            throw RuntimeException("Cannot send text while not connected. Current status is " + state.status + ".")
        }
        val message = JSONObject()
        message.put("type", "input_text_message")
        message.put("text", text)
        sendData(message)
    }

    private fun disconnect() {
        if (state.status == UltravoxSessionStatus.DISCONNECTED) {
            return
        }
        state.status = UltravoxSessionStatus.DISCONNECTING
        room.disconnect()
        socket?.cancel()
        state.status = UltravoxSessionStatus.DISCONNECTED
    }

    private fun onDataReceived(event: RoomEvent.DataReceived) {
        val message = JSONObject(event.data.decodeToString())
        when (message["type"]) {
            "state" -> {
                when (message["state"]) {
                    "listening" -> state.status = UltravoxSessionStatus.LISTENING
                    "thinking" -> state.status = UltravoxSessionStatus.THINKING
                    "speaking" -> state.status = UltravoxSessionStatus.SPEAKING
                }
            }
            "transcript" -> {
                val transcript = message["transcript"] as JSONObject
                val medium =
                    if (message.has("medium") && message["medium"] == "text") Transcript.Medium.TEXT else Transcript.Medium.VOICE
                state.addOrUpdateTranscript(
                    Transcript(
                        transcript["text"] as String,
                        transcript["final"] as Boolean,
                        Transcript.Role.USER,
                        medium
                    )
                )
            }
            "voice_synced_transcript", "agent_text_transcript" -> {
                val medium = if (message["type"] == "agent_text_transcript") Transcript.Medium.TEXT else Transcript.Medium.VOICE
                if (message.has("text") && message["text"] != JSONObject.NULL) {
                    state.addOrUpdateTranscript(Transcript(message["text"] as String, message["final"] as Boolean, Transcript.Role.AGENT, medium))
                } else if (message.has("delta") && message["delta"] != JSONObject.NULL) {
                    val last = state.lastTranscript
                    if (last != null && last.speaker == Transcript.Role.AGENT) {
                        state.addOrUpdateTranscript(Transcript(last.text + message["delta"] as String, message["final"] as Boolean, Transcript.Role.AGENT, medium))
                    }
                }
            }
        }
    }

    private fun sendData(message: JSONObject) {
        coroScope.launch {
            room.localParticipant.publishData(message.toString().encodeToByteArray())
        }
    }

}