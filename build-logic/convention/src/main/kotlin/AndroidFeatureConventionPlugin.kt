import com.arcx.buildlogic.libs
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.project

/**
 * Every :feature module gets Compose, Hilt, navigation and the core modules it always needs.
 * Keeps feature build files down to a plugins block.
 */
class AndroidFeatureConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) = with(target) {
        pluginManager.apply("arcx.android.library.compose")
        pluginManager.apply("arcx.android.hilt")

        dependencies {
            add("implementation", project(":core:model"))
            add("implementation", project(":core:common"))
            add("implementation", project(":core:designsystem"))
            add("implementation", project(":core:domain"))
            add("implementation", libs.findLibrary("androidx-core-ktx").get())
            // Not pulled in transitively by navigation-compose or hilt-navigation-compose,
            // but every feature needs it for BackHandler and SAF result launchers.
            add("implementation", libs.findLibrary("androidx-activity-compose").get())
            add("implementation", libs.findLibrary("androidx-lifecycle-runtime-ktx").get())
            add("implementation", libs.findLibrary("androidx-navigation-compose").get())
            add("implementation", libs.findLibrary("androidx-hilt-navigation-compose").get())
            add("implementation", libs.findLibrary("compose-material-icons-extended").get())
        }
    }
}
