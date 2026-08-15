plugins {
    id("arcx.android.feature")
}

android {
    namespace = "com.arcx.feature.discover"
}

dependencies {
    // No kotlinx-serialization here on purpose. The bundle envelope and the Json that reads it
    // belong to :core:data, behind WorkflowBundleRepository; this module only ever handles the
    // WorkflowSpec it hands back, which comes from :core:model.

    // SAF document pickers, launched from Compose.
    implementation(libs.androidx.activity.compose)
}
