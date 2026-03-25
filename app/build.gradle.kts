plugins {
    alias(libs.plugins.android.application)
    id("com.google.android.gms.oss-licenses-plugin") version "0.11.0"
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
        versionCode = 6
        versionName = "0.5-alpha"
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
    implementation("io.github.kyant0:taglib:1.0.5")
    implementation(libs.play.services.oss.licenses)
}