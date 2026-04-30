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
import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion
import org.spdx.sbom.gradle.SpdxSbomTask
import org.spdx.sbom.gradle.extensions.DefaultSpdxSbomTaskExtension

plugins {
  id("org.jetbrains.kotlin.jvm")
  id("dependencies-plugin")
  id("org.spdx.sbom") version "0.4.0"
}

kotlin {
  explicitApi()
  compilerOptions {
    jvmTarget.set(JvmTarget.fromTarget(JvmCompatibility.release.toString()))
    languageVersion.set(KotlinVersion.KOTLIN_1_8)
    apiVersion.set(KotlinVersion.KOTLIN_1_8)
  }
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
  Deps.compilerDeps.forEach {
    compileOnly(it)
    r8Deps(it)
  }
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
val blastRadiusWithoutProtoJarScope by
  configurations.dependencyScope("blastRadiusWithoutProtoJarScope")
val blastRadiusWithoutProtoJarConfig by
  configurations.resolvable("blastRadiusWithoutProtoJarConfig") {
    extendsFrom(blastRadiusWithoutProtoJarScope)
  }
val blastRadiusProtoJarScope by configurations.dependencyScope("blastRadiusProtoJarScope")
val blastRadiusProtoJarConfig by
  configurations.resolvable("blastRadiusProtoJarConfig") { extendsFrom(blastRadiusProtoJarScope) }
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
  blastRadiusWithoutProtoJarScope(project(":blastradius", "blastradiusWithoutProtoJar"))
  blastRadiusProtoJarScope(project(":blastradius", "blastradiusProtoJar"))
  keepAnnoJarScope(project(":keepanno", "keepannoJar"))
  keepAnnoDepsJarExceptAsmScope(project(":keepanno", "keepannoDepsJarExceptAsm"))
  keepAnnoToolsJarScope(project(":keepanno", "keepannoToolsJar"))
  libanalyzerJarScope(project(":libanalyzer", "libanalyzer-jar"))
  libanalyzerProtoJarScope(project(":libanalyzer", "libanalyzer-proto-jar"))
  mainJarScope(project(":main", "mainJar"))
  mainResourcesScope(project(":main", "mainResources"))
  resourceShrinkerJarScope(project(":resourceshrinker", "resourceshrinkerJar"))
}

val downloadDepsTask = projectTask("shared", "downloadDeps")
val downloadTestDepsTask = projectTask("shared", "downloadTestDeps")

