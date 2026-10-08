plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.anindra.messages"
    compileSdk = 36

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.anindra.messages"
        minSdk = 29
        targetSdk = 36
        versionCode = 30
        versionName = "1.0.27"
    }

    // RELEASE_KEYSTORE lets a throwaway key stand in for a side build without
    // swapping the real release.keystore out of the way.
    val releaseKeystore = file(System.getenv("RELEASE_KEYSTORE") ?: "${rootProject.projectDir}/release.keystore")

    signingConfigs {
        create("release") {
            val ksFile = releaseKeystore
            if (ksFile.exists()) {
                val storePass = System.getenv("KEYSTORE_PASSWORD")
                val keyPass = System.getenv("KEY_PASSWORD")
                if (storePass != null && keyPass != null) {
                    storeFile = ksFile
                    storePassword = storePass
                    keyAlias = "messages"
                    keyPassword = keyPass
                }
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val ksFile = releaseKeystore
            signingConfig = if (ksFile.exists() &&
                System.getenv("KEYSTORE_PASSWORD") != null &&
                System.getenv("KEY_PASSWORD") != null
            ) {
                signingConfigs.getByName("release")
            } else {
                null
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    testOptions {
        // android.util.Log and friends throw in local unit tests, which makes
        // any code that only logs a problem untestable. Returning defaults keeps
        // the logging (which is how import problems get diagnosed on a device)
        // without needing a Robolectric runner for pure-logic tests.
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    implementation(project(":mms"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.work.runtime)
    implementation("androidx.biometric:biometric:1.1.0")
    implementation("androidx.fragment:fragment-ktx:1.6.2")
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.libphone)
    implementation(libs.coil.compose)
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")

    // Test-only: production still sends through the vendored stack. This is
    // what lets the interop test prove the two stacks read each other's PDUs
    // before the app is switched over to :mms.
    testImplementation(project(":mms"))
    // Real org.json on the unit-test classpath; the android.jar stubs throw.
    testImplementation("org.json:json:20240303")
}
