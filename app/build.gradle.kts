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
        versionCode = 2
        versionName = "2.0"

        // Clave de Google Maps: viene del "secret" MAPS_API_KEY de GitHub (o vacía = mapa de respaldo)
        val mapsKey = System.getenv("MAPS_API_KEY") ?: (project.findProperty("MAPS_API_KEY") as String?) ?: ""
        manifestPlaceholders["MAPS_API_KEY"] = mapsKey
        buildConfigField("String", "MAPS_API_KEY", "\"" + mapsKey + "\"")
    }

    buildFeatures {
        buildConfig = true
    }

    // Dos versiones del mismo launcher, una para cada carro
    flavorDimensions += "carro"
    productFlavors {
        create("prius") {
            dimension = "carro"
            applicationId = "com.juandiego.priuslauncher"
        }
        create("jeep") {
            dimension = "carro"
            applicationId = "com.juandiego.jeeplauncher"
        }
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
        textOutput = file("build/lint.txt")
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

dependencies {
    implementation("com.google.android.gms:play-services-maps:18.2.0")
}
