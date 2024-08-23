plugins {
    id("com.android.application")
}

android {
    compileSdk = 33
    defaultConfig {
        namespace = "com.prangesoftwaresolutions.audioanchor"
        applicationId = "com.prangesoftwaresolutions.audioanchor"
        minSdk = 19
        targetSdk = 29
        versionCode = 29
        versionName = "2.3.3"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
        }

        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-DEBUG"
            isDebuggable = true
        }
    }
    compileOptions {
        encoding = "UTF-8"
        sourceCompatibility(JavaVersion.VERSION_11)
        targetCompatibility(JavaVersion.VERSION_11)
    }
    packagingOptions {
        jniLibs {
            excludes += "/META-INF/*"
        }
        resources {
            excludes += "/META-INF/*"
        }
    }
}

dependencies {
// AndroidX
    implementation("androidx.annotation:annotation:1.4.0")
    implementation("androidx.appcompat:appcompat:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("androidx.core:core:1.8.0")
    implementation("androidx.media:media:1.6.0")
    implementation("androidx.preference:preference:1.2.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("com.google.android.material:material:1.6.1")

// Other
    implementation("com.google.code.gson:gson:2.9.1")
    implementation("com.nambimobile.widgets:expandable-fab:1.2.1")
}
