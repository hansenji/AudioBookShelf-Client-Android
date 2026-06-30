plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.compose.compiler)
    alias(libs.plugins.koin.compiler)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "dev.vikingsen.skald.feature.home"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

dependencies {
    implementation(project(":feature:home:api"))
    implementation(project(":domain"))
    implementation(project(":core:model"))
    implementation(project(":core:preferences"))
    implementation(project(":core:player"))
    
    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)
    implementation(libs.koin.androidx.compose)
    
    // Coil for displaying book/podcast covers
    implementation(libs.coil.compose)
    
    // Kotlin Serialization
    implementation(libs.ktor.serialization.kotlinx.json)

    testImplementation(testFixtures(project(":domain")))
    testImplementation(libs.androidx.compose.ui.test.junit4)
}

