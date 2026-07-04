import java.io.FileInputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// Mese/anno di build reali, calcolati da Gradle al momento della
// compilazione: niente stringa da ricordarsi di aggiornare a mano.
val buildDate: String = SimpleDateFormat("MMMM yyyy", Locale.ITALIAN).format(Date())

// Password di firma lette da keystore.properties (file locale, MAI versionato:
// vedi keystore.properties.example per il formato). Nessuna password in chiaro
// nel build script, che invece finisce su git.
val keystoreProps = Properties().apply {
    val propsFile = rootProject.file("keystore.properties")
    if (propsFile.exists()) load(FileInputStream(propsFile))
}

android {
    namespace = "com.cybersentinel.phoneguard"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.cybersentinel.phoneguard"
        minSdk = 26
        targetSdk = 34
        versionCode = 25
        versionName = "6.15"

        buildConfigField("String", "BUILD_DATE", "\"$buildDate\"")
    }

    buildFeatures {
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            // Chiave self-signed per distribuzione diretta (fuori Play Store).
            // Percorso/alias non sono segreti: hanno un default. Le password
            // NON hanno default: arrivano solo da keystore.properties (locale,
            // non versionato) o da proprietà Gradle/ambiente equivalenti.
            storeFile = file(
                keystoreProps.getProperty("storeFile")
                    ?: providers.gradleProperty("phoneguard.keystore").getOrElse("../phoneguard.keystore")
            )
            storePassword = keystoreProps.getProperty("storePassword")
                ?: providers.gradleProperty("phoneguard.storePassword").orNull
            keyAlias = keystoreProps.getProperty("keyAlias") ?: "phoneguard"
            keyPassword = keystoreProps.getProperty("keyPassword")
                ?: providers.gradleProperty("phoneguard.keyPassword").orNull
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
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.8.2")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
}
