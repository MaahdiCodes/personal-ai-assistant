import com.android.build.api.artifact.SingleArtifact
import java.util.Properties
import javax.xml.parsers.DocumentBuilderFactory
import org.w3c.dom.Element

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.room)
}

// Release signing key, created once by scripts/new-signing-key.ps1. Never committed.
val keystorePropertiesFile: File = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) keystorePropertiesFile.inputStream().use { load(it) }
}

android {
    namespace = "dev.maahdi.mavick"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        applicationId = "dev.maahdi.mavick"
        minSdk = 33
        targetSdk = 36
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            // Both phones (Pixel 7 Pro, Poco X7 Pro) are 64-bit ARM, so ship only that native code.
            abiFilters += "arm64-v8a"
        }
    }

    androidResources {
        // English only: drops the translations that AndroidX libraries bundle.
        localeFilters += "en"
    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("mavick") {
                storeFile = file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // The optimized build: this is the app you use every day.
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.findByName("mavick")
        }
        debug {
            // A separate app ("Mavick Debug") with its own data, so development and tests never
            // touch your real tasks.
            applicationIdSuffix = ".debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
    }

    testOptions {
        unitTests.isIncludeAndroidResources = true
        unitTests.all { test ->
            // Robolectric's Android 16 (API 36) runtime reaches into this JDK internal on JDK 21.
            test.jvmArgs("--add-exports=java.base/jdk.internal.access=ALL-UNNAMED")
        }
    }

    sourceSets {
        // Test helpers used by both the JVM tests and the on-phone tests.
        getByName("test").kotlin.directories += "src/sharedTest/kotlin"
        getByName("androidTest").kotlin.directories += "src/sharedTest/kotlin"
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }

    // Google-encrypted dependency metadata is only useful to the Play Store.
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    lint {
        disable += setOf(
            // Versions are pinned on purpose to match Android Studio 2025.3.1 (gradle/libs.versions.toml).
            "AndroidGradlePluginVersion",
            "GradleDependency",
            "NewerVersionAvailable",
            // Mavick runs on two ARM phones, not ChromeOS.
            "ChromeOsAbiSupport",
        )
    }
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui.tooling.preview)
    debugImplementation(libs.androidx.compose.ui.tooling)
    // Already pulled in by other libraries; declared because AndroidManifest.xml trims its startup list.
    implementation(libs.androidx.startup.runtime)

    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.sqlcipher.android)
    implementation(libs.kotlinx.coroutines.android)

    testImplementation(libs.junit)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.androidx.test.ext.junit)

    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.ext.junit)
    androidTestImplementation(libs.truth)
    androidTestImplementation(libs.kotlinx.coroutines.test)
}

// -------------------------------------------------------------------------------------------------
// Phone-safety checks. They run automatically whenever an APK is built (docs/PLAN.md §5.8).
// -------------------------------------------------------------------------------------------------

/**
 * The only permissions Mavick may request. Anything else in the merged manifest fails the build,
 * so no library can quietly add network access (or anything else). "<appId>" stands for the
 * variant's application ID.
 */
val allowedPermissions = setOf(
    // Added by androidx.core; protects the app's own internal broadcasts.
    "<appId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION",
)

/**
 * Largest APK allowed per build type, in bytes. Raise a budget deliberately (and update
 * docs/PLAN.md §5.8), never just to make a build pass. Phase 0 release: about 3.4 MB.
 */
val apkSizeBudgets = mapOf(
    "release" to 8L * 1024 * 1024,
    "debug" to 40L * 1024 * 1024,
)

androidComponents {
    onVariants { variant ->
        val variantName = variant.name
        val taskSuffix = variantName.replaceFirstChar { it.uppercase() }
        val applicationId = variant.applicationId
        val mergedManifest = variant.artifacts.get(SingleArtifact.MERGED_MANIFEST)
        val apkDirectory = variant.artifacts.get(SingleArtifact.APK)
        val buildTypeName = variant.buildType ?: variantName
        val apkSizeBudget = apkSizeBudgets[buildTypeName]
            ?: error("No APK size budget for build type '$buildTypeName'. Add one to apkSizeBudgets.")

        val checkPermissions = tasks.register("check${taskSuffix}Permissions") {
            group = "verification"
            description = "Fails if the $variantName build requests a permission that is not allow-listed."
            inputs.file(mergedManifest)
            doLast {
                val androidNamespace = "http://schemas.android.com/apk/res/android"
                val document = DocumentBuilderFactory.newInstance()
                    .apply { isNamespaceAware = true }
                    .newDocumentBuilder()
                    .parse(mergedManifest.get().asFile)

                val requested = listOf("uses-permission", "uses-permission-sdk-23").flatMap { tag ->
                    val nodes = document.getElementsByTagName(tag)
                    (0 until nodes.length).map { index ->
                        (nodes.item(index) as Element).getAttributeNS(androidNamespace, "name")
                    }
                }.toSortedSet()
                val allowed = allowedPermissions.map { it.replace("<appId>", applicationId.get()) }.toSet()
                val unexpected = requested - allowed
                if (unexpected.isNotEmpty()) {
                    throw GradleException(
                        "The $variantName build requests permissions that are not allow-listed: " +
                            "${unexpected.joinToString()}. Remove them (tools:node=\"remove\" in " +
                            "AndroidManifest.xml) or, if truly needed, add them to allowedPermissions " +
                            "in app/build.gradle.kts and docs/PLAN.md.",
                    )
                }

                val application = document.getElementsByTagName("application").item(0) as Element
                if (application.getAttributeNS(androidNamespace, "allowBackup") != "false") {
                    throw GradleException("android:allowBackup must be \"false\" in the $variantName build.")
                }

                logger.lifecycle(
                    "Permission check passed ($variantName): " +
                        requested.joinToString().ifEmpty { "no permissions requested" },
                )
            }
        }

        val checkApkSize = tasks.register("check${taskSuffix}ApkSize") {
            group = "verification"
            description = "Fails if the $variantName APK is larger than its size budget."
            inputs.dir(apkDirectory)
            doLast {
                val apks = apkDirectory.get().asFile.listFiles { file -> file.extension == "apk" }.orEmpty()
                if (apks.isEmpty()) throw GradleException("No $variantName APK found to measure.")
                apks.forEach { apk ->
                    val sizeMb = apk.length() / (1024.0 * 1024.0)
                    val budgetMb = apkSizeBudget / (1024.0 * 1024.0)
                    if (apk.length() > apkSizeBudget) {
                        throw GradleException(
                            "%s is %.1f MB, over its %.0f MB budget.".format(apk.name, sizeMb, budgetMb),
                        )
                    }
                    logger.lifecycle("APK size check passed: %s is %.1f MB (budget %.0f MB)".format(apk.name, sizeMb, budgetMb))
                }
            }
        }

        // An APK can only be packaged after its permissions pass, and every assembled APK is measured.
        tasks.matching { it.name == "package$taskSuffix" }.configureEach { dependsOn(checkPermissions) }
        tasks.matching { it.name == "assemble$taskSuffix" }.configureEach { finalizedBy(checkApkSize) }
        tasks.matching { it.name == "check" }.configureEach { dependsOn(checkPermissions) }
    }
}
