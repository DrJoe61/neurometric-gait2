plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("org.jetbrains.kotlin.plugin.serialization") version "2.2.10"
}

android {
    namespace = "com.brainsherpa.neurogait"
    compileSdk = 36

    defaultConfig {
        // Package must match the Google OAuth Android client registered in the
        // NeuroMetric MRP Google Cloud project (com.proneurolight.neurometric.pace + debug SHA-1).
        applicationId = "com.proneurolight.neurometric.pace"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Shared NeuroMetric Suite Web Client ID (validates the Google token in Supabase).
        buildConfigField(
            "String",
            "GOOGLE_WEB_CLIENT_ID",
            "\"227288305084-q8co1b48iee932bqlkbjksq7siu9k4i5.apps.googleusercontent.com\""
        )
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
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes.add("/META-INF/AL2.0")
            excludes.add("/META-INF/LGPL2.1")
            excludes.add("/META-INF/rxjava.properties")
            excludes.add("/META-INF/DEPENDENCIES")
            excludes.add("/META-INF/INDEX.LIST")
        }
    }
}

dependencies {
    // Android core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    implementation(libs.androidx.activity)
    implementation(libs.androidx.constraintlayout)

    // Movesense MDS library (mdslib 3.33.7) + its RxAndroidBle2 transport.
    // 3.x is built on RxJava 2 (io.reactivex.*) and RxAndroidBle v2, and ships arm64.
    implementation(files("libs/mdslib.aar"))
    implementation("com.polidea.rxandroidble2:rxandroidble:1.17.2")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")
    implementation("io.reactivex.rxjava2:rxandroid:2.1.1")

    // Gson for JSON parsing (kept for data structures if needed)
    implementation("com.google.code.gson:gson:2.10.1")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")

    // --- NeuroMetric Suite: Supabase + Google Sign-In (same stack as DFa1) ---
    implementation("androidx.credentials:credentials:1.5.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.5.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation(platform("io.github.jan-tennert.supabase:bom:3.1.4"))
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:auth-kt")
    implementation("io.ktor:ktor-client-android:3.1.0")

    // Testing
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}
