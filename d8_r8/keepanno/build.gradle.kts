// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto

plugins {
  id("r8-compiler-conventions")
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm")
  id("dependencies-plugin")
  id("com.google.protobuf")
}

val srcDir = getRoot().resolveAll("src", "keepanno", "java")

java {
  sourceSets.main.configure {
    java.srcDir(srcDir)
    proto { srcDir(getRoot().resolveAll("src", "keepanno", "proto")) }
  }
}

dependencies {
  apiExternal(libs.asm)
  implementationExternal(libs.guava)
  // Compile-only dependency of Guava 32.1.2-jre whose CLASS-retained annotations are referenced in
  // Guava bytecode.
  implementationExternal(libs.j2objcAnnotations)
  implementationExternal(libs.protobuf)
}

val externalRuntimeConfig = configurations.named("externalRuntimeConfig")

tasks {
  register<Jar>("keepAnnoAnnotationsJar") {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations.jar")
  }

  register<Jar>("keepAnnoLegacyAnnotationsJar") {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations-legacy.jar")
  }

  register<Jar>("keepAnnoAndroidXAnnotationsJar") {
    from(sourceSets.main.get().output)
    include("androidx/annotation/keep/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations-androidx.jar")
  }

  register<Javadoc>("keepAnnoAnnotationsDoc") {
    source = sourceSets.main.get().allJava
    include("com/android/tools/r8/keepanno/annotations/*")
  }

  register<Jar>("depsJar") {
    from(zipTrees(externalRuntimeConfig))
    // TODO(b/428166503): Add license information.
    exclude("META-INF/*.kotlin_module")
    exclude("META-INF/com.android.tools/**")
    exclude("META-INF/LICENSE*")
    exclude("META-INF/MANIFEST.MF")
    exclude("META-INF/maven/**")
    exclude("META-INF/proguard/**")
    exclude("META-INF/versions/**")
    exclude("META-INF/services/kotlin.reflect.**")
    exclude("javax/annotation/**")
    exclude("google/protobuf/**")
    duplicatesStrategy = DuplicatesStrategy.FAIL
    archiveFileName.set("keepanno-deps.jar")
  }

  register<Jar>("toolsJar") {
    from(sourceSets.main.get().output)
    // TODO(b/428166503): Add license information.
    entryCompression = ZipEntryCompression.STORED
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    exclude("keepspec.proto")
    exclude("com/android/tools/r8/keepanno/annotations/**")
    exclude("androidx/**")
    archiveFileName.set("keepanno-tools.jar")
  }
}

configurations.consumable("keepannoJar") { outgoing.artifact(tasks.named<Jar>("jar")) }

configurations.consumable("keepannoDepsJar") { outgoing.artifact(tasks.named<Jar>("depsJar")) }

configurations.consumable("keepannoToolsJar") { outgoing.artifact(tasks.named<Jar>("toolsJar")) }

configurations.consumable("keepannoAnnotationsJar") {
  outgoing.artifact(tasks.named<Jar>("keepAnnoAnnotationsJar"))
}

configurations.consumable("keepannoAndroidXAnnotationsJar") {
  outgoing.artifact(tasks.named<Jar>("keepAnnoAndroidXAnnotationsJar"))
}
