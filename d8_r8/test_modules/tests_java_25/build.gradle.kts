// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java25")) }
  toolchain { languageVersion = JavaLanguageVersion.of(25) }
}

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

dependencies { sharedDepsScope(project(":shared", "sharedDepsFiles")) }

dependencies {
  implementation(project(":main", "mainClassesOutput"))
  implementation(project(":main", "mainResources"))
  implementation(project(":main", "turboClassesOutput"))
  implementation(project(":testbase"))
  implementation(project(":testbase", "depsJar"))
}

tasks {
  withType<JavaCompile> {
    dependsOn(sharedDepsConfig)
    options.compilerArgs.add("--enable-preview")
  }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache"
    )
    TestingState.setUpTestingState(this)
    javaLauncher = getJavaLauncher(Jdk.JDK_25)
    systemProperty(
      "TEST_DATA_LOCATION",
      layout.buildDirectory.dir("classes/java/test").get().toString(),
    )
    systemProperty(
      "TESTBASE_DATA_LOCATION",
      project(":testbase")
        .tasks
        .named<JavaCompile>("compileJava")
        .get()
        .outputs
        .files
        .asPath
        .split(File.pathSeparator)[0],
    )
  }
}
