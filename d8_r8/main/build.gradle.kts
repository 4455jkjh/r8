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

val sharedDepsScope = configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig =
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope.get()) }

dependencies { sharedDepsScope(project(":third_party", "sharedDepsFiles")) }

val internalJarScope = configurations.dependencyScope("internalJarScope")
val internalJarResolvable =
  configurations.resolvable("internalJarResolvable") { extendsFrom(internalJarScope.get()) }
val internalClassesScope = configurations.dependencyScope("internalClassesScope")
val internalClassesResolvable =
  configurations.resolvable("internalClassesResolvable") { extendsFrom(internalClassesScope.get()) }

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
configurations.consumable("mainJar") {
  extendsFrom(internalJarResolvable.get())
  outgoing.artifact(tasks.named("jar"))
}

// Contains partial class files of :main and all class files of :utils but not third party
// dependencies.
configurations.consumable("mainClassesOutput") {
  extendsFrom(internalClassesResolvable.get())
  outgoing.artifact(tasks.named<JavaCompile>("compileJava").map { it.destinationDirectory })
}

// Contains partial class files of :main but not :utils nor third party dependencies.
configurations.consumable("mainResources") {
  outgoing.artifact(tasks.named<ProcessResources>("processResources").map { it.destinationDir })
}
