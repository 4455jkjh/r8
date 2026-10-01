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
  implementation(libs.gcpGradlePlugin) {
    // When create_local_maven_with_dependencies.py tries to import
    // androidx.build.gradle.gcpbuildcache:gcpbuildcache:1.0.2
    // it fails to pull in org.jetbrains.kotlin:kotlin-stdlib-common:2.2.21 that is a .pom file
    // without any jar. To work around it, we exclude it here.
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-common")
    // spdxGradlePluin pulls in a different version that is the only one pulled in by
    // create_local_maven_with_dependencies.py
    exclude(group = "com.fasterxml.jackson.core")
    exclude(group = "com.fasterxml.jackson.dataformat")
  }
  implementation(libs.androidxTracingDriverWire)
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
  plugins.register("r8-non-java8-test-conventions") {
    id = "r8-non-java8-test-conventions"
    implementationClass = "R8NonJava8TestConventionPlugin"
  }
  plugins.register("r8-settings") {
    id = "r8-settings"
    implementationClass = "R8SettingsPlugin"
  }
}

kotlin {
  compilerOptions {
    // Remove this when R8 upgrades to KGP 2.4
    freeCompilerArgs.add("-Xskip-metadata-version-check")
  }
  explicitApi()
}
