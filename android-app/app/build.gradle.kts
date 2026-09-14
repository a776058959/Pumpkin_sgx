plugins {
    id("com.android.application")
}

android {
    namespace = "com.pumpkin.server"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.pumpkin.server"
        minSdk = 24
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        ndk {
            abiFilters += "arm64-v8a"
        }
    }

    packaging {
        jniLibs {
            // 关键：必须把 jniLibs 里的可执行文件解压到 nativeLibraryDir，否则无法 exec
            useLegacyPackaging = true
            // 不要对可执行文件做 strip
            keepDebugSymbols += "**/libpumpkin.so"
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    lint {
        abortOnError = false
    }
}
