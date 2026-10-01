import java.util.Properties

val spbusSecrets = Properties().apply {
    rootProject.file("local.properties").takeIf { it.exists() }?.inputStream()?.use { load(it) }
}

fun spbusSecret(name: String): String = spbusSecrets.getProperty(name, "").trim()
fun buildConfigString(value: String): String = "\"${value.replace("\\", "\\\\").replace("\"", "\\\"")}\""

plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.example.spbus"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.example.spbus"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SPTRANS_TOKEN", buildConfigString(spbusSecret("sptrans.token")))
        buildConfigField("String", "THINGSPEAK_CHANNEL_ID", buildConfigString(spbusSecret("thingspeak.channel_id")))
        buildConfigField("String", "THINGSPEAK_READ_API_KEY", buildConfigString(spbusSecret("thingspeak.read_api_key")))
        buildConfigField("String", "THINGSPEAK_WRITE_API_KEY", buildConfigString(spbusSecret("thingspeak.write_api_key")))
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        buildConfig = true
    }
}

dependencies {
    implementation("androidx.browser:browser:1.8.0")

    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    implementation("org.osmdroid:osmdroid-android:6.1.20")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
}