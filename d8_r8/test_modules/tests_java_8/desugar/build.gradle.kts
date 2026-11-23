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
val javaBasePatchSourcesScope by configurations.dependencyScope("javaBasePatchSourcesScope")
val javaBasePatchSourcesConfig by
  configurations.resolvable("javaBasePatchSourcesConfig") { extendsFrom(javaBasePatchSourcesScope) }
val jdkTestLibrarySourcesScope by configurations.dependencyScope("jdkTestLibrarySourcesScope")
val jdkTestLibrarySourcesConfig by
  configurations.resolvable("jdkTestLibrarySourcesConfig") {
    extendsFrom(jdkTestLibrarySourcesScope)
  }

dependencies {
  testngScope(libs.testng)
  javaBasePatchSourcesScope(project(":third_party", "jdk11TestJavaBasePatchSources"))
  jdkTestLibrarySourcesScope(project(":third_party", "jdk11TestLibrarySources"))
  runtimeOnlyData(TestRuntimeData.JAVA_BASE_EXTENSION)
  runtimeOnlyData(TestRuntimeData.CORE_LAMBDA_STUBS)
  runtimeOnlyData(TestRuntimeData.DEPENDENCIES)
  runtimeOnlyData(TestRuntimeData.DESUGAR_JDK_LIBS_8)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_CONVERSIONS)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_0_9)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_0_10)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_0)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_1)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_5)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_O)
  runtimeOnlyData(TestRuntimeData.GSON)
  runtimeOnlyData(TestRuntimeData.GUAVA_JRE)
  runtimeOnlyData(TestRuntimeData.JACOCO)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
  runtimeOnlyData(TestRuntimeData.JDK_8)
  runtimeOnlyData(TestRuntimeData.JDK_11)
  runtimeOnlyData(TestRuntimeData.JDK11_TEST)
  runtimeOnlyData(TestRuntimeData.MULTIDEX)
  runtimeOnlyData(TestRuntimeData.R8_V2_0_74)
}

val compileJavaBaseExtension =
  tasks.register<JavaCompile>("compileJavaBaseExtension") {
    javaCompiler.set(javaToolchains.compilerFor { languageVersion.set(JavaLanguageVersion.of(11)) })
    sourceCompatibility = JavaVersion.VERSION_11.toString()
    targetCompatibility = JavaVersion.VERSION_11.toString()
    source(javaBasePatchSourcesConfig, jdkTestLibrarySourcesConfig)
    include("**/*.java")
    classpath = testngConfig
    destinationDirectory.set(layout.buildDirectory.dir("classes/javaBaseExtension"))
    val javaBasePatchFiles: FileCollection = javaBasePatchSourcesConfig
    options.compilerArgumentProviders.add(
      CommandLineArgumentProvider {
        listOf(
          "--add-reads",
          "java.base=ALL-UNNAMED",
          "--patch-module",
          "java.base=${javaBasePatchFiles.singleFile.absolutePath}",
        )
      }
    )
  }

val javaBaseExtensionJar =
  tasks.register<Jar>("javaBaseExtensionJar") {
    from(compileJavaBaseExtension.map { it.destinationDirectory })
    archiveFileName.set("java_base_extension.jar")
  }

configurations.consumable("javaBaseExtension") {
  outgoing.artifact(javaBaseExtensionJar.flatMap { it.archiveFile })
}
