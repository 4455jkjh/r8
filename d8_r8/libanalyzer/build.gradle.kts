// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
  id("com.google.protobuf")
}

val srcDir = getRoot().resolveAll("src", "libanalyzer", "java")

java {
  sourceSets.main.configure {
    java.srcDir(srcDir)
    proto { srcDir(getRoot().resolveAll("src", "libanalyzer", "proto")) }
  }
}

dependencies {
  compileOnly(libs.guava)
  compileOnly(libs.protobuf)
  compileOnly(project(":keepanno", "keepannoClasses"))
  compileOnly(project(":main", "mainClassesOutput"))
}

configurations.consumable("libanalyzerJar") { outgoing.artifact(tasks.named("jar")) }

configurations.consumable("libanalyzerClasses") {
  outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
  outgoing.artifact(
    tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin").map {
      it.destinationDirectory
    }
  )
}

configurations.consumable("libanalyzerSources") { outgoing.artifact(srcDir) }
