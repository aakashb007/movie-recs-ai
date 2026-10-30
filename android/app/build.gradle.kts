import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    alias(libs.plugins.roborazzi)
}

android {
    namespace = "com.reelwise.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.reelwise.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // One fixed key for every build, so new APKs install over old ones and Google sign-in (which
    // trusts this key's SHA-1) keeps working. Read from environment variables (CI) or, for local
    // builds, local.properties; never committed. Without it, builds use the local debug key.
    val localProps = Properties().apply {
        rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
    }
    fun signingValue(env: String, prop: String): String? =
        System.getenv(env)?.takeIf { it.isNotBlank() } ?: localProps.getProperty(prop)?.takeIf { it.isNotBlank() }
    val keystorePath = signingValue("REELWISE_KEYSTORE_FILE", "reelwise.storeFile") ?: "reelwise.jks"
    val keystore = file(keystorePath)
    val storePass = signingValue("REELWISE_KEYSTORE_PASSWORD", "reelwise.storePassword")
    val fixedKey = keystore.exists() && storePass != null
    signingConfigs {
        if (fixedKey) {
            create("reelwise") {
                storeFile = keystore
                storePassword = storePass
                keyAlias = signingValue("REELWISE_KEY_ALIAS", "reelwise.keyAlias") ?: "reelwise"
                keyPassword = signingValue("REELWISE_KEY_PASSWORD", "reelwise.keyPassword") ?: storePass
            }
        }
    }

    buildTypes {
        debug {
            signingConfig = signingConfigs.getByName(if (fixedKey) "reelwise" else "debug")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (fixedKey) "reelwise" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
    lint {
        abortOnError = true
        checkDependencies = false
        disable += setOf("GradleDependency", "NewerVersionAvailable", "AndroidGradlePluginVersion")
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.splashscreen)

    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    debugImplementation(libs.compose.ui.tooling)
    debugImplementation(libs.compose.ui.test.manifest)

    implementation(libs.hilt.android)
    ksp(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.androidx.compiler)

    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.coil.compose)
    implementation(libs.palette)
    implementation(libs.play.services.auth)
    implementation(libs.kotlinx.coroutines.play.services)
    implementation(libs.datastore.preferences)
    implementation(libs.work.runtime)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.roborazzi)
    testImplementation(libs.roborazzi.compose)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.junit)
    testImplementation(platform(libs.compose.bom))
    testImplementation(libs.compose.ui.test.junit4)
}
