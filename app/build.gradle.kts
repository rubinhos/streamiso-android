plugins {
    id("com.android.application")
}

// The release workflow passes the version taken from the Git tag: -PappVersion=1.2.3
val appVersion = providers.gradleProperty("appVersion").orElse("0.0.0").get()
val appVersionParts = Regex("""(\d+)\.(\d+)\.(\d+)""").matchEntire(appVersion)?.destructured?.toList()
    ?.map { it.toInt() }
    ?: throw GradleException("appVersion must look like 1.2.3, got: $appVersion")
require(appVersionParts[1] < 1000 && appVersionParts[2] < 1000) {
    "Minor and patch must stay below 1000 so that versionCode keeps growing: $appVersion"
}

// Set only by the release workflow. Without them the release APK comes out unsigned.
val keystorePath = providers.environmentVariable("STREAMISO_KEYSTORE_PATH").orNull

android {
    namespace = "io.github.rubinhos.streamiso"
    compileSdk = 36

    defaultConfig {
        applicationId = "io.github.rubinhos.streamiso"
        minSdk = 30
        // Stay below 37 for now: from API 37 on, reaching the local network needs a runtime permission.
        targetSdk = 36
        versionName = appVersion
        versionCode = (appVersionParts[0] * 1_000_000 + appVersionParts[1] * 1_000 + appVersionParts[2])
            .coerceAtLeast(1)
    }

    signingConfigs {
        if (keystorePath != null) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = providers.environmentVariable("KEYSTORE_PASSWORD").get()
                keyAlias = providers.environmentVariable("KEY_ALIAS").get()
                keyPassword = providers.environmentVariable("KEY_PASSWORD").get()
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            if (keystorePath != null) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
}
