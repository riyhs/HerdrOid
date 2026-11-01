plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "dev.riyaldi.herdroid.data"
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
  implementation(project(":core:domain"))
  implementation(project(":core:protocol"))
  implementation(project(":core:ssh"))
  implementation(project(":core:mirror"))

  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.serialization.json)
  testImplementation(libs.junit)
}
