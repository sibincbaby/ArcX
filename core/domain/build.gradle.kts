plugins {
    id("arcx.android.library")
    id("arcx.android.hilt")
}

android {
    namespace = "com.arcx.core.domain"
}

dependencies {
    api(project(":core:model"))
    // api: TimeSource and PromptTemplate show up in use-case constructors and prompt tooling.
    api(project(":core:common"))
}
