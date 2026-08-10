// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.gson.Gson
import java.net.URI
import java.nio.file.Files.readString
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.Callable
import org.gradle.api.tasks.bundling.Jar
import org.gradle.kotlin.dsl.provideDelegate
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("r8-conventions")
  id("dependencies-plugin")
  id("org.spdx.sbom")
}

if (project.hasProperty("spdxVersion")) {
  project.version = project.property("spdxVersion")!!
}

// We need all the runtime deps for SPDX generation.
val r8Deps by
  configurations.creating {
    isCanBeConsumed = false
    isCanBeResolved = true
  }

dependencies {
  compileOnly(libs.bundles.compilerDeps)
  r8Deps(libs.bundles.compilerDeps)
}

spdxSbom {
  targets {
    create("r8") {
      // Use of both compileClasspath and runtimeClasspath due to how the
      // dependencies jar is built and dependencies above therefore use
      // compileOnly for actual runtime dependencies.
      configurations.set(listOf("compileClasspath", "runtimeClasspath"))
      scm {
        uri.set("https://r8.googlesource.com/r8/")
        if (project.hasProperty("spdxRevision")) {
          revision.set(project.property("spdxRevision").toString())
        }
      }
      document {
        name.set("R8 Compiler Suite")
        // Generate version 5 UUID from fixed namespace UUID and name generated from revision
        // (git hash) and artifact name.
        if (project.hasProperty("spdxRevision")) {
          namespace.set(
            "https://spdx.google/" +
              uuid5(
                UUID.fromString("df17ea25-709b-4edc-8dc1-d3ca82c74e8e"),
                project.property("spdxRevision").toString() + "-r8",
              )
          )
        }
        creator.set("Organization: Google LLC")
        packageSupplier.set("Organization: Google LLC")
      }
    }
  }
}

val assistantJarScope by configurations.dependencyScope("assistantJarScope")
val assistantJarConfig by
  configurations.resolvable("assistantJarConfig") { extendsFrom(assistantJarScope) }
val keepRadiusWithoutProtoJarScope by
  configurations.dependencyScope("keepRadiusWithoutProtoJarScope")
val keepRadiusWithoutProtoJarConfig by
  configurations.resolvable("keepRadiusWithoutProtoJarConfig") {
    extendsFrom(keepRadiusWithoutProtoJarScope)
  }
val keepRadiusProtoJarScope by configurations.dependencyScope("keepRadiusProtoJarScope")
val keepRadiusProtoJarConfig by
  configurations.resolvable("keepRadiusProtoJarConfig") { extendsFrom(keepRadiusProtoJarScope) }
val keepAnnoJarScope by configurations.dependencyScope("keepAnnoJarScope")
val keepAnnoJarConfig by
  configurations.resolvable("keepAnnoJarConfig") { extendsFrom(keepAnnoJarScope) }
val keepAnnoDepsJarExceptAsmScope by configurations.dependencyScope("keepAnnoDepsJarExceptAsmScope")
val keepAnnoDepsJarExceptAsmConfig by
  configurations.resolvable("keepAnnoDepsJarExceptAsmConfig") {
    extendsFrom(keepAnnoDepsJarExceptAsmScope)
  }
val keepAnnoToolsJarScope by configurations.dependencyScope("keepAnnoToolsJarScope")
val keepAnnoToolsJarConfig by
  configurations.resolvable("keepAnnoToolsJarConfig") { extendsFrom(keepAnnoToolsJarScope) }

val resourceShrinkerJarScope by configurations.dependencyScope("resourceShrinkerJarScope")
val resourceShrinkerJarConfig by
  configurations.resolvable("resourceShrinkerJarConfig") { extendsFrom(resourceShrinkerJarScope) }

val libanalyzerJarScope by configurations.dependencyScope("libanalyzerJarScope")
val libanalyzerJarConfig by
  configurations.resolvable("libanalyzerJarConfig") { extendsFrom(libanalyzerJarScope) }
val libanalyzerProtoJarScope by configurations.dependencyScope("libanalyzerProtoJarScope")
val libanalyzerProtoJarConfig by
  configurations.resolvable("libanalyzerProtoJarConfig") { extendsFrom(libanalyzerProtoJarScope) }

val mainJarScope by configurations.dependencyScope("mainJarScope")
val mainJarConfig by configurations.resolvable("mainJarConfig") { extendsFrom(mainJarScope) }
val mainResourcesScope by configurations.dependencyScope("mainResourcesScope")
val mainResourcesConfig by
  configurations.resolvable("mainResourcesConfig") { extendsFrom(mainResourcesScope) }

