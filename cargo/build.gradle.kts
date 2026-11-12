plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cargo.launcher"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.cargo.launcher"
        minSdk = 28          // Android 9 (Pie), como el estéreo del Prius
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"
    }

    // Una app por carro: cada una abre directo con su vehículo (se puede cambiar en el menú del instalador).
    buildFeatures { buildConfig = true }
    flavorDimensions += "car"
    productFlavors {
        create("prius") {
            dimension = "car"
            applicationIdSuffix = ".prius"
            buildConfigField("String", "DEFAULT_VEHICLE", "\"PRIUS_2011\"")
        }
        create("jeep") {
            dimension = "car"
            applicationIdSuffix = ".jeep"
            buildConfigField("String", "DEFAULT_VEHICLE", "\"JEEP_SAHARA_2021\"")
        }
    }

    // Llave fija: cada versión nueva se instala encima de la anterior (sin "App not installed")
    signingConfigs {
        create("fija") {
            storeFile = file("cargo.keystore")
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
            signingConfig = signingConfigs.getByName("fija")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    lint {
        abortOnError = true
        checkReleaseBuilds = false
        textReport = true
        textOutput = file("build/lint.txt")
    }
}

dependencies {
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
}
