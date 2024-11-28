# Ultravox client SDK for Android

Android client SDK for [Ultravox](https://ultravox.ai).

[![maven package](https://img.shields.io/maven-central/v/ai.fixie/ultravox-client-sdk?label=ultravox-client-sdk&color=orange)](https://central.sonatype.com/artifact/ai.fixie/ultravox-client-sdk)

## Usage

```kotlin
session.listen("transcript") {
    run {
        val last = session.lastTranscript
        // Do stuff with the transcript
    }
}
session.joinCall(joinText.text.toString())
```

See the included example app for a more complete example. To get a `joinUrl`, you'll want to
integrate your server with the [Ultravox REST API](https://fixie-ai.github.io/ultradox/).

## Publishing

1. Bump the version in ultravox_client's build.gradle.kts *and in UltravoxSession.kt*
2. Open a PR in GitHub and get the changes merged. (This also runs tests, so please only publish
   from main!)
3. Uncomment the "LocalForCentralUpload" maven repository block in the same gradle file
4. Uncomment and properly populate the three signing-related values in gradle.properties
5. Run publishToMaven (from within AndroidStudio)
6. Compress the output "ai" directory into a zip file
7. Upload that zip to https://central.sonatype.com/publishing and work through the UI to publish
8. Create a new tag/release in GitHub please!
