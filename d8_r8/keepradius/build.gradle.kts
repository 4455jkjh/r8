// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.proto
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.Jar

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
  id("com.google.protobuf")
}

tasks.named("generateProto") { dependsOn(":third_party:downloadDeps") }

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "keepradius", "java"))
    proto { srcDir(getRoot().resolveAll("src", "keepradius", "proto")) }
  }
}

dependencies {
  compileOnly(project(":keepanno", "keepannoClasses"))
  compileOnly(Deps.protobuf)
  errorprone(Deps.errorprone)
}

tasks {
  jar {
    exclude("keepradius.proto")
    exclude("keepradiussummary.proto")
    exclude("com/android/tools/r8/keepradius/proto/**")
    archiveFileName.set("keepradius-exclude-deps.jar")
  }

  register<Jar>("protoJar") {
    from(sourceSets.main.get().output)
    include("com/android/tools/r8/keepradius/proto/**")
    archiveFileName.set("keepradius-proto.jar")
  }
}

tasks.withType<JavaCompile> {
  options.errorprone.excludedPaths.set(".*/build/generated/source/proto/main/java/.*")
}

configureErrorProneForJavaCompile()

val keepradiusWithoutProtoJar by
  configurations.consumable("keepradiusWithoutProtoJar") {
    outgoing.artifact(tasks.named<Jar>("jar"))
  }

val keepradiusProtoJar by
  configurations.consumable("keepradiusProtoJar") {
    outgoing.artifact(tasks.named<Jar>("protoJar"))
  }

val keepradiusJar by
  configurations.consumable("keepradiusJar") {
    outgoing.artifact(tasks.named<Jar>("jar"))
    outgoing.artifact(tasks.named<Jar>("protoJar"))
  }

val keepradiusSources by
  configurations.consumable("keepradiusSources") {
    outgoing.artifact(tasks.named<Jar>("sourcesJar"))
  }
