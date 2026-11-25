// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.getByType
import org.jetbrains.kotlin.gradle.plugin.KotlinBasePluginWrapper
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Convention plugin for Java/Kotlin modules that contribute code to the R8 compiler.
 *
 * Applies both `r8-conventions` (JVM/Kotlin/Protobuf/ErrorProne toolchain setup) and
 * `r8-dependency-flavors`, and publishes the module's jar, compiled class directories, sources
 * jars, and the Kotlin standard library into the corresponding flavor configurations.
 */
public class R8CompilerConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    // Prevent org.jetbrains.kotlin.jvm plugin from auto-adding kotlin-stdlib to `api` (which
    // would leak it onto apiElements, runtimeElements, externalRuntimeConfig, and
    // internal*Config); we add libs.kotlinStdLib to implementationExternal below instead. KGP
    // reads this property when applied, so r8-compiler-conventions must be applied before
    // org.jetbrains.kotlin.jvm.
    check(!target.plugins.hasPlugin(KotlinBasePluginWrapper::class.java)) {
      "Apply r8-compiler-conventions before org.jetbrains.kotlin.jvm so " +
        "kotlin.stdlib.default.dependency=false takes effect."
    }
    target.extensions.extraProperties.set("kotlin.stdlib.default.dependency", "false")
    target.pluginManager.apply("r8-conventions")
    target.pluginManager.apply("r8-dependency-flavors")
    target.plugins.configureEach {
      when (this) {
        is JavaPlugin -> {
          target.configurations.named("internalElements") {
            outgoing.artifact(target.tasks.named("jar"))
          }
          target.configurations.named("internalClassesElements") {
            outgoing.artifact(
              target.tasks.named("compileJava", JavaCompile::class.java).map {
                it.destinationDirectory
              }
            )
          }
          target.configurations.named("internalSourcesElements") {
            outgoing.artifact(target.tasks.named("sourcesJar"))
          }
        }
        is KotlinBasePluginWrapper -> {
          target.configurations.named("internalClassesElements") {
            outgoing.artifact(
              target.tasks.named("compileKotlin", KotlinCompile::class.java).map {
                it.destinationDirectory
              }
            )
          }
          val libs = target.extensions.getByType<VersionCatalogsExtension>().named("libs")
          target.dependencies.add("implementationExternal", libs.findLibrary("kotlinStdLib").get())
        }
      }
    }
  }
}
