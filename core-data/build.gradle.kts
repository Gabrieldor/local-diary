plugins {
    id("com.android.library")
    id("com.google.devtools.ksp") version "2.2.10-2.0.2"
}

android {
    namespace = "com.localdiary.coredata"
    compileSdk = 37

    defaultConfig {
        minSdk = 26
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    api("androidx.room:room-runtime:2.8.2")
    api("androidx.room:room-ktx:2.8.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.1")
    ksp("androidx.room:room-compiler:2.8.2")

    testImplementation("junit:junit:4.13.2")
}
