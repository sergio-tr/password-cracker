import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.compose.compiler)
}

val localProperties =
    Properties().apply {
        val file = rootProject.file("local.properties")
        if (file.exists()) {
            file.inputStream().use { load(it) }
        }
    }

fun localProp(key: String): String = localProperties.getProperty(key, "").orEmpty()

fun escapeBuildConfigString(value: String): String =
    value
        .replace("\\", "\\\\")
        .replace("\"", "\\\"")

android {
    namespace = "com.wifiauditlab.android"
    compileSdk = libs.versions.androidCompileSdk.get().toInt()

    defaultConfig {
        applicationId = "com.wifiauditlab.android"
        minSdk = libs.versions.androidMinSdk.get().toInt()
        targetSdk = libs.versions.androidTargetSdk.get().toInt()
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    flavorDimensions += "audience"
    productFlavors {
        create("standard") {
            dimension = "audience"
            isDefault = true
            buildConfigField("boolean", "REAL_WIFI_PROBE_ENABLED", "false")
            buildConfigField("String", "LAB_WIFI_SSID", "\"\"")
            buildConfigField("String", "LAB_WIFI_BSSID", "\"\"")
            buildConfigField("String", "LAB_WIFI_SECURITY", "\"WPA2_PERSONAL\"")
        }
        create("lab") {
            dimension = "audience"
            applicationIdSuffix = ".lab"
            versionNameSuffix = "-lab"
            // Fixed lab AP identity from local.properties (never a password).
            val ssid = escapeBuildConfigString(localProp("LAB_WIFI_SSID"))
            val bssid = escapeBuildConfigString(localProp("LAB_WIFI_BSSID"))
            val security =
                escapeBuildConfigString(
                    localProp("LAB_WIFI_SECURITY").ifBlank { "WPA2_PERSONAL" },
                )
            buildConfigField("boolean", "REAL_WIFI_PROBE_ENABLED", "true")
            buildConfigField("String", "LAB_WIFI_SSID", "\"$ssid\"")
            buildConfigField("String", "LAB_WIFI_BSSID", "\"$bssid\"")
            buildConfigField("String", "LAB_WIFI_SECURITY", "\"$security\"")
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
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
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(projects.shared.core)
    implementation(projects.shared.assessment)
    implementation(projects.shared.lab)
    implementation(projects.shared.persistence)

    implementation(libs.kotlin.coroutines.core)

    implementation(libs.sqldelight.android.driver)

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.navigation.compose)

    implementation(libs.koin.android)
    implementation(libs.koin.compose)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    testImplementation(libs.junit)
    testImplementation(libs.kotlin.coroutines.test)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.junit)
    androidTestImplementation(libs.kotlin.coroutines.test)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.activity.compose)
}
