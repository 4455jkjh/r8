// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Action
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ConfigurationContainer
import org.gradle.api.artifacts.ConsumableConfiguration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.ResolvableConfiguration
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider

public val R8_FLAVOR_ATTRIBUTE: Attribute<String> =
  Attribute.of("com.android.tools.r8.flavor", String::class.java)

public enum class R8Flavor(public val attributeValue: String) {
  STANDARD("standard"),
  INTERNAL("internal"),
  API_EXTERNAL("api-external"),
  EXTERNAL("external"),
  INTERNAL_CLASSES("internal-classes"),
  INTERNAL_SOURCES("internal-sources"),
}

public fun ConfigurationContainer.resolvableFlavor(
  name: String,
  flavor: R8Flavor,
  vararg scopes: NamedDomainObjectProvider<out Configuration>,
): NamedDomainObjectProvider<ResolvableConfiguration> =
  resolvable(name) {
    scopes.forEach { extendsFrom(it.get()) }
    attributes { attribute(R8_FLAVOR_ATTRIBUTE, flavor.attributeValue) }
  }

public fun ConfigurationContainer.consumableFlavor(
  name: String,
  flavor: R8Flavor,
  vararg scopes: NamedDomainObjectProvider<out Configuration>,
): NamedDomainObjectProvider<ConsumableConfiguration> =
  consumable(name) {
    scopes.forEach { extendsFrom(it.get()) }
    attributes { attribute(R8_FLAVOR_ATTRIBUTE, flavor.attributeValue) }
  }

