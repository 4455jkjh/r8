// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
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
  sourceSets.test.configure { java { srcDir(root.resolveAll("src", "test", "bootstrap")) } }
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

val distR8WithRelocatedDeps = project(":dist").tasks.getByName("r8WithRelocatedDeps")
val distSwissArmyKnife = project(":dist").tasks.getByName("swissArmyKnife")
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val sharedDownloadDepsInternalTask = projectTask("shared", "downloadDepsInternal")

val keepAnnoClassesScope by configurations.dependencyScope("keepAnnoClassesScope")
val keepAnnoClassesConfig by
  configurations.resolvable("keepAnnoClassesConfig") { extendsFrom(keepAnnoClassesScope) }

dependencies {
  keepAnnoClassesScope(project(":keepanno", "keepannoClasses"))
  implementation(project(":keepanno", "keepannoClasses"))
  implementation(project(":main", "mainJar"))
  implementation(project(":resourceshrinker", "resourceshrinkerClasses"))
  implementation(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(project(":testbase"))
  implementation(project(":testbase", "depsJar"))
}

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(distR8WithRelocatedDeps, distSwissArmyKnife)
    systemProperty(
      "TEST_DATA_LOCATION",
      layout.buildDirectory.dir("classes/java/test").get().toString(),
    )
    systemProperty(
      "TESTBASE_DATA_LOCATION",
      project.provider {
        project(":testbase")
          .tasks
          .named<JavaCompile>("compileJava")
          .get()
          .outputs
          .files
          .asPath
          .split(File.pathSeparator)[0]
      },
    )
    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      project.provider {
        extractClassesPaths("keepanno" + File.separator, keepAnnoClassesConfig.asPath)
      },
    )
    systemProperty("R8_SWISS_ARMY_KNIFE", distSwissArmyKnife.outputs.files.singleFile)
    systemProperty("R8_WITH_RELOCATED_DEPS", distR8WithRelocatedDeps.outputs.files.singleFile)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", distR8WithRelocatedDeps.outputs.files.singleFile)
  }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      archiveFileName.set("not_named_tests_bootstrap.jar")
    }

  val assembleDepsJar by
    registering(Jar::class) {
      dependsOn(sharedDownloadDepsTask)
      if (!project.hasProperty("no_internal")) {
        dependsOn(sharedDownloadDepsInternalTask)
      }
      from(Callable { testDependencies().map(::zipTree) })
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
