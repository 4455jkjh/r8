// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.Copy

plugins {
  `java-library`
  id("dependencies-plugin")
  id("r8-test-conventions")
}

val root = getRoot()

val partialTestClassesScope = configurations.dependencyScope("partialTestClassesScope")
val partialTestClassesConfig =
  configurations.resolvable("partialTestClassesConfig") {
    extendsFrom(partialTestClassesScope.get())
  }

java {
  sourceSets.test.configure { java { srcDir(root.resolveAll("src", "test", "java")) } }
  // We are using a new JDK to compile to an older language version, as we don't have JDK-8 for
  // Windows in our repo.
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

val distDepsFilesScope = configurations.dependencyScope("distDepsFilesScope")
val distDepsFiles =
  configurations.resolvable("distDepsFiles") { extendsFrom(distDepsFilesScope.get()) }
val mainResourcesScope = configurations.dependencyScope("mainResourcesScope")
val mainResources =
  configurations.resolvable("mainResources") { extendsFrom(mainResourcesScope.get()) }
val internalClassesConfig = configurations.named("internalClassesConfig")

dependencies {
  distDepsFilesScope(project(":dist", "depsFiles"))
  mainResourcesScope(project(":main", "mainResources"))
  implementation(project(":libanalyzer"))
  implementation(project(":main"))
  runtimeOnly(project(":main", "mainResources"))
  testCompileOnly(libs.androidxTracingDriverWire)
  testCompileOnly(libs.asmCommons)
  testCompileOnly(libs.toolsCommon)
  runtimeOnlyData(TestRuntimeData.AGSA)
  runtimeOnlyData(TestRuntimeData.ART_5_1_1)
  runtimeOnlyData(TestRuntimeData.ART_TESTS)
  runtimeOnlyData(TestRuntimeData.BOOKSTORY)
  runtimeOnlyData(TestRuntimeData.BUNDLETOOL)
  runtimeOnlyData(TestRuntimeData.CHROME)
  runtimeOnlyData(TestRuntimeData.CHROME_BENCHMARK)
  runtimeOnlyData(TestRuntimeData.CHROME_INTERNAL)
  runtimeOnlyData(TestRuntimeData.CLANK)
  runtimeOnlyData(TestRuntimeData.COMPILER_API)
  runtimeOnlyData(TestRuntimeData.COMPOSE_EXAMPLES_CHANGED_BITWISE_VALUE_PROPAGATION)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_CRANE)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_JETCASTER)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_JETCHAT)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_JETLAGGED)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_JETNEWS)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_JETSNACK)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_OWL)
  runtimeOnlyData(TestRuntimeData.COMPOSE_SAMPLES_REPLY)
  runtimeOnlyData(TestRuntimeData.DAGGER)
  runtimeOnlyData(TestRuntimeData.DESUGAR_JDK_LIBS_11)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_2_0_3)
  runtimeOnlyData(TestRuntimeData.EXAMPLES)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_N)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_O)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_O_GENERATED)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_O_LEGACY)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_P)
  runtimeOnlyData(TestRuntimeData.FEEDER)
  runtimeOnlyData(TestRuntimeData.FOSSIFY_FILE_MANAGER)
  runtimeOnlyData(TestRuntimeData.FRAMEWORK)
  runtimeOnlyData(TestRuntimeData.GMSCORE_LATEST)
  runtimeOnlyData(TestRuntimeData.GMSCORE_V10)
  runtimeOnlyData(TestRuntimeData.GSON)
  runtimeOnlyData(TestRuntimeData.GUAVA_JRE)
  runtimeOnlyData(TestRuntimeData.INTERNAL_ISSUE_127524985)
  runtimeOnlyData(TestRuntimeData.JACOCO)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
  runtimeOnlyData(TestRuntimeData.JDK_8)
  runtimeOnlyData(TestRuntimeData.JDK_11)
  runtimeOnlyData(TestRuntimeData.JDK_17)
  runtimeOnlyData(TestRuntimeData.JDK_21)
  runtimeOnlyData(TestRuntimeData.JDK_25)
  runtimeOnlyData(TestRuntimeData.JDK_27)
  runtimeOnlyData(TestRuntimeData.JDWP_TESTS)
  runtimeOnlyData(TestRuntimeData.JSR223)
  runtimeOnlyData(TestRuntimeData.KEEPASSDX)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_3_72)
  runtimeOnlyData(TestRuntimeData.NEST)
  runtimeOnlyData(TestRuntimeData.NEWPIPE)
  runtimeOnlyData(TestRuntimeData.NOWINANDROID)
  runtimeOnlyData(TestRuntimeData.OMNINOTES)
  runtimeOnlyData(TestRuntimeData.OPENTRACKS)
  runtimeOnlyData(TestRuntimeData.PROCESS_KEEP_RULES_BINARY_COMPATIBILITY)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_0_0)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_7_0)
  runtimeOnlyData(TestRuntimeData.PROTO_RUNTIME_EDITION2023)
  runtimeOnlyData(TestRuntimeData.PROTO_RUNTIME_LEGACY)
  runtimeOnlyData(TestRuntimeData.PROTO_TEST_EDITION2023)
  runtimeOnlyData(TestRuntimeData.PROTO_TEST_PROTO2)
  runtimeOnlyData(TestRuntimeData.PROTO_TEST_PROTO3)
  runtimeOnlyData(TestRuntimeData.R8)
  runtimeOnlyData(TestRuntimeData.R8_V3_2_54)
  runtimeOnlyData(TestRuntimeData.READYOU)
  runtimeOnlyData(TestRuntimeData.RETRACE_BENCHMARK)
  runtimeOnlyData(TestRuntimeData.RHINO)
  runtimeOnlyData(TestRuntimeData.RHINO_ANDROID)
  runtimeOnlyData(TestRuntimeData.SMALI)
  runtimeOnlyData(TestRuntimeData.OPENSOURCE_NEWPIPE)
  runtimeOnlyData(TestRuntimeData.SIGNAL_ANDROID)
  runtimeOnlyData(TestRuntimeData.SYSTEM_UI)
  runtimeOnlyData(TestRuntimeData.TACHIYOMI)
  runtimeOnlyData(TestRuntimeData.TIVI)
  runtimeOnlyData(TestRuntimeData.TUSKY)
  runtimeOnlyData(TestRuntimeData.YOUTUBE_21_40)
  runtimeOnlyData(TestRuntimeData.YOUTUBE_21_40_D8_MERGE)
  // For each child project, add its test classes to the test class configuration.
  childProjects.values.forEach { childProject ->
    partialTestClassesScope(project(childProject.path, "partialTestClasses"))
    runtimeOnlyDataFrom(childProject.path)
    add("jacocoExecScope", project(childProject.path, "jacocoExecElements"))
  }
}

