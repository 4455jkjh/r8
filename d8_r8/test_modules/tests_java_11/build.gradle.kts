// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
  id("r8-non-java8-test-conventions")
}

val root = getRoot()

java {
  sourceSets.test.configure { java.srcDir(root.resolveAll("src", "test", "java11")) }
  toolchain { languageVersion = JavaLanguageVersion.of(11) }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include(
        "com/android/tools/r8/jdk9/stringconcatfactory/StringConcatFactoryArrayTestClass.java"
      )
      include(
        "com/android/tools/r8/jdk9/stringconcatfactory/StringConcatFactorySpecialCharactersTestClass.java"
      )
      include("com/android/tools/r8/jdk9/stringconcatfactory/StringConcatFactoryTestClass.java")
      include(
        "com/android/tools/r8/jdk9/stringconcatfactory/StringConcatFactoryValueOfTestClass.java"
      )
    }
  }
}

dependencies {
  runtimeOnlyData(TestRuntimeData.CORE_LAMBDA_STUBS)
  runtimeOnlyData(TestRuntimeData.DESUGAR_JDK_LIBS_8)
  runtimeOnlyData(TestRuntimeData.DESUGAR_LIBRARY_CONVERSIONS)
  runtimeOnlyData(TestRuntimeData.JAVA_8_RUNTIME)
}
