# Ultravox client SDK for Android

Android client SDK for [Ultravox](https://ultravox.ai).

[![maven package](https://img.shields.io/maven-central/v/ai.fixie/ultravox-client-sdk?label=ultravox-client-sdk&color=orange)](https://central.sonatype.com/artifact/ai.fixie/ultravox-client-sdk)

## Usage

```kotlin
val sessionState = session.joinCall(joinText.text.toString())
sessionState.listen("transcript") {
    run {
        val last = sessionState.lastTranscript
        // Do stuff with the transcript
    }
}
```

See the included example app for a more complete example. To get a `joinUrl`, you'll want to
integrate your server with the [Ultravox REST API](https://fixie-ai.github.io/ultradox/).

