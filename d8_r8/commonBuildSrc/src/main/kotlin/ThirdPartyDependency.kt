// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import java.io.File
import org.gradle.api.attributes.Attribute

public val ARTIFACT_INTERNAL: Attribute<Boolean> =
  Attribute.of("com.android.tools.r8.artifact.internal", Boolean::class.javaObjectType)

public val ARTIFACT_MISSING_ON_WINDOWS: Attribute<Boolean> =
  Attribute.of("com.android.tools.r8.artifact.missing_on_windows", Boolean::class.javaObjectType)

public enum class DependencyType {
  GOOGLE_STORAGE,
  X20,
}

public data class ThirdPartyDependency(
  val packageName: String,
  val path: File,
  val testOnly: Boolean = false,
  val type: DependencyType = DependencyType.GOOGLE_STORAGE,
  val missingOnWindows: Boolean = false,
) {
  val tarGzFile: File = path.resolveSibling("${path.name}.tar.gz")
  val sha1File: File = path.resolveSibling("${path.name}.tar.gz.sha1")
  val successFile: File = path.resolveSibling("${path.name}.success")
}
