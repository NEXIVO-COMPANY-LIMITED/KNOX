plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.dagger.hilt.android")
    id("com.google.gms.google-services")
}

android {
    namespace = "com.mdm.dpc"
    // minSdk/targetSdk/compileSdk left as explicit placeholders — plan §32 open question #2:
    // confirm the org's actual minimum supported Android OS version before finalizing.
    compileSdk = 34

    defaultConfig {
        applicationId = "com.mdm.dpc"
        minSdk = 26   // PLACEHOLDER — Device Owner + all §6.1 DPM APIs used here require this or higher;
                       // raise per the org's actual fleet policy (see plan §32 Q2).
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false // enable + configure R8 rules before any real release build
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { viewBinding = true }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")

    // DI
    implementation("com.google.dagger:hilt-android:2.51.1")
    ksp("com.google.dagger:hilt-android-compiler:2.51.1")

    // Networking (§13 device<->backend REST client)
    implementation("com.squareup.retrofit2:retrofit:2.11.0")
    implementation("com.squareup.retrofit2:converter-moshi:2.11.0")
    implementation("com.squareup.okhttp3:logging-interceptor:4.12.0")
    implementation("com.squareup.moshi:moshi-kotlin:1.15.1")

    // Local storage (§7.3 `storage/` — cached policy/commands/config)
    implementation("androidx.room:room-runtime:2.6.1")
    implementation("androidx.room:room-ktx:2.6.1")
    ksp("androidx.room:room-compiler:2.6.1")

    // Keystore-backed encrypted storage for device credentials (§6, §18 identity/security)
    implementation("androidx.security:security-crypto:1.1.0-alpha06")

    // Background sync (§13/§19 WorkManager-driven pull cycle)
    implementation("androidx.work:work-runtime-ktx:2.9.1")

    // FCM wake trigger (§13)
    implementation("com.google.firebase:firebase-messaging-ktx:24.0.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
