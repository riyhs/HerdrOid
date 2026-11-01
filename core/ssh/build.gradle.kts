plugins {
  alias(libs.plugins.android.library)
}

android {
  namespace = "dev.riyaldi.herdroid.ssh"
  compileSdk = 37

  defaultConfig {
    minSdk = 26
  }

  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
  }

  packaging {
    resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
  }
}

kotlin {
  jvmToolchain(17)
}

dependencies {
  implementation(libs.jsch)
  implementation(libs.bcprov)
  testImplementation(libs.junit)
}
