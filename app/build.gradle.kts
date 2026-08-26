import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.hilt.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

val localPropertiesFile = rootProject.layout.projectDirectory.file("local.properties")
val localProperties = Properties().apply {
    providers.fileContents(localPropertiesFile)
        .asText
        .orNull
        ?.reader()
        ?.use(::load)
}

val sciChartLicenseKey = providers
    .environmentVariable("SCICHART_LICENSE_KEY")
    .orElse(localProperties.getProperty("SCICHART_LICENSE_KEY").orEmpty())
    .get()

fun String.asBuildConfigString(): String = buildString {
    append('"')
    for (character in this@asBuildConfigString) {
        when (character) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            else -> append(character)
        }
    }
    append('"')
}

android {
    namespace = "com.snn.scichart"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.snn.scichart"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField(
            type = "String",
            name = "SCICHART_LICENSE_KEY",
            value = sciChartLicenseKey.asBuildConfigString(),
        )
        buildConfigField(type = "long", name = "POINT_INTERVAL_MILLIS", value = "1_000L")
    }

    buildTypes {
        release {
            optimization {
                enable = true
            }
        }
        create("benchmark") {
            initWith(getByName("release"))
            signingConfig = signingConfigs.getByName("debug")
            matchingFallbacks += listOf("release")
            isDebuggable = false
            buildConfigField(type = "long", name = "POINT_INTERVAL_MILLIS", value = "50L")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.hilt.android)
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.scichart.core)
    implementation(libs.scichart.data)
    implementation(libs.scichart.drawing)
    implementation(libs.scichart.charting)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
