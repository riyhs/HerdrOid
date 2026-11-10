plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.compose.compiler)
}

val releaseKeystore = providers.gradleProperty("herdroid.keystore").orNull
  ?: providers.environmentVariable("HERDROID_KEYSTORE").orNull
val releaseStorePassword = providers.gradleProperty("herdroid.keystorePassword").orNull
  ?: providers.environmentVariable("HERDROID_KEYSTORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("herdroid.keyAlias").orNull
  ?: providers.environmentVariable("HERDROID_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("herdroid.keyPassword").orNull
  ?: providers.environmentVariable("HERDROID_KEY_PASSWORD").orNull
val releaseSigningValues = listOf(releaseKeystore, releaseStorePassword, releaseKeyAlias, releaseKeyPassword)
val releaseSigningCount = releaseSigningValues.count { !it.isNullOrBlank() }
require(releaseSigningCount == 0 || releaseSigningCount == releaseSigningValues.size) {
  "Set all four release signing values, or none: HERDROID_KEYSTORE, " +
    "HERDROID_KEYSTORE_PASSWORD, HERDROID_KEY_ALIAS, HERDROID_KEY_PASSWORD."
}
val releaseSigningReady = releaseSigningCount == releaseSigningValues.size

val appVersionName = providers.gradleProperty("herdroid.versionName").orNull
  ?: providers.environmentVariable("HERDROID_VERSION_NAME").orNull
  ?: "0.1"
val appVersionCode = (providers.gradleProperty("herdroid.versionCode").orNull
  ?: providers.environmentVariable("HERDROID_VERSION_CODE").orNull)?.toIntOrNull() ?: 1

android {
  namespace = "dev.riyaldi.herdroid"
  compileSdk = 37
  defaultConfig {
    applicationId = "dev.riyaldi.herdroid"
    minSdk = 26
    targetSdk = 37
    versionCode = appVersionCode
    versionName = appVersionName
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  signingConfigs {
    if (releaseSigningReady) {
      create("release") {
        storeFile = rootProject.file(releaseKeystore!!)
        storePassword = releaseStorePassword
        keyAlias = releaseKeyAlias
        keyPassword = releaseKeyPassword
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
    create("benchmark") {
      initWith(getByName("release"))
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
      matchingFallbacks += listOf("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
  buildFeatures {
    compose = true
    aidl = false
    buildConfig = true
    shaders = false
  }

  packaging {
    jniLibs {
      useLegacyPackaging = false
    }
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
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
  implementation(libs.androidx.activity.compose)

  // Arch Components
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.viewmodel.compose)

  // Compose
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material.icons.core)

  // Instrumented tests
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  debugImplementation(libs.androidx.compose.ui.test.manifest)

  // Local tests
  testImplementation(libs.junit)

  // Instrumented tests: JUnit rules and runners
  androidTestImplementation(libs.androidx.test.core)
  androidTestImplementation(libs.androidx.test.ext.junit)
  androidTestImplementation(libs.androidx.test.runner)
  androidTestImplementation(libs.jsch)
  androidTestImplementation(libs.bcprov)

  // HerdrOid modules
  implementation(project(":core:domain"))
  implementation(project(":core:data"))
  androidTestImplementation(project(":core:ssh"))

  // Native terminal and coroutines
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.termlib)
}
