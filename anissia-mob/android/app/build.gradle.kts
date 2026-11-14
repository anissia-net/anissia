plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("dev.flutter.flutter-gradle-plugin")
}

val releaseKeystore = providers.environmentVariable("ANISSIA_ANDROID_KEYSTORE").orNull
val releaseStorePassword = providers.environmentVariable("ANISSIA_ANDROID_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.environmentVariable("ANISSIA_ANDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.environmentVariable("ANISSIA_ANDROID_KEY_PASSWORD").orNull
val canSignRelease = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword).all { !it.isNullOrBlank() }

android {
    namespace = "anissia.android"
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    defaultConfig {
        applicationId = "anissia.android"
        minSdk = 35
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    if (canSignRelease) {
        signingConfigs.create("production") {
            storeFile = file(releaseKeystore!!)
            storePassword = releaseStorePassword
            keyAlias = releaseKeyAlias
            keyPassword = releaseKeyPassword
        }
    }
    buildTypes {
        release {
            if (canSignRelease) signingConfig = signingConfigs.getByName("production")
        }
    }
}

gradle.taskGraph.whenReady {
    if (allTasks.any { it.project == project && it.name.contains("Release") } && !canSignRelease) {
        throw GradleException("Release signing requires ANISSIA_ANDROID_KEYSTORE, ANISSIA_ANDROID_STORE_PASSWORD, ANISSIA_ANDROID_KEY_ALIAS and ANISSIA_ANDROID_KEY_PASSWORD.")
    }
}

kotlin {
    compilerOptions {
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17
    }
}

flutter { source = "../.." }
