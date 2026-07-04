plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.cybersentinel.phoneguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cybersentinel.phoneguard"
        minSdk = 26
        targetSdk = 34
        versionCode = 10
        versionName = "5.0"
    }

    signingConfigs {
        create("release") {
            // Chiave self-signed per distribuzione diretta (fuori Play Store).
            // Percorso e password sovrascrivibili da gradle.properties/ambiente.
            storeFile = file(
                providers.gradleProperty("phoneguard.keystore")
                    .getOrElse("../phoneguard.keystore")
            )
            storePassword = providers.gradleProperty("phoneguard.storePassword")
                .getOrElse("phoneguard2026")
            keyAlias = "phoneguard"
            keyPassword = providers.gradleProperty("phoneguard.keyPassword")
                .getOrElse("phoneguard2026")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
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
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
