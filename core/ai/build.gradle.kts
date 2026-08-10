plugins {
    id("arcx.android.library")
    id("arcx.android.hilt")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.arcx.core.ai"
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.retrofit.core)
    implementation(libs.okhttp.core)
    implementation(libs.okhttp.logging)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(libs.okhttp.mockwebserver)
}
