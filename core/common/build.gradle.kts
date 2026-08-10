plugins {
    id("arcx.android.library")
    id("arcx.android.hilt")
}

android {
    namespace = "com.arcx.core.common"
}

dependencies {
    api(project(":core:model"))
    implementation(libs.androidx.core.ktx)
}
