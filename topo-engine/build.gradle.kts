plugins {
    kotlin("jvm")
}

// Bytecode targets Java 17 but any installed JDK >= 17 can build it
// (Android Studio's bundled JDK works; no separate JDK 17 download needed).
java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}
kotlin {
    compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) }
}

// The calculation engine remains platform-independent and can be tested without Android.

tasks.register<JavaExec>("verifyEngine") {
    group = "verification"
    description = "Runs the engine's standalone numeric verification harness."
    dependsOn(tasks.testClasses)
    classpath = sourceSets["test"].runtimeClasspath
    mainClass.set("com.topocalcbtp.engine.EngineVerificationKt")
}
