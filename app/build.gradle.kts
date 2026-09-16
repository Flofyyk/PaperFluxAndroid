plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.accar.openflux"; compileSdk = 35
    defaultConfig {
        applicationId = "com.accar.openflux"
        minSdk = 26
        targetSdk = 35
        versionCode = 10
        versionName = "0.4.6"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += "arm64-v8a" }
    }
    buildFeatures { buildConfig = true }
    buildTypes { getByName("debug") {
        buildConfigField("String", "RELAY_TOKEN", "\"${project.findProperty("relayToken") ?: ""}\"")
    } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}

// Always package the UI from its build output at the path loaded by WebView.
val buildPaperFluxWeb by tasks.registering(Exec::class) {
    workingDir(rootProject.file("../paperflux-design-20260912"))
    commandLine("cmd", "/c", "npm run build")
}
val syncPaperFluxWeb by tasks.registering(Copy::class) {
    dependsOn(buildPaperFluxWeb)
    from(rootProject.file("../paperflux-design-20260912/dist/index.html"))
    into(layout.projectDirectory.dir("src/main/assets/paperflux"))
}
tasks.named("preBuild") { dependsOn(syncPaperFluxWeb) }
