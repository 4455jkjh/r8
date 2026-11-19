// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-compiler-conventions")
  id("dependencies-plugin")
}

dependencies { compileOnly(project(":keepanno")) }

val srcDir = getRoot().resolveAll("src", "assistant", "java")

java {
  // Compiled with --release 8 for dexing at min-api 1, while depending on Java 11 :keepanno.
  disableAutoTargetJvm()
  sourceSets.main.configure { java.srcDir(srcDir) }
  tasks.withType<JavaCompile>().configureEach { options.release.set(8) }
}

val jarTask =
  tasks.named<Jar>("jar") {
    // This path & name is hardcoded in ToolHelper.
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("assistant.jar")
  }

configurations.consumable("assistantJar") { outgoing.artifact(jarTask) }