fun mainJarDependencies(): FileCollection {
  return project.files(
    Callable {
      r8Deps.filter({
        "$it".contains("third_party") &&
          "$it".contains("dependencies") &&
          !"$it".contains("errorprone")
      })
    }
  )
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

tasks {
  withType<Exec> { doFirst { println("Executing command: ${commandLine.joinToString(" ")}") } }

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

  val consolidatedLicense by registering {
    dependsOn(downloadDepsTask)
    dependsOn(downloadTestDepsTask)
    val root = getRoot()
    val r8License = root.resolve("LICENSE")
    val libraryLicense = root.resolve("LIBRARY-LICENSE")
    val libraryLicenseFiles = fileTree(root.resolve("library-licensing"))
    inputs.files(
      Callable {
        listOf(r8License, libraryLicense, libraryLicenseFiles) +
          mainJarDependencies().map(::zipTree)
      }
    )

    val license = getRoot().resolveAll("build", "generatedLicense", "LICENSE")
    outputs.files(license)

    doLast {
      val dependencies = mutableListOf<String>()
      configurations
        .findByName("runtimeClasspath")!!
        .resolvedConfiguration
        .resolvedArtifacts
        .forEach {
          val identifier = it.id.componentIdentifier
          if (identifier is ModuleComponentIdentifier) {
            dependencies.add("${identifier.group}:${identifier.module}")
          }
        }
      val libraryLicenses = libraryLicense.readText()
      dependencies.forEach {
        if (!libraryLicenses.contains("- artifact: $it")) {
          throw GradleException("No license for $it in LIBRARY_LICENSE")
        }
      }
      license.getParentFile().mkdirs()
      license.createNewFile()
      license.writeText(
        buildString {
          append("This file lists all licenses for code distributed.\n")
          append("All non-library code has the following 3-Clause BSD license.\n")
          append("\n")
          append("\n")
          append(r8License.readText())
          append("\n")
          append("\n")
          append("Summary of distributed libraries:\n")
          append("\n")
          append(libraryLicense.readText())
          append("\n")
          append("\n")
          append("Licenses details:\n")
          libraryLicenseFiles.sorted().forEach { file ->
            append("\n\n")
            append(file.readText())
          }
        }
      )
    }
  }

  val swissArmyKnifeExcludeRules: PatternFilterable.() -> Unit = {
    exclude("com/android/tools/r8/threading/providers/**")
    exclude("META-INF/*.kotlin_module")
    exclude("**/*.kotlin_metadata")
    exclude("blastradius.proto")
    exclude("keepspec.proto")
    exclude("LICENSE")
    exclude("androidx/")
    exclude("androidx/annotation/")
    exclude("androidx/annotation/keep/**")
  }

  val swissArmyKnifeJarFiles =
    objects.fileCollection().apply {
      from(assistantJarConfig)
      from(blastRadiusWithoutProtoJarConfig)
      from(keepAnnoJarConfig)
      from(libanalyzerJarConfig)
      from(mainJarConfig)
      from(resourceShrinkerJarConfig)
    }

  val swissArmyKnife by
    registering(Jar::class) {
      dependsOn(swissArmyKnifeJarFiles)
      from(swissArmyKnifeJarFiles.map { zipTree(it).matching(swissArmyKnifeExcludeRules) })
      from(getRoot().resolve("LICENSE"))
      entryCompression = ZipEntryCompression.STORED
      manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("r8-full-exclude-deps.jar")
    }

  val threadingModuleBlockingJar by
    registering(Zip::class) {
      dependsOn(mainJarConfig)
      from(mainJarConfig.elements.map { it.map { zipTree(it) } })
      include("com/android/tools/r8/threading/providers/blocking/**")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("threading-module-blocking.jar")
    }

  val threadingModuleSingleThreadedJar by
    registering(Zip::class) {
      dependsOn(mainJarConfig)
      from(mainJarConfig.elements.map { it.map { zipTree(it) } })
      include("com/android/tools/r8/threading/providers/singlethreaded/**")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
      archiveFileName.set("threading-module-single-threaded.jar")
    }

  dependencies {
    add(depsJarFilesScope.name, mainJarDependencies())
    add(depsJarFilesScope.name, project(":resourceshrinker", "resourceshrinkerDepsJar"))
    add(depsJarFilesScope.name, files(threadingModuleBlockingJar))
    add(depsJarFilesScope.name, files(threadingModuleSingleThreadedJar))
  }

  artifacts { add(depsFiles.name, consolidatedLicense) }

  val depsJarFilesConfig by
    configurations.resolvable("depsJarFilesConfig") { extendsFrom(depsJarFilesScope) }

  // Jar containing all 3p deps, plus R8 threading modules.
  val depsJar by
    registering(Zip::class) {
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

  val protoJar by
    registering(Zip::class) {
      dependsOn(blastRadiusProtoJarConfig, libanalyzerProtoJarConfig)
      from(blastRadiusProtoJarConfig.map(::zipTree))
      from(libanalyzerProtoJarConfig.map(::zipTree))
      exclude("META-INF/MANIFEST.MF")
      archiveFileName.set("proto.jar")
      destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    }

  val r8WithRelocatedDepsManifest by
    registering(Jar::class) {
      manifest { attributes["Main-Class"] = "com.android.tools.r8.SwissArmyKnife" }
      archiveFileName.set("r8-manifest.jar")
    }

  val r8WithRelocatedDeps by
    registering(Exec::class) {
      dependsOn(depsJar, protoJar, swissArmyKnife, mainResourcesConfig)
      dependsOn(r8WithRelocatedDepsManifest)
      val output = getRoot().resolveAll("build", "libs", "r8.jar")
      outputs.file(output)
      inputs.files(
        Callable {
          listOf(
            depsJar.get().getSingleOutputFile(),
            protoJar.getSingleOutputFile(),
            swissArmyKnife.getSingleOutputFile(),
            mainResourcesConfig,
            r8WithRelocatedDepsManifest.getSingleOutputFile(),
          )
        }
      )
      doFirst {
        val deps = depsJar.get().getSingleOutputFile()
        val proto = protoJar.getSingleOutputFile()
        val swissArmyKnifeJar = swissArmyKnife.getSingleOutputFile()
        val mainResourcesDir = mainResourcesConfig.singleFile
        val manifestJar = r8WithRelocatedDepsManifest.getSingleOutputFile()
        val pkg = "com.android.tools.r8"
        commandLine =
          baseCompilerCommandLine(
            swissArmyKnifeJar,
            deps,
            "relocator",
            listOf(
              "--input",
              "$deps",
              "--input",
              "$proto",
              // Include the Java resources belonging to R8.
              "--input",
              "$mainResourcesDir",
              "--input",
              "$manifestJar",
              // Ensure we don't include the LICENSE and Java resources from swissArmyKnifeJar.
              "--input-no-res",
              "$swissArmyKnifeJar",
              "--output",
              "$output",
              "--map",
              "com.android.tools.r8.**->${pkg}",
              "--map",
              "com.android.tools.r8.keepanno.annotations.**->${pkg}.keepanno.annotations",
              "--map",
              "com.android.tools.r8.keepanno.**->${pkg}.relocated.keepanno",
              "--map",
              "org.objectweb.asm.**->${pkg}.org.objectweb.asm",
            ) + relocateDepsExceptAsm(pkg) + listOf("--map-diagnostics", "warning", "error"),
          )
      }
    }

  val keepAnnoToolsWithRelocatedDeps by
    registering(Exec::class) {
      dependsOn(
        depsJar,
        keepAnnoDepsJarExceptAsmConfig,
        keepAnnoToolsJarConfig,
        swissArmyKnifeJarFiles,
      )
      val output = getRoot().resolveAll("build", "libs", "keepanno-tools.jar")
      outputs.file(output)
      inputs.files(
        Callable {
          listOf(
            depsJar.get().getSingleOutputFile(),
            keepAnnoDepsJarExceptAsmConfig.singleFile,
            keepAnnoToolsJarConfig.singleFile,
            swissArmyKnifeJarFiles,
          )
        }
      )
      doFirst {
        val deps = depsJar.get().getSingleOutputFile()
        val keepAnnoDeps = keepAnnoDepsJarExceptAsmConfig.singleFile
        val keepAnnoTools = keepAnnoToolsJarConfig.singleFile
        val pkg = "com.android.tools.r8.keepanno"
        commandLine =
          baseCompilerCommandLine(
            swissArmyKnifeJarFiles,
            deps,
            "relocator",
            listOf(
              "--input-no-res",
              "$keepAnnoDeps",
              "--input-no-res",
              "$keepAnnoTools",
              "--output",
              "$output",
              "--map",
              "com.android.tools.r8.keepanno.**->${pkg}",
            ) + relocateDepsExceptAsm(pkg) + listOf("--map-diagnostics", "warning", "error"),
          )
      }
    }

  val processKeepRulesLibWithRelocatedDeps by
    registering(Exec::class) {
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

fun Task.getSingleOutputFile(): File = getOutputs().getSingleOutputFile()

fun TaskOutputs.getSingleOutputFile(): File = getFiles().getSingleFile()

fun TaskProvider<*>.getSingleOutputFile(): File = get().getSingleOutputFile()
