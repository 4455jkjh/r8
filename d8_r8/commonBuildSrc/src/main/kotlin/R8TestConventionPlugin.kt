// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.register
import org.gradle.testing.jacoco.plugins.JacocoTaskExtension

public abstract class SerialTestService : BuildService<BuildServiceParameters.None>

public class R8TestConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.pluginManager.apply("jacoco")
    target.tasks.named("jacocoTestReport").configure { enabled = false }

    target.tasks.named<Test>("test").configure {
      extensions.configure<JacocoTaskExtension> { isEnabled = false }
    }

    val serialTestService =
      target.gradle.sharedServices.registerIfAbsent(
        "serialTestService",
        SerialTestService::class.java,
      ) {
        maxParallelUsages.set(1)
      }

    val jacocoDir = target.layout.buildDirectory.dir("jacoco")
    val cleanTestCoverageExec =
      target.tasks.register<Delete>("cleanTestCoverageExec") { delete(jacocoDir) }

    val testSourceSet = target.extensions.getByType<SourceSetContainer>().getByName("test")
    val testCoverage =
      target.tasks.register<Test>("testCoverage") {
        dependsOn(cleanTestCoverageExec)
        testClassesDirs = testSourceSet.output.classesDirs
        classpath = testSourceSet.runtimeClasspath
        configure(isR8Lib = false, r8Jar = null, r8LibPartitionMapFile = null)
        usesService(serialTestService)
        val processors = Runtime.getRuntime().availableProcessors()
        maxParallelForks = minOf(maxParallelForks, maxOf(processors.div(8), 1))
        val activeProcessorsPerFork = maxOf(processors.div(maxParallelForks), 4)
        jvmArgs("-XX:ActiveProcessorCount=$activeProcessorsPerFork")
        extensions.configure<JacocoTaskExtension> {
          isEnabled = true
          includes = listOf("com.android.tools.*", "androidx.annotation.keep.*")
          excludes = listOf("*Test", "*Test$*", "*Tests", "*Tests$*")
        }
      }

    val jacocoExecScope = target.configurations.dependencyScope("jacocoExecScope")
    target.configurations.consumable("jacocoExecElements") {
      extendsFrom(jacocoExecScope.get())
      outgoing.artifact(jacocoDir) { builtBy(testCoverage) }
    }
  }
}
