plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

fun ozellik(ad: String): String =
    System.getenv(ad) ?: (project.findProperty(ad) as String?) ?: ""

val surumKodu = (System.getenv("GITHUB_RUN_NUMBER") ?: "1").toInt()

android {
    namespace = "com.siam.tvasistan"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.siam.tvasistan"
        minSdk = 26
        targetSdk = 34
        versionCode = surumKodu
        versionName = "0.1.$surumKodu"

        buildConfigField("String", "GUNCELLEME_REPO", "\"${ozellik("GUNCELLEME_REPO")}\"")
    }

    // Sabit imza: her derleme aynı anahtarla imzalanır, böylece TV'de
    // "adb install -r" / Downloader ile güncelleme imza hatası vermez.
    signingConfigs {
        create("ortak") {
            storeFile = rootProject.file("imza/tv-asistan.jks")
            storePassword = System.getenv("IMZA_SIFRE") ?: "tvasistan"
            keyAlias = "tvasistan"
            keyPassword = System.getenv("IMZA_SIFRE") ?: "tvasistan"
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.getByName("ortak")
        }
        getByName("debug") {
            signingConfig = signingConfigs.getByName("ortak")
        }
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        checkReleaseBuilds = false
        abortOnError = false
    }
}

dependencies {
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
}
