package ai.ultravox

enum class UltravoxSessionStatus(val live: Boolean) {
    DISCONNECTED(false),
    DISCONNECTING(false),
    CONNECTING(false),
    IDLE(true),
    LISTENING(true),
    THINKING(true),
    SPEAKING(true)
}