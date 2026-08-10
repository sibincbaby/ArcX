plugins {
    id("arcx.android.library.compose")
}

android {
    namespace = "com.arcx.core.designsystem"
}

dependencies {
    implementation(libs.androidx.core.ktx)
    api(libs.compose.material.icons.extended)
}
