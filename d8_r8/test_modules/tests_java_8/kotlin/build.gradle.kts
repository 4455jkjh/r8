// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import org.gradle.api.tasks.Copy

// The majority of the build setup is done by tests_java_8.
java {
  sourceSets.test.configure {
    java.srcDir(getRoot().resolveAll("src", "test", "java8", "kotlin"))
  }
}

tasks {
  named<Copy>("processTestResources") {
    from(sourceSets.test.get().java.srcDirs) {
      include("com/android/tools/r8/kotlin/**/*.kt")
      include("com/android/tools/r8/kotlin/metadata/sealed_app/invalid.kt_txt")
      include("com/android/tools/r8/kotlin/metadata/context_parameters_lib/lib.txt")
      include("com/android/tools/r8/kotlin/metadata/context_parameters_app/main.txt")
      include("com/android/tools/r8/kotlin/metadata/context_receiver_lib/lib.txt")
      include("com/android/tools/r8/kotlin/metadata/context_receiver_app/main.txt")
      include("com/android/tools/r8/partial/kotlin/**/*.kt")
    }
  }
}
