plugins {
    id("arcx.android.feature")
}

android {
    namespace = "com.arcx.feature.workflow"
}

dependencies {
    // BackHandler, so system back can stop and ask before throwing away an unsaved workflow.
    implementation(libs.androidx.activity.compose)
}
