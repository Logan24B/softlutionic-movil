plugins { id("com.android.application"); id("com.google.devtools.ksp") }
val apiUrl = providers.gradleProperty("SOFTLUTIONIC_BASE_URL").getOrElse("https://softlutionic-sistema-eqh8cjdbbre4fndd.westus-01.azurewebsites.net/")
require(apiUrl.startsWith("https://") && apiUrl.endsWith("/")) { "BASE_URL debe usar HTTPS y terminar en /" }
android {
 namespace = "com.softlutionic.mobile"
 compileSdk = 34
 defaultConfig {
  applicationId = "com.softlutionic.mobile"
  minSdk = 29
  targetSdk = 34
  versionCode = 1
  versionName = "1.0.0"
  buildConfigField("String", "BASE_URL", "\"$apiUrl\"")
 }
 buildFeatures { viewBinding = true; buildConfig = true }
 compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
dependencies {
 implementation("androidx.core:core-ktx:1.13.1")
 implementation("androidx.appcompat:appcompat:1.7.0")
 implementation("com.google.android.material:material:1.12.0")
 implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.8.7")
 implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
 implementation("androidx.recyclerview:recyclerview:1.3.2")
 implementation("androidx.room:room-runtime:2.7.2")
 implementation("androidx.room:room-ktx:2.7.2")
 ksp("androidx.room:room-compiler:2.7.2")
 implementation("com.squareup.retrofit2:retrofit:2.11.0")
 implementation("com.squareup.retrofit2:converter-gson:2.11.0")
 implementation("com.squareup.okhttp3:okhttp:4.12.0")
 implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
 testImplementation("junit:junit:4.13.2")
}
