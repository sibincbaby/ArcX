plugins {
    id("arcx.android.library.compose")
    id("arcx.android.hilt")
}

android {
    namespace = "com.arcx.integration.entrypoints"
}

dependencies {
    implementation(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:designsystem"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.glance.appwidget)
    implementation(libs.androidx.glance.material3)
}
