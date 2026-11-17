// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import javax.inject.Inject
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.file.ArchiveOperations
import org.gradle.api.file.FileTree
import org.gradle.api.provider.Provider

public interface InjectedArcOps {
  @get:Inject public val arcOps: ArchiveOperations
}

/**
 * Lazily expands each archive in [config] into a [FileTree] using [ArchiveOperations.zipTree].
 * Preserves producer task dependencies and avoids capturing [Project] for configuration cache
 * compatibility.
 */
public fun Project.zipTrees(config: Provider<out Configuration>): Provider<List<FileTree>> {
  val injected = objects.newInstance(InjectedArcOps::class.java)
  return config
    .flatMap { it.elements }
    .map { elements -> elements.map { injected.arcOps.zipTree(it) } }
}
