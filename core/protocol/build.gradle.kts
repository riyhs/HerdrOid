plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "dev.riyaldi.herdroid.protocol"
  compileSdk = 37

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.kotlinx.coroutines.android)
  testImplementation(libs.junit)
}
