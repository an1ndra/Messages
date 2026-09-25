plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.klinker.android.send_message"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
    }

    useLibrary("org.apache.http.legacy")

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation("com.jakewharton.timber:timber:5.0.1")
}