dependencies {
  assistantJarScope(project(":assistant", "assistantJar"))
  keepRadiusWithoutProtoJarScope(project(":keepradius", "keepradiusWithoutProtoJar"))
  keepRadiusProtoJarScope(project(":keepradius", "keepradiusProtoJar"))
  keepAnnoJarScope(project(":keepanno", "keepannoJar"))
  keepAnnoDepsJarExceptAsmScope(project(":keepanno", "keepannoDepsJarExceptAsm"))
  keepAnnoToolsJarScope(project(":keepanno", "keepannoToolsJar"))
  libanalyzerJarScope(project(":libanalyzer", "libanalyzer-jar"))
  libanalyzerProtoJarScope(project(":libanalyzer", "libanalyzer-proto-jar"))
  mainJarScope(project(":main", "mainJar"))
  mainResourcesScope(project(":main", "mainResources"))
  resourceShrinkerJarScope(project(":resourceshrinker", "resourceshrinkerJar"))
}

val sharedDepsScope by configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig by
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope) }

val sharedTestDepsScope by configurations.dependencyScope("sharedTestDepsScope")
val sharedTestDepsConfig by
  configurations.resolvable("sharedTestDepsConfig") { extendsFrom(sharedTestDepsScope) }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  sharedTestDepsScope(project(":third_party", "sharedTestDepsFiles"))
}

val depsJarFilesScope by configurations.dependencyScope("depsJarFilesScope")

val depsJarFiles by configurations.consumable("depsJarFiles") { extendsFrom(depsJarFilesScope) }

val depsFiles by configurations.consumable("depsFiles") { extendsFrom(depsJarFilesScope) }

fun relocateDepsExceptAsm(pkg: String): List<String> {
  return listOf(
    "--map",
    "android.aapt.**->${pkg}.android.aapt",
    "--map",
    "androidx.annotation.**->${pkg}.androidx.annotation",
    "--map",
    "androidx.collection.**->${pkg}.androidx.collection",
    "--map",
    "androidx.tracing.**->${pkg}.androidx.tracing",
    "--map",
    "com.android.**->${pkg}.com.android",
    "--map",
    "com.android.zipflinger.**->${pkg}.com.android.zipflinger",
    "--map",
    "com.google.common.**->${pkg}.com.google.common",
    "--map",
    "com.google.gson.**->${pkg}.com.google.gson",
    "--map",
    "com.google.thirdparty.**->${pkg}.com.google.thirdparty",
    "--map",
    "com.squareup.wire.**->${pkg}.com.squareup.wire",
    "--map",
    "it.unimi.dsi.fastutil.**->${pkg}.it.unimi.dsi.fastutil",
    "--map",
    "kotlin.**->${pkg}.jetbrains.kotlin",
    "--map",
    "kotlinx.**->${pkg}.jetbrains.kotlinx",
    "--map",
    "okio.**->${pkg}.okio",
    "--map",
    "org.jetbrains.**->${pkg}.org.jetbrains",
    "--map",
    "org.intellij.**->${pkg}.org.intellij",
    "--map",
    "org.checkerframework.**->${pkg}.org.checkerframework",
    "--map",
    "com.google.j2objc.**->${pkg}.com.google.j2objc",
    "--map",
    "com.google.protobuf.**->${pkg}.com.google.protobuf",
    "--map",
    "perfetto.protos.**->${pkg}.perfetto.protos",
    "--map",
    "org.jspecify.annotations.**->${pkg}.org.jspecify.annotations",
    "--map",
    "_COROUTINE.**->${pkg}._COROUTINE",
  )
}

interface InjectedArcOps {
  @get:Inject val arcOps: ArchiveOperations
}

