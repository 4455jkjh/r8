// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

java {
  sourceSets.test.configure { java.srcDir(getRoot().resolveAll("src", "test", "java8", "debug")) }
}

dependencies {
  runtimeOnlyData(TestRuntimeData.EXAMPLES)
  runtimeOnlyData(TestRuntimeData.EXAMPLES_ANDROID_N)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
  runtimeOnlyData(TestRuntimeData.JDK_9)
  runtimeOnlyData(TestRuntimeData.KOTLIN_COMPILER_2_4_20)
}
