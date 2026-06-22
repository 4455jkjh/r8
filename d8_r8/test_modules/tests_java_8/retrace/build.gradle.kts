// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.tasks.Copy

java {
  sourceSets.test.configure {
    java.srcDir(getRoot().resolveAll("src", "test", "java8", "retrace"))
  }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include("com/android/tools/r8/retrace/kt/InlineFunction.kt")
      include("com/android/tools/r8/retrace/kt/InlineFunctionsInSameFile.kt")
      include("com/android/tools/r8/retrace/kt/Main.kt")
      include("com/android/tools/r8/retrace/kt/MainInstance.kt")
      include("com/android/tools/r8/retrace/kt/MainNested.kt")
      include("com/android/tools/r8/retrace/kt/MainNestedFirstLine.kt")
      include("com/android/tools/r8/retrace/kt/MainWithMultipleInlines.kt")
      include("com/android/tools/r8/retrace/kt/NestedInlineFunction.kt")
    }
  }
}
