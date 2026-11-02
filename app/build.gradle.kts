plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

// versionCode is always derived from versionName (MAJOR*10000+MINOR*100+PATCH, times 100, plus a
// pre-release slot: alpha.N=N, beta.N=33+N, rc.N=66+N, stable=99). Keep in sync with SemanticVersion.kt.
fun deriveVersionCode(name: String): Int {
    val match = Regex("""(0|[1-9]\d?)\.(0|[1-9]\d?)\.(0|[1-9]\d?)(?:-(alpha|beta|rc)\.([1-9]\d?))?""").matchEntire(name)
        ?: throw GradleException("versionName '$name' must be MAJOR.MINOR.PATCH or MAJOR.MINOR.PATCH-(alpha|beta|rc).N")
    val (major, minor, patch, kind, number) = match.destructured
    if (kind.isNotEmpty() && number.toInt() > 32) throw GradleException("pre-release number must be 1..32")
    val slot = if (kind.isEmpty()) 99 else listOf("alpha", "beta", "rc").indexOf(kind) * 33 + number.toInt()
    return ((major.toInt() * 10_000 + minor.toInt() * 100 + patch.toInt()) * 100) + slot
}

val releaseVersionName = providers.gradleProperty("versionName").getOrElse("0.2.0-rc.1")

android {
    namespace = "io.github.alandi123.noblexlauncher"
    compileSdk = 35

    defaultConfig {
        applicationId = "io.github.alandi123.noblexlauncher"
        minSdk = 28
        targetSdk = 30
        versionCode = deriveVersionCode(releaseVersionName)
        versionName = releaseVersionName
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
