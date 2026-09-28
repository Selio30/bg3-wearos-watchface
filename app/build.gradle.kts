plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.bg3.watchface"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.bg3.watchface"
        minSdk = 30
        targetSdk = 34
        versionCode = 1
        versionName = "1.0.0"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("debug") // Allows direct adb testing
        }
        debug {
            applicationIdSuffix = ".debug"
            isDebuggable = true
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        viewBinding = false
        compose = false
    }
}

dependencies {
    // Pure Watch Face Format (WFF) uses no runtime executable code or Kotlin/Java dependencies
}
