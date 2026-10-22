plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "cl.inacap.aquaalerta"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "cl.inacap.aquaalerta"
        minSdk = 24
        targetSdk = 37
        versionCode = 1
        versionName = "2.0"
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
}

dependencies {
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.constraintlayout)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    testImplementation(libs.junit)
}
