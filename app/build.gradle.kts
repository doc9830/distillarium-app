import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val contentManifestUrl = providers
    .gradleProperty("distillarium.contentManifestUrl")
    .orElse("https://github.com/doc9830/distillarium-content/releases/download/content-latest/manifest.json")
    .get()

val appManifestUrl = providers
    .gradleProperty("distillarium.appManifestUrl")
    .orElse("https://github.com/doc9830/distillarium-app/releases/download/app-latest/app-manifest.json")
    .get()

val appVersionName = Properties().apply {
    layout.projectDirectory.file("version.properties").asFile.inputStream().use(::load)
}.getProperty("versionName")?.trim()
    ?: error("app/version.properties: не задан versionName")

val appVersionMatch = Regex("""^(\d+)\.(\d+)\.(\d+)$""").matchEntire(appVersionName)
    ?: error("app/version.properties: versionName должен быть вида 1.0.0")
val (appMajor, appMinor, appPatch) = appVersionMatch.destructured
require(appMinor.toInt() in 0..999) { "versionName: minor должен быть от 0 до 999" }
require(appPatch.toInt() in 0..999) { "versionName: patch должен быть от 0 до 999" }
val appVersionCode = appMajor.toInt() * 1_000_000 + appMinor.toInt() * 1_000 + appPatch.toInt()
require(appVersionCode > 0) { "versionName: versionCode должен быть положительным" }

val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.isFile) {
        keystorePropertiesFile.inputStream().use(::load)
    }
}

fun String.asBuildConfigString(): String =
    "\"" + replace("\\", "\\\\").replace("\"", "\\\"") + "\""

android {
    namespace = "ru.distillarium"
    compileSdk = 36
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "ru.distillarium"
        minSdk = 26
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersionName

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "CONTENT_MANIFEST_URL", contentManifestUrl.asBuildConfigString())
        buildConfigField("String", "APP_UPDATE_MANIFEST_URL", appManifestUrl.asBuildConfigString())
    }

    signingConfigs {
        if (keystorePropertiesFile.isFile) {
            create("release") {
                storeFile = rootProject.file(
                    keystoreProperties.getProperty("storeFile")
                        ?: error("keystore.properties: не задан storeFile"),
                )
                storePassword = keystoreProperties.getProperty("storePassword")
                    ?: error("keystore.properties: не задан storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                    ?: error("keystore.properties: не задан keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                    ?: error("keystore.properties: не задан keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("release")
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

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
    }

    lint {
        abortOnError = true
        warningsAsErrors = false
    }
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(project(":core:content"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp)

    implementation(libs.commonmark)
    implementation(libs.commonmark.gfm.tables)

    debugImplementation(libs.androidx.compose.ui.tooling)

    testImplementation(libs.junit)
}
