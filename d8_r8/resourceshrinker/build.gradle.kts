// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.util.concurrent.Callable
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
}

java {
  sourceSets.main.configure {
    kotlin.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
    java.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
  }
}

fun jarDependencies(): FileCollection {
  return sourceSets.main
    .get()
    .compileClasspath
    .filter({
      "$it".contains("third_party") &&
        "$it".contains("dependencies") &&
        !"$it".contains("errorprone")
    })
}

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  compileOnly(libs.asm)
  compileOnly(libs.guava)
  compileOnly(libs.protobuf)
  compileOnly(libs.fastUtil)
  implementation(libs.toolsAapt2Proto)
  implementation(libs.toolsLayoutlibApi)
  implementation(libs.toolsCommon)
  implementation(libs.toolsSdkCommon)
}

tasks {
  withType<KotlinCompile> { dependsOn(sharedDepsConfig) }
  register<Jar>("depsJar") {
    from(Callable { jarDependencies().map(::zipTree) })
    exclude("**/*.proto")
    exclude("versions-offline/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("resourceshrinker_deps.jar")
  }
}

val resourceshrinkerJar by
  configurations.consumable("resourceshrinkerJar") { outgoing.artifact(tasks.named<Jar>("jar")) }

val resourceshrinkerDepsJar by
  configurations.consumable("resourceshrinkerDepsJar") {
    outgoing.artifact(tasks.named<Jar>("depsJar"))
  }

val resourceshrinkerSources by
  configurations.consumable("resourceshrinkerSources") {
    outgoing.artifact(tasks.named<Jar>("sourcesJar"))
  }

val resourceshrinkerClasses by
  configurations.consumable("resourceshrinkerClasses") {
    outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
    outgoing.artifact(tasks.named<KotlinCompile>("compileKotlin").map { it.destinationDirectory })
  }
