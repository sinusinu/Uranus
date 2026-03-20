plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "kr.pe.sinu.uranus"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "kr.pe.sinu.uranus"
        minSdk = 24
        targetSdk = 36
        versionCode = 3
        versionName = "0.3-alpha"
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
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
    androidResources {
        @Suppress("UnstableApiUsage")
        localeFilters += listOf("en", "ko")
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    implementation(libs.activity)
    implementation(libs.constraintlayout)
    implementation(libs.viewpager2)
    implementation(libs.media3.exoplayer)
    implementation(libs.media3.session)
    implementation(libs.media3.ui)
    implementation(libs.recyclerview)
    implementation(libs.documentfile)
}