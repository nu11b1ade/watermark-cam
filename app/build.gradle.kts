plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}
android {
  namespace = "com.nu11b1ade.watermarkcam"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.nu11b1ade.watermarkcam"
    minSdk = 26
    targetSdk = 37
    versionCode = 1
    versionName = "0.1.0"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }
  buildFeatures { compose = true }
  testOptions { unitTests.isIncludeAndroidResources = true }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
  packaging {
    resources {
      excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
  }
}
dependencies {
  val bom = platform("androidx.compose:compose-bom:2026.09.00")
  implementation(bom)
  implementation("androidx.activity:activity-compose:1.13.0")
  implementation("androidx.compose.material3:material3")
  implementation("androidx.compose.material:material-icons-extended")
  implementation("androidx.compose.ui:ui")
  implementation("androidx.compose.ui:ui-tooling-preview")
  debugImplementation("androidx.compose.ui:ui-tooling")
  implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.4")
  implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.4")
  implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.4")
  implementation("androidx.navigation:navigation-compose:2.9.5")
  implementation("androidx.camera:camera-camera2:1.6.2")
  implementation("androidx.camera:camera-lifecycle:1.6.2")
  implementation("androidx.camera:camera-view:1.6.2")
  implementation("androidx.core:core-ktx:1.17.0")
  implementation("androidx.exifinterface:exifinterface:1.4.1")
  implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test:rules:1.7.0")
  androidTestImplementation("androidx.test:core:1.7.0")
}
