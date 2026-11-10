# AdsManager

Reusable AdMob + UMP consent stack for Android apps.

## Installation

Add JitPack to your root `settings.gradle.kts`:

```kotlin
dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }
}
```

Then add the dependency:

```kotlin
dependencies {
    implementation("com.github.umirtech:AdsManager:v1.0.0")
}
```

## Usage

Initialize once from `Application.onCreate`:

```kotlin
AdsManager.init(
    this,
    AdsManagerConfig(
        imageNativeUnitId = "...",
        videoNativeUnitId = "...",
        interstitialUnitId = "...",
        rewardedUnitId = "...",
        onConsentResolved = { allowed -> /* wire analytics consent */ },
    ),
)
```

Then request consent from an Activity:

```kotlin
AdsManager.requestConsent(this)
```

Debug builds automatically use Google's test ad units.

See the source for the full API.
