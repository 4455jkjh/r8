// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
}

dependencies { compileOnly(project(":keepanno", "keepannoClasses")) }

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "assistant", "java")) } }

val jarTask =
  tasks.named<Jar>("jar") {
    // This path & name is hardcoded in ToolHelper.
    destinationDirectory.set(getRoot().resolveAll("build", "libs"))
    archiveFileName.set("assistant.jar")
  }

val assistantJar by configurations.consumable("assistantJar") { outgoing.artifact(jarTask) }
