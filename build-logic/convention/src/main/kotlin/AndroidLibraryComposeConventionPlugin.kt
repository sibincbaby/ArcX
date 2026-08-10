import com.android.build.api.dsl.LibraryExtension
import com.arcx.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("arcx.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }

        dependencies { addComposeDependencies(this@with) }
    }
}

/** Shared by the compose library and application conventions. */
internal fun DependencyHandler.addComposeDependencies(project: Project) {
    val libs = project.libs
    val bom = project.dependencies.platform(libs.findLibrary("compose-bom").get())
    add("implementation", bom)
    add("androidTestImplementation", bom)
    add("implementation", libs.findLibrary("compose-ui").get())
    add("implementation", libs.findLibrary("compose-ui-graphics").get())
    add("implementation", libs.findLibrary("compose-ui-tooling-preview").get())
    add("implementation", libs.findLibrary("compose-foundation").get())
    add("implementation", libs.findLibrary("compose-material3").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-runtime-compose").get())
    add("implementation", libs.findLibrary("androidx-lifecycle-viewmodel-compose").get())
    add("debugImplementation", libs.findLibrary("compose-ui-tooling").get())
}
