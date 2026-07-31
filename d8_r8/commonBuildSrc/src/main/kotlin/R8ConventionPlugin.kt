import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType

public class R8ConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.plugins.configureEach {
      when (this) {
        is JavaPlugin -> {
          target.extensions.getByType<JavaPluginExtension>().apply {
            sourceCompatibility = JvmCompatibility.sourceCompatibility
            targetCompatibility = JvmCompatibility.targetCompatibility
            toolchain { languageVersion.set(JavaLanguageVersion.of(JvmCompatibility.release)) }
            withSourcesJar()
          }
          target.tasks.withType<JavaCompile>().configureEach {
            options.release.set(JvmCompatibility.release)
          }
        }
      }
    }
  }
}
