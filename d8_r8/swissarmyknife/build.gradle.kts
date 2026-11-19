// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-compiler-conventions")
  id("dependencies-plugin")
}

dependencies {
  implementationInternal(project(":libanalyzer"))
  implementationInternal(project(":main"))
}

val internalRuntimeConfig = configurations.named("internalRuntimeConfig")

tasks {
  named<Jar>("jar") {
    from(zipTrees(internalRuntimeConfig)) {
      exclude("com/android/tools/r8/threading/providers/**")
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
}

configurations.consumable("swissArmyKnifeJar") { outgoing.artifact(tasks.named("jar")) }
