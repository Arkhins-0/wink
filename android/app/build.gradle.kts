import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

// --- Version -----------------------------------------------------------------
// Four segments. Bump both together for every release: the tag pushed to
// GitHub is "v" + versionName, and the app compares versionName against the
// latest release to decide whether to show the update popup.
val winkVersionName = "0.0.0.0"
val winkVersionCode = 1

// --- Build-time configuration --------------------------------------------------
// Values reach the app through BuildConfig. Each is looked up, in order, as an
// environment variable, a key in the repository's .env file, a key in
// local.properties, then a Gradle property (gradle.properties or -P). Only the
// keys asked for below are read; the database and storage secrets in .env are
// the website's and never reach the APK.
val dotenv = Properties().apply {
    // The shared .env sits at the repository root, one level above android/;
    // an android/.env beside this build, if present, wins over it.
    val file = listOf(rootProject.file(".env"), rootProject.file("../.env")).firstOrNull { it.exists() }
    if (file != null) {
        file.readLines(Charsets.UTF_8).forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#") || !line.contains('=')) return@forEach
            val key = line.substringBefore('=').trim().removePrefix("export ").trim()
            var value = line.substringAfter('=').trim()
            val quoted = value.length >= 2 &&
                ((value.startsWith('"') && value.endsWith('"')) || (value.startsWith('\'') && value.endsWith('\'')))
            if (quoted) value = value.substring(1, value.length - 1)
            if (key.isNotEmpty()) setProperty(key, value)
        }
    }
}
val local = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) file.inputStream().use { load(it) }
}
fun setting(envName: String, propName: String, default: String = ""): String =
    (System.getenv(envName)?.takeIf { it.isNotBlank() }
        ?: dotenv.getProperty(envName)?.takeIf { it.isNotBlank() }
        ?: local.getProperty(propName)?.takeIf { it.isNotBlank() }
        ?: project.findProperty(propName)?.toString()?.takeIf { it.isNotBlank() }
        ?: default).trim()

// Release signing: environment / .env first (CI, from secrets), then a keystore
// file on disk (a developer's machine). Neither is required — a release build
// with no signing config is simply unsigned.
val keystorePath = setting("WINK_KEYSTORE_PATH", "wink.keystore.path", "release.keystore.jks")
val keystoreFile = rootProject.file(keystorePath).let { if (it.isAbsolute) it else file(keystorePath) }
val hasSigning = keystoreFile.exists()

android {
    namespace = "com.arkhins.wink"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arkhins.wink"
        minSdk = 26
        targetSdk = 35
        versionCode = winkVersionCode
        versionName = winkVersionName

        buildConfigField("String", "BASE_URL", "\"${setting("WINK_BASE_URL", "wink.baseUrl", "https://wink.arkhins.com")}\"")
        buildConfigField("String", "UPDATE_URL", "\"${setting("WINK_UPDATE_URL", "wink.updateUrl")}\"")
        buildConfigField("String", "GITHUB_REPO", "\"${setting("WINK_GITHUB_REPO", "wink.githubRepo")}\"")
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = keystoreFile
                storePassword = setting("WINK_KEYSTORE_PASSWORD", "wink.keystore.password", "wink-release")
                keyAlias = setting("WINK_KEY_ALIAS", "wink.key.alias", "wink")
                keyPassword = setting("WINK_KEY_PASSWORD", "wink.key.password", "wink-release")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.06.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    debugImplementation("androidx.compose.ui:ui-tooling")

    implementation("androidx.core:core-ktx:1.16.0")
    implementation("androidx.core:core-splashscreen:1.0.1")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.1")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
}
