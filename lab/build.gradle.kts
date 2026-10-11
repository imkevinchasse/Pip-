// A tiny desktop test bench: runs Pip's REAL code (the same source files the app uses) against
// the REAL models, so problems show up here instead of on a phone. Not part of the app.
plugins {
    kotlin("jvm") version "2.2.10"
    application
}

repositories { mavenCentral() }

dependencies {
    implementation("com.microsoft.onnxruntime:onnxruntime:1.20.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("net.java.dev.jna:jna:5.13.0")
    implementation("com.alphacephei:vosk:0.3.45")
}

sourceSets.main {
    kotlin.srcDir("../app/src/main/java")
    kotlin.include(
        "Lab*.kt",
        "com/example/core/**",
        "com/example/personality/**",
        "com/example/model/**",
        "com/example/manager/**",
        "com/example/engine/OnnxTokenModel.kt",
        "com/example/engine/WhisperModel.kt",
        "com/example/engine/Mel.kt"
    )
    kotlin.srcDir("src/main/kotlin")
}

kotlin { jvmToolchain(17) }

application { mainClass.set("LabKt") }
