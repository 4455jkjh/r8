// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.Jar

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
  id("com.google.protobuf")
}

tasks.named("generateProto") { dependsOn(":third_party:downloadDeps") }

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "libanalyzer", "java"))
    proto { srcDir(getRoot().resolveAll("src", "libanalyzer", "proto")) }
  }
}

dependencies {
  compileOnly(libs.guava)
  compileOnly(libs.protobuf)
  compileOnly(project(":keepanno", "keepannoClasses"))
  compileOnly(project(":main", "mainClassesOutput"))
  compileOnly(project(":main", "turboClassesOutput"))
  errorprone(libs.errorprone)
}

val jarTask =
  tasks.named<Jar>("jar") {
    exclude("libraryanalyzerresult.proto")
    exclude("com/android/tools/r8/libanalyzer/proto/**")
    archiveFileName.set("libanalyzer-exclude-deps.jar")
    destinationDirectory.set(layout.buildDirectory.dir("sub-libs"))
  }

val protoJarTask =
  tasks.register<Jar>("protoJar") {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/libanalyzer/proto/**")
    archiveFileName.set("libanalyzer-proto.jar")
    destinationDirectory.set(layout.buildDirectory.dir("sub-libs"))
  }

val libanalyzerJar by configurations.consumable("libanalyzer-jar") { outgoing.artifact(jarTask) }

val libanalyzerProtoJar by
  configurations.consumable("libanalyzer-proto-jar") { outgoing.artifact(protoJarTask) }

val libanalyzerSourcesJar by
  configurations.consumable("libanalyzer-sources-jar") {
    outgoing.artifact(tasks.named<Jar>("sourcesJar"))
  }

val compileJavaJarTask =
  tasks.register<Jar>("compileJavaJar") {
    from(tasks.named("compileJava"))
    archiveFileName.set("libanalyzer-compile-java.jar")
    destinationDirectory.set(project.layout.buildDirectory.dir("sub-libs"))
  }

val libanalyzerCompileJava by
  configurations.consumable("libanalyzer-compile-java") { outgoing.artifact(compileJavaJarTask) }

tasks.withType<JavaCompile> {
  options.errorprone.excludedPaths.set(".*/build/generated/source/proto/main/java/.*")
}

configureErrorProneForJavaCompile()
