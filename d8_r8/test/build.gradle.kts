// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.kotlin.dsl.register

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
}

val r8LibArtifactName = "r8lib.jar"
val r8LibNoDepsArtifactName = "r8lib-exclude-deps.jar"
val r8LibPartitionMapName = "r8lib.jar_map.zip"
val r8LibNoDepsPartitionMapName = "r8lib-exclude-deps.jar_map.zip"
val r8WithRelocatedDepsArtifactName = "r8.jar"
val processKeepRulesArtifactName = "processkeepruleslib.jar"
val swissArmyKnifeArtifactName = "r8-full-exclude-deps.jar"
val packageTestDepsArtifactName = "test_deps_all.jar"
val rewrittenTestBaseArtifactName = "r8libtestbase-cf.jar"

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

val mainProtoJarTask = project(":dist").tasks.named<Zip>("protoJar")
val mainDepsJarTask = project(":dist").tasks.named<Zip>("depsJar")
val swissArmyKnifeTask = project(":swissarmyknife").tasks.named<Jar>("jar")
val processKeepRulesLibWithRelocatedDepsTask =
  project(":dist").tasks.named<CreateR8LibraryTask>("processKeepRulesLibWithRelocatedDeps")
val r8WithRelocatedDepsTask =
  project(":dist").tasks.named<SwissArmyKnifeTask>("r8WithRelocatedDeps")
