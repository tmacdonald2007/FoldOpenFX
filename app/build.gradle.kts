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
        versionCode = 1
        versionName = "0.1.0"
    }
}
