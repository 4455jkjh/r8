// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import net.ltgt.gradle.errorprone.errorprone
import org.gradle.api.Project
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.kotlin.dsl.withType

public fun Project.configureErrorProneForJavaCompile() {
  val treatWarningsAsErrors = !project.hasProperty("disable_warnings_as_errors")
  val enableErrorProne = !project.hasProperty("disable_errorprone")
  dependencies.add("errorprone", getLibraryByName("errorprone"))
  tasks.withType<JavaCompile>().configureEach {
    options.errorprone.enabled.set(enableErrorProne)
    options.errorprone.excludedPaths.set(".*/build/generated/source/proto/main/java/.*")
    if (enableErrorProne) {
      // Non-default / Experimental checks - explicitly enforced.
      enableCheck(this, "RemoveUnusedImports", treatWarningsAsErrors)
      enableCheck(this, "InconsistentOverloads", treatWarningsAsErrors)
      enableCheck(this, "MissingDefault", treatWarningsAsErrors)
      enableCheck(this, "MultipleTopLevelClasses", treatWarningsAsErrors)
      enableCheck(this, "NarrowingCompoundAssignment", treatWarningsAsErrors)
      enableCheck(this, "UnnecessarilyFullyQualified", treatWarningsAsErrors)

      // Warnings that cause unwanted edits (e.g., inability to write informative asserts).
      options.errorprone.disable("AlreadyChecked")

      // JavaDoc related warnings. Would be nice to resolve but of no real consequence.
      options.errorprone.disable("InvalidLink")
      options.errorprone.disable("InvalidBlockTag")
      options.errorprone.disable("InvalidInlineTag")
      options.errorprone.disable("EmptyBlockTag")
      options.errorprone.disable("MissingSummary")
      options.errorprone.disable("UnrecognisedJavadocTag")
      options.errorprone.disable("AlmostJavadoc")

      // Moving away from identity and canonical items is not planned.
      options.errorprone.disable("IdentityHashMapUsage")
    }

    // Make all warnings errors. Warnings that we have chosen not to fix (or suppress) are disabled
    // outright below.
    if (treatWarningsAsErrors) {
      options.compilerArgs.add("-Werror")
    }
  }
}

private fun enableCheck(task: JavaCompile, warning: String, treatWarningsAsErrors: Boolean) {
  if (treatWarningsAsErrors) {
    task.options.errorprone.error(warning)
  } else {
    task.options.errorprone.warn(warning)
  }
}
