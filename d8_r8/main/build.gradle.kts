// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-compiler-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

val srcDir = getRoot().resolveAll("src", "main", "java")

java {
  sourceSets {
    main {
      resources.srcDirs(getRoot().resolveAll("src", "main", "resources"))
      java { srcDir(srcDir) }
    }
  }
}

val bundledResourcesScope = configurations.dependencyScope("bundledResourcesScope")
val bundledResourcesConfig =
  configurations.resolvable("bundledResourcesConfig") { extendsFrom(bundledResourcesScope.get()) }

dependencies {
  bundledResourcesScope(project(":third_party", "apiDatabase"))
  apiInternal(project(":assistant"))
  apiInternal(project(":keepradius"))
  apiInternal(project(":keepanno"))
  apiInternal(project(":threading"))
  apiInternal(project(":utils"))
  implementationExternal(libs.androidxCollection)
  implementationExternal(libs.androidxTracingDriver)
  implementationExternal(libs.androidxTracingDriverWire)
  apiExternal(libs.asm)
  implementationExternal(libs.asmCommons)
  implementationExternal(libs.asmUtil)
  apiExternal(libs.fastUtil)
  apiExternal(libs.gson)
  apiExternal(libs.guava)
  // Compile-only dependency of Guava 32.1.2-jre whose CLASS-retained annotations are referenced in
  // Guava bytecode.
  implementationExternal(libs.j2objcAnnotations)
  implementationExternal(libs.kotlinMetadata)
  implementationExternal(libs.protobuf)
  implementationExternal(libs.protobufUtil)
  apiExternal(libs.toolsAapt2Proto)
  implementationExternal(libs.toolsAnnotations)
  implementationExternal(libs.toolsCommon) {
    // R8's resource shrinker does not use JNA native platform helpers in tools:common.
    exclude(group = "net.java.dev.jna", module = "jna-platform")
  }
  implementationExternal(libs.toolsLayoutlibApi) {
    // R8 only uses layoutlib resource model types, not its kxml2 parser or JNA via tools:common.
    exclude(group = "net.java.dev.jna", module = "jna-platform")
    exclude(group = "net.sf.kxml", module = "kxml2")
  }
  implementationExternal(libs.toolsSdkCommon) {
    // R8's resource shrinker only uses a subset of sdk-common and never invokes Studio SDK
    // management, ADB/ddmlib, kxml2, JAXB, BouncyCastle, analytics, JNA, or kotlin-reflect.
    exclude(group = "com.android.tools", module = "sdklib")
    exclude(group = "com.android.tools.analytics-library")
    exclude(group = "com.android.tools.ddms", module = "ddmlib")
    exclude(group = "javax.inject", module = "javax.inject")
    exclude(group = "net.java.dev.jna", module = "jna-platform")
    exclude(group = "net.sf.kxml", module = "kxml2")
    exclude(group = "org.bouncycastle")
    exclude(group = "org.glassfish.jaxb", module = "jaxb-runtime")
    exclude(group = "org.jetbrains.intellij.deps", module = "trove4j")
    exclude(group = "org.jetbrains.kotlin", module = "kotlin-reflect")
  }
  implementationExternal(libs.zipflinger)
}

tasks {
  jar {
    doLast {
      enforceUncompressedEntries(archiveFile.get().asFile, setOf("resources/api_database.ser"))
    }
  }
}

tasks.withType<JavaCompile> {
  logger.info("NOTE: Running with JDK: " + org.gradle.internal.jvm.Jvm.current().javaHome)
}

tasks.named<Jar>("sourcesJar") { from(bundledResourcesConfig) }

tasks.withType<ProcessResources> { from(bundledResourcesConfig) }

configurations.consumable("mainResources") {
  outgoing.artifact(tasks.named<ProcessResources>("processResources").map { it.destinationDir })
}
