import org.gradle.api.file.DuplicatesStrategy
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayInputStream
import java.io.FileInputStream
import java.util.Base64
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.parcelize")
    id("com.google.devtools.ksp")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
    alias(libs.plugins.spotless)
}

configurations.configureEach {
    resolutionStrategy.cacheChangingModulesFor(0, "seconds")
}

fun getProps(propName: String): String {
    val propsInEnv = System.getenv("LOCAL_PROPERTIES")
    if (propsInEnv != null) {
        val props = Properties()
        props.load(ByteArrayInputStream(Base64.getDecoder().decode(propsInEnv)))
        val value = props.getProperty(propName)
        if (value != null) {
            return value
        }
    }
    val propsFile = rootProject.file("local.properties")
    if (propsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(propsFile))
        val value = props.getProperty(propName)
        if (value != null) {
            return value
        }
    }
    return ""
}

fun getVersionProps(propName: String): String {
    val propsFile = rootProject.file("version.properties")
    if (propsFile.exists()) {
        val props = Properties()
        props.load(FileInputStream(propsFile))
        val value = props.getProperty(propName)
        if (value != null) {
            return value
        }
    }
    return ""
}

android {
    namespace = "io.nekohasekai.sfa"
    compileSdk = 37
    compileSdkMinor = 1

    ndkVersion = "28.0.13004108"

    System.getenv("ANDROID_NDK_HOME")?.let { ndkPath = it }

    ksp {
        arg("room.incremental", "true")
        arg("room.schemaLocation", "${projectDir}/schemas")
    }

    defaultConfig {
        // XeroBox's own package name (README): it installs next to the original app
        // instead of clashing with it. Override with XEROBOX_APP_ID in local.properties.
        // The Kotlin package (namespace) stays io.nekohasekai.sfa to keep upstream merges easy.
        applicationId = getProps("XEROBOX_APP_ID").trim().ifEmpty { "com.xerobox.vpn" }
        minSdk = 24
        targetSdk = 37
        versionCode = getVersionProps("VERSION_CODE").toInt()
        versionName = getVersionProps("VERSION_NAME")
        base.archivesName.set("XeroBox-${versionName}")
    }

    signingConfigs {
        create("release") {
            // Path from local.properties KEYSTORE_FILE (see SIGNING.md). Release builds need it.
            storeFile = getProps("KEYSTORE_FILE").takeIf { it.isNotEmpty() }?.let { file(it) }
            storePassword = getProps("KEYSTORE_PASS")
            keyAlias = getProps("ALIAS_NAME")
            keyPassword = getProps("ALIAS_PASS")
        }
    }

    // XeroBox server built into the app (login, sync, invites, updates). Set the
    // production https address as XEROBOX_SERVER_URL in local.properties; debug builds
    // fall back to the emulator's view of a panel on this PC.
    // XEROBOX_GITHUB_REPO (owner/repo) is the update fallback; empty = no fallback.
    val serverUrl = getProps("XEROBOX_SERVER_URL").trim().trimEnd('/')
    val githubRepo = getProps("XEROBOX_GITHUB_REPO").trim()
    // Where users find this app's source code (GPL-3.0 requires offering it): your fork.
    val sourceUrl = getProps("XEROBOX_SOURCE_URL").trim().ifEmpty {
        if (githubRepo.isNotEmpty()) "https://github.com/$githubRepo" else "https://github.com/SagerNet/sing-box-for-android"
    }
    defaultConfig {
        buildConfigField("String", "UPDATE_GITHUB_REPO", "\"$githubRepo\"")
        buildConfigField("String", "SOURCE_URL", "\"$sourceUrl\"")
        // Official download page for the in-app QR code, used until the panel sends one
        // (dashboard → Settings → App links). The default is a placeholder under the reserved
        // .invalid domain, which never resolves, and the app labels it as such.
        val downloadUrl = getProps("XEROBOX_DOWNLOAD_URL").trim().ifEmpty { "https://download.xerobox.invalid/android" }
        buildConfigField("String", "DOWNLOAD_URL", "\"$downloadUrl\"")
    }

    buildTypes {
        debug {
            if (getProps("KEYSTORE_PASS").isNotEmpty()) {
                signingConfig = signingConfigs.getByName("release")
            }
            buildConfigField("String", "SERVER_URL", "\"${serverUrl.ifEmpty { "http://10.0.2.2:8088" }}\"")
        }
        release {
            buildConfigField("String", "SERVER_URL", "\"$serverUrl\"")
            isMinifyEnabled = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("release")
            vcsInfo.include = false
        }
    }

    dependenciesInfo {
        includeInApk = false
    }

    // One build for every user (website, Telegram, GitHub releases). The source folders that
    // upstream splits into Play/legacy flavours are all part of it: minApi24 (API 24+ code),
    // github (self-updater) and other (installer, vendor hooks).
    sourceSets {
        getByName("main") {
            java.directories.addAll(listOf("src/minApi24/java", "src/github/java", "src/other/java"))
            aidl.directories.add("src/minApi24/aidl")
        }
    }

    splits {
        abi {
            isEnable = true
            isUniversalApk = false
            reset()
            include("arm64-v8a", "armeabi-v7a", "x86_64") // 32-bit ARM: older and budget phones
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Required by android-tree-sitter
        isCoreLibraryDesugaringEnabled = true
    }

    androidResources {
        generateLocaleConfig = true
    }

    buildFeatures {
        viewBinding = true
        aidl = true
        compose = true
        buildConfig = true
    }

    packaging {
        jniLibs {
            useLegacyPackaging = true
        }
    }

    lint {
        fatal += "NewApi"
    }

    applicationVariants.configureEach {
        outputs.configureEach {
            val output = this as com.android.build.gradle.internal.api.BaseVariantOutputImpl
            var fileName = output.outputFileName
            fileName = fileName.replace("-release", "")
            output.outputFileName = fileName
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // libbox
    implementation(files("libs/libbox.aar"))

    // Enterprise licensing library.
    // Drop the vendor .jar/.aar into app/libs/ named license-*.jar / license-*.aar
    // (or enterprise-*). The name filter keeps libbox.aar out of this set, and an
    // empty match is not an error, so the project still builds before the lib lands.
    implementation(
        fileTree("libs") {
            include("license-*.jar", "license-*.aar", "enterprise-*.jar", "enterprise-*.aar")
        },
    )
    // If the library is published to a Maven repo instead, delete the fileTree above
    // and declare the coordinate here (add the repo to settings.gradle.kts):
    // implementation("com.yourvendor:licensing:1.2.3")

    // Add-on libraries from other teams (see EXTENSIONS.md).
    // Drop them into app/libs/ named ext-<name>.aar / ext-<name>.jar; nothing else to edit
    // here. A local .aar does not bring its own dependencies: add those as implementation()
    // lines below, or ask the team for a Maven coordinate instead:
    // implementation("com.partner:analytics:1.0.0")
    implementation(fileTree("libs") { include("ext-*.aar", "ext-*.jar") })

    // API level specific versions
    val lifecycleVersion24 = "2.11.0"
    val roomVersion24 = "2.8.4"
    val workVersion24 = "2.11.2"
    val cameraVersion24 = "1.6.1"
    val browserVersion24 = "1.10.0"
    val webkitVersion24 = "1.16.0"
    val coreVersion24 = "1.19.0"
    val materialVersion24 = "1.14.0"

    // Common dependencies (no API level difference)
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("androidx.constraintlayout:constraintlayout:2.2.1")
    implementation("androidx.navigation:navigation-fragment-ktx:2.9.8")
    implementation("androidx.navigation:navigation-ui-ktx:2.9.8")
    implementation("com.google.zxing:core:3.5.4")
    implementation("androidx.coordinatorlayout:coordinatorlayout:1.3.0")
    implementation("androidx.preference:preference-ktx:1.2.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.11.0")
    implementation("com.android.tools.smali:smali-dexlib2:3.0.9") {
        exclude(group = "com.google.guava", module = "guava")
    }
    implementation("com.google.guava:guava:33.6.0-android")

    // AndroidX (API 24+)

    implementation("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion24")
    implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion24")
    implementation("androidx.lifecycle:lifecycle-process:$lifecycleVersion24")
    implementation("androidx.room:room-runtime:$roomVersion24")
    implementation("androidx.work:work-runtime-ktx:$workVersion24")
    implementation("androidx.camera:camera-view:$cameraVersion24")
    implementation("androidx.camera:camera-lifecycle:$cameraVersion24")
    implementation("androidx.camera:camera-camera2:$cameraVersion24")
    implementation("androidx.browser:browser:$browserVersion24")
    implementation("androidx.webkit:webkit:$webkitVersion24")
    implementation("androidx.core:core-ktx:$coreVersion24")
    implementation("com.google.android.material:material:$materialVersion24")
    "ksp"("androidx.room:room-compiler:$roomVersion24")

    // Configuration editor: sora-editor (tree-sitter)
    val soraVersion = "0.23.6"
    val treeSitterVersion = "4.3.2"
    implementation("io.github.Rosemoe.sora-editor:editor:$soraVersion")
    implementation("io.github.Rosemoe.sora-editor:language-treesitter:$soraVersion")
    implementation("com.itsaky.androidide.treesitter:android-tree-sitter:$treeSitterVersion")
    implementation("com.itsaky.androidide.treesitter:tree-sitter-json:$treeSitterVersion")

    // Shizuku
    val shizukuVersion = "13.1.5"
    implementation("dev.rikka.shizuku:api:$shizukuVersion")
    implementation("dev.rikka.shizuku:provider:$shizukuVersion")

    // libsu for ROOT package query
    val libsuVersion = "6.0.0"
    implementation("com.github.topjohnwu.libsu:core:$libsuVersion")
    implementation("com.github.topjohnwu.libsu:service:$libsuVersion")

    // Compose
    val composeBom24 = platform("androidx.compose:compose-bom:2026.06.01")
    val activityVersion24 = "1.13.0"
    val lifecycleComposeVersion24 = "2.11.0"

    implementation(composeBom24)
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material3.adaptive:adaptive")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.activity:activity-compose:$activityVersion24")
    implementation("androidx.navigation:navigation-compose:2.9.8")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleComposeVersion24")
    implementation("androidx.compose.runtime:runtime-livedata")

    // Debug/Test dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    androidTestImplementation(composeBom24)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Common Compose-related libraries
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.github.jeziellago:compose-markdown:0.7.2")
    implementation("org.kodein.emoji:emoji-kt:2.5.0")

    // Terminal emulator
    val libghosttyVersion = "0.1.0-SNAPSHOT"
    implementation("io.github.sagernet:libghostty-android:$libghosttyVersion")
    implementation("io.github.sagernet:libghostty-android-extras:$libghosttyVersion")
    implementation("io.github.sagernet:libghostty-android-compose:$libghosttyVersion")

    // Xposed API for self-hooking VPN hide module
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly(project(":libxposed-api"))
}

tasks.withType<KotlinCompile>().configureEach {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

spotless {
    kotlin {
        target("src/**/*.kt")
        ktlint(libs.versions.ktlint.get())
            .editorConfigOverride(mapOf(
                "ktlint_standard_backing-property-naming" to "disabled",
                "ktlint_standard_blank-line-before-declaration" to "disabled",
                "ktlint_standard_blank-line-between-when-conditions" to "disabled",
                "ktlint_standard_filename" to "disabled",
                "ktlint_standard_max-line-length" to "disabled",
                "ktlint_standard_property-naming" to "disabled",
            ))
    }
    java {
        target("src/**/*.java")
        googleJavaFormat()
    }
}
