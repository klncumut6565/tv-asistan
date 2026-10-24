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

    // Sabit imza: her sürüm aynı anahtarla imzalanır (güncelleme imza hatası vermez).
    // Anahtar REPODA DEĞİL: GitHub Secrets'tan (IMZA_KEYSTORE_B64 + IMZA_SIFRE) CI'da çözülür.
    // Android, güncellemeyi yalnızca imza eşleşirse kabul eder — anahtar gizli kaldıkça
    // başkası TV Asistan yerine geçen bir APK üretemez.
    val imzaDosyasi = System.getenv("IMZA_DOSYASI")?.let { file(it) }
    val imzaSifre = System.getenv("IMZA_SIFRE")
    signingConfigs {
        if (imzaDosyasi != null && imzaSifre != null) {
            create("ortak") {
                storeFile = imzaDosyasi
                storePassword = imzaSifre
                keyAlias = "tvasistan"
                keyPassword = imzaSifre
            }
        }
    }

    buildTypes {
        getByName("release") {
            isMinifyEnabled = false
            signingConfig = signingConfigs.findByName("ortak")
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
