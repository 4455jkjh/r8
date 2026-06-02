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

dependencyResolutionManagement { repositories { maven { url = uri("third_party/dependencies") } } }

fun includeProject(path: String) {
  val name = path.removePrefix(":")
  val dir = "d8_r8/${name.replace(":", "/")}"
  include(path)
  project(path).projectDir = file(dir)
}

fun includeTestProject(path: String) {
  val name = path.removePrefix(":")
  val dir = "d8_r8/test_modules/${name.replace(":", "/")}"
  include(path)
  project(path).projectDir = file(dir)
}

includeProject(":shared")
includeProject(":assistant")
includeProject(":keepradius")
includeProject(":keepanno")
includeProject(":libanalyzer")
includeProject(":resourceshrinker")
includeProject(":main")
includeProject(":utils")
includeProject(":dist")
includeProject(":library_desugar")
includeProject(":test")

includeTestProject(":testbase")
includeTestProject(":tests_bootstrap")
includeTestProject(":tests_java_8")
includeTestProject(":tests_java_8:ir")
includeTestProject(":tests_java_9")
includeTestProject(":tests_java_11")
includeTestProject(":tests_java_17")
includeTestProject(":tests_java_21")
includeTestProject(":tests_java_25")
