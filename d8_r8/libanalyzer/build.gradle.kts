// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.proto
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.Jar
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  `kotlin-dsl`
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

// It seems like the use of a local maven repo does not allow adding the plugin with the id+version
// syntax. Also, for some reason the 'protobuf' extension object cannot be directly referenced.
// This configures the plugin "old style" and pulls out the extension object manually.
buildscript { dependencies { classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.4") } }

apply(plugin = "com.google.protobuf")

var os = DefaultNativePlatform.getCurrentOperatingSystem()
var protobuf = project.extensions.getByName("protobuf") as ProtobufExtension

protobuf.protoc {
  if (os.isLinux) {
    path = getRoot().resolveAll("third_party", "protoc", "linux-x86_64", "bin", "protoc").path
  } else if (os.isMacOsX) {
    path = getRoot().resolveAll("third_party", "protoc", "osx-x86_64", "bin", "protoc").path
  } else {
    assert(os.isWindows)
    path = getRoot().resolveAll("third_party", "protoc", "win64", "bin", "protoc.exe").path
  }
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "libanalyzer", "java"))
    proto { srcDir(getRoot().resolveAll("src", "libanalyzer", "proto")) }
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
  withSourcesJar()
}

kotlin { explicitApi() }

dependencies {
  compileOnly(Deps.guava)
  compileOnly(Deps.protobuf)
  compileOnly(":keepanno")
  compileOnly(project(":main", "mainClassesOutput"))
  compileOnly(project(":main", "turboClassesOutput"))
  errorprone(Deps.errorprone)
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
