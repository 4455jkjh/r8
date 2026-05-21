// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker

import com.android.ide.common.resources.usage.ResourceStore
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource
import com.android.resources.FolderTypeRelationship
import com.android.resources.ResourceFolderType
import com.android.resources.ResourceType

private fun ResourceStore.isJarPathReachable(folder: String, name: String): Boolean {
  val folderType = ResourceFolderType.getFolderType(folder) ?: return true
  val resourceName = name.substringBefore('.')
  return FolderTypeRelationship.getRelatedResourceTypes(folderType)
    .filterNot { it == ResourceType.ID }
    .flatMap { getResources(it, resourceName) }
    .any { it.isReachable }
}

public fun ResourceStore.isJarPathReachable(path: String): Boolean {
  val (_, folder, name) = path.split('/', limit = 3)
  return isJarPathReachable(folder, name)
}

public fun ResourceStore.getResourcesFor(path: String): List<Resource> {
  val (_, folder, name) = path.split('/', limit = 3)
  val folderType = ResourceFolderType.getFolderType(folder) ?: return emptyList()
  val resourceName = name.substringBefore('.')
  return FolderTypeRelationship.getRelatedResourceTypes(folderType)
    .filterNot { it == ResourceType.ID }
    .flatMap { getResources(it, resourceName) }
    .toList()
}