tasks {
  withType<Exec> { doFirst { println("Executing command: ${commandLine.joinToString(" ")}") } }

  val filteredDepsJar =
    register<Jar>("filteredDepsJar") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      from(
        Callable {
          r8Deps.incoming.files
            .filter {
              val path = it.absolutePath
              path.contains("third_party") &&
                path.contains("dependencies") &&
                !path.contains("errorprone")
            }
            .map { injected.arcOps.zipTree(it) }
        }
      )

      archiveFileName.set("r8-deps-merged.jar")
      destinationDirectory.set(layout.buildDirectory.dir("libs"))
      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }

  withType<SpdxSbomTask> {
    taskExtension.set(
      object : DefaultSpdxSbomTaskExtension() {
        override fun mapRepoUri(input: URI?, moduleId: ModuleVersionIdentifier): URI? {

          // Locate the file origin.json with URL for download location.
          fun getOriginJson(): java.nio.file.Path {
            var repositoryDir =
              moduleId.group.replace('.', '/') + "/" + moduleId.name + "/" + moduleId.version
            return Paths.get("third_party", "dependencies", repositoryDir, "origin.json")
          }

          // Simple data model of the content of origin.json generated by the tool to download
          // and create a local repository. E.g.:
          /*
              {
                "artifacts": [
                  {
                    "file": "org/ow2/asm/asm/9.5/asm-9.5.pom",
                    "repo": "https://repo1.maven.org/maven2/",
                    "artifact": "org.ow2.asm:asm:pom:9.5"
                  },
                  {
                    "file": "org/ow2/asm/asm/9.5/asm-9.5.jar",
                    "repo": "https://repo1.maven.org/maven2/",
                    "artifact": "org.ow2.asm:asm:jar:9.5"
                  }
                ]
              }
          */
          data class Artifact(val file: String, val repo: String, val artifact: String)
          data class Artifacts(val artifacts: List<Artifact>)

          // Read origin.json.
          val json = readString(getOriginJson())
          val artifacts = Gson().fromJson(json, Artifacts::class.java)
          return URI.create(artifacts.artifacts.get(0).repo)
        }
      }
    )
  }

  val consolidatedLicense =
    register<ConsolidatedLicenseTask>("consolidatedLicense") {
      r8License = File(getRootDir(), "LICENSE")
      libraryLicenseMap = File(getRootDir(), "LIBRARY-LICENSE")
      libraryLicenses = File(getRootDir(), "library-licensing")
      dependencies =
        configurations.named("runtimeClasspath").map { runtimeClasspath ->
          runtimeClasspath.incoming.resolutionResult.allComponents.mapNotNull {
            val compId = it.id
            if (compId is ModuleComponentIdentifier) {
              "${compId.group}:${compId.module}"
            } else null
          }
        }
      consolidatedOutputFile = File(getRootDir(), "build/generatedLicense/LICENSE")
    }

  val swissArmyKnifeExcludeRules: PatternFilterable.() -> Unit = {
    exclude("com/android/tools/r8/threading/providers/**")
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    exclude("keepradius.proto")
    exclude("keepspec.proto")
    exclude("LICENSE")
    exclude("androidx/")
    exclude("androidx/annotation/")
    exclude("androidx/annotation/keep/**")
  }

  val configsToMerge =
    listOf(
      assistantJarConfig,
      keepRadiusWithoutProtoJarConfig,
      keepAnnoJarConfig,
      libanalyzerJarConfig,
      mainJarConfig,
      resourceShrinkerJarConfig,
    )

  val swissArmyKnife =
    register<Jar>("swissArmyKnife") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      configsToMerge.forEach { config ->
        dependsOn(config)
        from(
          config.elements.map {
            it.map { injected.arcOps.zipTree(it).matching(swissArmyKnifeExcludeRules) }
          }
        )
      }
      from(File(getRootDir(), "LICENSE"))
      entryCompression = ZipEntryCompression.STORED
      manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
      destinationDirectory.set(File(getRootDir(), "build/libs"))
      archiveFileName.set("r8-full-exclude-deps.jar")
    }

  val threadingModuleBlockingJar =
    register<Zip>("threadingModuleBlockingJar") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      dependsOn(mainJarConfig)
      from(mainJarConfig.elements.map { it.map { injected.arcOps.zipTree(it) } })
      include("com/android/tools/r8/threading/providers/blocking/**")
      destinationDirectory.set(File(getRootDir(), "build/libs"))
      archiveFileName.set("threading-module-blocking.jar")
    }

  val threadingModuleSingleThreadedJar =
    register<Zip>("threadingModuleSingleThreadedJar") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      dependsOn(mainJarConfig)
      from(mainJarConfig.elements.map { it.map { injected.arcOps.zipTree(it) } })
      include("com/android/tools/r8/threading/providers/singlethreaded/**")
      destinationDirectory.set(File(getRootDir(), "build/libs"))
      archiveFileName.set("threading-module-single-threaded.jar")
    }

  dependencies {
    add(depsJarFilesScope.name, files(filteredDepsJar))
    add(depsJarFilesScope.name, project(":resourceshrinker", "resourceshrinkerDepsJar"))
    add(depsJarFilesScope.name, files(threadingModuleBlockingJar))
    add(depsJarFilesScope.name, files(threadingModuleSingleThreadedJar))
  }

  val filteredDepsJarConfig by configurations.consumable("filteredDepsJarConfig")

  artifacts {
    add(depsFiles.name, consolidatedLicense)
    add(filteredDepsJarConfig.name, filteredDepsJar)
  }

  val depsJarFilesConfig by
    configurations.resolvable("depsJarFilesConfig") { extendsFrom(depsJarFilesScope) }

  // Jar containing all 3p deps, plus R8 threading modules.
  val depsJar =
    register<Zip>("depsJar") {
      dependsOn(depsJarFilesConfig)
      from(Callable { depsJarFilesConfig.files.map(::zipTree) })
      from(consolidatedLicense)
      include("**/*.class")
      include("META-INF/services/kotlin.metadata.internal.extensions.MetadataExtensions")
      include("LICENSE")
      exclude("**/module-info.class")
      exclude("javax/annotation/**")
      exclude("wireless/**")
      exclude("META-INF/versions/**")

      // Disabling compression makes this step go from 4s -> 2s as of Nov 2025,
      // as measured by "gradle --profile".
      entryCompression = ZipEntryCompression.STORED

      duplicatesStrategy = DuplicatesStrategy.EXCLUDE
      archiveFileName.set("deps.jar")
    }

  val protoJar =
    register<Zip>("protoJar") {
      val injected = project.objects.newInstance<InjectedArcOps>()
      dependsOn(keepRadiusProtoJarConfig, libanalyzerProtoJarConfig)
      from(keepRadiusProtoJarConfig.elements.map { it.map { injected.arcOps.zipTree(it) } })
      from(libanalyzerProtoJarConfig.elements.map { it.map { injected.arcOps.zipTree(it) } })
      exclude("META-INF/MANIFEST.MF")
      archiveFileName.set("proto.jar")
      destinationDirectory.set(File(getRootDir(), "build/libs"))
    }

  val r8WithRelocatedDepsManifest =
    register<Jar>("r8WithRelocatedDepsManifest") {
      manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
      archiveFileName.set("r8-manifest.jar")
    }

  val r8WithRelocatedDeps =
    register<SwissArmyKnifeTask>("r8WithRelocatedDeps") {
      swissArmyKnifeClasspath.from(swissArmyKnife, depsJar)
      compiler = "relocator"
      inputFiles.from(depsJar)
      inputFiles.from(protoJar)
      inputFiles.from(mainResourcesConfig)
      inputFiles.from(r8WithRelocatedDepsManifest)
      inputNoResFiles.from(swissArmyKnife)
      val pkg = "com.android.tools.r8"
      extraArgs =
        listOf(
          "--map",
          "com.android.tools.r8.**->${pkg}",
          "--map",
          "com.android.tools.r8.keepanno.annotations.**->${pkg}.keepanno.annotations",
          "--map",
          "com.android.tools.r8.keepanno.**->${pkg}.relocated.keepanno",
          "--map",
          "org.objectweb.asm.**->${pkg}.org.objectweb.asm",
        ) + relocateDepsExceptAsm(pkg) + listOf("--map-diagnostics", "warning", "error")
      outputFile = File(getRootDir(), "build/libs/r8.jar")
    }

  register<SwissArmyKnifeTask>("keepAnnoToolsWithRelocatedDeps") {
    swissArmyKnifeClasspath.from(swissArmyKnife, depsJar)
    compiler = "relocator"
    inputNoResFiles.from(keepAnnoDepsJarExceptAsmConfig)
    inputNoResFiles.from(keepAnnoToolsJarConfig)
    val pkg = "com.android.tools.r8.keepanno"
    extraArgs =
      listOf("--map", "com.android.tools.r8.keepanno.**->${pkg}") +
        relocateDepsExceptAsm(pkg) +
        listOf("--map-diagnostics", "warning", "error")
    outputFile = File(getRootDir(), "build/libs/keepanno-tools.jar")
  }

  register<Exec>("processKeepRulesLibWithRelocatedDeps") {
    dependsOn(r8WithRelocatedDeps)
    dependOnPythonScripts()
    val keepRulesFile = getRoot().resolveAll("src", "main", "keep_processkeeprules.txt")
    val outputJar = getRoot().resolveAll("build", "libs", "processkeepruleslib.jar")
    outputs.file(outputJar)
    inputs.files(
      Callable { listOf(keepRulesFile, r8WithRelocatedDeps.get().getSingleOutputFile()) }
    )
    doFirst {
      val r8WithRelocatedDepsJar = r8WithRelocatedDeps.get().getSingleOutputFile()
      commandLine =
        createR8LibCommandLine(
          r8WithRelocatedDepsJar,
          r8WithRelocatedDepsJar,
          outputJar,
          listOf(keepRulesFile),
          excludingDepsVariant = false,
          debugVariant = false,
          classpath = listOf(),
          enableKeepAnnotations = false,
        )
    }
  }
}

fun Task.getSingleOutputFile(): File = outputs.files.singleFile
