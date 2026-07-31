// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-conventions")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

dependencies {
  compileOnly(project(":main", "mainClassesOutput"))
  compileOnly(project(":main", "turboClassesOutput"))
  errorprone(Deps.errorprone)
}

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "tools", "java")) } }

configureErrorProneForJavaCompile()
