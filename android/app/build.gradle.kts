import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val uploadSigningFile = file(providers.environmentVariable("OFFZONE_SIGNING_PROPERTIES")
    .orElse("${System.getProperty("user.home")}/.config/offzone/credentials/android-signing.properties").get())
val uploadSigning = Properties().apply {
    if (uploadSigningFile.isFile) uploadSigningFile.inputStream().use { load(it) }
}
android {
    signingConfigs {
        create("upload") {
            storeFile = uploadSigning.getProperty("storeFile")?.let { file(it) }
            storePassword = uploadSigning.getProperty("storePassword")
            keyAlias = uploadSigning.getProperty("keyAlias")
            keyPassword = uploadSigning.getProperty("keyPassword")
            storeType = "PKCS12"
        }
    }
    namespace = "com.exchip.offzone"
    compileSdk = 36
    defaultConfig {
        applicationId = "com.exchip.roomdns"
        minSdk = 26
        targetSdk = 36
        versionCode = 2
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        mapOf(
            "FIREBASE_API_KEY" to "", "FIREBASE_APP_ID" to "1:842668693484:android:90a67f18aa795dabf212c9",
            "FIREBASE_PROJECT_ID" to "roomdns-exchip", "GOOGLE_WEB_CLIENT_ID" to "",
            "REVENUECAT_PUBLIC_KEY" to "", "PRO_ENTITLEMENT_ID" to "offzone_pro",
            "PRO_OFFERING_ID" to "offzone_pro", "UNLOCK_PRODUCT_ID" to "",
            "TERMS_URL" to "", "PRIVACY_URL" to "https://offzone-privacy-support.wogus5357.chatgpt.site/privacy",
        ).forEach { (key, fallback) ->
            val value = providers.gradleProperty(key).orElse(fallback).get()
            buildConfigField("String", key, "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        }
        buildConfigField("boolean", "PRO_OFFER_READY", "false")
        buildConfigField("boolean", "UNLOCK_PASS_READY", "false")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildTypes {
        getByName("release") { signingConfig = signingConfigs.getByName("upload") }
        debug {
        applicationIdSuffix = ".debug"
        buildConfigField("String", "FIREBASE_APP_ID", "\"1:842668693484:android:b484e4679cd99a9ef212c9\"")
    } }
}
androidComponents { onVariants(selector().withBuildType("debug")) { it.applicationId.set("com.exchip.offzone.debug") } }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.06.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation(platform("com.google.firebase:firebase-bom:34.19.0"))
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")
    implementation("com.google.firebase:firebase-functions")
    implementation("androidx.credentials:credentials:1.3.0")
    implementation("androidx.credentials:credentials-play-services-auth:1.3.0")
    implementation("com.google.android.libraries.identity.googleid:googleid:1.1.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-play-services:1.9.0")
    implementation("com.revenuecat.purchases:purchases:10.15.1")
    debugImplementation("androidx.compose.ui:ui-tooling")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
