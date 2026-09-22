// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

pluginManagement {
  repositories {
    maven { url = uri("../../third_party/dependencies") }
    maven { url = uri("../../third_party/dependencies_plugin") }
  }
}

dependencyResolutionManagement {
  repositories {
    maven { url = uri("../../third_party/dependencies") }
    maven { url = uri("../../third_party/dependencies_plugin") }
  }
  versionCatalogs { create("libs") { from(files("../../gradle/libs.versions.toml")) } }
}

rootProject.name = "common-build-src"

// This is duplicated in settings.gradle.kts, keep code in sync.
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

buildCache { local { directory = resolveBuildCacheDir(rootDir.resolve("../..").normalize()) } }
