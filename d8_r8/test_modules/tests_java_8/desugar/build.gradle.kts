// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.Copy
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

// The majority of the build setup is done by tests_java_8.
java {
  sourceSets.test.configure { java.srcDir(getRoot().resolveAll("src", "test", "java8", "desugar")) }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include("com/android/tools/r8/desugar/nestaccesscontrol/methodparameters/Outer.java")
      include("com/android/tools/r8/desugar/twr/TwrTestSource.java")
      include("com/android/tools/r8/desugar/desugaredlibrary/kotlin/Blog.kt")
      include("com/android/tools/r8/desugar/desugaredlibrary/kotlin/Main.kt")
    }
  }
}

val testngScope by configurations.dependencyScope("testngScope")
val testngConfig by configurations.resolvable("testngConfig") { extendsFrom(testngScope) }

dependencies {
  testngScope(libs.testng)
  runtimeOnlyData(project(":third_party", "coreLambdaStubs"))
  runtimeOnlyData(project(":third_party", "gson"))
  runtimeOnlyData(project(":third_party", "guavaJre"))
  runtimeOnlyData(project(":third_party", "jacoco"))
  runtimeOnlyData(project(":tests_java_8:desugar", "javaBaseExtension"))
  runtimeOnlyData(project(":third_party", "multidex"))
}

val compileJavaBaseExtension =
  tasks.register<JavaCompile>("compileJavaBaseExtension") {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(11)) })
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
    val bootlibJavaBaseDir =
      resolve(ThirdPartyDeps.jdk11Test, "lib", "testlibrary", "bootlib", "java.base")
    source(bootlibJavaBaseDir, resolve(ThirdPartyDeps.jdk11Test, "lib", "testlibrary", "jdk"))
    include("**/*.java")
    classpath = testngConfig
    destinationDirectory.set(layout.buildDirectory.dir("classes/javaBaseExtension"))
    options.compilerArgs.addAll(
      listOf(
        "--add-reads",
        "java.base=ALL-UNNAMED",
        "--patch-module",
        "java.base=${bootlibJavaBaseDir.singleFile.absolutePath}",
      )
    )
  }

val javaBaseExtensionJar =
  tasks.register<Jar>("javaBaseExtensionJar") {
    from(compileJavaBaseExtension.map { it.destinationDirectory })
    archiveFileName.set("java_base_extension.jar")
  }

configurations.consumable("javaBaseExtension") {
  attributes { attribute(TEST_DEP_PROP, "JAVA_BASE_EXTENSION") }
  outgoing.artifact(javaBaseExtensionJar.flatMap { it.archiveFile })
}
