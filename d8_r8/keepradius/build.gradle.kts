// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import com.google.protobuf.gradle.ProtobufExtension
import com.google.protobuf.gradle.proto
import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.tasks.bundling.Jar
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform

plugins {
  `java-library`
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

// It seems like the use of a local maven repo does not allow adding the plugin with the id+version
// syntax. Also, for some reason the 'protobuf' extension object cannot be directly referenced.
// This configures the plugin "old style" and pulls out the extension object manually.
buildscript { dependencies { classpath("com.google.protobuf:protobuf-gradle-plugin:0.9.4") } }

apply(plugin = "com.google.protobuf")

tasks.named("generateProto") { dependsOn(":shared:downloadDeps") }

var os = DefaultNativePlatform.getCurrentOperatingSystem()
var protobuf = project.extensions.getByName("protobuf") as ProtobufExtension

protobuf.protoc {
  if (os.isLinux) {
    path = getRoot().resolveAll("third_party", "protoc", "linux-x86_64", "bin", "protoc").path
  } else if (os.isMacOsX) {
    path = getRoot().resolveAll("third_party", "protoc", "osx-x86_64", "bin", "protoc").path
  } else {
    assert(os.isWindows)
    path = getRoot().resolveAll("third_party", "protoc", "win64", "bin", "protoc.exe").path
  }
}

java {
  sourceSets.main.configure {
    java.srcDir(getRoot().resolveAll("src", "keepradius", "java"))
    proto { srcDir(getRoot().resolveAll("src", "keepradius", "proto")) }
  }
  sourceCompatibility = JvmCompatibility.sourceCompatibility
  targetCompatibility = JvmCompatibility.targetCompatibility
  toolchain { languageVersion = JavaLanguageVersion.of(JvmCompatibility.release) }
  withSourcesJar()
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

  val protoJar by
    registering(Jar::class) {
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
