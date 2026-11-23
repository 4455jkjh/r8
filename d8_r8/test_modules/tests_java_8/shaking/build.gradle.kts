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
  runtimeOnlyData(TestRuntimeData.EXAMPLES)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_N)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
  runtimeOnlyData(TestRuntimeData.JDK_9)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_1_3_72)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_0_0)
  runtimeOnlyData(TestRuntimeData.PROGUARD_7_7_0)
  runtimeOnlyData(TestRuntimeData.R8)
}
