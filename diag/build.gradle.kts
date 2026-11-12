plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Mini app de diagnóstico: instala en Android 4.4+ y muestra la versión REAL de la pantalla.
android {
    namespace = "com.cargo.diag"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.cargo.diag"
        minSdk = 19
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }
    signingConfigs {
        create("fija") {
            storeFile = file("../cargo/cargo.keystore")
            storePassword = "priuslauncher"
            keyAlias = "prius"
            keyPassword = "priuslauncher"
            enableV1Signing = true
            enableV2Signing = true
        }
    }
    buildTypes { debug { signingConfig = signingConfigs.getByName("fija") } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
