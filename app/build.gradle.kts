plugins {
    id("com.android.application")
}

android {
    compileSdk = 31
    defaultConfig {
        applicationId = "com.prangesoftwaresolutions.audioanchor"
        minSdk = 19
        targetSdk = 30
        versionCode = 26
        versionName = "2.3.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "proguard-rules.pro")
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
// Android support
    implementation("androidx.appcompat:appcompat:1.4.1")
    implementation("com.google.android.material:material:1.5.0")
    implementation("androidx.constraintlayout:constraintlayout:2.1.3")
    implementation("androidx.annotation:annotation:1.3.0")
    implementation("androidx.recyclerview:recyclerview:1.2.1")
    implementation("com.getbase:floatingactionbutton:1.10.1")
    implementation("androidx.swiperefreshlayout:swiperefreshlayout:1.1.0")
    implementation("androidx.core:core:1.7.0")
    implementation("androidx.media:media:1.5.0")
    implementation("androidx.preference:preference:1.2.0")


// Gson
    implementation("com.google.code.gson:gson:2.9.0")

// Test
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation("androidx.test.ext:junit:1.1.3")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.4.0")
}
