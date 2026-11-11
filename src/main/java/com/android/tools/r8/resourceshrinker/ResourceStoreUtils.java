// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import com.android.ide.common.resources.usage.ResourceStore;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.FolderTypeRelationship;
import com.android.resources.ResourceFolderType;
import com.android.resources.ResourceType;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import java.util.List;

public final class ResourceStoreUtils {

  private static final Splitter SLASH_SPLITTER = Splitter.on('/').limit(3);

  private ResourceStoreUtils() {}

  public static boolean isJarPathReachable(ResourceStore resourceStore, String path) {
    List<String> parts = SLASH_SPLITTER.splitToList(path);
    return isJarPathReachable(resourceStore, parts.get(1), parts.get(2));
  }

  private static boolean isJarPathReachable(
      ResourceStore resourceStore, String folder, String name) {
    ResourceFolderType folderType = ResourceFolderType.getFolderType(folder);
    if (folderType == null) {
      return true;
    }
    String resourceName = substringBeforeDot(name);
    for (ResourceType type : FolderTypeRelationship.getRelatedResourceTypes(folderType)) {
      if (type == ResourceType.ID) {
        continue;
      }
      for (Resource resource : resourceStore.getResources(type, resourceName)) {
        if (resource.isReachable()) {
          return true;
        }
      }
    }
    return false;
  }

  public static List<Resource> getResourcesFor(ResourceStore resourceStore, String path) {
    List<String> parts = SLASH_SPLITTER.splitToList(path);
    ResourceFolderType folderType = ResourceFolderType.getFolderType(parts.get(1));
    if (folderType == null) {
      return ImmutableList.of();
    }
    String resourceName = substringBeforeDot(parts.get(2));
    ImmutableList.Builder<Resource> result = ImmutableList.builder();
    for (ResourceType type : FolderTypeRelationship.getRelatedResourceTypes(folderType)) {
      if (type == ResourceType.ID) {
        continue;
      }
      result.addAll(resourceStore.getResources(type, resourceName));
    }
    return result.build();
  }

  private static String substringBeforeDot(String value) {
    int dotIndex = value.indexOf('.');
    return dotIndex == -1 ? value : value.substring(0, dotIndex);
  }
}
