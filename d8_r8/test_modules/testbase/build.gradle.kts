// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.JavaVersion

plugins {
  `java-library`
  id("dependencies-plugin")
  id("r8-dependency-flavors")
  id("test-runtime-data-dep")
}

val root = getRoot()

java {
  sourceSets.main.configure { java { srcDir(root.resolveAll("src", "test", "testbase", "java")) } }

  // Compiled targeting Java 8 for test inputs, while running on JDK 11+ against Java 11 :main.
  disableAutoTargetJvm()
  sourceCompatibility = JavaVersion.VERSION_1_8
  targetCompatibility = JavaVersion.VERSION_1_8
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
}

val keepAnnoJarScope = configurations.dependencyScope("keepAnnoJarScope")
val keepAnnoJarConfig =
  configurations.resolvable("keepAnnoJarConfig") { extendsFrom(keepAnnoJarScope.get()) }
val externalRuntimeConfig = configurations.named("externalRuntimeConfig")

dependencies {
  // Declare local runtime dependencies.
  runtimeOnlyData(TestRuntimeData.AAPT2)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_14)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_15)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_19)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_21)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_22)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_23)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_24)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_25)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_26)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_27)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_28)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_29)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_30)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_31)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_32)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_33)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_34)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_35)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_36)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_36_1)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_37)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_37_1)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_37_2)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_LIBCORE_LATEST)
  runtimeOnlyData(TestRuntimeData.ANDROID_JAR_MAIN)
  runtimeOnlyData(TestRuntimeData.ART_DEFAULT)
  runtimeOnlyData(TestRuntimeData.ART_5_1_1)
  runtimeOnlyData(TestRuntimeData.ART_6_0_1)
  runtimeOnlyData(TestRuntimeData.ART_7_0_0)
  runtimeOnlyData(TestRuntimeData.ART_8_1_0)
  runtimeOnlyData(TestRuntimeData.ART_9_0_0)
  runtimeOnlyData(TestRuntimeData.ART_10_0_0)
  runtimeOnlyData(TestRuntimeData.ART_12_0_0)
  runtimeOnlyData(TestRuntimeData.ART_13_0_0)
  runtimeOnlyData(TestRuntimeData.ART_14_0_0)
  runtimeOnlyData(TestRuntimeData.ART_15_0_0)
  runtimeOnlyData(TestRuntimeData.ART_16_0_0)
  runtimeOnlyData(TestRuntimeData.ART_16_1_0)
  runtimeOnlyData(TestRuntimeData.ART_17_0_0)
  runtimeOnlyData(TestRuntimeData.ART_17_1_0)
  runtimeOnlyData(TestRuntimeData.ART_17_2_0)
  runtimeOnlyData(TestRuntimeData.ART_MAIN)
  runtimeOnlyData(TestRuntimeData.ART_TESTS)
  runtimeOnlyData(TestRuntimeData.ART_TESTS_LEGACY)
  runtimeOnlyData(TestRuntimeData.CHROME_HEADLESS)
  runtimeOnlyData(TestRuntimeData.DALVIK_4_0_4)
  runtimeOnlyData(TestRuntimeData.DALVIK_4_4_4)
  runtimeOnlyData(TestRuntimeData.DEPENDENCIES)
  runtimeOnlyData(TestRuntimeData.DESUGAR_JDK_LIBS_11)
  runtimeOnlyData(TestRuntimeData.DESUGAR_JDK_LIBS_8)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_CONVERSIONS)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_0_9)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_0_10)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_0)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_1)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_RELEASE_1_1_5)
  runtimeOnlyData(TestRuntimeData.DEX2OAT_33_10)
  runtimeOnlyData(TestRuntimeData.DEX2OAT_35_14)
  runtimeOnlyData(TestRuntimeData.DEX2OAT_36_0)
  runtimeOnlyData(TestRuntimeData.DEX2OAT_HEAD)
  runtimeOnlyData(TestRuntimeData.GOOGLE_JAVA_FORMAT)
  runtimeOnlyData(TestRuntimeData.GOOGLE_KOTLIN_FORMAT)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
  runtimeOnlyData(TestRuntimeData.JDK_8)
  runtimeOnlyData(TestRuntimeData.JDK_9)
  runtimeOnlyData(TestRuntimeData.JDK_11)
  runtimeOnlyData(TestRuntimeData.JDK_17)
  runtimeOnlyData(TestRuntimeData.JDK_21)
  runtimeOnlyData(TestRuntimeData.JDK_25)
  runtimeOnlyData(TestRuntimeData.JDWP_TESTS)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_3_72)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_4_20)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_5_0)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_6_0)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_7_0)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_8_0)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_9_21)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_0_20)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_1_10)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_2_0)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_3_10)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_4_20)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_DEV)
  runtimeOnlyData(TestRuntimeData.KOTLIN_R8_TEST_RESOURCES)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_0_0)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_7_0)
}

dependencies {
  keepAnnoJarScope(project(":keepanno", "keepannoJar"))
  implementation(project(":libanalyzer"))
  api(project(":main"))
  api(project(":third_party", "jasminJar"))
  api(project(":third_party", "jdwpTestsJar"))
  api(libs.asm)
  api(libs.asmUtil)
  api(libs.ddmLib)
  api(libs.fastUtil)
  api(libs.guava)
  api(libs.javassist)
  api(libs.junitJupiter)
  api(libs.junitVintageEngine)
  api(libs.kotlinMetadata)
  api(libs.kotlinReflect)
  api(libs.playwright)
  api(libs.protobuf)
  api(libs.smali)
  api(libs.smaliUtil)
  api(libs.toolsAapt2Proto)
  runtimeOnly(project(":threading_blocking"))
  runtimeOnly(libs.junitPlatform)
}

tasks {
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
    from(zipTrees(externalRuntimeConfig))
    from(zipTrees(keepAnnoJarConfig))
    exclude("**/*.proto")
    exclude("versions-offline/**")
    exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    exclude("com/android/tools/r8/keepanno/annotations/**")
    exclude("androidx/annotation/keep/**")
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveFileName.set("deps.jar")
  }
}

configurations.consumable("testJar") { outgoing.artifact(tasks.named("assembleTestJar")) }

configurations.consumable("depsJar") { outgoing.artifact(tasks.named("assembleDepsJar")) }
