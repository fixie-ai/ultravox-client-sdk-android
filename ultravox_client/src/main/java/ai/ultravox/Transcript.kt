package ai.ultravox

data class Transcript(
    val text: String,
    val isFinal: Boolean,
    val speaker: Role,
    val medium: Medium,
) {
    enum class Role { USER, AGENT }
    enum class Medium { VOICE, TEXT }
}
