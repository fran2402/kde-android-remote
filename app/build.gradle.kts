import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}

/** The newest version of [artifact] in the unzipped Home APIs SDK under home-sdk/, if present. */
fun homeSdkVersion(artifact: String): String? = rootProject.file("home-sdk").takeIf { it.isDirectory }
    ?.walkTopDown()
    ?.firstOrNull { it.isDirectory && it.invariantSeparatorsPath.endsWith("com/google/android/gms/$artifact") }
    ?.listFiles { f -> f.isDirectory }
    ?.map { it.name }
    ?.maxWithOrNull(compareBy<String>({ it.substringBefore('.').toIntOrNull() ?: 0 }, { it }))

val homeVersion = homeSdkVersion("play-services-home")
val homeTypesVersion = homeSdkVersion("play-services-home-types") ?: homeVersion

android {
    namespace = "io.github.fran2402.kderemote"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.fran2402.kderemote"
        // The Google Home APIs need Android 10.
        minSdk = 29
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
    }

    // Google sign-in for the Home tab is tied to the signing key's SHA-1, so CI signs with a fixed
    // key from the SIGNING_KEYSTORE / SIGNING_PASSWORD secrets when they are set.
    val stableKeystore = System.getenv("SIGNING_KEYSTORE_FILE")?.let { file(it) }?.takeIf { it.exists() }
    signingConfigs {
        if (stableKeystore != null) {
            create("stable") {
                storeFile = stableKeystore
                storePassword = System.getenv("SIGNING_PASSWORD")
                keyAlias = "kde-remote"
                keyPassword = System.getenv("SIGNING_PASSWORD")
            }
        }
    }

    buildTypes {
        debug {
            signingConfigs.findByName("stable")?.let { signingConfig = it }
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            // Personal sideloaded app: without the stable key, sign with the debug key so it still installs.
            signingConfig = signingConfigs.findByName("stable") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets {
        getByName("main") {
            java.srcDir(if (homeVersion != null) "src/googleHome/java" else "src/noGoogleHome/java")
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources {
            excludes += setOf("META-INF/versions/9/OSGI-INF/MANIFEST.MF", "META-INF/{AL2.0,LGPL2.1}")
        }
    }

    lint {
        // KDE Connect pins certificates itself after pairing (see LocalIdentity).
        disable += setOf("TrustAllX509TrustManager", "CustomX509TrustManager")
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2025.10.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.activity:activity-compose:1.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.bouncycastle:bcpkix-jdk18on:1.81")
    if (homeVersion != null) {
        implementation("com.google.android.gms:play-services-home:$homeVersion")
        implementation("com.google.android.gms:play-services-home-types:$homeTypesVersion")
    }
    debugImplementation("androidx.compose.ui:ui-tooling")
}
