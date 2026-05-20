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
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java9")) }
  sourceCompatibility = JavaVersion.VERSION_1_9
  targetCompatibility = JavaVersion.VERSION_1_9
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
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
  withType<JavaCompile> { dependsOn(sharedDepsConfig) }

  withType<Test> {
    notCompatibleWithConfigurationCache(
      "Failure storing the configuration cache: cannot serialize object of type 'org.gradle.api.internal.project.DefaultProject', a subtype of 'org.gradle.api.Project', as these are not supported with the configuration cache"
    )
    TestingState.setUpTestingState(this)
    systemProperty(
      "TEST_DATA_LOCATION",
      layout.buildDirectory.dir("classes/java/test").get().toString(),
    )
  }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_tests_java_9.jar")
    }
}

val testJar by configurations.consumable("testJar")

artifacts { add(testJar.name, tasks.named("assembleTestJar")) }
