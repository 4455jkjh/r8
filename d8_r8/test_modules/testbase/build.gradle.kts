// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.util.concurrent.Callable
import org.gradle.api.JavaVersion

plugins {
  `java-library`
  id("dependencies-plugin")
}

val root = getRoot()

java {
  sourceSets.main.configure { java { srcDir(root.resolveAll("src", "test", "testbase", "java")) } }

  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

val keepAnnoJarScope = configurations.dependencyScope("keepAnnoJarScope")
val keepAnnoJarConfig =
  configurations.resolvable("keepAnnoJarConfig") { extendsFrom(keepAnnoJarScope.get()) }
val resourceShrinkerDepsJarScope = configurations.dependencyScope("resourceShrinkerDepsJarScope")
val resourceShrinkerDepsJarConfig =
  configurations.resolvable("resourceShrinkerDepsJarConfig") {
    extendsFrom(resourceShrinkerDepsJarScope.get())
  }
val sharedDepsScope = configurations.dependencyScope("sharedDepsScope")
val sharedDepsConfig =
  configurations.resolvable("sharedDepsConfig") { extendsFrom(sharedDepsScope.get()) }

val sharedTestDepsScope = configurations.dependencyScope("sharedTestDepsScope")
val sharedTestDepsConfig =
  configurations.resolvable("sharedTestDepsConfig") { extendsFrom(sharedTestDepsScope.get()) }

dependencies {
  sharedDepsScope(project(":third_party", "sharedDepsFiles"))
  sharedTestDepsScope(project(":third_party", "sharedTestDepsFiles"))
  // Declare local runtime dependencies.
  runtimeOnlyData(project(":third_party", "aapt2"))
  runtimeOnlyData(project(":third_party", "chromeHeadless"))
  runtimeOnlyData(project(":third_party", "dependenciesBucket"))
  runtimeOnlyData(project(":third_party", "desugarJdkLibs11"))
  runtimeOnlyData(project(":third_party", "desugarJdkLibs8"))
  runtimeOnlyData(project(":third_party", "desugarLibraryConversions"))
  runtimeOnlyData(project(":third_party", "googleJavaFormat"))
  runtimeOnlyData(project(":third_party", "googleKotlinFormat"))
  runtimeOnlyData(project(":third_party", "jdwpTests"))
  runtimeOnlyData(project(":third_party", "kotlinR8TestResources"))
}

dependencies {
  keepAnnoJarScope(project(":keepanno", "keepannoJar"))
  implementation(project(":keepanno", "keepannoJar"))
  implementation(project(":libanalyzer", "libanalyzer-compile-java"))
  implementation(project(":main", "mainClassesOutput"))
  implementation(project(":main", "mainResources"))
  resourceShrinkerDepsJarScope(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(project(":resourceshrinker", "resourceshrinkerClasses"))
  implementation(project(":resourceshrinker", "resourceshrinkerDepsJar"))
  implementation(libs.androidxCollection)
  implementation(libs.androidxTracingDriver)
  implementation(libs.androidxTracingDriverWire)
  implementation(libs.asm)
  implementation(libs.asmCommons)
  implementation(libs.asmUtil)
  implementation(libs.gson)
  implementation(libs.guava)
  implementation(libs.javassist)
  implementation(libs.junitJupiter)
  implementation(libs.junitVintageEngine)
  implementation(libs.kotlinMetadata)
  implementation(libs.kotlinReflect)
  implementation(libs.kotlinStdLib)
  implementation(libs.playwright)
  implementation(libs.protobufUtil)
  implementation(libs.ddmLib)
  implementation(resolve(ThirdPartyDeps.jasmin, "jasmin-2.4.jar"))
  implementation(resolve(ThirdPartyDeps.jdwpTests, "apache-harmony-jdwp-tests-host.jar"))
  implementation(libs.fastUtil)
  implementation(libs.smali)
  implementation(libs.smaliUtil)
  runtimeOnly(libs.junitPlatform)
}

fun testDependencies(): FileCollection {
  return sourceSets.test.get().compileClasspath.filter {
    "$it".contains("third_party") &&
      !"$it".contains("errorprone") &&
      !"$it".contains("third_party/gradle")
  }
}

tasks {
  withType<JavaCompile> {
    dependsOn(sharedDepsConfig)
    dependsOn(sharedTestDepsConfig)
  }

  withType<JavaExec> {
    if (name.endsWith("main()")) {
      // IntelliJ pass the main execution through a stream which is
      // not compatible with gradle configuration cache.
      notCompatibleWithConfigurationCache("JavaExec created by IntelliJ")
    }
  }

  register<Jar>("assembleTestJar") {
    from(sourceSets.main.get().output)
    // TODO(b/296486206): Seems like IntelliJ has a problem depending on test source sets.
    // Renaming
    //  this from the default name (testbase.jar) will allow IntelliJ to find the resources in
    //  the jar and not show red underlines. However, navigation to base classes will not work.
    archiveFileName.set("not_named_testbase.jar")
  }

  register<Jar>("assembleDepsJar") {
    dependsOn(keepAnnoJarConfig)
    dependsOn(resourceShrinkerDepsJarConfig)
    dependsOn(sharedDepsConfig)
    dependsOn(sharedTestDepsConfig)
    from(Callable { testDependencies().map(::zipTree) })
    from(keepAnnoJarConfig.map { it.map(::zipTree) })
    from(resourceShrinkerDepsJarConfig.map { it.map(::zipTree) })
    exclude("com/android/tools/r8/keepanno/annotations/**")
    exclude("androidx/annotation/keep/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}

configurations.consumable("testJar") { outgoing.artifact(tasks.named("assembleTestJar")) }

configurations.consumable("depsJar") { outgoing.artifact(tasks.named("assembleDepsJar")) }
