plugins {
  alias(libs.plugins.android.test)
}

android {
  namespace = "dev.riyaldi.herdroid.benchmark"
  compileSdk = 37
  defaultConfig {
    minSdk = 26
    targetSdk = 37
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  targetProjectPath = ":app"
  experimentalProperties["android.experimental.self-instrumenting"] = true
  buildTypes {
    create("benchmark") {
      signingConfig = signingConfigs.getByName("debug")
      isDebuggable = false
      isMinifyEnabled = false
      matchingFallbacks += listOf("release")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }
}

kotlin {
  jvmToolchain(17)
}

tasks.matching { it.name.startsWith("checkTestedAppObfuscation") }.configureEach {
  enabled = false
}

dependencies {
  implementation(libs.kotlin.stdlib)
  implementation(libs.androidx.test.core)
  implementation(libs.androidx.test.ext.junit)
  implementation(libs.androidx.test.runner)
  implementation(libs.androidx.benchmark.macro.junit4)
  implementation(libs.androidx.uiautomator)
  implementation(libs.androidx.profileinstaller)
  implementation(libs.androidx.tracing)
}
