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

val internalClassesConfig = configurations.named("internalClassesConfig")
val mainResourcesScope = configurations.dependencyScope("mainResourcesScope")
val mainResources =
  configurations.resolvable("mainResources") { extendsFrom(mainResourcesScope.get()) }

dependencies {
  mainResourcesScope(project(":main", "mainResources"))
  runtimeOnlyData(project(":third_party", "jdk21"))
  runtimeOnlyData(project(":third_party", "jdk21Float16Test"))
}

tasks {
  withType<Test> {
    dependsOn(project(":dist").tasks.named("compileAssistantRuntimeDex"))
    dependsOn(internalClassesConfig, mainResources)
    javaLauncher = getJavaLauncher(Jdk.JDK_21)
    systemProperty(
      "BUILD_PROP_R8_RUNTIME_PATH",
      project.files(internalClassesConfig, mainResources).asPath,
    )
  }
}
