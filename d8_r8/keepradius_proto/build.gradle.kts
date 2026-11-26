// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto

plugins {
  `java-library`
  id("r8-conventions")
  id("r8-dependency-flavors")
  id("dependencies-plugin")
  id("com.google.protobuf")
}

java {
  sourceSets.main.configure { proto { srcDir(getRoot().resolveAll("src", "keepradius", "proto")) } }
}

dependencies { apiExternal(libs.protobuf) }

tasks.processResources { exclude("**/*.proto") }

configurations.named("protoElements") { outgoing.artifact(tasks.named("jar")) }

configurations.named("internalSourcesElements") { outgoing.artifact(tasks.named("sourcesJar")) }