tasks {
  val createArtTests =
    register<CreateArtTestsTask>("createArtTests") {
      artTestCaseDirectory =
        project(":third_party").tasks.named<DownloadDependency>("download_art-tests").flatMap {
          it.outputDir
        }
      outputDir = layout.buildDirectory.dir("generated/test/java")
    }
  java.sourceSets.test.configure { java.srcDir(createArtTests) }
  "compileTestJava" { dependsOn(":testbase:compileJava") }
  withType<JavaCompile> { dependsOn(":testbase:compileJava") }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  register<Jar>("assembleTestJar") {
    from(sourceSets.test.get().output)
    from(partialTestClassesConfig)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
    // Renaming
    //  this from the default name (tests_java_8.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_tests_java_8.jar")
  }

  named<Copy>("processTestResources") {
    dependsOn(createArtTests)
    val r8 = "com/android/tools/r8"
    from(sourceSets.test.get().java.srcDirs) {
      include("$r8/cf/KeepDeserializeLambdaMethodTest.java")
      include("$r8/desugaring/interfacemethods/methodparameters/I.java")
    }
    from(root.resolveAll("src", "test")) { include("examplesDagger/**") }
  }

  register<JavaExec>("generateApiAmendments") {
    dependsOn(partialTestClassesConfig)
    mainClass.set("com.android.tools.r8.androidapi.GenerateApiAmendments")
    classpath = sourceSets.test.get().runtimeClasspath + partialTestClassesConfig.get()
    // The output is deliberately not Gradle tracked since its a circular dependency.
  }

  register<JavaExec>("generateApiDatabase") {
    dependsOn(partialTestClassesConfig)
    mainClass.set("com.android.tools.r8.apimodel.AndroidApiHashingDatabaseBuilderGeneratorTest")
    classpath = sourceSets.test.get().runtimeClasspath + partialTestClassesConfig.get()
    // The output is deliberately not Gradle tracked since its a circular dependency.
  }
}

configurations.consumable("testJar") { outgoing.artifact(tasks.named("assembleTestJar")) }

fun Test.setupTestTask() {
  TestingState.setUpTestingState(this)
  systemProperty(
    "TEST_DATA_LOCATION",
    project.layout.buildDirectory.dir("classes/java/test").get().toString(),
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
  dependsOn(distDepsFiles)
  dependsOn(internalClassesConfig)
  dependsOn(project(":dist").tasks.named("r8AssistantLib"))
  systemProperty(
    "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
    extractClassesPaths("keepanno" + File.separator, internalClassesConfig.get().asPath),
  )
  // This path is set when compiling examples jar task in DependenciesPlugin.
  val r8RuntimePath = project.files(internalClassesConfig, distDepsFiles, mainResources).asPath
  systemProperty("BUILD_PROP_PROCESS_KEEP_RULES_RUNTIME_PATH", r8RuntimePath)
  systemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8RuntimePath)
  systemProperty("R8_DEPS", distDepsFiles.get().asPath)
  systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
}

tasks.withType<Test> { setupTestTask() }

tasks.register("testAll") {
  // Added child dependencies to Test directly would force all child tests to run before the
  // parent tests. This task runs all test tasks as siblings, allowing concurrent execution of all
  // tests.
  // Depend specifically on the "test" task by name so we don't also trigger "testCoverage".
  dependsOn(tasks.named("test"))
  childProjects.values.forEach { childProject -> dependsOn("${childProject.path}:test") }
}

// Setup child-project boilerplate, only requiring them to set their java source set.
subprojects {
  apply(plugin = "java-library")
  apply(plugin = "dependencies-plugin")
  apply(plugin = "r8-test-conventions")

  extensions.configure<JavaPluginExtension> {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
    toolchain { languageVersion = JavaLanguageVersion.of(11) }
  }

  dependencies {
    add("implementation", project(":main"))
    add("runtimeOnly", project(":main", "mainResources"))
  }

  tasks.withType<Test> { setupTestTask() }

  configurations.consumable("partialTestClasses") {
    outgoing.artifact(layout.buildDirectory.dir("classes/java/test")) {
      builtBy(tasks.named("compileTestJava"))
    }
    outgoing.artifact(layout.buildDirectory.dir("resources/test")) {
      builtBy(tasks.named("processTestResources"))
    }
  }
}
