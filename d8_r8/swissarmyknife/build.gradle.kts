// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.tasks.bundling.Jar

plugins {
  `java-base`
  id("dependencies-plugin")
}

val compilerModules = configurations.dependencyScope("compilerModules")

dependencies {
  compilerModules(project(":libanalyzer"))
  compilerModules(project(":main"))
}

val internalJars =
  configurations.resolvableFlavor("internalJars", R8Flavor.INTERNAL, compilerModules)

configurations.consumableFlavor("internalElements", R8Flavor.INTERNAL, compilerModules)

configurations.consumableFlavor("externalElements", R8Flavor.EXTERNAL, compilerModules)

val jarTask =
  tasks.register<Jar>("jar") {
    from(zipTrees(internalJars)) {
      exclude("com/android/tools/r8/keepradius/proto/**")
      exclude("com/android/tools/r8/libanalyzer/proto/**")
      exclude("META-INF/*.kotlin_module")
      exclude("**/*.kotlin_metadata")
      exclude("keepradius.proto")
      exclude("keepradiussummary.proto")
      exclude("keepspec.proto")
      exclude("libraryanalyzerresult.proto")
      exclude("LICENSE")
      exclude("androidx/")
      exclude("androidx/annotation/")
      exclude("androidx/annotation/keep/**")
    }
    from(File(getRootDir(), "LICENSE"))
    entryCompression = ZipEntryCompression.STORED
    manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
    destinationDirectory.set(File(getRootDir(), "build/libs"))
    archiveFileName.set("r8-full-exclude-deps.jar")
  }

configurations.consumable("swissArmyKnifeJar") { outgoing.artifact(jarTask) }
