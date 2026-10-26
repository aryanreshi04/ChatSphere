import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")
}

// Maps Android keys are public client identifiers: restrict package/SHA-1 and Maps SDK API.
val mapsProperties=Properties().apply {
    val config=rootProject.file("maps.properties")
    if(config.exists())config.inputStream().use { load(it) }
}
val mapsApiKey=providers.environmentVariable("CHATSPHERE_MAPS_API_KEY").orNull
    ?: mapsProperties.getProperty("MAPS_API_KEY", "")

val useFirebaseEmulators = providers.gradleProperty("firebaseEmulators").map { it.toBoolean() }.getOrElse(false)

android {
    namespace = "com.jos.myapplication"

    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = if (useFirebaseEmulators) "com.jos.myapplication.demo" else "com.jos.myapplication"
        buildConfigField("boolean", "USE_FIREBASE_EMULATORS", useFirebaseEmulators.toString())
        buildConfigField("boolean", "GOOGLE_MAPS_CONFIGURED", mapsApiKey.isNotBlank().toString())
        manifestPlaceholders["MAPS_API_KEY"]=mapsApiKey
        minSdk = 28
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
}

if (useFirebaseEmulators) {
    // A separate demo application uses programmatic fake Firebase options; never production credentials.
    tasks.matching { it.name.endsWith("GoogleServices") }.configureEach { enabled = false }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)

    // Firebase
    implementation(platform("com.google.firebase:firebase-bom:34.18.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-messaging")
    // Bundled real on-device English model: available without a runtime model download.
    implementation("com.google.mlkit:smart-reply:17.0.4")
    implementation("androidx.work:work-runtime-ktx:2.10.0")

    implementation("com.squareup.okhttp3:okhttp:5.5.1")

    implementation("com.google.android.gms:play-services-maps:20.0.0")

    // Navigation
    implementation("androidx.navigation:navigation-compose:2.8.4")

    // Material icons
    implementation("androidx.compose.material:material-icons-extended:1.7.5")

    // Tests
    testImplementation(libs.junit)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
