// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-compiler-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

dependencies {
  apiExternal(libs.guava)
  // Compile-only dependency of Guava 32.1.2-jre whose CLASS-retained annotations are referenced in
  // Guava bytecode.
  implementationExternal(libs.j2objcAnnotations)
  apiExternal(libs.fastUtil)
}

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "utils", "java")) } }
