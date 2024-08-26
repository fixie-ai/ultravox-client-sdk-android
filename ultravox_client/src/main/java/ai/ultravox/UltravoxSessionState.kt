package ai.ultravox

import android.util.Log
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.toImmutableList


typealias UltravoxSessionListener = (ImmutableList<Transcript>, UltravoxSessionStatus) -> Unit

class UltravoxSessionState() {
    private val transcripts = ArrayList<Transcript>()
    internal val lastTranscript: Transcript?
        get() = transcripts.lastOrNull()

    var status = UltravoxSessionStatus.DISCONNECTED
        internal set(value) {
            val prev = field
            field = value
            if (prev != value) {
                fireListeners(statusChangeListeners.toImmutableList())
            }
        }

    private val transcriptChangeListeners = ArrayList<UltravoxSessionListener>()
    private val statusChangeListeners = ArrayList<UltravoxSessionListener>()

    @Suppress("unused")
    fun listenForTranscriptChanges(listener: UltravoxSessionListener) {
        transcriptChangeListeners.add(listener)
    }

    @Suppress("unused")
    fun listenForStatusChanges(listener: UltravoxSessionListener) {
        statusChangeListeners.add(listener)
    }

    internal fun addOrUpdateTranscript(transcript: Transcript) {
        val last = lastTranscript
        if (last != null && !last.isFinal && last.speaker == transcript.speaker) {
            transcripts.removeLast()
        }
        transcripts.add(transcript)
        fireListeners(transcriptChangeListeners.toImmutableList())
    }

    private fun fireListeners(listeners: ImmutableList<UltravoxSessionListener>) {
        val transcripts = this.transcripts.toImmutableList()
        for (listener in listeners) {
            try {
                listener(transcripts, status)
            } catch (e: Exception) {
                Log.w("UltravoxClient", "Listener error: ", e)
            }
        }
    }
}
