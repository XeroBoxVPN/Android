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
    id("com.github.triplet.play")
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

    flavorDimensions += "vendor"
    productFlavors {
        // EXTERNAL_PURCHASE: the plan card may send users to buy outside the store (the Telegram
        // bot). Google Play's payments policy does not allow that for digital subscriptions, so
        // the Play build only shows the plan.
        create("play") {
            buildConfigField("boolean", "EXTERNAL_PURCHASE", "false")
        }
        create("other") {
            buildConfigField("boolean", "EXTERNAL_PURCHASE", "true")
        }
        create("otherLegacy") {
            minSdk = 21
            buildConfigField("boolean", "EXTERNAL_PURCHASE", "true")
        }
    }

    sourceSets {
        getByName("play") {
            java.directories.add("src/minApi24/java")
            aidl.directories.add("src/minApi24/aidl")
        }
        getByName("other") {
            java.directories.addAll(listOf("src/minApi24/java", "src/github/java"))
            aidl.directories.add("src/minApi24/aidl")
        }
        getByName("otherLegacy") {
            java.directories.addAll(listOf("src/minApi21/java", "src/github/java"))
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
            fileName = fileName.replace("-play", "-play")
            fileName = fileName.replace("-otherLegacy", "-legacy-android-5")
            fileName = fileName.replace("-other", "")
            output.outputFileName = fileName
        }
    }
}

