plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jlleitschuh.gradle.ktlint")
    id("io.gitlab.arturbosch.detekt")
}

android {
    namespace = "com.motosensorlogger"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.motosensorlogger"
        minSdk = 30 // Android 11 for GNSS status callback support
        targetSdk = 34
        versionCode = 2
        versionName = "0.0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        // Performance optimizations
        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        getByName("debug") {
            // Use a consistent debug keystore for all builds
            // This allows CI builds to update existing installations
            storeFile = file("debug.keystore")
            storePassword = "android"
            keyAlias = "androiddebugkey"
            keyPassword = "android"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )

            // Native optimization flags for maximum performance
            ndk {
                debugSymbolLevel = "NONE"
            }
        }

        debug {
            isMinifyEnabled = false
            isDebuggable = true
            signingConfig = signingConfigs.getByName("debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"

        // Performance optimizations for Kotlin
        freeCompilerArgs =
            listOf(
                "-opt-in=kotlin.RequiresOptIn",
                "-opt-in=kotlin.ExperimentalStdlibApi",
                "-opt-in=kotlinx.coroutines.ExperimentalCoroutinesApi",
            )
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    // Lint configuration moved below to avoid duplicate blocks

    // Performance packaging options
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    
    lint {
        warningsAsErrors = false
        abortOnError = false
        checkDependencies = true
        disable += listOf("ObsoleteLintCustomCheck")
        // TODO: Enable strict lint checking after fixing existing issues
        // warningsAsErrors = true
        // abortOnError = true
    }
    
    lint {
        // Use custom lint configuration
        lintConfig = file("lint.xml")
        // Treat these as errors
        error += "DefaultLocale"
        error += "StringFormatInvalid"
        // Check dependencies
        checkDependencies = true
        // Abort on error for release builds
        abortOnError = false // Set to true for CI/CD
        // Generate HTML report
        htmlReport = true
        htmlOutput = file("build/reports/lint-results.html")
    }
}

configurations {
    implementation {
        exclude(group = "org.bouncycastle", module = "bcprov-jdk18on")
    }
}

dependencies {
    // Core Android dependencies
    implementation("androidx.core:core-ktx:1.17.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.9.2")
    implementation("androidx.activity:activity-ktx:1.10.1")
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")

    // Coroutines for high-performance async operations
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.10.2")

    // Location services
    implementation("com.google.android.gms:play-services-location:21.3.0")

    // High-performance CSV writing
    implementation("com.opencsv:opencsv:5.12.0")

    // Lifecycle components for sensor management
    implementation("androidx.lifecycle:lifecycle-service:2.9.2")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.9.2")

    // WorkManager for background processing
    implementation("androidx.work:work-runtime-ktx:2.10.3")

    // Charting library for real-time telemetry visualization
    implementation("com.github.PhilJay:MPAndroidChart:v3.1.0")

    // Testing
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.mockito:mockito-core:5.19.0")
    testImplementation("org.mockito:mockito-inline:5.2.0")
    testImplementation("org.mockito.kotlin:mockito-kotlin:6.0.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    testImplementation("androidx.test:core:1.7.0")
    testImplementation("org.robolectric:robolectric:4.15.1")
    testImplementation("org.bouncycastle:bcprov-jdk18on:1.81")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
}

// Ktlint configuration
ktlint {
    android.set(true)
    ignoreFailures.set(true)
    reporters {
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.HTML)
        reporter(org.jlleitschuh.gradle.ktlint.reporter.ReporterType.CHECKSTYLE)
    }
    filter {
        exclude("**/generated/**")
        exclude("**/build/**")
    }
}

// Detekt configuration
detekt {
    buildUponDefaultConfig = true
    config.setFrom("$rootDir/config/detekt/detekt.yml")
    baseline = file("$projectDir/detekt-baseline.xml")

    reports {
        html.required.set(true)
        xml.required.set(true)
        txt.required.set(false)
        sarif.required.set(false)
    }
}
