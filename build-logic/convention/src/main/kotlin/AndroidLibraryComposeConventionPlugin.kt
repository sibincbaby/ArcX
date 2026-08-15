import com.android.build.api.dsl.LibraryExtension
import com.arcx.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.jetbrains.kotlin.compose.compiler.gradle.ComposeCompilerGradlePluginExtension

class AndroidLibraryComposeConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("arcx.android.library")
        pluginManager.apply("org.jetbrains.kotlin.plugin.compose")

        extensions.configure<LibraryExtension> {
            buildFeatures.compose = true
        }
        configureCompose()

        dependencies { addComposeDependencies(this@with) }
    }
}

/**
 * Shared Compose compiler setup: the stability configuration always, the stability report on
 * demand.
 *
 * The configuration file is what tells the compiler that :core:model is safe to compare by value.
 * Without it every domain type is assumed unstable, which turns every list row into an
 * instance-equality comparison against an object the ViewModel just reallocated.
 *
 * The report is off by default because it costs build time and writes files nobody wants in a
 * normal build.
 *
 * Off by default because it costs build time and writes files nobody wants in a normal build.
 * It is the only way to see which composables the compiler decided it cannot skip, and which
 * parameter made that decision — guessing at that from the source is how an app ends up
 * recomposing whole screens because one field in a state class is a plain List.
 *
 *     ./gradlew assembleDebug -Pcompose.reports
 *     build/compose-reports/  (module-composables.txt, module-classes.txt)
 */
internal fun Project.configureCompose() {
    extensions.configure<ComposeCompilerGradlePluginExtension> {
        stabilityConfigurationFiles.add(
            isolated.rootProject.projectDirectory.file("compose-stability.conf"),
        )
        if (providers.gradleProperty("compose.reports").isPresent) {
            val dir = layout.buildDirectory.dir("compose-reports")
            reportsDestination.set(dir)
            metricsDestination.set(dir)
        }
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