dependencies {
    coreLibraryDesugaring("com.android.tools:desugar_jdk_libs:2.1.5")

    // libbox
    "playImplementation"(files("libs/libbox.aar"))
    "otherImplementation"(files("libs/libbox.aar"))
    "otherLegacyImplementation"(files("libs/libbox-legacy.aar"))

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

    val lifecycleVersion21 = "2.9.4"
    val roomVersion21 = "2.7.2"
    val workVersion21 = "2.10.5"
    val cameraVersion21 = "1.4.2"
    val browserVersion21 = "1.9.0"
    val webkitVersion21 = "1.14.0"
    val coreVersion21 = "1.17.0"
    val materialVersion21 = "1.13.0"

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

    // API 24+ dependencies (play/other)
    "playImplementation"("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion24")
    "playImplementation"("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion24")
    "playImplementation"("androidx.lifecycle:lifecycle-process:$lifecycleVersion24")
    "playImplementation"("androidx.room:room-runtime:$roomVersion24")
    "playImplementation"("androidx.work:work-runtime-ktx:$workVersion24")
    "playImplementation"("androidx.camera:camera-view:$cameraVersion24")
    "playImplementation"("androidx.camera:camera-lifecycle:$cameraVersion24")
    "playImplementation"("androidx.camera:camera-camera2:$cameraVersion24")
    "playImplementation"("androidx.browser:browser:$browserVersion24")
    "playImplementation"("androidx.webkit:webkit:$webkitVersion24")
    "playImplementation"("androidx.core:core-ktx:$coreVersion24")
    "playImplementation"("com.google.android.material:material:$materialVersion24")
    "playAnnotationProcessor"("androidx.room:room-compiler:$roomVersion24")
    "kspPlay"("androidx.room:room-compiler:$roomVersion24")

    "otherImplementation"("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion24")
    "otherImplementation"("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion24")
    "otherImplementation"("androidx.lifecycle:lifecycle-process:$lifecycleVersion24")
    "otherImplementation"("androidx.room:room-runtime:$roomVersion24")
    "otherImplementation"("androidx.work:work-runtime-ktx:$workVersion24")
    "otherImplementation"("androidx.camera:camera-view:$cameraVersion24")
    "otherImplementation"("androidx.camera:camera-lifecycle:$cameraVersion24")
    "otherImplementation"("androidx.camera:camera-camera2:$cameraVersion24")
    "otherImplementation"("androidx.browser:browser:$browserVersion24")
    "otherImplementation"("androidx.webkit:webkit:$webkitVersion24")
    "otherImplementation"("androidx.core:core-ktx:$coreVersion24")
    "otherImplementation"("com.google.android.material:material:$materialVersion24")
    "kspOther"("androidx.room:room-compiler:$roomVersion24")

    // API 21 dependencies (otherLegacy)
    "otherLegacyImplementation"("androidx.lifecycle:lifecycle-livedata-ktx:$lifecycleVersion21")
    "otherLegacyImplementation"("androidx.lifecycle:lifecycle-viewmodel-ktx:$lifecycleVersion21")
    "otherLegacyImplementation"("androidx.lifecycle:lifecycle-process:$lifecycleVersion21")
    "otherLegacyImplementation"("androidx.room:room-runtime:$roomVersion21")
    "otherLegacyImplementation"("androidx.work:work-runtime-ktx:$workVersion21")
    "otherLegacyImplementation"("androidx.camera:camera-view:$cameraVersion21")
    "otherLegacyImplementation"("androidx.camera:camera-lifecycle:$cameraVersion21")
    "otherLegacyImplementation"("androidx.camera:camera-camera2:$cameraVersion21")
    "otherLegacyImplementation"("androidx.browser:browser:$browserVersion21")
    "otherLegacyImplementation"("androidx.webkit:webkit:$webkitVersion21")
    "otherLegacyImplementation"("androidx.core:core-ktx:$coreVersion21")
    "otherLegacyImplementation"("com.google.android.material:material:$materialVersion21")
    "kspOtherLegacy"("androidx.room:room-compiler:$roomVersion21")

    // Configuration editor: sora-editor (tree-sitter) for API 24+, EditorKit kept for legacy
    val soraVersion = "0.23.6"
    val treeSitterVersion = "4.3.2"
    "playImplementation"("io.github.Rosemoe.sora-editor:editor:$soraVersion")
    "playImplementation"("io.github.Rosemoe.sora-editor:language-treesitter:$soraVersion")
    "playImplementation"("com.itsaky.androidide.treesitter:android-tree-sitter:$treeSitterVersion")
    "playImplementation"("com.itsaky.androidide.treesitter:tree-sitter-json:$treeSitterVersion")
    "otherImplementation"("io.github.Rosemoe.sora-editor:editor:$soraVersion")
    "otherImplementation"("io.github.Rosemoe.sora-editor:language-treesitter:$soraVersion")
    "otherImplementation"("com.itsaky.androidide.treesitter:android-tree-sitter:$treeSitterVersion")
    "otherImplementation"("com.itsaky.androidide.treesitter:tree-sitter-json:$treeSitterVersion")
    "otherLegacyImplementation"("com.blacksquircle.ui:editorkit:2.2.0")
    "otherLegacyImplementation"("com.blacksquircle.ui:language-json:2.2.0")

    // Play Store specific
    "playImplementation"("com.google.android.play:app-update-ktx:2.1.0")
    "playImplementation"("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")

    // Shizuku (play and other flavors, API 24+ only)
    val shizukuVersion = "13.1.5"
    "playImplementation"("dev.rikka.shizuku:api:$shizukuVersion")
    "playImplementation"("dev.rikka.shizuku:provider:$shizukuVersion")
    "otherImplementation"("dev.rikka.shizuku:api:$shizukuVersion")
    "otherImplementation"("dev.rikka.shizuku:provider:$shizukuVersion")

    // libsu for ROOT package query (all flavors)
    val libsuVersion = "6.0.0"
    "playImplementation"("com.github.topjohnwu.libsu:core:$libsuVersion")
    "playImplementation"("com.github.topjohnwu.libsu:service:$libsuVersion")
    "otherImplementation"("com.github.topjohnwu.libsu:core:$libsuVersion")
    "otherImplementation"("com.github.topjohnwu.libsu:service:$libsuVersion")
    "otherLegacyImplementation"("com.github.topjohnwu.libsu:core:$libsuVersion")
    "otherLegacyImplementation"("com.github.topjohnwu.libsu:service:$libsuVersion")

    // Compose dependencies - API 24+ (play/other)
    val composeBom24 = platform("androidx.compose:compose-bom:2026.06.01")
    val activityVersion24 = "1.13.0"
    val lifecycleComposeVersion24 = "2.11.0"

    "playImplementation"(composeBom24)
    "playImplementation"("androidx.compose.material3:material3")
    "playImplementation"("androidx.compose.material3.adaptive:adaptive")
    "playImplementation"("androidx.compose.ui:ui")
    "playImplementation"("androidx.compose.ui:ui-tooling-preview")
    "playImplementation"("androidx.compose.material:material-icons-extended")
    "playImplementation"("androidx.activity:activity-compose:$activityVersion24")
    "playImplementation"("androidx.navigation:navigation-compose:2.9.8")
    "playImplementation"("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleComposeVersion24")
    "playImplementation"("androidx.compose.runtime:runtime-livedata")

    "otherImplementation"(composeBom24)
    "otherImplementation"("androidx.compose.material3:material3")
    "otherImplementation"("androidx.compose.material3.adaptive:adaptive")
    "otherImplementation"("androidx.compose.ui:ui")
    "otherImplementation"("androidx.compose.ui:ui-tooling-preview")
    "otherImplementation"("androidx.compose.material:material-icons-extended")
    "otherImplementation"("androidx.activity:activity-compose:$activityVersion24")
    "otherImplementation"("androidx.navigation:navigation-compose:2.9.8")
    "otherImplementation"("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleComposeVersion24")
    "otherImplementation"("androidx.compose.runtime:runtime-livedata")

    // Compose dependencies - API 21 (otherLegacy)
    val composeBom21 = platform("androidx.compose:compose-bom:2025.01.00")
    val activityVersion21 = "1.11.0"
    val lifecycleComposeVersion21 = "2.9.4"

    "otherLegacyImplementation"(composeBom21)
    "otherLegacyImplementation"("androidx.compose.material3:material3")
    "otherLegacyImplementation"("androidx.compose.material3.adaptive:adaptive")
    "otherLegacyImplementation"("androidx.compose.ui:ui")
    "otherLegacyImplementation"("androidx.compose.ui:ui-tooling-preview")
    "otherLegacyImplementation"("androidx.compose.material:material-icons-extended")
    "otherLegacyImplementation"("androidx.activity:activity-compose:$activityVersion21")
    "otherLegacyImplementation"("androidx.navigation:navigation-compose:2.9.8")
    "otherLegacyImplementation"("androidx.lifecycle:lifecycle-viewmodel-compose:$lifecycleComposeVersion21")
    "otherLegacyImplementation"("androidx.compose.runtime:runtime-livedata")

    // Debug/Test dependencies
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    "androidTestPlayImplementation"(composeBom24)
    "androidTestOtherImplementation"(composeBom24)
    "androidTestOtherLegacyImplementation"(composeBom21)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")

    // Common Compose-related libraries
    implementation("sh.calvin.reorderable:reorderable:3.1.0")
    implementation("com.github.jeziellago:compose-markdown:0.7.2")
    implementation("org.kodein.emoji:emoji-kt:2.5.0")

    // Terminal emulator
    val libghosttyVersion = "0.1.0-SNAPSHOT"
    implementation("io.github.sagernet:libghostty-android:$libghosttyVersion")
    implementation("io.github.sagernet:libghostty-android-extras:$libghosttyVersion")
    "playImplementation"("io.github.sagernet:libghostty-android-compose:$libghosttyVersion")
    "otherImplementation"("io.github.sagernet:libghostty-android-compose:$libghosttyVersion")
    "otherLegacyImplementation"("io.github.sagernet:libghostty-android-compose-legacy:$libghosttyVersion")

    // Xposed API for self-hooking VPN hide module
    compileOnly("de.robv.android.xposed:api:82")
    compileOnly(project(":libxposed-api"))
}

val playCredentialsJSON = rootProject.file("service-account-credentials.json")
if (playCredentialsJSON.exists()) {
    play {
        serviceAccountCredentials.set(playCredentialsJSON)
        defaultToAppBundles.set(true)
        val version = getVersionProps("VERSION_NAME")
        track.set(
            if (version.contains("alpha") || version.contains("beta")/* || version.contains("rc")*/) {
                "beta"
            } else {
                "production"
            }
        )
    }
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
