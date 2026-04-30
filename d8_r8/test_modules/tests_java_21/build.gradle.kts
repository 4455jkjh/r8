// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java21")) }
  toolchain { languageVersion = JavaLanguageVersion.of(21) }
}

val assistantClassesScope by configurations.dependencyScope("assistantClassesScope")
val assistantClassesOutput =
  configurations.resolvable("assistantClassesOutput") { extendsFrom(assistantClassesScope) }
val sharedDownloadDepsTask = projectTask("shared", "downloadDeps")
val mainClassesScope by configurations.dependencyScope("mainClassesScope")
val mainClassesOutput =
  configurations.resolvable("mainClassesOutput") { extendsFrom(mainClassesScope) }
val mainResourcesScope by configurations.dependencyScope("mainResourcesScope")
val mainResources = configurations.resolvable("mainResources") { extendsFrom(mainResourcesScope) }
val turboClassesScope by configurations.dependencyScope("turboClassesScope")
val turboClassesOutput =
  configurations.resolvable("turboClassesOutput") { extendsFrom(turboClassesScope) }

dependencies {
  assistantClassesScope(project(":assistant", "assistantJar"))
  mainClassesScope(project(":main", "mainClassesOutput"))
  mainResourcesScope(project(":main", "mainResources"))
  turboClassesScope(project(":main", "turboClassesOutput"))
  implementation(project(":assistant", "assistantJar"))
  implementation(project(":main", "mainClassesOutput"))
  implementation(project(":main", "mainResources"))
  implementation(project(":main", "turboClassesOutput"))
  implementation(project(":testbase"))
  implementation(project(":testbase", "depsJar"))
}

tasks {
  withType<JavaCompile> { dependsOn(sharedDownloadDepsTask) }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache"
    )
    TestingState.setUpTestingState(this)
    javaLauncher = getJavaLauncher(Jdk.JDK_21)
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
    systemProperty(
      "BUILD_PROP_R8_RUNTIME_PATH",
      project.files(mainClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(turboClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(mainResources).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(assistantClassesOutput).asPath.split(File.pathSeparator)[0],
    )
  }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_tests_java_21.jar")
    }
}

val testJar by configurations.consumable("testJar")

artifacts { add(testJar.name, tasks.named("assembleTestJar")) }
