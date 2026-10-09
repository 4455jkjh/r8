// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
  id("r8-non-java8-test-conventions")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java21")) }
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

val assistantClassesScope = configurations.dependencyScope("assistantClassesScope")
val assistantClassesOutput =
  configurations.resolvable("assistantClassesOutput") { extendsFrom(assistantClassesScope.get()) }

val mainClassesScope = configurations.dependencyScope("mainClassesScope")
val mainClassesOutput =
  configurations.resolvable("mainClassesOutput") { extendsFrom(mainClassesScope.get()) }
val mainResourcesScope = configurations.dependencyScope("mainResourcesScope")
val mainResources =
  configurations.resolvable("mainResources") { extendsFrom(mainResourcesScope.get()) }

dependencies {
  assistantClassesScope(project(":assistant", "assistantJar"))
  mainClassesScope(project(":main", "mainClassesOutput"))
  mainResourcesScope(project(":main", "mainResources"))
  implementation(project(":assistant", "assistantJar"))
  runtimeOnlyData(project(":third_party", "jdk21Float16Test"))
}

tasks {
  withType<Test> {
    javaLauncher = getJavaLauncher(Jdk.JDK_21)
    systemProperty(
      "BUILD_PROP_R8_RUNTIME_PATH",
      project.files(mainClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(mainResources).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(assistantClassesOutput).asPath.split(File.pathSeparator)[0],
    )
  }
}
