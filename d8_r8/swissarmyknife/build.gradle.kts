// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.kotlin.dsl.newInstance

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
}

interface InjectedArcOps {
  @get:Inject val arcOps: ArchiveOperations
}

val bundleScope by configurations.dependencyScope("bundleScope")
val bundleConfig by configurations.resolvable("bundleConfig") { extendsFrom(bundleScope) }

dependencies {
  bundleScope(project(":assistant", "assistantJar"))
  bundleScope(project(":keepradius", "keepradiusWithoutProtoJar"))
  bundleScope(project(":keepanno", "keepannoJar"))
  bundleScope(project(":libanalyzer", "libanalyzer-jar"))
  bundleScope(project(":main", "mainJar"))
  bundleScope(project(":resourceshrinker", "resourceshrinkerJar"))
}

tasks {
  named<Jar>("jar") {
    val injected = project.objects.newInstance<InjectedArcOps>()
    from(
      bundleConfig.elements.map {
        it.map {
          injected.arcOps.zipTree(it).matching {
            exclude("com/android/tools/r8/threading/providers/**")
            exclude("META-INF/*.kotlin_module")
            exclude("**/*.kotlin_metadata")
            exclude("keepradius.proto")
            exclude("keepspec.proto")
            exclude("LICENSE")
            exclude("androidx/")
            exclude("androidx/annotation/")
            exclude("androidx/annotation/keep/**")
          }
        }
      }
    )
    from(File(getRootDir(), "LICENSE"))
    entryCompression = ZipEntryCompression.STORED
    manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
    destinationDirectory.set(File(getRootDir(), "build/libs"))
    archiveFileName.set("r8-full-exclude-deps.jar")
  }
}
