plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "dev.mulvey.justchess"
    compileSdk = 35

    defaultConfig {
        applicationId = "dev.mulvey.justchess"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters += listOf("arm64-v8a")
        }
        buildConfigField("String", "STOCKFISH_VERSION", "\"17.1\"")
        buildConfigField("String", "SOURCE_URL", "\"https://github.com/pudgyturtle/just-chess\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
        debug {
            isMinifyEnabled = false
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
    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    testOptions {
        unitTests.isReturnDefaultValues = true
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.6")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.6")
    implementation("androidx.navigation:navigation-compose:2.8.3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    implementation("org.apache.commons:commons-lang3:3.17.0")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.9.0")
    debugImplementation("androidx.compose.ui:ui-tooling")
}


val stockfishSo = file("src/main/jniLibs/arm64-v8a/libstockfish.so")
tasks.register("fetchStockfish") {
    group = "build"
    description = "Download official Stockfish 17.1 Android armv8 into jniLibs if missing"
    outputs.file(stockfishSo)
    doLast {
        if (stockfishSo.exists() && stockfishSo.length() > 1_000_000L) {
            logger.lifecycle("Stockfish binary already present (${stockfishSo.length()} bytes)")
            return@doLast
        }
        val script = rootProject.file("scripts/fetch-stockfish.sh")
        exec { commandLine("bash", script.absolutePath) }
        if (!stockfishSo.exists()) {
            throw GradleException("Stockfish fetch did not produce ${stockfishSo}")
        }
    }
}
tasks.matching { it.name == "preBuild" }.configureEach { dependsOn("fetchStockfish") }
