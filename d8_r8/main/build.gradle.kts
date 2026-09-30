// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

java {
  sourceSets {
    val srcDir = getRoot().resolveAll("src", "main", "java")

    main {
      resources.srcDirs(
        getRoot().resolveAll("third_party", "api_database", "api_database"),
        getRoot().resolveAll("src", "main", "resources"),
      )
      java { srcDir(srcDir) }
    }
  }
}

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

dependencies { sharedDepsScope(project(":third_party", "sharedDepsFiles")) }

val internalJarScope by configurations.dependencyScope("internalJarScope")
val internalJarResolvable by
  configurations.resolvable("internalJarResolvable") { extendsFrom(internalJarScope) }
val internalClassesScope by configurations.dependencyScope("internalClassesScope")
val internalClassesResolvable by
  configurations.resolvable("internalClassesResolvable") { extendsFrom(internalClassesScope) }

dependencies {
  internalJarScope(project(":utils", "isolatedJar"))
  internalClassesScope(project(":utils", "isolatedClasses"))
  implementation(project(":assistant", "assistantJar"))
  implementation(project(":keepradius", "keepradiusJar"))
  implementation(project(":keepanno", "keepannoClasses"))
  implementation(project(":resourceshrinker", "resourceshrinkerJar"))
  implementation(project(":utils"))
  compileOnly(libs.bundles.compilerDeps)
}

tasks {
  jar {
    doLast {
      enforceUncompressedEntries(archiveFile.get().asFile, setOf("resources/api_database.ser"))
    }
  }
}

tasks.withType<JavaCompile> {
  dependsOn(sharedDepsConfig)
  logger.info("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)
}

tasks.named("sourcesJar") { dependsOn(sharedDepsConfig) }

tasks.withType<ProcessResources> { dependsOn(sharedDepsConfig) }

// Contains both :main jar and :utils jar but not third party dependencies.
val mainJar by configurations.consumable("mainJar") { extendsFrom(internalJarResolvable) }
// Contains partial class files of :main and all class files of :utils but not third party
// dependencies.
val mainClassesOutput by
  configurations.consumable("mainClassesOutput") { extendsFrom(internalClassesResolvable) }
// Contains partial class files of :main but not :utils nor third party dependencies.
val mainResources by configurations.consumable("mainResources")

artifacts {
  add(mainJar.name, tasks.named("jar"))
  add(
    mainClassesOutput.name,
    tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory },
  )
  add(
    mainResources.name,
    tasks.named<ProcessResources>("processResources").map { it.destinationDir },
  )
}
