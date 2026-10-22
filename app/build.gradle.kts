import java.util.Properties

plugins {
  id("com.android.application")
  id("org.jetbrains.kotlin.plugin.compose")
}

// Inject only into the manifest. Never put the value in BuildConfig or build output.
val amapKey = providers.environmentVariable("WMCAM_AMAP_KEY").orNull?.trim()?.takeIf { it.isNotEmpty() } ?: run {
  val file = File(System.getProperty("user.home"), "keys/watermark-cam-amap.properties")
  if (!file.isFile) "" else runCatching {
    Properties().apply { file.inputStream().use { load(it) } }.getProperty("amapKey", "").trim()
  }.getOrDefault("")
}

// Release signing: read from ~/keys/watermark-cam-keystore.properties (keys: storeFile,
// storePassword, keyAlias, keyPassword) or env vars WMCAM_KEYSTORE, WMCAM_STORE_PASSWORD,
// WMCAM_KEY_ALIAS, WMCAM_KEY_PASSWORD. Key material never lives in this repo.
// If neither is available the release build is left unsigned; debug builds are unaffected.
val releaseSigning: Map<String, String>? = run {
  val env = System.getenv()
  val fromEnv = listOf("WMCAM_KEYSTORE", "WMCAM_STORE_PASSWORD", "WMCAM_KEY_ALIAS", "WMCAM_KEY_PASSWORD")
    .associateWith { env[it].orEmpty() }
  if (fromEnv.values.all { it.isNotBlank() }) {
    mapOf(
      "storeFile" to fromEnv.getValue("WMCAM_KEYSTORE"),
      "storePassword" to fromEnv.getValue("WMCAM_STORE_PASSWORD"),
      "keyAlias" to fromEnv.getValue("WMCAM_KEY_ALIAS"),
      "keyPassword" to fromEnv.getValue("WMCAM_KEY_PASSWORD"),
    )
  } else {
    val f = File(System.getProperty("user.home"), "keys/watermark-cam-keystore.properties")
    if (!f.isFile) null else {
      val props = Properties().apply { f.inputStream().use { load(it) } }
      val keys = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
      if (keys.all { !props.getProperty(it).isNullOrBlank() }) keys.associateWith { props.getProperty(it) } else null
    }
  }
}?.takeIf { File(it.getValue("storeFile")).isFile }
android {
  namespace = "com.nu11b1ade.watermarkcam"
  compileSdk = 37
  defaultConfig {
    applicationId = "com.nu11b1ade.watermarkcam"
    minSdk = 26
    targetSdk = 37
    versionCode = 4
    versionName = "0.2.2"
    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    manifestPlaceholders["amapKey"] = amapKey
    buildConfigField("boolean", "AMAP_ENABLED", amapKey.isNotBlank().toString())
  }
  signingConfigs {
    if (releaseSigning != null) {
      create("release") {
        storeFile = File(releaseSigning.getValue("storeFile"))
        storePassword = releaseSigning.getValue("storePassword")
        keyAlias = releaseSigning.getValue("keyAlias")
        keyPassword = releaseSigning.getValue("keyPassword")
      }
    }
  }
  buildTypes {
    release {
      isMinifyEnabled = true
      isShrinkResources = true
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      if (releaseSigning != null) signingConfig = signingConfigs.getByName("release")
    }
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
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
  implementation("com.amap.api:search:9.7.1")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
  testImplementation("org.robolectric:robolectric:4.16.1")
  testImplementation("androidx.compose.ui:ui-test-junit4")
  debugImplementation("androidx.compose.ui:ui-test-manifest")
  androidTestImplementation("androidx.test.ext:junit:1.3.0")
  androidTestImplementation("androidx.test:runner:1.7.0")
  androidTestImplementation("androidx.test:rules:1.7.0")
  androidTestImplementation("androidx.test:core:1.7.0")
}
