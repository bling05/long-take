import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("com.google.devtools.ksp")
}

// API keys baked in at build time, from the git-ignored secrets.properties (see secrets.properties.example).
val secrets = Properties().apply {
    val f = rootProject.file("secrets.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.longtake"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.longtake"
        minSdk = 26
        targetSdk = 35
        versionCode = 4
        versionName = "1.3"
        buildConfigField("String", "YOUTUBE_API_KEY", "\"${secrets.getProperty("YOUTUBE_API_KEY", "")}\"")
        // Profiles / social (Firebase). Leave blank to run without profiles.
        buildConfigField("String", "FIREBASE_PROJECT_ID", "\"${secrets.getProperty("FIREBASE_PROJECT_ID", "")}\"")
        buildConfigField("String", "FIREBASE_APP_ID", "\"${secrets.getProperty("FIREBASE_APP_ID", "")}\"")
        buildConfigField("String", "FIREBASE_API_KEY", "\"${secrets.getProperty("FIREBASE_API_KEY", "")}\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"${secrets.getProperty("GOOGLE_WEB_CLIENT_ID", "")}\"")
        // true = talk to a local Firebase emulator on this computer (for testing only).
        buildConfigField("boolean", "FIREBASE_USE_EMULATOR", secrets.getProperty("FIREBASE_USE_EMULATOR", "false"))
    }

    // Signs builds with the app's own key when it's present (keystore/ and its password in
    // secrets.properties are kept out of git). Without it, the standard debug key is used,
    // so anyone can still build the app; Google sign-in needs the real key.
    val keystore = rootProject.file("keystore/essayfinder.jks")
    val hasKeystore = keystore.exists() && secrets.getProperty("KEYSTORE_PASSWORD") != null
    signingConfigs {
        if (hasKeystore) create("app") {
            storeFile = keystore
            storePassword = secrets.getProperty("KEYSTORE_PASSWORD")
            keyAlias = secrets.getProperty("KEY_ALIAS", "essayfinder")
            keyPassword = secrets.getProperty("KEY_PASSWORD", secrets.getProperty("KEYSTORE_PASSWORD"))
        }
    }

    buildTypes {
        debug {
            if (hasKeystore) signingConfig = signingConfigs.getByName("app")
        }
        release {
            isMinifyEnabled = false
            signingConfig = if (hasKeystore) signingConfigs.getByName("app") else signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += setOf(
                "META-INF/*.kotlin_module",
                "META-INF/versions/**",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*",
                "META-INF/NOTICE*",
                "META-INF/INDEX.LIST",
                "META-INF/*.md",
            )
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.3")

    val composeBom = platform("androidx.compose:compose-bom:2024.12.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.navigation:navigation-compose:2.8.5")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.core:core-ktx:1.15.0")

    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")
    implementation("androidx.datastore:datastore-preferences:1.1.1")

    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")

    implementation("com.anthropic:anthropic-java:3.3.0")

    implementation(platform("com.google.firebase:firebase-bom:33.7.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
