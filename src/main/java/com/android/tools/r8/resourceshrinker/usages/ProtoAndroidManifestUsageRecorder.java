/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.tools.r8.resourceshrinker.usages;

import com.android.aapt.Resources.Item;
import com.android.aapt.Resources.Reference;
import com.android.aapt.Resources.XmlAttribute;
import com.android.aapt.Resources.XmlNode;
import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import com.google.common.collect.ImmutableList;
import java.util.List;

public final class ProtoAndroidManifestUsageRecorder {

  private ProtoAndroidManifestUsageRecorder() {}

  /**
   * Records only resources from element attributes that have reference items with resolved ids or
   * names.
   */
  public static List<Resource> recordUsagesFromNode(XmlNode node, ResourceShrinkerModel model) {
    if (!node.hasElement()) {
      return ImmutableList.of();
    }
    ImmutableList.Builder<Resource> reachableResources = ImmutableList.builder();
    recordUsagesFromNode(node, model, reachableResources);
    return reachableResources.build();
  }

  private static void recordUsagesFromNode(
      XmlNode node, ResourceShrinkerModel model, ImmutableList.Builder<Resource> out) {
    if (!node.hasElement()) {
      return;
    }
    for (XmlAttribute attribute : node.getElement().getAttributeList()) {
      if (!attribute.hasCompiledItem()) {
        continue;
      }
      Item item = attribute.getCompiledItem();
      if (!item.hasRef()) {
        continue;
      }
      Reference ref = item.getRef();
      // If resource id is available prefer this id to name.
      if (ref.getId() != 0) {
        Resource resource = model.getResourceStore().getResource(ref.getId());
        if (resource != null) {
          ResourceUsageModel.markReachable(resource);
          out.add(resource);
        }
      } else {
        for (Resource resource :
            model.getResourceStore().getResourcesFromUrl("@" + ref.getName())) {
          ResourceUsageModel.markReachable(resource);
          out.add(resource);
        }
      }
    }
    for (XmlNode child : node.getElement().getChildList()) {
      recordUsagesFromNode(child, model, out);
    }
  }
}
