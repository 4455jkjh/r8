// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import kotlin.io.readText
import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.TaskAction

@CacheableTask
public abstract class ConsolidatedLicenseTask : DefaultTask() {
  @get:[InputFile PathSensitive(PathSensitivity.NONE)]
  public abstract val r8License: RegularFileProperty

  @get:Input public abstract val dependencies: ListProperty<String>

  @get:[InputFile PathSensitive(PathSensitivity.NONE)]
  public abstract val libraryLicenseMap: RegularFileProperty

  @get:[InputFiles PathSensitive(PathSensitivity.RELATIVE)]
  public abstract val libraryLicenses: DirectoryProperty

  @get:OutputFile public abstract val consolidatedOutputFile: RegularFileProperty

  @TaskAction
  public fun createLicense() {
    val libraryLicenseMapText = libraryLicenseMap.get().asFile.readText()
    dependencies.get().forEach { dependency ->
      if (!libraryLicenseMapText.contains("- artifact: $dependency")) {
        throw GradleException("No license for $dependency in LIBRARY_LICENSE")
      }
    }

    val outputFile = consolidatedOutputFile.get().asFile
    outputFile.parentFile.mkdirs()
    outputFile.writeText(
      buildString {
        append("This file lists all licenses for code distributed.\n")
        append("All non-library code has the following 3-Clause BSD license.\n")
        append("\n")
        append("\n")
        append(r8License.get().asFile.readText())
        append("\n")
        append("\n")
        append("Summary of distributed libraries:\n")
        append("\n")
        append(libraryLicenseMapText)
        append("\n")
        append("\n")
        append("Licenses details:\n")
        libraryLicenses.asFileTree.sorted().forEach { file ->
          append("\n\n")
          append(file.readText())
        }
      }
    )
  }
}
