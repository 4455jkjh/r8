// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto
import java.util.concurrent.Callable

plugins {
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
  id("com.google.protobuf")
}

tasks.named("generateProto") { dependsOn(sharedDepsConfig) }

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "keepanno", "java"))
    proto { srcDir(getRoot().resolveAll("src", "keepanno", "proto")) }
  }
}

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  compileOnly(libs.asm)
  compileOnly(libs.guava)
  compileOnly(libs.protobuf)
}

tasks {
  register<Jar>("keepAnnoAnnotationsJar") {
    dependsOn(sharedDepsConfig)
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations.jar")
  }

  register<Jar>("keepAnnoLegacyAnnotationsJar") {
    dependsOn(sharedDepsConfig)
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepanno/annotations/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations-legacy.jar")
  }

  register<Jar>("keepAnnoAndroidXAnnotationsJar") {
    dependsOn(sharedDepsConfig)
    from(sourceSets.main.get().output)
    include("androidx/annotation/keep/*")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("keepanno-annotations-androidx.jar")
  }

  named<Jar>("jar") { dependsOn(sharedDepsConfig) }

  register<Javadoc>("keepAnnoAnnotationsDoc") {
    source = sourceSets.main.get().allJava
    include("com/android/tools/r8/keepanno/annotations/*")
  }

  fun dependenciesExceptAsm(): FileCollection {
    return sourceSets.main
      .get()
      .compileClasspath
      .filter({
        "$it".contains("third_party") &&
          "$it".contains("dependencies") &&
          !("$it".contains("errorprone") || "$it".contains("ow2"))
      })
  }

  fun dependenciesOnlyAsm(): FileCollection {
    return sourceSets.main
      .get()
      .compileClasspath
      .filter({
        "$it".contains("third_party") &&
          "$it".contains("dependencies") &&
          ("$it".contains("errorprone") || "$it".contains("ow2"))
      })
  }

  register<Jar>("depsJarExceptAsm") {
    dependsOn(sharedDepsConfig)
    from(Callable { dependenciesExceptAsm().map(::zipTree) })
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
    archiveFileName.set("keepanno-deps-except-asm.jar")
  }

  register<Jar>("depsJarOnlyAsm") {
    dependsOn(sharedDepsConfig)
    from(Callable { dependenciesOnlyAsm().map(::zipTree) })
    // TODO(b/428166503): Add license information if needed.
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
    archiveFileName.set("keepanno-deps-only-asm.jar")
  }

  register<Jar>("toolsJar") {
    dependsOn(sharedDepsConfig)
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

val keepannoJar by
  configurations.consumable("keepannoJar") { outgoing.artifact(tasks.named<Jar>("jar")) }

val keepannoDepsJarExceptAsm by
  configurations.consumable("keepannoDepsJarExceptAsm") {
    outgoing.artifact(tasks.named<Jar>("depsJarExceptAsm"))
  }

val keepannoToolsJar by
  configurations.consumable("keepannoToolsJar") { outgoing.artifact(tasks.named<Jar>("toolsJar")) }

val keepannoAndroidXAnnotationsJar by
  configurations.consumable("keepannoAndroidXAnnotationsJar") {
    outgoing.artifact(tasks.named<Jar>("keepAnnoAndroidXAnnotationsJar"))
  }

val keepannoDepsJarOnlyAsm by
  configurations.consumable("keepannoDepsJarOnlyAsm") {
    outgoing.artifact(tasks.named<Jar>("depsJarOnlyAsm"))
  }

val keepannoSources by
  configurations.consumable("keepannoSources") { outgoing.artifact(tasks.named<Jar>("sourcesJar")) }

val keepannoClasses by
  configurations.consumable("keepannoClasses") {
    outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
    outgoing.artifact(
      tasks.named<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>("compileKotlin").map {
        it.destinationDirectory
      }
    )
  }
