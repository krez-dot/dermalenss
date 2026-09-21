import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
}

// Loaded from the gitignored local.properties so the key never lands in git history.
val localProperties = Properties().apply {
    val file = rootProject.file("local.properties")
    if (file.exists()) load(file.inputStream())
}
val mapsApiKey: String = localProperties.getProperty("MAPS_API_KEY", "")
val appsScriptUrl: String = localProperties.getProperty("APPS_SCRIPT_URL", "")
val contributionUploadSecret: String = localProperties.getProperty("CONTRIBUTION_UPLOAD_SECRET", "")
// The *Web* client ID from Firebase Console -> Authentication -> Sign-in method -> Google ->
// Web SDK configuration -- not an Android client ID. Credential Manager's Google ID flow signs
// requests against this one regardless of platform; see SETUP.md for the full walkthrough.
val googleWebClientId: String = localProperties.getProperty("GOOGLE_WEB_CLIENT_ID", "")

android {
    namespace = "com.dermalens.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.dermalens.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        manifestPlaceholders["MAPS_API_KEY"] = mapsApiKey
        buildConfigField("String", "MAPS_API_KEY", "\"$mapsApiKey\"")
        buildConfigField("String", "APPS_SCRIPT_URL", "\"$appsScriptUrl\"")
        buildConfigField("String", "CONTRIBUTION_UPLOAD_SECRET", "\"$contributionUploadSecret\"")
        buildConfigField("String", "GOOGLE_WEB_CLIENT_ID", "\"$googleWebClientId\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    androidResources {
        // TFLite loads its model via mmap; AAPT compressing the file breaks that.
        noCompress += "tflite"
    }
}

dependencies {
    // ── Core ──────────────────────────────────────────────────────────────
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)

    // ── Jetpack Compose ───────────────────────────────────────────────────
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation("androidx.compose.material:material-icons-extended")

    // ── Navigation ────────────────────────────────────────────────────────
    implementation("androidx.navigation:navigation-compose:2.7.7")

    // ── CameraX ───────────────────────────────────────────────────────────
    implementation("androidx.camera:camera-core:1.3.2")
    implementation("androidx.camera:camera-camera2:1.3.2")
    implementation("androidx.camera:camera-lifecycle:1.3.2")
    implementation("androidx.camera:camera-view:1.3.2")

    // Declared explicitly rather than leaned on as a camera-core transitive: every bitmap we
    // decode has to honour EXIF orientation or the model sees sideways photos (see ImageLoading.kt).
    implementation("androidx.exifinterface:exifinterface:1.3.7")

    // ── TensorFlow Lite ───────────────────────────────────────────────────
    implementation("org.tensorflow:tensorflow-lite:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-support:0.4.4")
    implementation("org.tensorflow:tensorflow-lite-gpu:2.14.0")
    implementation("org.tensorflow:tensorflow-lite-gpu-delegate-plugin:0.4.4")

    // ── Room DB ───────────────────────────────────────────────────────────
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // ── Coroutines ────────────────────────────────────────────────────────
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3")

    // ── WorkManager (scan reminders) ──────────────────────────────────────
    implementation("androidx.work:work-runtime-ktx:2.9.0")

    // ── Google Maps & Places ──────────────────────────────────────────────
    implementation("com.google.android.gms:play-services-maps:18.2.0")
    implementation("com.google.android.gms:play-services-location:21.2.0")
    implementation("com.google.maps.android:maps-compose:4.3.3")
    implementation("com.google.android.libraries.places:places:3.4.0")

    // ── ViewModel + LiveData ──────────────────────────────────────────────
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.7.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.7.0")

    // ── DataStore (user prefs) ────────────────────────────────────────────
    implementation("androidx.datastore:datastore-preferences:1.0.0")

    // ── Coil (image loading) ──────────────────────────────────────────────
    implementation("io.coil-kt:coil-compose:2.6.0")

    // ── Firebase (email/password auth for registered accounts only --
    // guest mode bypasses this entirely and stays on local Room DB) ────────
    // Note: intentionally not the newest BoM available -- 34.x's firebase-auth requires
    // Kotlin 2.3.0 metadata, but this project is pinned to Kotlin 2.0.21. 33.5.1 is the
    // newest version that's actually compatible with this project's Kotlin version.
    implementation(platform("com.google.firebase:firebase-bom:33.5.1"))
    implementation("com.google.firebase:firebase-auth")

    // ── Sign in with Google (Credential Manager) ─────────────────────────
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")

    // ── Testing ───────────────────────────────────────────────────────────
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
