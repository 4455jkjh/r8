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
  // CLASS-retained keep annotations on ThreadingModule/ThreadingModuleProvider must be visible on
  // downstream compile classpaths under -Werror.
  compileOnlyApi(project(":keepanno"))
}

java { sourceSets.main.configure { java.srcDir(getRoot().resolveAll("src", "threading", "java")) } }
