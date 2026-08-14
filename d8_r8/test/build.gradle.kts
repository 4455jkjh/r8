// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.nio.file.Paths
import org.gradle.kotlin.dsl.register

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
}

val testJarsScope by configurations.dependencyScope("testJarsScope")
val testJars by configurations.resolvable("testJars") { extendsFrom(testJarsScope) }

val testbaseTestJarsScope by configurations.dependencyScope("testbaseTestJarsScope")
val testbaseTestJars by
  configurations.resolvable("testbaseTestJars") { extendsFrom(testbaseTestJarsScope) }

val testDepsJarsScope by configurations.dependencyScope("testDepsJarsScope")
val testDepsJars by configurations.resolvable("testDepsJars") { extendsFrom(testDepsJarsScope) }

val mainDepsJarFilesScope by configurations.dependencyScope("mainDepsJarFilesScope")
val mainDepsJarFilesConfig by
  configurations.resolvable("mainDepsJarFilesConfig") { extendsFrom(mainDepsJarFilesScope) }

val assistantJarScope by configurations.dependencyScope("assistantJarScope")
val assistantJarConfig by
  configurations.resolvable("assistantJarConfig") { extendsFrom(assistantJarScope) }
val keepAnnoAndroidXAnnotationsJarScope by
  configurations.dependencyScope("keepAnnoAndroidXAnnotationsJarScope")
val keepAnnoAndroidXAnnotationsJarConfig by
  configurations.resolvable("keepAnnoAndroidXAnnotationsJarConfig") {
    extendsFrom(keepAnnoAndroidXAnnotationsJarScope)
  }
val keepAnnoDepsJarOnlyAsmScope by configurations.dependencyScope("keepAnnoDepsJarOnlyAsmScope")
val keepAnnoDepsJarOnlyAsmConfig by
  configurations.resolvable("keepAnnoDepsJarOnlyAsmConfig") {
    extendsFrom(keepAnnoDepsJarOnlyAsmScope)
  }
val keepAnnoClassesScope by configurations.dependencyScope("keepAnnoClassesScope")
val keepAnnoClassesConfig by
  configurations.resolvable("keepAnnoClassesConfig") { extendsFrom(keepAnnoClassesScope) }

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

val sharedTestDepsScope by configurations.dependencyScope("sharedTestDepsScope")
val sharedTestDepsConfig by
  configurations.resolvable("sharedTestDepsConfig") { extendsFrom(sharedTestDepsScope) }

val sharedDepsInternalScope by configurations.dependencyScope("sharedDepsInternalScope")
val sharedDepsInternalConfig by
  configurations.resolvable("sharedDepsInternalConfig") { extendsFrom(sharedDepsInternalScope) }

val sharedTestDepsInternalScope by configurations.dependencyScope("sharedTestDepsInternalScope")
val sharedTestDepsInternalConfig by
  configurations.resolvable("sharedTestDepsInternalConfig") {
    extendsFrom(sharedTestDepsInternalScope)
  }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  sharedTestDepsScope(project(":third_party", "sharedTestDepsFiles"))
  sharedDepsInternalScope(project(":third_party", "sharedDepsInternalFiles"))
  sharedTestDepsInternalScope(project(":third_party", "sharedTestDepsInternalFiles"))
  assistantJarScope(project(":assistant", "assistantJar"))
  keepAnnoAndroidXAnnotationsJarScope(project(":keepanno", "keepannoAndroidXAnnotationsJar"))
  keepAnnoDepsJarOnlyAsmScope(project(":keepanno", "keepannoDepsJarOnlyAsm"))
  keepAnnoClassesScope(project(":keepanno", "keepannoClasses"))
  testJarsScope(project(":tests_java_8", "testJar"))
  testJarsScope(project(":tests_java_11", "testJar"))
  testJarsScope(project(":tests_java_17", "testJar"))
  testJarsScope(project(":tests_java_21", "testJar"))
  testJarsScope(project(":tests_java_25", "testJar"))
  testJarsScope(project(":tests_bootstrap", "testJar"))
  testbaseTestJarsScope(project(":testbase", "testJar"))
  testDepsJarsScope(project(":tests_bootstrap", "depsJar"))
  testDepsJarsScope(project(":testbase", "depsJar"))
  mainDepsJarFilesScope(project(":dist", "depsJarFiles"))
}

