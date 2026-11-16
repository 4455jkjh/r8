// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
  id("com.google.protobuf")
}

val srcDir = getRoot().resolveAll("src", "keepradius", "java")

java {
  sourceSets.main.configure {
    java.srcDir(srcDir)
    proto { srcDir(getRoot().resolveAll("src", "keepradius", "proto")) }
  }
}

dependencies {
  compileOnly(project(":keepanno", "keepannoClasses"))
  compileOnly(libs.protobuf)
  compileOnly(libs.protobufUtil)
}

configurations.consumable("keepradiusJar") { outgoing.artifact(tasks.named("jar")) }

configurations.consumable("keepradiusClasses") {
  outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
}

configurations.consumable("keepradiusSources") { outgoing.artifact(srcDir) }
