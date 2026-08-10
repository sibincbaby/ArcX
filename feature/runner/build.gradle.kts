plugins {
    id("arcx.android.feature")
}

android {
    namespace = "com.arcx.feature.runner"
}

dependencies {
    // The runner drives the system document picker and the notification permission prompt
    // straight from composition, which is the one thing the feature convention does not bring.
    implementation(libs.androidx.activity.compose)
}
