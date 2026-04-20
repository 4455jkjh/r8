// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

// :utils is assumed to use a subset of :main's dependencies.
dependencies {
  compileOnly(Deps.guava)

  errorprone(Deps.errorprone)
}

configureErrorProneForJavaCompile()

java {
  sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "utils", "java")) }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

val isolatedJar by configurations.consumable("isolatedJar")
val isolatedClasses by configurations.consumable("isolatedClasses")

artifacts {
  add(isolatedJar.name, tasks.named("jar"))
  add(isolatedClasses.name, tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
}
