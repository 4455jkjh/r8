// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm") version "2.0.21" apply false
  id("dependencies-plugin")
}

tasks {
  val clean by registering {
    dependsOn(gradle.includedBuild("commonBuildSrc").task(":clean"))
    dependsOn(gradle.includedBuild("shared").task(":clean"))
    dependsOn(gradle.includedBuild("assistant").task(":clean"))
    dependsOn(gradle.includedBuild("blastradius").task(":clean"))
    dependsOn(gradle.includedBuild("keepanno").task(":clean"))
    dependsOn(":libanalyzer:clean")
    dependsOn(gradle.includedBuild("resourceshrinker").task(":clean"))
    dependsOn(":main:clean")
    dependsOn(gradle.includedBuild("library_desugar").task(":clean"))
    dependsOn(":test:clean")
    dependsOn(":dist:clean")
  }

  val r8 by registering { dependsOn(":dist:r8WithRelocatedDeps") }

  val swissArmyKnife by registering { dependsOn(":dist:swissArmyKnife") }

  val r8lib by registering { dependsOn(":test:assembleR8LibWithRelocatedDeps") }
}
