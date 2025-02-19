// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

pluginManagement {
  repositories {
    maven {
      url = uri("file:../../third_party/dependencies_plugin")
    }
    maven {
      url = uri("file:../../third_party/dependencies")
    }
  }
}

dependencyResolutionManagement {
  repositories {
    maven {
      url = uri("file:../../third_party/dependencies")
    }
  }
}

rootProject.name = "r8"

val root = rootProject.projectDir.parentFile
includeBuild(root.resolve("shared"))
includeBuild(root.resolve("assistant"))
includeBuild(root.resolve("keepanno"))
includeBuild(root.resolve("resourceshrinker"))
