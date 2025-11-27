plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    id("com.chaquo.python")
}

android {
    namespace = "com.rhino.voiceauthenticatorxpython"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.rhino.voiceauthenticatorxpython"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        
        // Cấu hình Python/Chaquopy
        ndk {
            abiFilters += listOf("arm64-v8a", "x86_64")
        }
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
    kotlinOptions {
        jvmTarget = "11"
    }
}

// Cấu hình Python với Chaquopy
chaquopy {
    defaultConfig {
        version = "3.10"  // change below path to actual path to your system's python 3.10 (must be 3.10)
        buildPython("C:/Users/Monkey/AppData/Local/Programs/Python/Python310/python.exe")

        pip {
            install("scipy")  // Thử cài scipy trước để có OpenBLAS
            install("scikit-learn")
            install("python-speech-features==0.6")
        }
    }
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}