// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test
import org.gradle.kotlin.dsl.getByType
import org.gradle.kotlin.dsl.named
import org.gradle.kotlin.dsl.project
import org.gradle.kotlin.dsl.register
import org.gradle.kotlin.dsl.withType

public class R8NonJava8TestConventionPlugin : Plugin<Project> {
  override fun apply(target: Project) {
    target.dependencies.apply {
      add("implementation", project(":main", "mainClassesOutput"))
      add("implementation", project(":main", "mainResources"))
      add("implementation", project(":testbase"))
      add("implementation", project(":testbase", "depsJar"))
      add("runtimeOnlyDataScope", project(":testbase", "runtimeOnlyDataElements"))
    }

    val sharedDepsScope = target.configurations.dependencyScope("sharedDepsScope")
    val sharedDepsConfig =
      target.configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope.get()) }
    target.dependencies.add(
      sharedDepsScope.name,
      target.dependencies.project(":third_party", "sharedDepsFiles"),
    )
    target.tasks.withType<JavaCompile>().configureEach { dependsOn(sharedDepsConfig) }
    target.tasks.withType<Test>().configureEach {
      TestingState.setUpTestingState(this)
      systemProperty(
        "TEST_DATA_LOCATION",
        target.layout.buildDirectory.dir("classes/java/test").get().toString(),
      )
      systemProperty(
        "TESTBASE_DATA_LOCATION",
        target
          .project(":testbase")
          .tasks
          .named<JavaCompile>("compileJava")
          .get()
          .outputs
          .files
          .asPath
          .split(File.pathSeparator)[0],
      )
    }
    val assembleTestJar =
      target.tasks.register<Jar>("assembleTestJar") {
        val sourceSets = target.extensions.getByType<SourceSetContainer>()
        from(sourceSets.getByName("test").output)
        // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
        // Renaming
        //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources
        // in
        //  the jar and not show red underlines. However, navigation to base classes will not work.
        archiveFileName.set("not_named_tests_java.jar")
      }
    val testJar = target.configurations.consumable("testJar")
    target.artifacts { add(testJar.name, assembleTestJar) }
  }
}
