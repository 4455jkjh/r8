// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

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
val distSwissArmyKnife = project(":swissarmyknife").tasks.getByName("jar")
val sharedDepsScope = configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig =
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope.get()) }

val sharedDepsInternalScope = configurations.dependencyScope("sharedDepsInternalScope")
val sharedDepsInternalConfig =
  configurations.resolvable("sharedDepsInternalConfig") {
    extendsFrom(sharedDepsInternalScope.get())
  }

val distDepsFilesScope = configurations.dependencyScope("distDepsFilesScope")
val distDepsFiles =
  configurations.resolvable("distDepsFiles") { extendsFrom(distDepsFilesScope.get()) }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  sharedDepsInternalScope(project(":third_party", "sharedDepsInternalFiles"))
  distDepsFilesScope(project(":dist", "filteredDepsJarConfig"))
}

val keepAnnoClassesScope = configurations.dependencyScope("keepAnnoClassesScope")
val keepAnnoClassesConfig =
  configurations.resolvable("keepAnnoClassesConfig") { extendsFrom(keepAnnoClassesScope.get()) }

dependencies {
  keepAnnoClassesScope(project(":keepanno", "keepannoClasses"))
  implementation(project(":keepanno", "keepannoClasses"))
  implementation(project(":main", "mainJar"))
  implementation(project(":resourceshrinker", "resourceshrinkerClasses"))
  implementation(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(project(":testbase"))
  implementation(project(":testbase", "depsJar"))
  runtimeOnlyData(project(":testbase", "runtimeOnlyDataElements"))
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
    dependsOn(distR8WithRelocatedDeps, distSwissArmyKnife, distDepsFiles)
    systemProperty("R8_DEPS", distDepsFiles.get().asPath)
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
      extractClassesPaths("keepanno" + File.separator, keepAnnoClassesConfig.get().asPath),
    )
    systemProperty("R8_SWISS_ARMY_KNIFE", distSwissArmyKnife.outputs.files.singleFile)
    systemProperty("R8_WITH_RELOCATED_DEPS", distR8WithRelocatedDeps.outputs.files.singleFile)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", distR8WithRelocatedDeps.outputs.files.singleFile)
  }

  register<Jar>("assembleTestJar") {
    from(sourceSets.test.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
    archiveFileName.set("not_named_tests_bootstrap.jar")
  }

  register<Jar>("assembleDepsJar") {
    dependsOn(sharedDepsConfig)
    if (!project.hasProperty("no_internal")) {
      dependsOn(sharedDepsInternalConfig)
    }
    from(testDependencies().map(::zipTree))
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}

configurations.consumable("testJar") { outgoing.artifact(tasks.named("assembleTestJar")) }

configurations.consumable("depsJar") { outgoing.artifact(tasks.named("assembleDepsJar")) }
