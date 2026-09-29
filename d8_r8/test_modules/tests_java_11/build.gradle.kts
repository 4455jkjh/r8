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

dependencies {
  runtimeOnlyData(project(":third_party", "coreLambdaStubs"))
  runtimeOnlyData(project(":third_party", "desugarLibraryConversions"))
}
