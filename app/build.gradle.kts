plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }

android { namespace = "com.accar.openflux"; compileSdk = 35
    defaultConfig {
        applicationId = "com.accar.openflux"
        minSdk = 26
        targetSdk = 35
        versionCode = 45
        versionName = "0.4.27"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += listOf("armeabi-v7a", "arm64-v8a", "x86", "x86_64") }
    }
    buildFeatures { buildConfig = true }
    splits {
        abi {
            isEnable = true
            reset()
            include("armeabi-v7a", "arm64-v8a", "x86", "x86_64")
            isUniversalApk = true
        }
    }
    // The old isolated-worker binary is unused by the single-process Session
    // implementation. Do not ship it in every APK.
    packaging {
        jniLibs.useLegacyPackaging = true
        jniLibs.excludes += "**/libopenflux-worker.so"
    }
    buildTypes {
        getByName("debug") {
            buildConfigField("String", "RELAY_TOKEN", "\"${project.findProperty("relayToken") ?: ""}\"")
        }
        getByName("release") {
            // Keep the existing installation certificate so updates preserve
            // profiles. Release disables debugging; never distribute this key.
            signingConfig = signingConfigs.getByName("debug")
            isDebuggable = false
            isMinifyEnabled = false
            buildConfigField("String", "RELAY_TOKEN", "\"\"")
        }
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.activity:activity-ktx:1.9.3")
    implementation("androidx.appcompat:appcompat:1.7.0")
    implementation("androidx.webkit:webkit:1.12.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.journeyapps:zxing-android-embedded:4.3.0")
}

// Always package the UI from its build output at the path loaded by WebView.
val npmCommand = if (System.getProperty("os.name").startsWith("Windows")) listOf("cmd", "/c", "npm") else listOf("npm")
val installPaperFluxWeb by tasks.registering(Exec::class) {
    workingDir(rootProject.file("web"))
    commandLine(npmCommand + "ci")
    inputs.files(rootProject.file("web/package.json"), rootProject.file("web/package-lock.json"))
    outputs.dir(rootProject.file("web/node_modules"))
}
val buildPaperFluxWeb by tasks.registering(Exec::class) {
    dependsOn(installPaperFluxWeb)
    workingDir(rootProject.file("web"))
    commandLine(npmCommand + listOf("run", "build"))
}
val syncPaperFluxWeb by tasks.registering(Copy::class) {
    dependsOn(buildPaperFluxWeb)
    from(rootProject.file("web/dist/index.html"))
    into(layout.projectDirectory.dir("src/main/assets/paperflux"))
}
tasks.named("preBuild") { dependsOn(syncPaperFluxWeb) }
