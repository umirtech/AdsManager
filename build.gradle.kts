plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android) apply false
    alias(libs.plugins.kotlin.compose)
    id("maven-publish")
}

android {
    namespace = "com.umirtech.adsmanager"
    compileSdk {
        version = release(37)
    }
    defaultConfig {
        minSdk = 25
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    publishing {
        singleVariant("release") {
            withSourcesJar()
        }
    }
}

kotlin {
    jvmToolchain(21)
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.kotlinx.coroutines.android)

    api(libs.play.services.ads)
    api(libs.ump.user.messaging.platform)

    testImplementation(libs.junit)
}

publishing {
    publications {
        create<MavenPublication>("release") {
            groupId = "com.umirtech"
            artifactId = "adsmanager"
            version = "1.0.0"
            afterEvaluate { from(components["release"]) }
        }
    }
}
