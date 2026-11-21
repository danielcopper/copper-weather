import org.gradle.testing.jacoco.plugins.JacocoTaskExtension
import org.sonarqube.gradle.SonarExtension

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ktlint)
    alias(libs.plugins.detekt)
}

// The app's version, from version.txt (release.yml says how it gets there).
val appVersion =
    providers
        .fileContents(rootProject.layout.projectDirectory.file("version.txt"))
        .asText
        .get()
        .trim()

/**
 * The version code of a "major.minor.patch" version: major × 1,000,000 + minor × 1,000 + patch, so a later release
 * always has a larger code while minor and patch stay below 1,000, which this checks. VersionTest restates the rule
 * to check the built app against it.
 */
fun versionCodeOf(version: String): Int {
    val parts = version.split(".").map { it.toIntOrNull() ?: -1 }
    require(parts.size == 3 && parts[0] >= 0 && parts[1] in 0..999 && parts[2] in 0..999) {
        "version.txt holds \"$version\", not major.minor.patch with minor and patch below 1000"
    }
    val (major, minor, patch) = parts
    return major * 1_000_000 + minor * 1_000 + patch
}

android {
    namespace = "dev.cupr.weather"
    compileSdk = 37

    defaultConfig {
        applicationId = "dev.cupr.weather"
        minSdk = 31
        targetSdk = 37
        versionCode = versionCodeOf(appVersion)
        versionName = appVersion
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
            enableUnitTestCoverage = true
        }
    }

    buildFeatures {
        compose = true
    }

    packaging {
        jniLibs {
            // Compose's one native library; without an NDK AGP cannot strip it and says so on every build. It
            // ships as AndroidX builds it either way.
            keepDebugSymbols += "**/libandroidx.graphics.path.so"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { test ->
                // Robolectric needs more than the default 512 MB test heap.
                test.maxHeapSize = "1536m"
                // Robolectric sets up API 37's shared memory through JDK internals.
                test.jvmArgs("--add-opens=java.base/jdk.internal.access=ALL-UNNAMED")
                // Some tests read files rather than what the build makes of them: the module's sources, version.txt,
                // and the weather icons' script and SVGs (WeatherIconsTest). Declared as inputs, a change to them runs
                // the tests again rather than taking their result from the build cache.
                test.inputs
                    .dir("src")
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("sources")
                test.inputs
                    .dir(rootProject.layout.projectDirectory.dir("scripts"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("scripts")
                test.inputs
                    .dir(rootProject.layout.projectDirectory.dir("icons"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("icons")
                test.inputs
                    .file(rootProject.layout.projectDirectory.file("version.txt"))
                    .withPathSensitivity(PathSensitivity.RELATIVE)
                    .withPropertyName("versionFile")
                // Coverage of the classes Robolectric loads into its sandbox.
                test.extensions.configure<JacocoTaskExtension> {
                    isIncludeNoLocationClasses = true
                    excludes = listOf("jdk.internal.*")
                }
            }
        }
    }

    lint {
        warningsAsErrors = true
        abortOnError = true
        checkDependencies = true
    }
}

// The unit tests' coverage, written by `createDebugUnitTestCoverageReport`.
extensions.configure<SonarExtension> {
    properties {
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory
                .file("reports/coverage/test/debug/report.xml")
                .get()
                .asFile.absolutePath,
        )
    }
}

kotlin {
    compilerOptions {
        allWarningsAsErrors = true
    }
}

ktlint {
    version.set(
        libs.versions.ktlint.cli
            .get(),
    )
}

detekt {
    buildUponDefaultConfig = true
    config.setFrom("detekt.yml")
    source.setFrom("src/main/kotlin", "src/test/kotlin")
}

// The resolved versions of this module's configurations are locked in gradle.lockfile (`mise run deps-refresh`).
dependencyLocking {
    lockAllConfigurations()
}

dependencies {
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    constraints {
        // Lifecycle in the catalog's version, not the older one activity-compose asks for (libs.versions.toml);
        // Lifecycle's libraries align with each other, so this lifts them all.
        implementation(libs.lifecycle.runtime)
    }
    implementation(libs.okhttp)
    implementation(libs.work.runtime)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.robolectric)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.work.testing)
    // The app's screen under Robolectric: ui-test-junit4 in the Compose BOM's version, espresso-core in its own
    // (libs.versions.toml).
    testImplementation(libs.compose.ui.test.junit4)
    testImplementation(libs.espresso.core)
}
