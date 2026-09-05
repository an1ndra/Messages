plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.anindra.messages"
    compileSdk = 35

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    defaultConfig {
        applicationId = "com.anindra.messages"
        minSdk = 29
        targetSdk = 35
        versionCode = 23
        versionName = "1.0.20"
    }

    signingConfigs {
        create("release") {
            val ksFile = file("${rootProject.projectDir}/release.keystore")
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
            val ksFile = file("${rootProject.projectDir}/release.keystore")
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
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
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
    debugImplementation(libs.androidx.ui.tooling)
    testImplementation("junit:junit:4.13.2")
}
