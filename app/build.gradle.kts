import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    id("com.google.gms.google-services")      // Google Services (for Firebase)
    id("com.google.dagger.hilt.android")      // Hilt
    id("com.google.devtools.ksp")


}

// Secrets that must not be committed live in local.properties (git-ignored).
val localProps = Properties().apply {
    val f = rootProject.file("local.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}

android {
    namespace = "com.escrowmalawi"
    compileSdk {
        version = release(37)
    }


    defaultConfig {
        applicationId = "com.escrowmalawi"
        minSdk = 24
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"

        // Sepolia RPC endpoint, e.g. RPC_URL=https://eth-sepolia.g.alchemy.com/v2/<key>
        buildConfigField("String", "RPC_URL", "\"${localProps.getProperty("RPC_URL", "")}\"")
        // Demo mode: shared test accounts that sign for every user. Leave blank in
        // local.properties to use each user's own wallet instead. TEST KEYS ONLY.
        buildConfigField("String", "DEMO_BUYER_KEY", "\"${localProps.getProperty("DEMO_BUYER_KEY", "")}\"")
        buildConfigField("String", "DEMO_SELLER_KEY", "\"${localProps.getProperty("DEMO_SELLER_KEY", "")}\"")
        // Deployed P2PEscrow contract (Sepolia)
        buildConfigField("String", "ESCROW_CONTRACT", "\"0x1e0bb30abc2a905c451831c8e3a1f9c537f93eea\"")

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    implementation("com.google.dagger:hilt-android:2.59.2")
    implementation(libs.androidx.credentials)
    implementation(libs.androidx.credentials.play.services.auth)
    implementation(libs.googleid)
    implementation(libs.androidx.compose.foundation)
    ksp("com.google.dagger:hilt-android-compiler:2.59.2")
    implementation("androidx.hilt:hilt-navigation-compose:1.3.0")
    ksp("androidx.hilt:hilt-compiler:1.3.0")
    implementation(libs.androidx.security.crypto)

    // Database - Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Firebase
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.auth)
    implementation(libs.firebase.firestore)
    implementation(libs.firebase.storage)
    implementation(libs.firebase.appcheck)
    implementation(libs.firebase.appcheck.playintegrity)
    implementation(libs.firebase.appcheck.debug)

    // Image Loading
    implementation(libs.coil.compose)
    implementation("androidx.activity:activity-compose:1.9.0")
    // Blockchain
    implementation(libs.web3j)


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
