import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.room)
}

// Release signing (Phase 15): the upload key's details live in the gitignored keystore.properties
// (storeFile, storePassword, keyAlias, keyPassword). Without it, release builds come out unsigned.
val keystore = providers.fileContents(rootProject.layout.projectDirectory.file("keystore.properties")).asText.orNull
    ?.let { text -> Properties().apply { load(text.reader()) } }

// Cloud AI (Phase 20): where the backend is, from the gitignored cloud.properties (cloudUrl,
// cloudRegion, playCloudProjectNumber; see docs/CLOUD_API.md). Without it the app builds and its
// cloud features say they aren't set up in this build.
val cloud = providers.fileContents(rootProject.layout.projectDirectory.file("cloud.properties")).asText.orNull
    ?.let { text -> Properties().apply { load(text.reader()) } }

// versionCode counts commits, so every build from a newer commit can be uploaded over the last.
val commitCount = providers.exec {
    commandLine("git", "rev-list", "--count", "HEAD")
    isIgnoreExitValue = true
}.standardOutput.asText.map { it.trim().toIntOrNull() ?: 1 }

android {
    namespace = "com.savie.precut"
    compileSdk = 37

    defaultConfig {
        applicationId = "com.savie.precut"
        minSdk = 26
        targetSdk = 37
        versionCode = commitCount.get()
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "CLOUD_URL", "\"${cloud?.getProperty("cloudUrl").orEmpty()}\"")
        buildConfigField("String", "CLOUD_REGION", "\"${cloud?.getProperty("cloudRegion").orEmpty()}\"")
        buildConfigField("long", "PLAY_CLOUD_PROJECT_NUMBER", "${cloud?.getProperty("playCloudProjectNumber")?.toLongOrNull() ?: 0L}L")
    }

    signingConfigs {
        if (keystore != null) {
            create("release") {
                storeFile = rootProject.file(keystore.getProperty("storeFile"))
                storePassword = keystore.getProperty("storePassword")
                keyAlias = keystore.getProperty("keyAlias")
                keyPassword = keystore.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.findByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

composeCompiler {
    // `./gradlew :app:compileDebugKotlin -PcomposeReports` writes stability reports and metrics to
    // app/build/compose_compiler (used for the performance pass; off for normal builds).
    if (project.hasProperty("composeReports")) {
        reportsDestination = layout.buildDirectory.dir("compose_compiler")
        metricsDestination = layout.buildDirectory.dir("compose_compiler")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.androidx.navigation.compose)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.androidx.hilt.lifecycle.viewmodel.compose)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    implementation(libs.kotlinx.serialization.json)

    implementation(libs.okhttp)
    implementation(libs.play.integrity)
    implementation(libs.play.app.update)
    implementation(libs.play.billing)
    // Remove background (Phase 22A): runs MediaPipe's selfie segmenter on the phone.
    implementation(libs.litert)

    implementation(libs.coil.compose)
    implementation(libs.coil.video)

    implementation(libs.androidx.media3.exoplayer)
    implementation(libs.androidx.media3.transformer)
    implementation(libs.androidx.media3.effect)
    implementation(libs.androidx.media3.ui.compose)

    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    // Compose's test library brings Espresso 3.5, which can't drive Android 16+ (InputManager changed).
    androidTestImplementation(libs.androidx.test.espresso.core)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.okhttp.mockwebserver)
}
