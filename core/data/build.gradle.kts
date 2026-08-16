plugins {
    id("arcx.android.library")
    id("arcx.android.hilt")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "com.arcx.core.data"
}

room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    api(project(":core:model"))
    implementation(project(":core:common"))
    implementation(project(":core:domain"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.androidx.datastore.preferences)
    implementation(libs.kotlinx.serialization.json)

    // A real SQLite engine on the JVM, so `MigrationsTest` can execute a migration against a
    // populated v3 table and read the rows back. Asserting the SQL string instead would pass on a
    // statement SQLite rejects, and the one thing a migration must never get wrong — what happens
    // to the rows that were already there — is exactly what a string cannot show.
    testImplementation(libs.sqlite.jdbc)
}
