plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "io.github.alandi123.noblexlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.alandi123.noblexlauncher"
        minSdk = 28
        targetSdk = 30
        versionCode = providers.gradleProperty("versionCode").getOrElse("1").toInt()
        versionName = providers.gradleProperty("versionName").getOrElse("0.1.0")
        testInstrumentationRunner = "android.test.InstrumentationTestRunner"
    }

    val signingPath = providers.environmentVariable("NOBLEX_KEYSTORE_PATH").orNull
    if (signingPath != null) {
        signingConfigs {
            create("release") {
                storeFile = file(signingPath)
                storePassword = providers.environmentVariable("NOBLEX_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("NOBLEX_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("NOBLEX_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            if (signingPath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    lint {
        // The APK is distributed through GitHub Releases, not Google Play.
        disable += "ExpiredTargetSdkVersion"
    }

    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.work:work-runtime-ktx:2.10.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}
