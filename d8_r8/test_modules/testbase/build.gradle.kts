// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.util.concurrent.Callable
import org.gradle.api.JavaVersion

plugins {
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.main.configure { java { srcDir(root.resolveAll("src", "test", "testbase", "java")) } }

  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

val keepAnnoJarScope by configurations.dependencyScope("keepAnnoJarScope")
val keepAnnoJarConfig by
  configurations.resolvable("keepAnnoJarConfig") { extendsFrom(keepAnnoJarScope) }
val resourceShrinkerDepsJarScope by configurations.dependencyScope("resourceShrinkerDepsJarScope")
val resourceShrinkerDepsJarConfig by
  configurations.resolvable("resourceShrinkerDepsJarConfig") {
    extendsFrom(resourceShrinkerDepsJarScope)
  }
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val sharedDownloadTestDepsTask = projectTask("shared", "downloadTestDeps")

dependencies {
  keepAnnoJarScope(project(":keepanno", "keepannoJar"))
  implementation(project(":keepanno", "keepannoJar"))
  implementation(project(":libanalyzer", "libanalyzer-compile-java"))
  implementation(project(":main", "mainClassesOutput"))
  implementation(project(":main", "mainResources"))
  implementation(project(":main", "turboClassesOutput"))
  resourceShrinkerDepsJarScope(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(project(":resourceshrinker", "resourceshrinkerClasses"))
  implementation(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(Deps.androidxCollection)
  implementation(Deps.androidxTracingDriver)
  implementation(Deps.androidxTracingDriverWire)
  implementation(Deps.asm)
  implementation(Deps.asmCommons)
  implementation(Deps.asmUtil)
  implementation(Deps.gson)
  implementation(Deps.guava)
  implementation(Deps.javassist)
  implementation(Deps.junitJupiter)
  implementation(Deps.junitVintageEngine)
  implementation(Deps.kotlinStdLib)
  implementation(Deps.kotlinReflect)
  implementation(Deps.kotlinMetadata)
  implementation(resolve(ThirdPartyDeps.ddmLib, "ddmlib.jar"))
  implementation(resolve(ThirdPartyDeps.jasmin, "jasmin-2.4.jar"))
  implementation(resolve(ThirdPartyDeps.jdwpTests, "apache-harmony-jdwp-tests-host.jar"))
  implementation(Deps.fastUtil)
  implementation(Deps.smali)
  implementation(Deps.smaliUtil)
  runtimeOnly(Deps.junitPlatform)
}

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  withType<JavaCompile> {
    dependsOn(sharedDownloadDepsTask)
    dependsOn(sharedDownloadTestDepsTask)
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.main.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (testbase.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_testbase.jar")
    }

  val assembleDepsJar by
    registering(Jar::class) {
      dependsOn(keepAnnoJarConfig)
      dependsOn(resourceShrinkerDepsJarConfig)
      dependsOn(sharedDownloadDepsTask)
      dependsOn(sharedDownloadTestDepsTask)
      from(Callable { testDependencies().map(::zipTree) })
      from(keepAnnoJarConfig.map(::zipTree))
      from(resourceShrinkerDepsJarConfig.map(::zipTree))
      exclude("com/android/tools/r8/keepanno/annotations/**")
      exclude("androidx/annotation/keep/**")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("deps.jar")
    }
}

val testJar by configurations.consumable("testJar")

val depsJar by configurations.consumable("depsJar")

artifacts {
  add(testJar.name, tasks.named("assembleTestJar"))
  add(depsJar.name, tasks.named("assembleDepsJar"))
}
