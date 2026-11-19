// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("r8-conventions")
  id("r8-dependency-flavors")
  id("dependencies-plugin")
  id("net.ltgt.errorprone")
}

dependencies {
  compileOnly(project(":main"))
  compileOnly(project(":utils"))
}

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "tools", "java")) } }
