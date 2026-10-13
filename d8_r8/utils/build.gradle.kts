// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

// :utils is assumed to use a subset of :main's dependencies.
dependencies {
  compileOnly(libs.guava)
  compileOnly(libs.fastUtil)
}

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "utils", "java")) } }

configurations.consumable("isolatedJar") { outgoing.artifact(tasks.named("jar")) }

configurations.consumable("isolatedClasses") {
  outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
}
