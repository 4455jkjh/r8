import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.ProtobufPlugin
import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.plugins.JavaPluginExtension
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.withType
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper

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
        is KotlinBasePluginWrapper -> {
          target.extensions.getByType<KotlinJvmProjectExtension>().apply {
            explicitApi()
            compilerOptions {
              jvmTarget.set(JvmTarget.fromTarget(JvmCompatibility.release.toString()))
              languageVersion.set(KotlinVersion.KOTLIN_1_8)
              apiVersion.set(KotlinVersion.KOTLIN_1_8)
            }
          }
        }
        is ProtobufPlugin -> {
          target.extensions.getByType<ProtobufExtension>().apply {
            val os = DefaultNativePlatform.getCurrentOperatingSystem()
            protoc {
              path =
                when {
                  os.isLinux -> File(target.rootDir, "third_party/protoc/linux-x86_64/bin/protoc")
                  os.isMacOsX -> File(target.rootDir, "third_party/protoc/osx-x86_64/bin/protoc")
                  else -> {
                    check(os.isWindows)
                    File(target.rootDir, "third_party/protoc/win64/bin/protoc")
                  }
                }.path
            }
          }
        }
      }
    }
  }
}
