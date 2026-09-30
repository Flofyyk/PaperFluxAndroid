plugins { id("com.android.application") }

android {
    namespace = "com.paperflux.speedprobe"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.paperflux.speedprobe"
        minSdk = 26
        targetSdk = 35
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}
