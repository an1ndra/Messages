plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.anindra.messages.mms"
    compileSdk = 36

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    // The PDU and SMIL layers are deliberately free of android.* imports so they
    // run under plain JUnit; android.util.Log would otherwise throw in unit
    // tests and force a Robolectric runner on code that needs none.
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.androidx.work.runtime)

    testImplementation("junit:junit:4.13.2")
}
