// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-compiler-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

dependencies { apiInternal(project(":threading")) }

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "threading_blocking", "java"))
  }
}

val jarTask = tasks.named<Jar>("jar") { archiveFileName.set("threading-module-blocking.jar") }

configurations.consumable("threadingModuleJar") { outgoing.artifact(jarTask) }
