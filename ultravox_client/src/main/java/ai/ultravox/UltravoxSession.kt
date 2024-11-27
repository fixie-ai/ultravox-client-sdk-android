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
typealias ClientToolImplementation = (JSONObject) -> ClientToolResult
typealias AsyncClientToolImplementation = suspend (JSONObject) -> ClientToolResult

/**
 * Manager for a single session with Ultravox. The session manages state and
 * emits events (via registered listeners) to notify consumers of state changes.
 * The following events may be emitted:
 *
 *     - "status": Fired when the session status changes.
 *     - "transcripts": Fired when a transcript is added or updated.
 *     - "experimental_message": Fired when an experimental message is received. The message is
 *         available via lastExperimentalMessage.
 *     - "mic_muted": Fired when the user's microphone is muted or unmuted.
 *     - "speaker_muted": Fired when the user's speaker (agent output audio) is muted or unmuted.
 *     - "data_message": Fired when any data message is received, including those typically handled
 *         by this SDK. The message is available via lastDataMessage. See
 *         https://docs.ultravox.ai/datamessages for message types.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class UltravoxSession(
    ctx: Context,
    private val coroScope: CoroutineScope,
    private val client: OkHttpClient = OkHttpClient(),
    private val experimentalMessages: Set<String> = HashSet(),
) {
    companion object {
        const val ULTRAVOX_SDK_VERSION = "0.1.5"
    }

    private var socket: WebSocket? = null
    private var room: Room = LiveKit.create(ctx)

    private val _transcripts = ArrayList<Transcript?>()

    /** An immutable copy of all the session's transcripts. */
    val transcripts
        get() = _transcripts.filterNotNull().toImmutableList()

    /** The most recent transcript for the session. */
    val lastTranscript
        get() = _transcripts.lastOrNull()

    /** The session's current status. */
    var status = UltravoxSessionStatus.DISCONNECTED
        private set(value) {
            val prev = field
            field = value
            if (prev != value) {
                fireListeners("status")
            }
        }

    /** The most recently received experimental message. */
    var lastExperimentalMessage: JSONObject? = null
        private set(value) {
            field = value
            fireListeners("experimental_message")
        }

    /** The most recently received data message. */
    var lastDataMessage: JSONObject? = null
        private set(value) {
            field = value
            fireListeners("data_message")
        }

    /** Whether the user's microphone is muted. (This does not inspect hardware state.) */
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

    /** Toggles the mute state of the user's microphone. See micMuted. */
    fun toggleMicMuted() {
        micMuted = !micMuted
    }

    /**
     * Whether the user's speaker (that is, output audio from the agent) is muted.
     * (This does not inspect hardware state or system volume.)
     */
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

    /** Toggles the mute state of the user's speaker (agent audio output). See speakerMuted. */
    fun toggleSpeakerMuted() {
        speakerMuted = !speakerMuted
    }

    private val registeredSyncTools = HashMap<String, ClientToolImplementation>()
    private val registeredAsyncTools = HashMap<String, AsyncClientToolImplementation>()

    /**
     * Registers a client tool implementation with the given name. If the call is started with a
     * client-implemented tool, this implementation will be invoked when the model calls the tool.
     *
     * See https://docs.ultravox.ai/tools/ for more information.
     */
    fun registerToolImplementation(name: String, impl: ClientToolImplementation) {
        registeredSyncTools[name] = impl
    }

    /** Override of [registerToolImplementation] for suspendable tool implementations. */
    fun registerToolImplementation(name: String, impl: AsyncClientToolImplementation) {
        registeredAsyncTools[name] = impl
    }

    private val listeners = HashMap<String, ArrayList<UltravoxSessionListener>>()

    /** Sets up listening for a particular type of event. */
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

    /** Connects to a call using the given joinUrl. */
    fun joinCall(
        joinUrl: String,
        @Suppress("FORBIDDEN_VARARG_PARAMETER_TYPE", "UNUSED_PARAMETER")
        vararg forceNamedParams: Nothing,
        clientVersion: String? = null
    ) {
        if (status != UltravoxSessionStatus.DISCONNECTED) {
            throw RuntimeException("Cannot join a new call while already in a call")
        }
        status = UltravoxSessionStatus.CONNECTING
        val httpUrl = if (joinUrl.startsWith("wss://") or joinUrl.startsWith("ws://")) {
            // This is the expected case, but OkHttp expects http(s) protocol even
            // for WebSocket requests for some reason.
            joinUrl.replaceFirst("ws", "http").toHttpUrl()
        } else {
            joinUrl.toHttpUrl()
        }
        val urlBuilder = httpUrl.newBuilder()
        var uvClientVersion = "android_$ULTRAVOX_SDK_VERSION"
        if (clientVersion != null) {
            uvClientVersion += ":$clientVersion"
        }
        urlBuilder.addQueryParameter("clientVersion", uvClientVersion)
        urlBuilder.addQueryParameter("apiVersion", "1")
        if (experimentalMessages.isNotEmpty()) {
            urlBuilder.addQueryParameter(
                "experimentalMessages",
                experimentalMessages.joinToString(",")
            )
        }
        val req = Request.Builder().url(urlBuilder.build()).build()
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

    /** Leaves the current call (if any). */
    fun leaveCall() {
        disconnect()
    }

    /**
     * Sets the agent's output medium. If the agent is currently speaking, this will take effect at
     * the end of the agent's utterance. Also see [speakerMuted].
     */
    fun setOutputMedium(medium: Transcript.Medium) {
        if (!status.live) {
            throw RuntimeException(
                "Cannot set output medium while not connected. Current status is $status."
            )
        }
        val message = JSONObject()
        message.put("type", "set_output_medium")
        message.put("medium", medium.name.lowercase())
        sendData(message)
    }

    /** Sends a message via text. */
    fun sendText(text: String) {
        if (!status.live) {
            throw RuntimeException(
                "Cannot send text while not connected. Current status is $status."
            )
        }
        val message = JSONObject()
        message.put("type", "input_text_message")
        message.put("text", text)
        sendData(message)
    }

    /** Sends an arbitrary data message to the server.
     *
     * See https://docs.ultravox.ai/datamessages for message types.
     */
    fun sendData(message: JSONObject) {
        if (!message.has("type")) {
            throw RuntimeException("Cannot send a data message without a type.")
        }
        coroScope.launch {
            room.localParticipant.publishData(message.toString().encodeToByteArray())
        }
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
        lastDataMessage = message
        when (message["type"]) {
            "state" -> {
                when (message["state"]) {
                    "listening" -> status = UltravoxSessionStatus.LISTENING
                    "thinking" -> status = UltravoxSessionStatus.THINKING
                    "speaking" -> status = UltravoxSessionStatus.SPEAKING
                }
            }

            "transcript" -> {
                val medium =
                    if (message["medium"] == "voice")
                        Transcript.Medium.VOICE
                    else Transcript.Medium.TEXT
                val role =
                    if (message["role"] == "agent") Transcript.Role.AGENT else Transcript.Role.USER
                val ordinal = message["ordinal"] as Int
                val isFinal = if (message.has("final")) message["final"] as Boolean else false
                if (message.has("text")) {
                    addOrUpdateTranscript(
                        ordinal,
                        medium,
                        role,
                        isFinal,
                        text = message["text"] as String
                    )
                } else if (message.has("delta")) {
                    addOrUpdateTranscript(
                        ordinal,
                        medium,
                        role,
                        isFinal,
                        delta = message["delta"] as String
                    )
                }
            }

            "client_tool_invocation" -> {
                invokeClientTool(
                    message["toolName"] as String,
                    message["invocationId"] as String,
                    message["parameters"] as JSONObject
                )
            }

            else -> {
                if (experimentalMessages.isNotEmpty()) {
                    lastExperimentalMessage = message
                }
            }
        }
    }

    private fun addOrUpdateTranscript(
        ordinal: Int,
        medium: Transcript.Medium,
        speaker: Transcript.Role,
        isFinal: Boolean,
        text: String? = null,
        delta: String? = null
    ) {
        while (_transcripts.size < ordinal) {
            _transcripts.add(null)
        }
        if (_transcripts.size == ordinal) {
            _transcripts.add(Transcript(text ?: delta ?: "", isFinal, speaker, medium))
        } else {
            val priorText = _transcripts[ordinal]?.text ?: ""
            _transcripts[ordinal] =
                Transcript(text ?: (priorText + (delta ?: "")), isFinal, speaker, medium)
        }
        fireListeners("transcripts")
    }

    private fun invokeClientTool(toolName: String, invocationId: String, parameters: JSONObject) {
        val message = JSONObject()
        message.put("type", "client_tool_result")
        message.put("invocationId", invocationId)
        if (registeredSyncTools.containsKey(toolName)) {
            try {
                sendToolResult(message, registeredSyncTools[toolName]!!(parameters))
            } catch (ex: Exception) {
                sendToolFailure(message, toolName, ex)
            }
        } else if (registeredAsyncTools.containsKey(toolName)) {
            coroScope.launch {
                try {
                    sendToolResult(message, registeredAsyncTools[toolName]!!(parameters))
                } catch (ex: Exception) {
                    sendToolFailure(message, toolName, ex)
                }
            }
        } else {
            Log.w("UltravoxClient", "Missing tool implementation for $toolName")
            message.put("errorType", "undefined")
            message.put("errorMessage", "Client tool $toolName is not registered (Android client)")
            sendData(message)
        }
    }

    private fun sendToolResult(message: JSONObject, result: ClientToolResult) {
        message.put("result", result.result)
        if (result.responseType != null) {
            message.put("responseType", result.responseType)
        }
        sendData(message)
    }

    private fun sendToolFailure(message: JSONObject, toolName: String, ex: Exception) {
        Log.w("UltravoxClient", "Error invoking client tool $toolName", ex)
        message.put("errorType", "implementation-error")
        message.put("errorMessage", "${ex.message}\n${ex.printStackTrace()}")
        sendData(message)
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
