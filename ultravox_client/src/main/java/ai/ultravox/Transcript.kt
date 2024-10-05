package ai.ultravox

/** A transcription of a single utterance. */
data class Transcript(
    /** The possibly-incomplete text of an utterance. */
    val text: String,
    /** Whether the text is complete or the utterance is ongoing. */
    val isFinal: Boolean,
    /** Who emitted the utterance. */
    val speaker: Role,
    /** The medium through which the utterance was emitted. */
    val medium: Medium,
) {
    /** The participant who created a transcript. */
    enum class Role { USER, AGENT }

    /** The medium by which the transcript was communicated. */
    enum class Medium { VOICE, TEXT }
}
