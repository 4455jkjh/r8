// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm") version "2.2.21" apply false
  id("com.google.protobuf") version "0.9.4" apply false
  id("dependencies-plugin")
}

tasks {
  register<Delete>("clean") {
    delete(rootProject.layout.buildDirectory)
    gradle.includedBuilds.forEach { dependsOn(it.task(":clean")) }
    subprojects.forEach { dependsOn(it.tasks.named("clean")) }
  }
  register("r8") { dependsOn(":dist:r8WithRelocatedDeps") }
  register("swissArmyKnife") { dependsOn(":dist:swissArmyKnife") }
  register("r8lib") { dependsOn(":test:assembleR8LibWithRelocatedDeps") }
}
