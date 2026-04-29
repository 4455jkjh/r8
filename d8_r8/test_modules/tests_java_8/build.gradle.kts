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
  sourceSets.test.configure {
    java {
      srcDir(root.resolveAll("src", "test", "java"))
      // Generated art tests
      srcDir(root.resolveAll("build", "generated", "test", "java"))
    }
  }
  // We are using a new JDK to compile to an older language version, as we don't have JDK-8 for
  // Windows in our repo.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

// If we depend on keepanno by referencing the project source outputs we get an error regarding
// incompatible java class file version. By depending on the jar we circumvent that.
val keepAnnoClassesScope by configurations.dependencyScope("keepAnnoClassesScope")
val keepAnnoClassesConfig by
  configurations.resolvable("keepAnnoClassesConfig") { extendsFrom(keepAnnoClassesScope) }
val resourceShrinkerClassesScope by configurations.dependencyScope("resourceShrinkerClassesScope")
val resourceShrinkerClassesConfig by
  configurations.resolvable("resourceShrinkerClassesConfig") {
    extendsFrom(resourceShrinkerClassesScope)
  }
val assistantClassesScope by configurations.dependencyScope("assistantClassesScope")
val assistantClassesOutput =
  configurations.resolvable("assistantClassesOutput") { extendsFrom(assistantClassesScope) }
val distDepsFilesScope by configurations.dependencyScope("distDepsFilesScope")
val distDepsFiles by configurations.resolvable("distDepsFiles") { extendsFrom(distDepsFilesScope) }
val mainClassesScope by configurations.dependencyScope("mainClassesScope")
val mainClassesOutput =
  configurations.resolvable("mainClassesOutput") { extendsFrom(mainClassesScope) }
val mainResourcesScope by configurations.dependencyScope("mainResourcesScope")
val mainResources = configurations.resolvable("mainResources") { extendsFrom(mainResourcesScope) }
val turboClassesScope by configurations.dependencyScope("turboClassesScope")
val turboClassesOutput =
  configurations.resolvable("turboClassesOutput") { extendsFrom(turboClassesScope) }
val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

val sharedDepsInternalScope by configurations.dependencyScope("sharedDepsInternalScope")
val sharedDepsInternalConfig by
  configurations.resolvable("sharedDepsInternalConfig") { extendsFrom(sharedDepsInternalScope) }

dependencies {
  sharedDepsScope(project(":shared", "sharedDepsFiles"))
  sharedDepsInternalScope(project(":shared", "sharedDepsInternalFiles"))
}

dependencies {
  keepAnnoClassesScope(project(":keepanno", "keepannoClasses"))
  assistantClassesScope(project(":assistant", "assistantJar"))
  distDepsFilesScope(project(":dist", "depsFiles"))
  mainClassesScope(project(":main", "mainClassesOutput"))
  mainResourcesScope(project(":main", "mainResources"))
  turboClassesScope(project(":main", "turboClassesOutput"))
  implementation(project(":assistant", "assistantJar"))
  implementation(project(":blastradius", "blastradiusJar"))
  implementation(project(":keepanno", "keepannoClasses"))
  implementation(project(":libanalyzer", "libanalyzer-compile-java"))
  implementation(project(":main", "mainClassesOutput"))
  implementation(project(":main", "mainResources"))
  implementation(project(":main", "turboClassesOutput"))
  resourceShrinkerClassesScope(project(":resourceshrinker", "resourceshrinkerClasses"))
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
  getByName<Delete>("clean") {
    // TODO(b/327315907): Don't generating into the root build dir.
    delete.add(
      getRoot()
        .resolveAll("build", "generated", "test", "java", "com", "android", "tools", "r8", "art")
    )
  }

  val createArtTests by
    registering(Exec::class) {
      dependsOn(sharedDepsConfig)
      dependOnPythonScripts()
      // TODO(b/327315907): Don't generating into the root build dir.
      val outputDir =
        getRoot()
          .resolveAll("build", "generated", "test", "java", "com", "android", "tools", "r8", "art")
      val createArtTestsScript = getRoot().resolveAll("tools", "create_art_tests.py")
      inputs.dir(getRoot().resolveAll("tests", "2017-10-04"))
      outputs.dir(outputDir)
      workingDir(getRoot())
      commandLine("python3", createArtTestsScript)
    }
  "compileTestJava" {
    dependsOn(sharedDepsConfig)
    dependsOn(":testbase:compileJava")
  }
  withType<JavaCompile> {
    dependsOn(createArtTests)
    dependsOn(sharedDepsConfig)
    dependsOn(":testbase:compileJava")
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  val sourceSetDependencyTask by registering {
    dependsOn(":tests_java_9:${getExampleJarsTaskName("examplesJava9")}")
  }

  withType<Test> {
    TestingState.setUpTestingState(this)
    dependsOn(distDepsFiles)
    dependsOn(sharedDepsConfig)
    if (!project.hasProperty("no_internal")) {
      dependsOn(sharedDepsInternalConfig)
    }
    dependsOn(sourceSetDependencyTask)
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
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths("keepanno" + File.separator, keepAnnoClassesConfig.asPath),
    )
    // This path is set when compiling examples jar task in DependenciesPlugin.
    val r8RuntimePath =
      project.files(mainClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        project.files(turboClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        distDepsFiles.asPath +
        File.pathSeparator +
        project.files(mainResources).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        keepAnnoClassesConfig.asPath +
        File.pathSeparator +
        project.files(assistantClassesOutput).asPath.split(File.pathSeparator)[0] +
        File.pathSeparator +
        resourceShrinkerClassesConfig.asPath
    systemProperty("BUILD_PROP_PROCESS_KEEP_RULES_RUNTIME_PATH", r8RuntimePath)
    systemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8RuntimePath)
    systemProperty("R8_DEPS", distDepsFiles.asPath)
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
  }

  val assembleTestJar by
    registering(Jar::class) {
      from(sourceSets.test.get().output)
      // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
      // Renaming
      //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
      //  the jar and not show red underlines. However, navigation to base classes will not work.
      archiveFileName.set("not_named_tests_java_8.jar")
    }
}

val testJar by configurations.consumable("testJar")

artifacts { add(testJar.name, tasks.named("assembleTestJar")) }
