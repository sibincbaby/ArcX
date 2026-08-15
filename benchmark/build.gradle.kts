plugins {
    alias(libs.plugins.android.test)
}

/**
 * Macrobenchmark: startup and frame timing, measured by driving the installed app from a separate
 * process.
 *
 * This exists because `dumpsys gfxinfo` could not answer the question it was being asked. Four
 * consecutive runs of an identical build varied by 25% at the median and 50% at the 90th
 * percentile — wider than any change worth making — because there was no warm-up, no repetition
 * and no control over compilation state. Macrobenchmark handles all three and reports a
 * confidence interval, so a result either clears the noise or is honestly reported as not
 * clearing it.
 */
android {
    namespace = "com.arcx.benchmark"
    compileSdk = libs.versions.compileSdk.get().toInt()

    defaultConfig {
        // 24 is the floor for macrobenchmark; the app's own floor is higher and unaffected.
        minSdk = 24
        targetSdk = libs.versions.targetSdk.get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    // This is the harness, not the thing being measured, so it is an ordinary debuggable APK
    // signed with the debug key. What must not be debuggable is the app it drives — see the
    // matching `benchmark` build type in :app, which is release-like on purpose because a
    // debuggable target would have ART's optimisations turned off underneath the measurement.
    buildTypes {
        create("benchmark") {
            isDebuggable = true
            signingConfig = getByName("debug").signingConfig
            // Falls back to the app's release configuration for anything not set here.
            matchingFallbacks += listOf("release")
        }
    }

    targetProjectPath = ":app"

    // Self-instrumenting: the benchmark runs in its own process so it can stop, start and
    // recompile the app under test. Without this it would share a process with its target and
    // could not measure a cold start at all.
    experimentalProperties["android.experimental.self-instrumenting"] = true
}

dependencies {
    implementation(libs.androidx.benchmark.macro.junit4)
    implementation(libs.androidx.test.ext.junit)
    implementation(libs.androidx.test.runner)
    implementation(libs.androidx.uiautomator)
    implementation(libs.junit)
}

androidComponents {
    // Only the benchmark variant is worth building; the others would be empty artifacts.
    beforeVariants { it.enable = it.buildType == "benchmark" }
}
