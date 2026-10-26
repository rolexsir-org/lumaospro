import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// ---------------------------------------------------------------------------
// Signing.
// Release signing is driven by an optional, gitignored keystore.properties file:
//   storeFile=keystore/lumaos-pro-upload.jks
//   storePassword=...
//   keyAlias=lumaos-pro-upload
//   keyPassword=...
// Without complete credentials, release outputs remain unsigned. They are not
// uploadable to Play; never silently sign a production variant with a debug key.
// ---------------------------------------------------------------------------
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) {
        keystorePropsFile.inputStream().use { load(it) }
    }
}
val releaseSigningFields = listOf("storeFile", "storePassword", "keyAlias", "keyPassword")
if (keystoreProps.isNotEmpty()) {
    val missingFields = releaseSigningFields.filter { keystoreProps.getProperty(it).isNullOrBlank() }
    if (missingFields.isNotEmpty()) {
        throw GradleException("Incomplete release signing configuration; missing: ${missingFields.joinToString()}.")
    }
    val configuredStore = rootProject.file(keystoreProps.getProperty("storeFile"))
    if (!configuredStore.isFile) {
        throw GradleException("The configured release keystore file does not exist.")
    }
}

android {
    namespace = "com.lumaos.pro.launcher"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.lumaos.pro.launcher"
        minSdk = 26
        targetSdk = 36
        // 1.0.0 is the first public release and the first build that carries
        // Design System v3.2. The pre-release 0.1.0 build never left this
        // workspace and was never uploaded, so its versionCode is retired rather
        // than reused.
        //
        // versionCode = major * 100 + minor * 10 + patch:
        //   1.0.0 -> 100, 1.0.1 -> 101, 1.1.0 -> 110. Monotonic, and room for
        //   99 patches and 9 minors inside each major without renumbering.
        versionCode = 100
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        resourceConfigurations += setOf("en")
    }

    signingConfigs {
        if (keystoreProps.isNotEmpty()) {
            create("release") {
                // rootProject.file, not file(): inside the module's android {}
                // block, file() resolves relative to app/, so a path written as
//                 "keystore/lumaos-pro-upload.jks" would look for app/keystore/lumaos-pro-upload.jks.
                storeFile = keystoreProps["storeFile"]?.let { rootProject.file(it as String) }
                storePassword = keystoreProps["storePassword"] as String?
                keyAlias = keystoreProps["keyAlias"] as String?
                keyPassword = keystoreProps["keyPassword"] as String?
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            if (keystoreProps.isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            "-opt-in=androidx.compose.material3.ExperimentalMaterial3Api"
        )
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "META-INF/DEPENDENCIES",
                "META-INF/LICENSE*"
            )
        }
    }

    // Keep the test task honest: unit tests run on the JVM only, against pure
    // Kotlin logic, so they stay fast and need no emulator.
    testOptions {
        unitTests {
            // Robolectric needs the real merged resources: inflating
            // activity_home.xml is part of what these tests verify.
            isIncludeAndroidResources = true
        }
    }
}

dependencies {
    // --- Core Android -------------------------------------------------
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.recyclerview)

    // --- Lifecycle (drives the whole intelligence engine) -------------
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.lifecycle.viewmodel.compose)

    // --- Compose (settings + dashboard surfaces only) -----------------
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)
    debugImplementation(libs.androidx.compose.ui.tooling)

    // --- Persistence --------------------------------------------------
    implementation(libs.androidx.datastore.preferences)

    implementation(libs.kotlinx.coroutines.android)

    // --- Tests ---------------------------------------------------------
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
}

// --- Test JVM tuning --------------------------------------------------------
// Robolectric loads a full Android framework image, so the test JVM needs more
// than Gradle's 512 MB default. Robolectric's ~150 MB android-all jar is
// fetched into .cache/ so it stays out of the project snapshot.
tasks.withType<Test>().configureEach {
    maxHeapSize = "900m"
    systemProperty("robolectric.dependency.dir", file("${rootDir}/.cache/robolectric").absolutePath)
    // DesignTokensParityTest reads the authored Design System v3.2 bundle from the
    // repository, not from a copy, so a drift between docs/design/v3.2 and the
    // Kotlin tokens fails the suite instead of being discovered by eye.
    systemProperty(
        "lumaos.designTokensDir",
        rootProject.file("docs/design/v3.2/tokens").absolutePath
    )
}
