plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.jetbrains.kotlin.android)
    `maven-publish`
    signing
}

android {
    namespace = "ai.ultravox"
    compileSdk = 34

    defaultConfig {
        minSdk = 21
        aarMetadata {
            minCompileSdk = 21
        }

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    publishing {
        singleVariant("release") {
            withJavadocJar()
            withSourcesJar()
        }
    }
}

publishing {
    publications {
        register<MavenPublication>("release") {
            groupId = "ai.fixie"
            artifactId = "ultravox-client-sdk"
            version = "0.1.4"

            pom {
                name = "Ultravox Client"
                description = "Android client code for joining an Ultravox call"
                url = "https://github.com/fixie-ai/ultravox-client-sdk-android"
                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        name = "Mike Depinet"
                        email = "mike@fixie.ai"
                        organization = "Fixie.AI"
                        organizationUrl = "https://fixie.ai"
                    }
                }
                scm {
                    connection = "scm:git:git://github.com/fixie-ai/ultravox-client-sdk-android.git"
                    developerConnection =
                        "scm:git:ssh://github.com:fixie-ai/ultravox-client-sdk-android.git"
                    url = "https://github.com/fixie-ai/ultravox-client-sdk-android/tree/main"
                }
            }

            afterEvaluate {
                from(components["release"])
            }
        }
    }
    // To publish, uncomment this block and set the file url appropriately.
    // You'll also need to set signing.{keyId,password,secretKeyRingFile} in
    // gradle.properties after creating, uploading, and exporting a gpg key.
//    repositories {
//        maven {
//            name = "LocalForCentralUpload"
//            url = uri("file:///home/mike/tmp/maven")
//        }
//    }
}

signing {
    sign(publishing.publications["release"])
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.livekit.android)
    implementation(libs.kotlinx.collections.immutable)
    implementation(libs.okhttp)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
