plugins {
    id("arcx.android.feature")
    alias(libs.plugins.kotlin.serialization)
}

android {
    namespace = "com.arcx.feature.discover"
}

dependencies {
    // The bundled gallery and the import/export envelope are the same JSON shape.
    implementation(libs.kotlinx.serialization.json)
    // SAF document pickers, launched from Compose.
    implementation(libs.androidx.activity.compose)
}