val keepAnnoToolsWithRelocatedDepsTask =
  project(":dist").tasks.named<SwissArmyKnifeTask>("keepAnnoToolsWithRelocatedDeps")

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
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(testJars.elements.map { it.map { injected.arcOps.zipTree(it) } })
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8tests.jar")
    }

  val packageTestDeps =
    register<Jar>("packageTestDeps") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(testDepsJars.elements.map { it.map { injected.arcOps.zipTree(it) } })
      from(
        keepAnnoAndroidXAnnotationsJarConfig.elements.map { it.map { injected.arcOps.zipTree(it) } }
      )
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata", "org/jspecify/**", "org/jspecify")
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set(packageTestDepsArtifactName)
    }

  val packageTestBase =
    register<Jar>("packageTestBase") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(testbaseTestJars.elements.map { it.map { injected.arcOps.zipTree(it) } })
      exclude("META-INF/*.kotlin_module", "**/*.kotlin_metadata")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8test_base.jar")
    }

  val packageTestBaseExcludeKeep =
    register<Jar>("packageTestBaseExcludeKeep") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(packageTestBase.map { injected.arcOps.zipTree(it.archiveFile) })
      // TODO(b/328353718): we have com.android.tools.r8.Keep in both test_base and main
      exclude("com/android/tools/r8/Keep.class")
      archiveFileName.set("r8test_base_no_keep.jar")
    }

  fun SwissArmyKnifeTask.executeRelocator(jarProvider: TaskProvider<Jar>, artifactName: String) {
    swissArmyKnifeClasspath.from(r8WithRelocatedDepsTask.flatMap { it.outputFile })
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

  fun SwissArmyKnifeTask.generateKeepRulesForR8Lib(
    targetJarProviders: List<Provider<RegularFile>>,
    testJarProviders: List<Provider<RegularFile>>,
    artifactName: String,
  ) {
    swissArmyKnifeClasspath.from(r8WithRelocatedDepsTask.flatMap { it.outputFile })
    compiler = "tracereferences"
    libs.from(
      getJavaHome(Jdk.JDK_25),
      packageTestDeps.flatMap { it.archiveFile },
      mainDepsJarFilesConfig,
    )
    targetJarProviders.forEach { targets.from(it) }
    testJarProviders.forEach { sources.from(it) }
    outputFile = layout.buildDirectory.file("libs/$artifactName")
    extraArgs = listOf("--keep-rules", "--allowobfuscation")
    obfuscateAllEnums = true
  }

  val generateKeepRulesForR8LibWithRelocatedDeps =
    register<SwissArmyKnifeTask>("generateKeepRulesForR8LibWithRelocatedDeps") {
      generateKeepRulesForR8Lib(
        listOf(r8WithRelocatedDepsTask.flatMap { it.outputFile }),
        listOf(
          relocateTestsForR8LibWithRelocatedDeps.flatMap { it.outputFile },
          relocateTestBaseForR8LibWithRelocatedDeps.flatMap { it.outputFile },
        ),
        "generated-keep-rules-r8lib.txt",
      )
    }

  val generateKeepRulesForR8LibNoDeps =
    register<SwissArmyKnifeTask>("generateKeepRulesForR8LibNoDeps") {
      generateKeepRulesForR8Lib(
        listOf(
          swissArmyKnifeTask.flatMap { it.archiveFile },
          mainProtoJarTask.flatMap { it.archiveFile },
        ),
        listOf(packageTests.flatMap { it.archiveFile }, packageTestBase.flatMap { it.archiveFile }),
        "generated-keep-rules-r8lib-exclude-deps.txt",
      )
    }

  fun CreateR8LibraryTask.assembleR8Lib(
    inputJarProvider: Provider<RegularFile>,
    generatedKeepRulesProvider: TaskProvider<SwissArmyKnifeTask>,
    classpath: List<Provider<RegularFile>>,
    artifactName: String,
  ) {
    r8compilerClasspath.from(r8WithRelocatedDepsTask.flatMap { it.outputFile })
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
        inputJarProvider = swissArmyKnifeTask.flatMap { it.archiveFile },
        generatedKeepRulesProvider = generateKeepRulesForR8LibNoDeps,
        classpath =
          listOf(
            mainDepsJarTask.flatMap { it.archiveFile },
            mainProtoJarTask.flatMap { it.archiveFile },
          ),
        artifactName = r8LibNoDepsArtifactName,
      )
    }

  val assembleR8LibWithRelocatedDeps =
    register<CreateR8LibraryTask>("assembleR8LibWithRelocatedDeps") {
      assembleR8Lib(
        inputJarProvider = r8WithRelocatedDepsTask.flatMap { it.outputFile },
        generatedKeepRulesProvider = generateKeepRulesForR8LibWithRelocatedDeps,
        classpath = listOf(),
        artifactName = r8LibArtifactName,
      )
    }

  register<CreateR8LibraryTask>("keepAnnoToolsLib") {
    r8compilerClasspath.from(r8WithRelocatedDepsTask.flatMap { it.outputFile })
    inputJar = keepAnnoToolsWithRelocatedDepsTask.flatMap { it.outputFile }
    pgConfigs.from(File(rootDir, "src/keepanno/keep.txt"))
    inputClasspath.from(keepAnnoDepsJarOnlyAsmConfig)
    enableKeepAnnotations = true

    setOutputJarFile(File(rootDir, "build/libs/keepanno-toolslib.jar"))
  }

  abstract class GenerateKeepRulesForR8LibTask : DefaultTask() {
    @get:InputFile abstract val r8LibJarMap: RegularFileProperty
    @get:OutputFile abstract val keepRules: RegularFileProperty

    @TaskAction
    fun writeKeepRules() {
      keepRules
        .get()
        .asFile
        .writeText(
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
    register<GenerateKeepRulesForR8LibTask>("generateTestKeepRulesR8LibWithRelocatedDeps") {
      r8LibJarMap = assembleR8LibWithRelocatedDeps.flatMap { it.outputPgMap }
      keepRules = File(rootDir, "libs/r8lib-tests-keep.txt")
    }

  val generateTestKeepRulesR8LibNoDeps =
    register<GenerateKeepRulesForR8LibTask>("generateTestKeepRulesR8LibNoDeps") {
      r8LibJarMap = assembleR8LibNoDeps.flatMap { it.outputPgMap }
      keepRules = File(rootDir, "libs/r8lib-exclude-deps-tests-keep.txt")
    }

  fun SwissArmyKnifeTask.rewriteTestsForR8Lib(
    keepRulesFileProvider: TaskProvider<GenerateKeepRulesForR8LibTask>,
    r8JarProvider: Provider<RegularFile>,
    testJarProvider: Provider<RegularFile>,
    artifactName: String,
    addTestBaseClasspath: Boolean,
  ) {
    swissArmyKnifeClasspath.from(r8WithRelocatedDepsTask.flatMap { it.outputFile })
    compiler = "r8"
    libs.from(getJavaHome(Jdk.JDK_25))
    classpath.from(r8JarProvider, packageTestDeps)
    if (addTestBaseClasspath) {
      classpath.from(packageTestBaseExcludeKeep)
    }
    pgConfigs.from(keepRulesFileProvider.flatMap { it.keepRules })
    outputFile = File(rootDir, "build/libs/$artifactName")
    extraArgs = listOf("--classfile", "--debug")
    jar = testJarProvider
    obfuscateAllEnums = true
  }

  val rewriteTestsForR8LibWithRelocatedDeps =
    register<SwissArmyKnifeTask>("rewriteTestsForR8LibWithRelocatedDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibWithRelocatedDeps,
        r8WithRelocatedDepsTask.flatMap { it.outputFile },
        relocateTestsForR8LibWithRelocatedDeps.flatMap { it.outputFile },
        "r8libtestdeps-cf.jar",
        true,
      )
    }

  val rewriteTestBaseForR8LibWithRelocatedDeps =
    register<SwissArmyKnifeTask>("rewriteTestBaseForR8LibWithRelocatedDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibWithRelocatedDeps,
        r8WithRelocatedDepsTask.flatMap { it.outputFile },
        relocateTestBaseForR8LibWithRelocatedDeps.flatMap { it.outputFile },
        rewrittenTestBaseArtifactName,
        false,
      )
    }

  val rewriteTestsForR8LibNoDeps =
    register<SwissArmyKnifeTask>("rewriteTestsForR8LibNoDeps") {
      rewriteTestsForR8Lib(
        generateTestKeepRulesR8LibNoDeps,
        swissArmyKnifeTask.flatMap { it.archiveFile },
        packageTests.flatMap { it.archiveFile },
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
      dependsOn(cleanUnzipTests)
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(packageTests.map { injected.arcOps.zipTree(it.archiveFile) })
      into(layout.buildDirectory.dir("unpacked/test"))
    }

  val unzipTestBase =
    register<Copy>("unzipTestBase") {
      dependsOn(cleanUnzipTests)
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(packageTestBase.map { injected.arcOps.zipTree(it.archiveFile) })
      into(layout.buildDirectory.dir("unpacked/testbase"))
    }

  fun Copy.unzipRewrittenTestsForR8Lib(
    rewrittenTestJarProvider: TaskProvider<SwissArmyKnifeTask>,
    outDirName: String,
  ) {
    val injected = project.objects.newInstance<InjectedArcOps>()
    from(rewrittenTestJarProvider.map { injected.arcOps.zipTree(it.outputFile) })
    into(layout.buildDirectory.dir("unpacked/$outDirName"))
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

  fun Test.addAsInputAvailableViaSystemProperty(
    name: String,
    jarFile: Provider<RegularFile>,
    usePrebuiltLib: Boolean,
  ) {
    if (!usePrebuiltLib) {
      inputs.file(jarFile).withPropertyName(name).withNormalizer(ClasspathNormalizer::class.java)
    }
    systemProperty(name, jarFile.get().asFile.absolutePath)
  }

  fun Test.setupR8LibTestEnvironment(
    packageTestDeps: FileCollection,
    r8LibJar: Provider<RegularFile>,
    unpackedTests: FileCollection,
    rewrittenTestBase: File,
    r8WithRelocatedDepsJar: File,
    r8LibPartitionMapFile: File?,
    testDataLocation: File,
    testBaseDataLocation: File,
    processKeepRulesJar: Provider<RegularFile>,
    swissArmyKnifeJar: Provider<RegularFile>,
    usePrebuiltLib: Boolean,
  ) {
    configure(
      isR8Lib = true,
      r8Jar = r8WithRelocatedDepsJar,
      r8LibPartitionMapFile = r8LibPartitionMapFile,
    )
    // R8lib should be used instead of the main output and all the tests in r8 should be mapped and
    // exists in r8LibTestPath.
    classpath = files(packageTestDeps, r8LibJar, unpackedTests, rewrittenTestBase)
    testClassesDirs = unpackedTests
    systemProperty("TEST_DATA_LOCATION", testDataLocation.absolutePath)
    systemProperty("TESTBASE_DATA_LOCATION", testBaseDataLocation.absolutePath)
    systemProperty("R8_WITH_RELOCATED_DEPS", r8WithRelocatedDepsJar.absolutePath)
    addAsInputAvailableViaSystemProperty(
      "BUILD_PROP_PROCESS_KEEP_RULES_RUNTIME_PATH",
      processKeepRulesJar,
      usePrebuiltLib,
    )
    addAsInputAvailableViaSystemProperty("BUILD_PROP_R8_RUNTIME_PATH", r8LibJar, usePrebuiltLib)
    addAsInputAvailableViaSystemProperty("R8_SWISS_ARMY_KNIFE", swissArmyKnifeJar, usePrebuiltLib)
  }

  fun Test.testR8Lib(
    r8Lib: TaskProvider<CreateR8LibraryTask>,
    unzipRewrittenTests: TaskProvider<Copy>,
  ) {
    logger.info("NOTE: Number of processors " + Runtime.getRuntime().availableProcessors())
    logger.info("NOTE: Max parallel forks " + maxParallelForks)
    val usePrebuiltLib = project.hasProperty("use_prebuilt_lib")
    if (usePrebuiltLib) {
      val libsDir = getRoot().resolveAll("build", "libs")
      val excludingDepsVariant = r8Lib.name == "assembleR8LibNoDeps"
      val r8LibJarFile =
        libsDir.resolve(if (excludingDepsVariant) r8LibNoDepsArtifactName else r8LibArtifactName)
      val r8LibPartitionMapFile =
        libsDir.resolve(
          if (excludingDepsVariant) r8LibNoDepsPartitionMapName else r8LibPartitionMapName
        )
      val r8WithRelocatedDepsJar = libsDir.resolve(r8WithRelocatedDepsArtifactName)
      val processKeepRulesJar = libsDir.resolve(processKeepRulesArtifactName)
      val swissArmyKnifeJar = libsDir.resolve(swissArmyKnifeArtifactName)
      val packageTestDepsJar = libsDir.resolve(packageTestDepsArtifactName)
      val rewrittenTestBaseJar = libsDir.resolve(rewrittenTestBaseArtifactName)
      val unpackedTestsDir =
        layout.buildDirectory
          .dir(
            if (excludingDepsVariant) "unpacked/rewrittentests-r8lib-exclude-deps"
            else "unpacked/rewrittentests-r8lib"
          )
          .get()
          .asFile
      val unpackedTestDir = layout.buildDirectory.dir("unpacked/test").get().asFile
      val unpackedTestBaseDir = layout.buildDirectory.dir("unpacked/testbase").get().asFile

      doFirst {
        val requiredFiles =
          listOf(
            r8LibJarFile,
            packageTestDepsJar,
            rewrittenTestBaseJar,
            r8WithRelocatedDepsJar,
            processKeepRulesJar,
            swissArmyKnifeJar,
          )
        for (file in requiredFiles) {
          if (!file.exists()) {
            throw GradleException(
              "Prebuilt artifact not found: ${file.absolutePath}. Ensure prepareTestArtifacts ran."
            )
          }
        }
        val requiredDirs = listOf(unpackedTestsDir, unpackedTestDir, unpackedTestBaseDir)
        for (dir in requiredDirs) {
          if (!dir.exists() || dir.listFiles().isNullOrEmpty()) {
            throw GradleException(
              "Prebuilt unpacked directory missing or empty: ${dir.absolutePath}."
            )
          }
        }
      }

      setupR8LibTestEnvironment(
        packageTestDeps = files(packageTestDepsJar),
        r8LibJar = layout.file(provider { r8LibJarFile }),
        unpackedTests = files(unpackedTestsDir),
        rewrittenTestBase = rewrittenTestBaseJar,
        r8WithRelocatedDepsJar = r8WithRelocatedDepsJar,
        r8LibPartitionMapFile = r8LibPartitionMapFile,
        testDataLocation = unpackedTestDir,
        testBaseDataLocation = unpackedTestBaseDir,
        processKeepRulesJar = layout.file(provider { processKeepRulesJar }),
        swissArmyKnifeJar = layout.file(provider { swissArmyKnifeJar }),
        usePrebuiltLib = usePrebuiltLib,
      )
    } else {
      dependsOn(
        packageTestDeps,
        r8Lib,
        r8WithRelocatedDepsTask,
        rewriteTestBaseForR8LibWithRelocatedDeps,
        unzipRewrittenTests,
        unzipTests,
        unzipTestBase,
      )
      val r8LibJar = r8Lib.flatMap { it.outputJar }
      val r8LibPartitionMapFile = r8Lib.flatMap { it.outputPartitionMap }
      val r8WithRelocatedDepsJar = r8WithRelocatedDepsTask.getSingleOutputFile()

      setupR8LibTestEnvironment(
        packageTestDeps = packageTestDeps.get().getOutputs().getFiles(),
        r8LibJar = r8LibJar,
        unpackedTests = unzipRewrittenTests.get().getOutputs().getFiles(),
        rewrittenTestBase = rewriteTestBaseForR8LibWithRelocatedDeps.getSingleOutputFile(),
        r8WithRelocatedDepsJar = r8WithRelocatedDepsJar,
        r8LibPartitionMapFile = r8LibPartitionMapFile.get().asFile,
        testDataLocation = unzipTests.getSingleOutputFile(),
        testBaseDataLocation = unzipTestBase.getSingleOutputFile(),
        processKeepRulesJar = processKeepRulesLibWithRelocatedDepsTask.flatMap { it.outputJar },
        swissArmyKnifeJar = swissArmyKnifeTask.flatMap { it.archiveFile },
        usePrebuiltLib = usePrebuiltLib,
      )
    }

    systemProperty(
      "BUILD_PROP_KEEPANNO_RUNTIME_PATH",
      extractClassesPaths("keepanno" + File.separator, keepAnnoClassesConfig.asPath),
    )
    systemProperty("R8_DEPS", mainDepsJarFilesConfig.asPath)
    systemProperty("com.android.tools.r8.artprofilerewritingcompletenesscheck", "true")

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

  // Prepares all build and test artifacts required to support use_prebuilt_lib.
  val prepareTestArtifactsForR8LibWithRelocatedDeps =
    register("prepareTestArtifactsForR8LibWithRelocatedDeps") {
      dependsOn(
        assembleR8LibWithRelocatedDeps,
        r8WithRelocatedDepsTask,
        rewriteTestBaseForR8LibWithRelocatedDeps,
        unzipRewrittenTestsForR8LibWithRelocatedDeps,
        unzipTests,
        unzipTestBase,
        packageTestDeps,
        processKeepRulesLibWithRelocatedDepsTask,
        swissArmyKnifeTask,
      )
    }

  // Prepares all build and test artifacts required to support use_prebuilt_lib for the
  // excluding-deps variant.
  val prepareTestArtifactsForR8LibNoDeps =
    register("prepareTestArtifactsForR8LibNoDeps") {
      dependsOn(
        assembleR8LibNoDeps,
        r8WithRelocatedDepsTask,
        rewriteTestBaseForR8LibWithRelocatedDeps,
        unzipRewrittenTestsForR8LibNoDeps,
        unzipTests,
        unzipTestBase,
        packageTestDeps,
        processKeepRulesLibWithRelocatedDepsTask,
        swissArmyKnifeTask,
      )
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
    if (!project.hasProperty("use_prebuilt_lib")) {
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
