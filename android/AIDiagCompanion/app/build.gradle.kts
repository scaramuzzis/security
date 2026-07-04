import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Password di firma lette da keystore.properties (file locale, MAI versionato:
// vedi keystore.properties.example per il formato). Nessuna password in chiaro
// nel build script, che invece finisce su git.
val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) load(FileInputStream(propsFile))
}

android {
    namespace = "com.cybersentinel.aidiag"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cybersentinel.aidiag"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        create("release") {
            storeFile = file(
                keystoreProps.getProperty("storeFile")
                    ?: providers.gradleProperty("aidiag.keystore").getOrElse("../aidiag.keystore")
            )
            storePassword = keystoreProps.getProperty("storePassword")
                ?: providers.gradleProperty("aidiag.storePassword").orNull
            keyAlias = keystoreProps.getProperty("keyAlias") ?: "aidiag"
            keyPassword = keystoreProps.getProperty("keyPassword")
                ?: providers.gradleProperty("aidiag.keyPassword").orNull
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
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    testImplementation("junit:junit:4.13.2")
}
