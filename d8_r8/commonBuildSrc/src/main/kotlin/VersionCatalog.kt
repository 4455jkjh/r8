import org.gradle.api.GradleException
import org.gradle.api.Project
import org.gradle.api.artifacts.MinimalExternalModuleDependency
import org.gradle.api.artifacts.VersionCatalog
import org.gradle.api.artifacts.VersionCatalogsExtension

private val Project.versionCatalog: VersionCatalog
  get() = project.extensions.getByType(VersionCatalogsExtension::class.java).find("libs").get()

public fun Project.getLibraryByName(name: String): MinimalExternalModuleDependency {
  val library = versionCatalog.findLibrary(name)
  return if (library.isPresent) {
    library.get().get()
  } else {
    throw GradleException("Could not find a library for `$name`")
  }
}