public fun DependencyHandler.apiInternal(dependencyNotation: Any): Dependency? {
  return add("apiInternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.apiInternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("apiInternal", dependencyNotation, configuration)
}

public fun DependencyHandler.implementationInternal(dependencyNotation: Any): Dependency? {
  return add("implementationInternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.implementationInternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("implementationInternal", dependencyNotation, configuration)
}

public fun DependencyHandler.runtimeOnlyInternal(dependencyNotation: Any): Dependency? {
  return add("runtimeOnlyInternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.runtimeOnlyInternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("runtimeOnlyInternal", dependencyNotation, configuration)
}

public fun DependencyHandler.apiExternal(dependencyNotation: Any): Dependency? {
  return add("apiExternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.apiExternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("apiExternal", dependencyNotation, configuration)
}

public fun DependencyHandler.implementationExternal(dependencyNotation: Any): Dependency? {
  return add("implementationExternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.implementationExternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("implementationExternal", dependencyNotation, configuration)
}

public fun DependencyHandler.runtimeOnlyExternal(dependencyNotation: Any): Dependency? {
  return add("runtimeOnlyExternal", dependencyNotation)
}

public fun <T : Any> DependencyHandler.runtimeOnlyExternal(
  dependencyNotation: Provider<T>,
  configuration: Action<in ExternalModuleDependency>,
) {
  addProvider("runtimeOnlyExternal", dependencyNotation, configuration)
}

/**
 * Configures R8's dependency flavor attributes, custom scopes, and transitive artifact views for
 * Java modules.
 *
 * This plugin must be applied to Java:
 * - **Participants** (compiler modules, via `r8-compiler-conventions` or directly), which declare
 *   dependencies using the custom scopes below and attach outgoing artifacts to the `*Elements`
 *   variants they contribute to.
 * - **Consumers** (tool and test modules, via `r8-test-conventions` or direct plugin application),
 *   which need `R8_FLAVOR_ATTRIBUTE = "standard"` on their Java compile/runtime classpaths for
 *   variant disambiguation and/or resolve the `*Config` views below.
 *
 * Non-Java packaging and distribution modules (using `java-base`, such as `:swissarmyknife` and
 * `:dist`) do not need this plugin and can declare flavor-attributed configurations directly using
 * [resolvableFlavor] and [consumableFlavor] with [R8Flavor].
 *
 * The custom dependency scopes mirror Gradle's `api`, `implementation`, and `runtimeOnly` semantics
 * for modules that participate in the compiler build:
 * - `apiInternal` / `implementationInternal` / `runtimeOnlyInternal`: First-party R8 project
 *   dependencies whose flavor views (`internalElements`, `internalClassesElements`,
 *   `internalSourcesElements`, `externalElements`) are traversed transitively.
 * - `apiExternal` / `implementationExternal` / `runtimeOnlyExternal`: Third-party library
 *   dependencies. A module's own `apiExternal` is exposed on its compile API (`apiElements`) and
 *   `apiExternalElements` (transitively across `apiInternal`) rather than its own standalone
 *   `externalRuntimeConfig`, while `externalElements` exports all `*External` dependencies so
 *   downstream consumers bundle them into `deps.jar` / `r8.jar`.
 *
 * Resolution flavors selected via [R8_FLAVOR_ATTRIBUTE]:
 * - `standard`: Default Gradle compile/runtime classpaths (`apiElements` exposes `api`,
 *   `apiInternal`, and `apiExternal`; `runtimeElements` excludes external jars so consumers control
 *   how third-party deps are bundled).
 * - `internal`: Transitive first-party module jars (`internalElements` -> `internalRuntimeConfig`).
 * - `api-external`: Unbundled third-party API jars exposed by a module (`apiExternalElements` ->
 *   `apiExternalConfig`).
 * - `external`: Transitive third-party runtime jars across internal modules (`externalElements` ->
 *   `externalRuntimeConfig`).
 * - `internal-classes`: Transitive first-party compiled class directories
 *   (`internalClassesElements` -> `internalClassesConfig`).
 * - `internal-sources`: Transitive first-party source jars (`internalSourcesElements` ->
 *   `internalSourcesConfig`).
 */
public class R8DependencyFlavorsPlugin : Plugin<Project> {

  override fun apply(target: Project) {
    // Dependency scopes declared on participating projects.
    val apiInternal = target.configurations.dependencyScope("apiInternal")
    val implementationInternal = target.configurations.dependencyScope("implementationInternal")
    val runtimeOnlyInternal = target.configurations.dependencyScope("runtimeOnlyInternal")
    val apiExternal = target.configurations.dependencyScope("apiExternal")
    val implementationExternal = target.configurations.dependencyScope("implementationExternal")
    val runtimeOnlyExternal = target.configurations.dependencyScope("runtimeOnlyExternal")

    target.configurations.configureEach {
      if (name == "api") {
        extendsFrom(apiInternal.get())
      }
    }

    target.plugins.withType(JavaPlugin::class.java) {
      // Standard Java classpath integration ("standard" flavor).
      val implementation =
        target.configurations.named("implementation") {
          extendsFrom(apiInternal.get(), implementationInternal.get())
        }
      val runtimeOnly =
        target.configurations.named("runtimeOnly") { extendsFrom(runtimeOnlyInternal.get()) }
      target.configurations.named("apiElements") {
        extendsFrom(apiInternal.get(), apiExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }
      target.configurations.named("runtimeElements") {
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }
      target.configurations.named("compileClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }
      target.configurations.named("runtimeClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get(), runtimeOnlyExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }
      target.configurations.named("testCompileClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }
      target.configurations.named("testRuntimeClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get(), runtimeOnlyExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, R8Flavor.STANDARD.attributeValue) }
      }

      // Transitive artifact views (consumable + resolvable pairs per flavor).
      target.configurations.consumableFlavor(
        "internalElements",
        R8Flavor.INTERNAL,
        apiInternal,
        implementationInternal,
        runtimeOnlyInternal,
      )
      target.configurations.resolvableFlavor(
        "internalRuntimeConfig",
        R8Flavor.INTERNAL,
        apiInternal,
        implementationInternal,
        runtimeOnlyInternal,
      )

      target.configurations.consumableFlavor(
        "apiExternalElements",
        R8Flavor.API_EXTERNAL,
        apiInternal,
        apiExternal,
      )
      target.configurations.resolvableFlavor(
        "apiExternalConfig",
        R8Flavor.API_EXTERNAL,
        apiInternal,
        apiExternal,
      )

      target.configurations.consumableFlavor(
        "externalElements",
        R8Flavor.EXTERNAL,
        apiInternal,
        implementationInternal,
        runtimeOnlyInternal,
        apiExternal,
        implementationExternal,
        runtimeOnlyExternal,
      )
      target.configurations.resolvableFlavor(
        "externalRuntimeConfig",
        R8Flavor.EXTERNAL,
        implementation,
        runtimeOnly,
        implementationExternal,
        runtimeOnlyExternal,
      )

      target.configurations.consumableFlavor(
        "internalClassesElements",
        R8Flavor.INTERNAL_CLASSES,
        apiInternal,
        implementationInternal,
        runtimeOnlyInternal,
      )
      target.configurations.resolvableFlavor(
        "internalClassesConfig",
        R8Flavor.INTERNAL_CLASSES,
        implementation,
        runtimeOnlyInternal,
      )

      target.configurations.consumableFlavor(
        "internalSourcesElements",
        R8Flavor.INTERNAL_SOURCES,
        apiInternal,
        implementationInternal,
        runtimeOnlyInternal,
      )
      target.configurations.resolvableFlavor(
        "internalSourcesConfig",
        R8Flavor.INTERNAL_SOURCES,
        implementation,
        runtimeOnlyInternal,
      )
    }
  }
}
