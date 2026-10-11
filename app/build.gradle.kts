plugins {
    id("com.android.application")
}

// Release key from the environment (GitHub secrets on CI); local builds use the debug key
val releaseKeystore = System.getenv("MIIPTV_KEYSTORE")?.let { file(it) }?.takeIf { it.exists() }

android {
    namespace = "es.mitv.iptv"
    compileSdk = 35

    defaultConfig {
        applicationId = "es.mitv.iptv"
        minSdk = 21
        targetSdk = 35
        versionCode = 6
        versionName = "1.4.0"
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = releaseKeystore
                storePassword = System.getenv("MIIPTV_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("MIIPTV_KEY_ALIAS")
                keyPassword = System.getenv("MIIPTV_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            // Without a release key (local builds) the debug key is fine for sideloading on your own TV
            signingConfig = signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    val media3 = "1.6.1"
    implementation("androidx.media3:media3-exoplayer:$media3")
    implementation("androidx.media3:media3-exoplayer-hls:$media3")
    implementation("androidx.media3:media3-ui:$media3")
    implementation("androidx.annotation:annotation:1.9.1")
    implementation("com.google.zxing:core:3.5.3")
    // Bundled FFmpeg audio decoder (Dolby AC-3 / E-AC-3, MP2...) for channels the TV cannot decode
    implementation("org.jellyfin.media3:media3-ffmpeg-decoder:1.6.1+1")

    testImplementation("junit:junit:4.13.2")
}
