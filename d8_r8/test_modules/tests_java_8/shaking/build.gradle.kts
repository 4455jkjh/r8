// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.tasks.Copy

// The majority of the build setup is done by tests_java_8.
java {
  sourceSets.test.configure { java.srcDir(getRoot().resolveAll("src", "test", "java8", "shaking")) }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include("com/android/tools/r8/shaking/ifrule/StaticFinalFieldInliningSource.java")
    }
  }
}

dependencies {
  runtimeOnlyData(project(":third_party", "examples"))
  runtimeOnlyData(project(":third_party", "examplesAndroidN"))
  runtimeOnlyData(project(":third_party", "java8Runtime"))
  runtimeOnlyData(project(":third_party", "proguard7_0_0"))
  runtimeOnlyData(project(":third_party", "proguard7_7_0"))
  runtimeOnlyData(project(":third_party", "r8"))
}
