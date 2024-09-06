package ai.ultravox

import android.os.Build
import android.util.Log
import kotlinx.collections.immutable.toImmutableList
import org.json.JSONObject


typealias UltravoxSessionListener = () -> Unit

class UltravoxSessionState {
    private val _transcripts = ArrayList<Transcript>()

    @Suppress("unused")
    val transcripts
        get() = _transcripts.toImmutableList()
    val lastTranscript: Transcript?
        get() = _transcripts.lastOrNull()

    var status = UltravoxSessionStatus.DISCONNECTED
        internal set(value) {
            val prev = field
            field = value
            if (prev != value) {
                fireListeners("status")
            }
        }

    var lastExperimentalMessage: JSONObject? = null
        internal set(value) {
            field = value
            fireListeners("experimentalMessage")
        }

    private val listeners = HashMap<String, ArrayList<UltravoxSessionListener>>()

    @Suppress("unused")
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

    internal fun addOrUpdateTranscript(transcript: Transcript) {
        val last = lastTranscript
        if (last != null && !last.isFinal && last.speaker == transcript.speaker) {
            _transcripts.removeLast()
        }
        _transcripts.add(transcript)
        fireListeners("transcript")
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
