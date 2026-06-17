// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.tasks.Copy

// The majority of the build setup is done by tests_java_8.
java {
  sourceSets.test.configure {
    java.srcDir(getRoot().resolveAll("src", "test", "java8", "naming"))
  }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include("com/android/tools/r8/naming/bridge/Creator.java")
      include("com/android/tools/r8/naming/bridge/Result.java")
      include("com/android/tools/r8/naming/bridge/ResultImpl.java")
      include("com/android/tools/r8/naming/bridge/Tester.java")
      include("com/android/tools/r8/naming/bridge/TesterImpl.java")
      include("com/android/tools/r8/naming/bridge/Main.java")
    }
  }
}
