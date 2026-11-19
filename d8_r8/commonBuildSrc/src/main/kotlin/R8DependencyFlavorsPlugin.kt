// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Action
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalModuleDependency
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.attributes.Attribute
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider

public val R8_FLAVOR_ATTRIBUTE: Attribute<String> =
  Attribute.of("com.android.tools.r8.flavor", String::class.java)

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
 * Configures R8's dependency flavor attributes, custom scopes, and transitive artifact views.
 *
 * This plugin must be applied to both:
 * - **Participants** (compiler and distribution modules, via `r8-compiler-conventions`), which
 *   declare dependencies using the custom scopes below and expose consumable `*Elements` variants.
 * - **Consumers** (packaging, tool, and test modules, via `r8-test-conventions` or direct plugin
 *   application), which need `R8_FLAVOR_ATTRIBUTE = "standard"` on their Java compile/runtime
 *   classpaths for variant disambiguation and/or resolve the `*Config` views below.
 *
 * The custom dependency scopes mirror Gradle's `api`, `implementation`, and `runtimeOnly` semantics
 * for modules that contribute code to the compiler:
 * - `apiInternal` / `implementationInternal` / `runtimeOnlyInternal`: First-party R8 project
 *   dependencies whose jars, classes, and sources belong in `r8-full-exclude-deps.jar`, `r8.jar`,
 *   `r8-src.jar`, and coverage, and whose external dependencies propagate when encapsulated.
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
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }
      target.configurations.named("runtimeElements") {
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }
      target.configurations.named("compileClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }
      target.configurations.named("runtimeClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get(), runtimeOnlyExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }
      target.configurations.named("testCompileClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }
      target.configurations.named("testRuntimeClasspath") {
        extendsFrom(apiExternal.get(), implementationExternal.get(), runtimeOnlyExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "standard") }
      }

      // Transitive artifact views (consumable + resolvable pairs per flavor).
      target.configurations.consumable("internalElements") {
        extendsFrom(apiInternal.get(), implementationInternal.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal") }
        outgoing.artifact(target.tasks.named("jar"))
      }
      target.configurations.resolvable("internalRuntimeConfig") {
        extendsFrom(apiInternal.get(), implementationInternal.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal") }
      }

      target.configurations.consumable("apiExternalElements") {
        extendsFrom(apiInternal.get(), apiExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "api-external") }
      }
      target.configurations.resolvable("apiExternalConfig") {
        extendsFrom(apiInternal.get(), apiExternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "api-external") }
      }

      target.configurations.consumable("externalElements") {
        extendsFrom(
          apiInternal.get(),
          implementationInternal.get(),
          runtimeOnlyInternal.get(),
          apiExternal.get(),
          implementationExternal.get(),
          runtimeOnlyExternal.get(),
        )
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "external") }
      }
      target.configurations.resolvable("externalRuntimeConfig") {
        extendsFrom(
          implementation.get(),
          runtimeOnly.get(),
          implementationExternal.get(),
          runtimeOnlyExternal.get(),
        )
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "external") }
      }

      target.configurations.consumable("internalClassesElements") {
        extendsFrom(apiInternal.get(), implementationInternal.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal-classes") }
      }
      target.configurations.resolvable("internalClassesConfig") {
        extendsFrom(implementation.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal-classes") }
      }

      target.configurations.consumable("internalSourcesElements") {
        extendsFrom(apiInternal.get(), implementationInternal.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal-sources") }
      }
      target.configurations.resolvable("internalSourcesConfig") {
        extendsFrom(implementation.get(), runtimeOnlyInternal.get())
        attributes { attribute(R8_FLAVOR_ATTRIBUTE, "internal-sources") }
      }
    }
  }
}
