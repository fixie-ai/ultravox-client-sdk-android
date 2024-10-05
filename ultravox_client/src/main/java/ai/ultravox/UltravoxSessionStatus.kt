package ai.ultravox

/** The current status of an UltravoxSession. */
enum class UltravoxSessionStatus(val live: Boolean) {
    /** The session is not connected and not attempting to connect. This is the initial state. */
    DISCONNECTED(false),

    /** The client is disconnecting from the session. */
    DISCONNECTING(false),

    /** The client is attempting to connect to the session. */
    CONNECTING(false),

    /** The client is connected and the server is warming up. */
    IDLE(true),

    /** The client is connected and the server is listening for voice input. */
    LISTENING(true),

    /** The client is connected and the server is considering its response. The user can still interrupt. */
    THINKING(true),

    /** The client is connected and the server is playing response audio. The user can interrupt as needed. */
    SPEAKING(true)
}