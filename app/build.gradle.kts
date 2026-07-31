plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "io.github.a1mohamad.toeflvocab"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.a1mohamad.toeflvocab"
        // API 26 is what `java.time` needs without desugaring, and progress
        // records are timestamped with `Instant`. The alternative — core library
        // desugaring — is one more moving part in a project whose only build
        // machine is a CI runner.
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "1.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        debug {
            // The screenshot harness is debug-only, exactly like the `#if DEBUG`
            // block it was ported from, so it never reaches a release APK.
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        freeCompilerArgs += listOf(
            // WordStats is decoded by a hand-written serializer, because two of
            // its rules — backfilling `completedCyclesThisRun` from
            // `completedCycles`, and trimming an over-full checklist — cannot be
            // expressed as default parameter values. Writing one means touching
            // the nullable-element and `explicitNulls` APIs, which are still
            // marked experimental.
            "-opt-in=kotlinx.serialization.ExperimentalSerializationApi",
        )
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    testOptions {
        unitTests {
            // The loader logs skipped rows through `android.util.Log`, which is
            // a stub in the JVM test runtime and throws rather than no-ops
            // unless this is on. The alternative — mocking the framework — is a
            // dependency and a fixture for something that only writes debug
            // output.
            isReturnDefaultValues = true
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    debugImplementation(libs.androidx.compose.ui.tooling)

    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
}
