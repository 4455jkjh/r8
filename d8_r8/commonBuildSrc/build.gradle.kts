// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `kotlin-dsl`
  `java-gradle-plugin`
}

dependencies {
  implementation(libs.errorproneGradlePlugin)
  implementation(libs.retryGradlePlugin)
  implementation(libs.gson)
  implementation(libs.kotlinGradlePlugin)
  implementation(libs.protobufGradlePlugin)
  implementation(libs.spdxGradlePlugin)
}

gradlePlugin {
  plugins.register("dependencies-plugin") {
    id = "dependencies-plugin"
    implementationClass = "DependenciesPlugin"
  }
  plugins.register("r8-conventions") {
    id = "r8-conventions"
    implementationClass = "R8ConventionPlugin"
  }
}

kotlin { explicitApi() }
