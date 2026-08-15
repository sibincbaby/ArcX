plugins {
    id("arcx.android.library.compose")
}

android {
    namespace = "com.arcx.core.designsystem"
}

dependencies {
    // Only for the category tints and the wiring chips, which have to agree across every
    // feature that draws a workflow. The panel components below stay model-agnostic.
    implementation(project(":core:model"))
    implementation(libs.androidx.core.ktx)
    api(libs.compose.material.icons.extended)
}
