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
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
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
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("com.github.mik3y:usb-serial-for-android:3.8.1")
}
