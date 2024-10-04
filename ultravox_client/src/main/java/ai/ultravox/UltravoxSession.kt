package ai.ultravox

import android.content.Context
import android.os.Build
import android.util.Log
import io.livekit.android.LiveKit
import io.livekit.android.events.RoomEvent
import io.livekit.android.events.collect
import io.livekit.android.room.Room
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject

typealias UltravoxSessionListener = () -> Unit

@Suppress("unused")
class UltravoxSession(
    ctx: Context,
    private val coroScope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient(),
    private val experimentalMessages: Set<String> = HashSet(),
) {
    private var socket: WebSocket? = null
    private var room: Room = LiveKit.create(ctx)

    private val _transcripts = ArrayList<Transcript>()

    val transcripts
        get() = _transcripts.toImmutableList()

    val lastTranscript
        get() = _transcripts.lastOrNull()

    var status = UltravoxSessionStatus.DISCONNECTED
        private set(value) {
            val prev = field
            field = value
            if (prev != value) {
                fireListeners("status")
            }
        }

    @Suppress("MemberVisibilityCanBePrivate")
    var lastExperimentalMessage: JSONObject? = null
        private set(value) {
            field = value
            fireListeners("experimental_message")
        }

    var micMuted: Boolean = false
        set(value) {
            val prev = field
            field = value
            if (prev != value) {
                coroScope.launch {
                    room.localParticipant.setMicrophoneEnabled(!value)
                    fireListeners("mic_muted")
                }
            }
        }

    fun toggleMicMuted() {
        micMuted = !micMuted
    }

    var speakerMuted: Boolean = false
        set(value) {
            val prev = field
            field = value
            if (prev != value) {
                coroScope.launch {
                    for (participant in room.remoteParticipants.values) {
                        for ((_, track) in participant.audioTrackPublications) {
                            track?.enabled = !value
                        }
                    }
                    fireListeners("speaker_muted")
                }
            }
        }

    fun toggleSpeakerMuted() {
        speakerMuted = !speakerMuted
    }

    private val listeners = HashMap<String, ArrayList<UltravoxSessionListener>>()

    fun listen(event: String, listener: UltravoxSessionListener) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            listeners.putIfAbsent(event, ArrayList())
        } else {
            if (!listeners.containsKey("event")) {
                listeners[event] = ArrayList()
            }
        }
        listeners[event]!!.add(listener)
    }

    fun joinCall(joinUrl: String) {
        if (status != UltravoxSessionStatus.DISCONNECTED) {
            throw RuntimeException("Cannot join a new call while already in a call")
        }
        status = UltravoxSessionStatus.CONNECTING
        var httpUrl = if (joinUrl.startsWith("wss://") or joinUrl.startsWith("ws://")) {
            // This is the expected case, but OkHttp expects http(s) protocol even
            // for WebSocket requests for some reason.
            joinUrl.replaceFirst("ws", "http").toHttpUrl()
        } else {
            joinUrl.toHttpUrl()
        }
        if (experimentalMessages.isNotEmpty()) {
            httpUrl = httpUrl.newBuilder()
                .addQueryParameter("experimentalMessages", experimentalMessages.joinToString(","))
                .build()
        }
        val req = Request.Builder().url(httpUrl).build()
        socket = client.newWebSocket(req, object : WebSocketListener() {
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
                        status = UltravoxSessionStatus.IDLE
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
    }

    fun leaveCall() {
        disconnect()
    }

    fun sendText(text: String) {
        if (!status.live) {
            throw RuntimeException("Cannot send text while not connected. Current status is $status.")
        }
        val message = JSONObject()
        message.put("type", "input_text_message")
        message.put("text", text)
        sendData(message)
    }

    private fun disconnect() {
        if (status == UltravoxSessionStatus.DISCONNECTED) {
            return
        }
        status = UltravoxSessionStatus.DISCONNECTING
        room.disconnect()
        socket?.cancel()
        status = UltravoxSessionStatus.DISCONNECTED
    }

    private fun onDataReceived(event: RoomEvent.DataReceived) {
        val message = JSONObject(event.data.decodeToString())
        when (message["type"]) {
            "state" -> {
                when (message["state"]) {
                    "listening" -> status = UltravoxSessionStatus.LISTENING
                    "thinking" -> status = UltravoxSessionStatus.THINKING
                    "speaking" -> status = UltravoxSessionStatus.SPEAKING
                }
            }

            "transcript" -> {
                val transcript = message["transcript"] as JSONObject
                val medium =
                    if (transcript.has("medium") && transcript["medium"] == "text") Transcript.Medium.TEXT else Transcript.Medium.VOICE
                addOrUpdateTranscript(
                    Transcript(
                        transcript["text"] as String,
                        transcript["final"] as Boolean,
                        Transcript.Role.USER,
                        medium
                    )
                )
            }

            "voice_synced_transcript", "agent_text_transcript" -> {
                val medium =
                    if (message["type"] == "agent_text_transcript") Transcript.Medium.TEXT else Transcript.Medium.VOICE
                if (message.has("text") && message["text"] != JSONObject.NULL) {
                    addOrUpdateTranscript(
                        Transcript(
                            message["text"] as String,
                            message["final"] as Boolean,
                            Transcript.Role.AGENT,
                            medium
                        )
                    )
                } else if (message.has("delta") && message["delta"] != JSONObject.NULL) {
                    val last = lastTranscript
                    if (last != null && last.speaker == Transcript.Role.AGENT) {
                        addOrUpdateTranscript(
                            Transcript(
                                last.text + message["delta"] as String,
                                message["final"] as Boolean,
                                Transcript.Role.AGENT,
                                medium
                            )
                        )
                    }
                }
            }

            else -> {
                if (experimentalMessages.isNotEmpty()) {
                    lastExperimentalMessage = message
                }
            }
        }
    }

    private fun addOrUpdateTranscript(transcript: Transcript) {
        val last = lastTranscript
        if (last != null && !last.isFinal && last.speaker == transcript.speaker) {
            _transcripts.removeLast()
        }
        _transcripts.add(transcript)
        fireListeners("transcript")
    }

    private fun sendData(message: JSONObject) {
        coroScope.launch {
            room.localParticipant.publishData(message.toString().encodeToByteArray())
        }
    }

    private fun fireListeners(event: String) {
        if (!listeners.containsKey(event)) {
            return
        }
        for (listener in listeners[event]!!) {
            try {
                listener()
            } catch (e: Exception) {
                Log.w("UltravoxClient", "Listener error: ", e)
            }
        }
    }
}