plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.koin.compiler)
}


android {
    namespace = "dev.vikingsen.skald.domain"
    compileSdk = 36
    defaultConfig {
        minSdk = 26
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    testFixtures {
        enable = true
    }
}


dependencies {
    implementation(project(":core:model"))
    implementation(libs.androidx.paging.common)
    
    // Coroutines and Core APIs
    implementation(libs.androidx.core.ktx)
    implementation(libs.koin.android)

    testFixturesApi(project(":core:model"))
    testFixturesApi(libs.androidx.paging.common)
    testFixturesImplementation(libs.kotlinx.coroutines.test)
}

