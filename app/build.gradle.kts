plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.juandiego.priuslauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.juandiego.priuslauncher"
        minSdk = 24          // Android 7.0 en adelante
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Llave fija: así cada versión nueva se instala encima de la anterior
    signingConfigs {
        create("fija") {
            storeFile = file("prius-launcher.keystore")
            storePassword = "priuslauncher"
            keyAlias = "prius"
            keyPassword = "priuslauncher"
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName("fija")
        }
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("fija")
        }
    }

    lint {
        abortOnError = false
        textReport = true
        textOutput = file("stdout")
        checkOnly += setOf("NewApi", "InlinedApi", "WrongConstant", "ResourceType", "MissingDefaultResource")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