val mainProtoJarTask = project(":dist").tasks.getByName("protoJar")
val mainDepsJarTask = project(":dist").tasks.getByName("depsJar")
val swissArmyKnifeTask = project(":dist").tasks.getByName<Jar>("swissArmyKnife")
val processKeepRulesLibWithRelocatedDepsTask =
  project(":dist").tasks.named<CreateR8LibraryTask>("processKeepRulesLibWithRelocatedDeps")
val r8WithRelocatedDepsTask =
  project(":dist").tasks.getByName<SwissArmyKnifeTask>("r8WithRelocatedDeps")
val keepAnnoToolsWithRelocatedDepsTask =
  project(":dist").tasks.getByName("keepAnnoToolsWithRelocatedDeps")

interface InjectedArcOps {
  @get:Inject val arcOps: ArchiveOperations
}

tasks {
  withType<Exec> { doFirst { println("Executing command: ${commandLine.joinToString(" ")}") } }

  "clean" {
    dependsOn(":testbase:clean")
    dependsOn(":tests_bootstrap:clean")
    dependsOn(":tests_java_8:clean")
    dependsOn(":tests_java_11:clean")
    dependsOn(":tests_java_17:clean")
    dependsOn(":tests_java_21:clean")
    dependsOn(":tests_java_25:clean")
  }

  val packageTests =
    register<Jar>("packageTests") {
      from(testJars.elements.map { it.map { zipTree(it) } })
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8tests.jar")
    }

  val packageTestDeps =
    register<Jar>("packageTestDeps") {
      dependsOn(keepAnnoAndroidXAnnotationsJarConfig)
      from(testDepsJars.elements.map { it.map { zipTree(it) } })
      from(keepAnnoAndroidXAnnotationsJarConfig.map(::zipTree))
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata", "org/jspecify/**", "org/jspecify")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("test_deps_all.jar")
    }

  val packageTestBase =
    register<Jar>("packageTestBase") {
      from(testbaseTestJars.elements.map { it.map { zipTree(it) } })
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8test_base.jar")
    }

  val packageTestBaseExcludeKeep =
    register<Jar>("packageTestBaseExcludeKeep") {
      dependsOn(packageTestBase)
      from(zipTree(packageTestBase.getSingleOutputFile()))
      // TODO(b/328353718): we have com.android.tools.r8.Keep in both test_base and main
      exclude("com/android/tools/r8/Keep.class")
      archiveFileName.set("r8test_base_no_keep.jar")
    }

  fun SwissArmyKnifeTask.executeRelocator(jarProvider: TaskProvider<Jar>, artifactName: String) {
    // TODO: convert r8WithRelocatedDepsTask usage to flatMap and remove dependsOn
    dependsOn(r8WithRelocatedDepsTask)
    swissArmyKnifeClasspath.from(r8WithRelocatedDepsTask.getSingleOutputFile())
    compiler = "relocator"
    inputFiles.from(jarProvider)
    outputFile = layout.buildDirectory.file("libs/$artifactName")
    extraArgs =
      listOf("--map", "kotlin.metadata.**->com.android.tools.r8.jetbrains.kotlin.metadata")
  }

  // When testing R8 lib with relocated deps we must relocate kotlin.metadata in the tests, since
  // types from kotlin.metadata are used on the R8 main/R8 test boundary.
  //
  // This is not needed when testing R8 lib excluding deps since we simply include the deps on the
  // classpath at runtime.
  val relocateTestsForR8LibWithRelocatedDeps =
    register<SwissArmyKnifeTask>("relocateTestsForR8LibWithRelocatedDeps") {
      executeRelocator(packageTests, "r8tests-relocated.jar")
    }

  val relocateTestBaseForR8LibWithRelocatedDeps =
    register<SwissArmyKnifeTask>("relocateTestBaseForR8LibWithRelocatedDeps") {
      executeRelocator(packageTestBase, "r8testbase-relocated.jar")
    }

  fun Exec.generateKeepRulesForR8Lib(
    targetJarProviders: List<Task>,
    testJarProviders: List<TaskProvider<*>>,
    artifactName: String,
  ) {
    dependsOn(mainDepsJarFilesConfig, packageTestDeps, r8WithRelocatedDepsTask)
    targetJarProviders.forEach(::dependsOn)
    testJarProviders.forEach(::dependsOn)
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    inputs.files(r8WithRelocatedDepsJar, testDepsJar)
    inputs.files(mainDepsJarFilesConfig)
    inputs.files(targetJarProviders.map { it.getSingleOutputFile() })
    inputs.files(testJarProviders.map { it.getSingleOutputFile() })
    val output = file(Paths.get("build", "libs", artifactName))
    outputs.file(output)
    val argList =
      mutableListOf(
        "--keep-rules",
        "--allowobfuscation",
        "--lib",
        "${getJavaHome(Jdk.JDK_25)}",
        "--lib",
        "$testDepsJar",
        "--output",
        "$output",
      )
    mainDepsJarFilesConfig.forEach {
      argList.add("--lib")
      argList.add("$it")
    }
    targetJarProviders.forEach {
      argList.add("--target")
      argList.add("${it.getSingleOutputFile()}")
    }
    testJarProviders.forEach {
      argList.add("--source")
      argList.add("${it.getSingleOutputFile()}")
    }
    commandLine =
      baseCompilerCommandLine(
        listOf("-Dcom.android.tools.r8.tracereferences.obfuscateAllEnums"),
        r8WithRelocatedDepsJar,
        "tracereferences",
        argList,
      )
  }

  val generateKeepRulesForR8LibWithRelocatedDeps =
    register<Exec>("generateKeepRulesForR8LibWithRelocatedDeps") {
      generateKeepRulesForR8Lib(
        listOf(r8WithRelocatedDepsTask),
        listOf(relocateTestsForR8LibWithRelocatedDeps, relocateTestBaseForR8LibWithRelocatedDeps),
        "generated-keep-rules-r8lib.txt",
      )
    }

  val generateKeepRulesForR8LibNoDeps =
    register<Exec>("generateKeepRulesForR8LibNoDeps") {
      generateKeepRulesForR8Lib(
        listOf(swissArmyKnifeTask, mainProtoJarTask),
        listOf(packageTests, packageTestBase),
        "generated-keep-rules-r8lib-exclude-deps.txt",
      )
    }

  fun CreateR8LibraryTask.assembleR8Lib(
    inputJarProvider: Provider<RegularFile>,
    generatedKeepRulesProvider: TaskProvider<Exec>,
    classpath: List<Task>,
    artifactName: String,
  ) {
    r8compilerClasspath.from(r8WithRelocatedDepsTask)
    inputJar = inputJarProvider
    inputClasspath.from(classpath)
    replaceInOutputJar = assistantJarConfig.elements.map { it.single().asFile }
    pgConfigs.from(
      File(rootDir, "src/main/keep.txt"),
      File(rootDir, "src/main/discard.txt"),
      generatedKeepRulesProvider,
      // TODO(b/294351878): Remove once enum issue is fixed
      File(rootDir, "src/main/keep_r8resourceshrinker.txt"),
    )
    enableKeepAnnotations = true
    enableHorizontalClassMerging = true
    if (classpath.isNotEmpty()) excludingDepsVariant = true

    setOutputJarFile(File(rootDir, "build/libs/$artifactName"))
  }

  val assembleR8LibNoDeps =
    register<CreateR8LibraryTask>("assembleR8LibNoDeps") {
      assembleR8Lib(
        inputJarProvider = swissArmyKnifeTask.archiveFile,
        generatedKeepRulesProvider = generateKeepRulesForR8LibNoDeps,
        classpath = listOf(mainDepsJarTask, mainProtoJarTask),
        artifactName = "r8lib-exclude-deps.jar",
      )
      // TODO: remove when swissArmyKnifeTask is accessed via a TaskProvider
      dependsOn(swissArmyKnifeTask)
    }

  val assembleR8LibWithRelocatedDeps =
    register<CreateR8LibraryTask>("assembleR8LibWithRelocatedDeps") {
      assembleR8Lib(
        inputJarProvider = r8WithRelocatedDepsTask.outputFile,
        generatedKeepRulesProvider = generateKeepRulesForR8LibWithRelocatedDeps,
        classpath = listOf(),
        artifactName = "r8lib.jar",
      )
      // TODO: remove when r8WithRelocatedDepsTask is accessed via a TaskProvider
      dependsOn(r8WithRelocatedDepsTask)
    }

  register<CreateR8LibraryTask>("keepAnnoToolsLib") {
    r8compilerClasspath.from(r8WithRelocatedDepsTask)
    inputJar = keepAnnoToolsWithRelocatedDepsTask.getSingleOutputFile()
    pgConfigs.from(File(rootDir, "src/keepanno/keep.txt"))
    inputClasspath.from(keepAnnoDepsJarOnlyAsmConfig)
    enableKeepAnnotations = true

    setOutputJarFile(File(rootDir, "build/libs/keepanno-toolslib.jar"))
  }

  fun Task.generateTestKeepRulesForR8Lib(r8LibJarMap: Provider<RegularFile>, artifactName: String) {
    inputs.files(r8LibJarMap)
    val output = rootProject.layout.buildDirectory.get().asFile.resolveAll("libs", artifactName)
    outputs.files(output)
    doLast {
      // TODO(b/299065371): We should be able to take in the partition map output.
      output.writeText(
        """-keep class ** { *; }
-dontshrink
-dontoptimize
-keepattributes *
-applymapping ${r8LibJarMap.get().asFile.absolutePath}
"""
      )
    }
  }

  val generateTestKeepRulesR8LibWithRelocatedDeps =
    register("generateTestKeepRulesR8LibWithRelocatedDeps") {
      generateTestKeepRulesForR8Lib(
        assembleR8LibWithRelocatedDeps.flatMap { it.outputPgMap },
        "r8lib-tests-keep.txt",
      )
    }

  val generateTestKeepRulesR8LibNoDeps =
    register("generateTestKeepRulesR8LibNoDeps") {
      generateTestKeepRulesForR8Lib(
        assembleR8LibNoDeps.flatMap { it.outputPgMap },
        "r8lib-exclude-deps-tests-keep.txt",
      )
    }

  fun Exec.rewriteTestsForR8Lib(
    keepRulesFileProvider: TaskProvider<Task>,
    r8JarProvider: Task,
    testJarProvider: TaskProvider<*>,
    artifactName: String,
    addTestBaseClasspath: Boolean,
  ) {
    dependsOn(
      keepRulesFileProvider,
      packageTestDeps,
      relocateTestsForR8LibWithRelocatedDeps,
      r8JarProvider,
      r8WithRelocatedDepsTask,
      testJarProvider,
      packageTestBaseExcludeKeep,
    )
    val keepRulesFile = keepRulesFileProvider.getSingleOutputFile()
    val r8Jar = r8JarProvider.getSingleOutputFile()
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val testBaseJar = packageTestBaseExcludeKeep.getSingleOutputFile()
    val testDepsJar = packageTestDeps.getSingleOutputFile()
    val testJar = testJarProvider.getSingleOutputFile()
    inputs.files(keepRulesFile, r8Jar, r8WithRelocatedDepsJar, testDepsJar, testJar)
    val outputJar = getRoot().resolveAll("build", "libs", artifactName)
    outputs.file(outputJar)
    val args =
      mutableListOf(
        "--classfile",
        "--debug",
        "--lib",
        "${getJavaHome(Jdk.JDK_25)}",
        "--classpath",
        "$r8Jar",
        "--classpath",
        "$testDepsJar",
        "--output",
        "$outputJar",
        "--pg-conf",
        "$keepRulesFile",
        "$testJar",
      )
    if (addTestBaseClasspath) {
      args.add("--classpath")
      args.add("$testBaseJar")
    }
    commandLine =
      baseCompilerCommandLine(
        listOf("-Dcom.android.tools.r8.tracereferences.obfuscateAllEnums"),
        r8WithRelocatedDepsJar,
        "r8",
        args,
      )
  }

  val rewriteTestsForR8LibWithRelocatedDeps =
    register<Exec>("rewriteTestsForR8LibWithRelocatedDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibWithRelocatedDeps,
        r8WithRelocatedDepsTask,
        relocateTestsForR8LibWithRelocatedDeps,
        "r8libtestdeps-cf.jar",
        true,
      )
    }

  val rewriteTestBaseForR8LibWithRelocatedDeps =
    register<Exec>("rewriteTestBaseForR8LibWithRelocatedDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibWithRelocatedDeps,
        r8WithRelocatedDepsTask,
        relocateTestBaseForR8LibWithRelocatedDeps,
        "r8libtestbase-cf.jar",
        false,
      )
    }

  val rewriteTestsForR8LibNoDeps =
    register<Exec>("rewriteTestsForR8LibNoDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibNoDeps,
        swissArmyKnifeTask,
        packageTests,
        "r8lib-exclude-deps-testdeps-cf.jar",
        true,
      )
    }

  val cleanUnzipTests =
    register<Delete>("cleanUnzipTests") {
      dependsOn(packageTests)
      val outputDir = layout.buildDirectory.dir("unpacked/test")
      setDelete(outputDir)
    }

  val unzipTests =
    register<Copy>("unzipTests") {
      dependsOn(cleanUnzipTests, packageTests)
      val outputDir = layout.buildDirectory.dir("unpacked/test")
      from(zipTree(packageTests.getSingleOutputFile()))
      into(outputDir)
    }

  val unzipTestBase =
    register<Copy>("unzipTestBase") {
      dependsOn(cleanUnzipTests, packageTestBase)
      val outputDir = layout.buildDirectory.dir("unpacked/testbase")
      from(zipTree(packageTestBase.getSingleOutputFile()))
      into(outputDir)
    }

  fun Copy.unzipRewrittenTestsForR8Lib(
    rewrittenTestJarProvider: TaskProvider<Exec>,
    outDirName: String,
  ) {
    dependsOn(rewrittenTestJarProvider)
    val outputDir = layout.buildDirectory.dir("unpacked/$outDirName")
    val rewrittenTestJar = rewrittenTestJarProvider.getSingleOutputFile()
    from(zipTree(rewrittenTestJar))
    into(outputDir)
  }

  val cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps =
    register<Delete>("cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps") {
      val outputDir = layout.buildDirectory.dir("unpacked/rewrittentests-r8lib")
      setDelete(outputDir)
    }

  val unzipRewrittenTestsForR8LibWithRelocatedDeps =
    register<Copy>("unzipRewrittenTestsForR8LibWithRelocatedDeps") {
      dependsOn(cleanUnzipRewrittenTestsForR8LibWithRelocatedDeps)
      unzipRewrittenTestsForR8Lib(rewriteTestsForR8LibWithRelocatedDeps, "rewrittentests-r8lib")
    }

  val cleanUnzipRewrittenTestsForR8LibNoDeps =
    register<Delete>("cleanUnzipRewrittenTestsForR8LibNoDeps") {
      val outputDir = layout.buildDirectory.dir("unpacked/rewrittentests-r8lib-exclude-deps")
      setDelete(outputDir)
    }

  val unzipRewrittenTestsForR8LibNoDeps =
    register<Copy>("unzipRewrittenTestsForR8LibNoDeps") {
      dependsOn(cleanUnzipRewrittenTestsForR8LibNoDeps)
      unzipRewrittenTestsForR8Lib(rewriteTestsForR8LibNoDeps, "rewrittentests-r8lib-exclude-deps")
    }

  fun Test.testR8Lib(
    r8Lib: TaskProvider<CreateR8LibraryTask>,
    unzipRewrittenTests: TaskProvider<Copy>,
  ) {
    fun Test.addAsInputAvailableViaSystemProperty(name: String, jarFile: Provider<RegularFile>) {
      inputs.file(jarFile).withPropertyName(name).withNormalizer(ClasspathNormalizer::class.java)
      systemProperty(name, jarFile.get().asFile.absolutePath)
    }
    logger.info("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    logger.info("NOTE: Max parallel forks " + maxParallelForks)
    dependsOn(
      packageTestDeps,
      r8Lib,
      r8WithRelocatedDepsTask,
      swissArmyKnifeTask,
      assembleR8LibNoDeps,
      rewriteTestBaseForR8LibWithRelocatedDeps,
      unzipRewrittenTests,
      unzipTests,
      unzipTestBase,
    )
    addAsInputAvailableViaSystemProperty(
      "BUILD_PROP_PROCESS_KEEP_RULES_RUNTIME_PATH",
      processKeepRulesLibWithRelocatedDepsTask.flatMap { it.outputJar },
    )
    val r8LibJar = r8Lib.flatMap { it.outputJar }
    val r8LibPartitionMapFile = r8Lib.flatMap { it.outputPartitionMap }
    val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()
    val swissArmyKnifeJar = swissArmyKnifeTask.getSingleOutputFile()
    configure(
      isR8Lib = true,
      r8Jar = r8WithRelocatedDepsJar,
      r8LibPartitionMapFile = r8LibPartitionMapFile.get().asFile,
    )
    addAsInputAvailableViaSystemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8LibJar)

    // R8lib should be used instead of the main output and all the tests in r8 should be mapped and
    // exists in r8LibTestPath.
    classpath =
      files(
        packageTestDeps.get().getOutputs().getFiles(),
        r8LibJar,
        unzipRewrittenTests.get().getOutputs().getFiles(),
        rewriteTestBaseForR8LibWithRelocatedDeps.getSingleOutputFile(),
      )
    testClassesDirs = unzipRewrittenTests.get().getOutputs().getFiles()
    systemProperty("TEST_DATA_LOCATION", unzipTests.getSingleOutputFile())
    systemProperty("TESTBASE_DATA_LOCATION", unzipTestBase.getSingleOutputFile())

    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths("keepanno" + File.separator, keepAnnoClassesConfig.asPath),
    )
    systemProperty("R8_DEPS", mainDepsJarFilesConfig.asPath)
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")
    systemProperty("R8_SWISS_ARMY_KNIFE", swissArmyKnifeJar)
    systemProperty("R8_WITH_RELOCATED_DEPS", r8WithRelocatedDepsJar)

    javaLauncher = getJavaLauncher(Jdk.JDK_25)

    reports.junitXml.outputLocation.set(getRoot().resolveAll("build", "test-results", "test"))
    reports.html.outputLocation.set(getRoot().resolveAll("build", "reports", "tests", "test"))
  }

  val testR8LibWithRelocatedDeps =
    register<Test>("testR8LibWithRelocatedDeps") {
      testR8Lib(assembleR8LibWithRelocatedDeps, unzipRewrittenTestsForR8LibWithRelocatedDeps)
    }

  val testR8LibNoDeps =
    register<Test>("testR8LibNoDeps") {
      testR8Lib(assembleR8LibNoDeps, unzipRewrittenTestsForR8LibNoDeps)
    }

  val sourceConfiguration =
    configurations.create("sourceConfiguration") {
      attributes {
        attribute(Category.CATEGORY_ATTRIBUTE, objects.named(Category.DOCUMENTATION))
        attribute(DocsType.DOCS_TYPE_ATTRIBUTE, objects.named(DocsType.SOURCES))
      }
      isTransitive = false
    }
  dependencies {
    sourceConfiguration(project(":assistant"))
    sourceConfiguration(project(":keepradius"))
    sourceConfiguration(project(":keepanno"))
    sourceConfiguration(project(":libanalyzer"))
    sourceConfiguration(project(":resourceshrinker"))
    sourceConfiguration(project(":main"))
  }
  register<Jar>("packageSources") {
    val injected = project.objects.newInstance<InjectedArcOps>()
    from(
      sourceConfiguration.elements.map { element ->
        element.map { injected.arcOps.zipTree(it.asFile) }
      }
    )
    archiveClassifier.set("sources")
    archiveFileName.set("r8-src.jar")
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
  }

  test {
    dependsOn(sharedDepsConfig)
    dependsOn(sharedTestDepsConfig)
    if (!project.hasProperty("no_internal")) {
      dependsOn(sharedDepsInternalConfig)
      dependsOn(sharedTestDepsInternalConfig)
    }
    // Build processkeepruleslib.jar when running with --only_internal.
    if (project.hasProperty("only_internal")) {
      dependsOn(processKeepRulesLibWithRelocatedDepsTask)
    }
    if (project.hasProperty("r8lib")) {
      dependsOn(testR8LibWithRelocatedDeps)
    } else if (project.hasProperty("r8lib_no_deps")) {
      dependsOn(testR8LibNoDeps)
    } else {
      dependsOn(":tests_java_8:testAll")
      dependsOn(":tests_java_11:test")
      dependsOn(":tests_java_17:test")
      dependsOn(":tests_java_21:test")
      dependsOn(":tests_java_25:test")
      dependsOn(":tests_bootstrap:test")
    }
  }
}

fun Task.getSingleOutputFile(): File = getOutputs().getSingleOutputFile()

fun TaskOutputs.getSingleOutputFile(): File = getFiles().getSingleFile()

fun TaskProvider<*>.getSingleOutputFile(): File = get().getSingleOutputFile()
