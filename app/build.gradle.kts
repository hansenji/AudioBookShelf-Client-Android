import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.koin.compiler)
  alias(libs.plugins.ksp)
}


android {
    namespace = "dev.vikingsen.skald"
    compileSdk = 37
    defaultConfig {
        applicationId = "dev.vikingsen.skald"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "1.0.0-2"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    val keystorePropertiesFile = rootProject.file("keystore.properties")
    val keystoreProperties = Properties()
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { keystoreProperties.load(it) }
    }

    signingConfigs {
        create("release") {
            val storeFilePath = keystoreProperties.getProperty("storeFile")
            if (storeFilePath != null) {
                storeFile = rootProject.file(storeFilePath)
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
        }
        debug {
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    testOptions {
        unitTests {
            isReturnDefaultValues = true
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
      compose = true
      aidl = false
      buildConfig = false
      shaders = false
    }

    packaging {
      resources {
        excludes += "/META-INF/{AL2.0,LGPL2.1}"
        excludes += "/META-INF/INDEX.LIST"
        excludes += "/META-INF/io.netty.versions.properties"
        excludes += "/META-INF/LICENSE.md"
        excludes += "/META-INF/LICENSE-notice.md"
      }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
  val composeBom = platform(libs.androidx.compose.bom)
  implementation(composeBom)
  androidTestImplementation(composeBom)

  // Core Android dependencies
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.core.splashscreen)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.adaptive.navigation.suite)
  implementation(libs.androidx.compose.material.icons.extended)
  // Tooling
  debugImplementation(libs.androidx.compose.ui.tooling)
  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests: jUnit, coroutines, Android runner
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.mockk)
  testImplementation(libs.ktor.client.mock)

  // Instrumented tests: jUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.androidx.test.espresso.core)

  // Navigation
  implementation(libs.androidx.navigation3.ui)
  implementation(libs.androidx.navigation3.runtime)
  implementation(libs.androidx.lifecycle.viewmodel.navigation3)

  // Koin DI
  implementation(libs.koin.android)
  implementation(libs.koin.androidx.compose)

  // Coil 3 & Ktor Network Fetcher
  implementation(libs.coil.compose)
  implementation(libs.coil.network.ktor3)

  // Subproject Modules
  implementation(project(":core:model"))
  implementation(project(":core:preferences"))
  implementation(project(":core:database"))
  implementation(project(":core:network"))
  implementation(project(":core:player"))
  implementation(project(":domain"))
  implementation(project(":data"))
  implementation(project(":feature:login:api"))
  implementation(project(":feature:login:impl"))
  implementation(project(":feature:home:api"))
  implementation(project(":feature:home:impl"))
  implementation(project(":feature:library:api"))
  implementation(project(":feature:library:impl"))
  implementation(project(":feature:player:api"))
  implementation(project(":feature:player:impl"))
  implementation(project(":feature:settings:api"))
  implementation(project(":feature:settings:impl"))
  implementation(project(":feature:androidauto"))
  implementation(project(":feature:miniplayer"))

  // AppFunctions
  implementation(libs.androidx.appfunctions)
  implementation(libs.androidx.appfunctions.service)
  ksp(libs.androidx.appfunctions.compiler)
}

ksp {
  arg("appfunctions:aggregateAppFunctions", "true")
}
