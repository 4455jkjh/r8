/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.tools.r8.resourceshrinker;

import com.android.aapt.Resources;
import com.android.aapt.Resources.Entry;
import com.android.aapt.Resources.Package;
import com.android.aapt.Resources.ResourceTable;
import com.google.protobuf.InvalidProtocolBufferException;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class ResourceTableUtil {

  private ResourceTableUtil() {}

  public static List<EntryWrapper> entries(ResourceTable resourceTable) {
    List<EntryWrapper> result = new ArrayList<>();
    for (Package resourcePackage : resourceTable.getPackageList()) {
      for (Resources.Type resourceType : resourcePackage.getTypeList()) {
        for (Entry resourceEntry : resourceType.getEntryList()) {
          int id = toIdentifier(resourcePackage, resourceType, resourceEntry);
          result.add(
              new EntryWrapper(
                  id, resourcePackage.getPackageName(), resourceType.getName(), resourceEntry));
        }
      }
    }
    return result;
  }

  public static ResourceTable nullOutEntriesWithIds(
      ResourceTable resourceTable, List<Integer> ids, boolean pruneResourceNames) {
    if (ids.isEmpty()) {
      return resourceTable;
    }
    Map<Integer, Map<Integer, IntSet>> packageMappings = calculatePackageMappings(ids);
    ResourceTable.Builder tableBuilder = resourceTable.toBuilder();
    for (Package.Builder packageBuilder : tableBuilder.getPackageBuilderList()) {
      Map<Integer, IntSet> typeMappings =
          packageMappings.get(packageBuilder.getPackageId().getId());
      if (typeMappings != null) {
        for (Resources.Type.Builder typeBuilder : packageBuilder.getTypeBuilderList()) {
          IntSet entryIds = typeMappings.get(typeBuilder.getTypeId().getId());
          if (entryIds != null) {
            for (Entry.Builder entryBuilder : typeBuilder.getEntryBuilderList()) {
              if (entryIds.contains(entryBuilder.getEntryId().getId())) {
                entryBuilder.clearConfigValue();
                if (pruneResourceNames) {
                  entryBuilder.clearName();
                }
                if (entryBuilder.hasOverlayableItem()) {
                  entryBuilder.clearOverlayableItem();
                }
              }
            }
          }
        }
      }
    }
    return tableBuilder.build();
  }

  private static Map<Integer, Map<Integer, IntSet>> calculatePackageMappings(List<Integer> ids) {
    Map<Integer, Map<Integer, IntSet>> packageMapping = new HashMap<>();
    for (int value : ids) {
      int packageId = packageIdFromIdentifier(value);
      int typeId = typeIdFromIdentifier(value);
      int entryId = entryIdFromIdentifier(value);
      packageMapping
          .computeIfAbsent(packageId, unused -> new HashMap<>())
          .computeIfAbsent(typeId, unused -> new IntOpenHashSet())
          .add(entryId);
    }
    return packageMapping;
  }

  public static final class EntryWrapper {
    private final int id;
    private final String packageName;
    private final String type;
    private final Entry entry;

    public EntryWrapper(int id, String packageName, String type, Entry entry) {
      this.id = id;
      this.packageName = packageName;
      this.type = type;
      this.entry = entry;
    }

    public int getId() {
      return id;
    }

    public String getPackageName() {
      return packageName;
    }

    public String getType() {
      return type;
    }

    public Entry getEntry() {
      return entry;
    }
  }

  public static int toIdentifier(Package resourcePackage, Resources.Type type, Entry entry) {
    return (resourcePackage.getPackageId().getId() << 24)
        | (type.getTypeId().getId() << 16)
        | entry.getEntryId().getId();
  }

  private static int packageIdFromIdentifier(int identifier) {
    return identifier >>> 24;
  }

  private static int typeIdFromIdentifier(int identifier) {
    return (identifier & 0x00FF0000) >> 16;
  }

  private static int entryIdFromIdentifier(int identifier) {
    return identifier & 0x0000FFFF;
  }

  public static byte[] nullOutEntriesWithIds(byte[] resourceTableBytes, List<Integer> ids)
      throws InvalidProtocolBufferException {
    ResourceTable table = ResourceTable.parseFrom(resourceTableBytes);
    ResourceTable shrunkenTable = nullOutEntriesWithIds(table, ids, false);
    return shrunkenTable.toByteArray();
  }
}
