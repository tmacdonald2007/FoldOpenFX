plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
android {
    namespace = "com.tmacdonald2007.foldopenfx"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.tmacdonald2007.foldopenfx"
        minSdk = 30
        targetSdk = 35
        versionCode = 4
        versionName = "0.4.2"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies { implementation("androidx.core:core-ktx:1.15.0") }
