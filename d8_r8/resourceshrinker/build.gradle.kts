// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.util.concurrent.Callable
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
  // Kotlin version is fixed by create_local_maven_dependencies.py
  id("org.jetbrains.kotlin.jvm") version "2.0.21"
  id("dependencies-plugin")
}

java {
  sourceSets.main.configure {
    kotlin.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
    java.srcDir(getRoot().resolveAll("src", "resourceshrinker", "java"))
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
  withSourcesJar()
}

kotlin {
  explicitApi()
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(JvmCompatibility.release.toString()))
    languageVersion.set(KotlinVersion.KOTLIN_1_8)
    apiVersion.set(KotlinVersion.KOTLIN_1_8)
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

dependencies {
  compileOnly(Deps.asm)
  compileOnly(Deps.guava)
  compileOnly(Deps.protobuf)
  compileOnly(Deps.fastUtil)
  compileOnly(files(resolve(ThirdPartyDeps.r8, "r8lib_9.0.32.jar")))
  implementation("com.android.tools.build:aapt2-proto:9.1.0-alpha09-14792394")
  implementation("com.android.tools.layoutlib:layoutlib-api:31.5.0-alpha04")
  implementation("com.android.tools:common:31.5.0-alpha04")
  implementation("com.android.tools:sdk-common:31.5.0-alpha04")
}

tasks {
  withType<KotlinCompile> { dependsOn(gradle.includedBuild("shared").task(":downloadDeps")) }
  val depsJar by
    registering(Jar::class) {
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
