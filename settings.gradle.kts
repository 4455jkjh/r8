// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

rootProject.name = "d8-r8"

// third_party/dependencies and third_party/dependencies_plugin
// is downloaded and populated by running 'tools/gradle.py'.
pluginManagement {
  repositories {
    maven { url = uri("third_party/dependencies") }
    maven { url = uri("third_party/dependencies_plugin") }
  }
  includeBuild(rootProject.projectDir.resolve("d8_r8/commonBuildSrc"))
}

/*
When create_local_maven_with_dependencies.py tries to import androidx.build.gradle.gcpbuildcache:gcpbuildcache:1.0.2
it fails to pull in org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21 that is a .pom file without any jar. To work
around it, we exclude it here. If create_local_maven_with_dependencies.py is ever fixed, buildscript and apply(plugin...
sections can be replaced with:
plugins {
  id("androidx.build.gradle.gcpbuildcache") version "1.0.2"
}
 */
buildscript {
  repositories {
    maven { url = uri("third_party/dependencies") }
    maven { url = uri("third_party/dependencies_plugin") }
  }
  dependencies {
    classpath("androidx.build.gradle.gcpbuildcache:gcpbuildcache:1.0.2") {
      exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    }
  }
}

apply(plugin = "androidx.build.gradle.gcpbuildcache")

fun enableBuildCache() {
  buildCache {
    remote(androidx.build.gradle.gcpbuildcache.GcpBuildCache::class.java) {
      projectId = "r8bot-265908"
      bucketName = "r8-build-cache"
      isPush =
        System.getenv().containsKey("R8_BOT_COMPILE_ONLY") ||
          System.getenv().containsKey("R8_BOT_SIZE")
    }
  }
}

if (System.getenv("SWARMING_BOT_ID") != null) {
  enableBuildCache()
} else {
  val uplinkLinux = File("/usr/bin/uplink-helper")
  val uplinkMac = File("/usr/local/bin/uplink-helper")
  if (uplinkLinux.exists() || uplinkMac.exists()) {
    // We are on a Google machine, enable remote cache automatically
    enableBuildCache()
  }
}

dependencyResolutionManagement { repositories { maven { url = uri("third_party/dependencies") } } }

// This is duplicated in d8_r8/commonBuildSrc/settings.gradle.kts, keep code in sync.
fun resolveBuildCacheDir(repoRoot: File): File {
  val gitFile = repoRoot.resolve(".git")
  if (gitFile.isFile) {
    try {
      val gitDirLine = gitFile.useLines { lines -> lines.firstOrNull { it.startsWith("gitdir:") } }
      if (gitDirLine != null) {
        val gitDirStr = gitDirLine.removePrefix("gitdir:").trim()
        val gitDir = repoRoot.resolve(gitDirStr).normalize()
        val commonDirFile = gitDir.resolve("commondir")
        val commonGitDir =
          if (commonDirFile.isFile) {
            gitDir.resolve(commonDirFile.readText().trim()).normalize()
          } else {
            gitDir.parentFile?.parentFile
          }
        val mainRepo = commonGitDir?.parentFile
        if (mainRepo != null && mainRepo.isDirectory && mainRepo != repoRoot) {
          return mainRepo.resolve(".buildcache")
        }
      }
    } catch (_: Exception) {
      // Fall through on error
    }
  }
  return repoRoot.resolve(".buildcache")
}

buildCache { local { directory = resolveBuildCacheDir(rootDir) } }

/**
 * path is a path to the folder containing the project, while projectPath is the gradle name (e.g.
 * :utils).
 */
private fun includeProject(path: String, projectPath: String) {
  val name = projectPath.removePrefix(":")
  val dir = "${path}${name.replace(":", "/")}"
  include(projectPath)
  project(projectPath).projectDir = file(dir)
}

fun includeProject(projectPath: String) {
  includeProject("d8_r8/", projectPath)
}

fun includeTestProject(projectPath: String) {
  includeProject("d8_r8/test_modules/", projectPath)
}

includeProject(":third_party")

includeProject(":assistant")

includeProject(":keepradius")

includeProject(":keepanno")

includeProject(":libanalyzer")

includeProject(":resourceshrinker")

includeProject(":main")

includeProject(":utils")

includeProject(":dist")

includeProject(":library_desugar")

includeProject(":swissarmyknife")

includeProject(":test")

includeProject(":tools")

includeTestProject(":testbase")

includeTestProject(":tests_bootstrap")

includeTestProject(":tests_java_8")

includeTestProject(":tests_java_8:apimodel")

includeTestProject(":tests_java_8:classmerging")

includeTestProject(":tests_java_8:ir")

includeTestProject(":tests_java_8:shaking")

includeTestProject(":tests_java_8:desugar")

includeTestProject(":tests_java_8:naming")

includeTestProject(":tests_java_8:kotlin")

includeTestProject(":tests_java_8:keepanno")

includeTestProject(":tests_java_8:retrace")

includeTestProject(":tests_java_8:optimize")

includeTestProject(":tests_java_8:regress")

includeTestProject(":tests_java_8:resolution")

includeTestProject(":tests_java_11")

includeTestProject(":tests_java_17")

includeTestProject(":tests_java_21")

includeTestProject(":tests_java_25")
