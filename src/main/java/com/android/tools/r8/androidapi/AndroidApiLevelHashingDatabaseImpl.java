// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.ThrowingCharIterator;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import java.io.UTFDataFormatException;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class AndroidApiLevelHashingDatabaseImpl implements AndroidApiLevelDatabase {
  private final List<DexString> androidApiExtensionPackages;
  private final Set<DexType> androidApiExtensionClasses;

  private final Map<DexReference, Optional<AndroidApiLevel>> lookupCache =
      new ConcurrentHashMap<>();
  private final Map<ApiDatabaseEntry.ConstantPoolEntry, Integer> constantPoolCache =
      new ConcurrentHashMap<>();
  private final InternalOptions options;
  private final DiagnosticsHandler diagnosticsHandler;
  private static volatile AndroidApiDataAccess dataAccess;

  private static AndroidApiDataAccess getDataAccess(
      InternalOptions options, DiagnosticsHandler diagnosticsHandler) {
    if (dataAccess == null) {
      synchronized (AndroidApiDataAccess.class) {
        if (dataAccess == null) {
          dataAccess = AndroidApiDataAccess.create(options, diagnosticsHandler);
        }
      }
    }
    return dataAccess;
  }

  public AndroidApiLevelHashingDatabaseImpl(
      List<AndroidApiForHashingReference> predefinedApiTypeLookup,
      InternalOptions options,
      DiagnosticsHandler diagnosticsHandler) {
    this.options = options;
    this.diagnosticsHandler = diagnosticsHandler;
    predefinedApiTypeLookup.forEach(
        predefinedApiReference -> {
          // Do not use computeIfAbsent since a return value of null implies the key should not be
          // inserted.
          lookupCache.put(
              predefinedApiReference.getReference(),
              Optional.of(predefinedApiReference.getApiLevel()));
        });

    // Register classes in the extension libraries.
    {
      ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
      options
          .apiModelingOptions()
          .forEachAndroidApiExtensionClassDescriptor(
              descriptor -> builder.add(options.itemFactory.createType(descriptor)));
      this.androidApiExtensionClasses = builder.build();
    }
    // Register packages for extension libraries.
    // TODO(b/326252366): Remove support for  list of extension packages in favour of only
    //  supporting passing extension libraries as JAR files.
    {
      ImmutableList.Builder<DexString> builder = ImmutableList.builder();
      options
          .apiModelingOptions()
          .forEachAndroidApiExtensionPackage(
              pkg ->
                  builder.add(
                      options.itemFactory.createString(
                          "L"
                              + pkg.replace(
                                  DescriptorUtils.JAVA_PACKAGE_SEPARATOR,
                                  DescriptorUtils.DESCRIPTOR_PACKAGE_SEPARATOR)
                              + "/")));
      this.androidApiExtensionPackages = builder.build();
    }

    assert predefinedApiTypeLookup.stream()
        .allMatch(added -> added.getApiLevel().isEqualTo(lookupApiLevel(added.getReference())));
  }

  @Override
  public AndroidApiLevel getTypeApiLevel(DexType type) {
    return lookupApiLevel(type);
  }

  @Override
  public AndroidApiLevel getMethodApiLevel(DexMethod method) {
    return lookupApiLevel(method);
  }

  @Override
  public AndroidApiLevel getFieldApiLevel(DexField field) {
    return lookupApiLevel(field);
  }

  // CHeck that extensionPackage is the exact/full package of the descriptor.
  private boolean isPackageOfClass(DexString extensionPackage, DexString descriptor) {
    int packageLengthInBytes = extensionPackage.content.length;
    // DexString content bytes has a terminating '\0'.
    assert descriptor.content[packageLengthInBytes - 2] == '/';
    ThrowingCharIterator<UTFDataFormatException> charIterator =
        descriptor.iterator(packageLengthInBytes - 1);
    while (charIterator.hasNext()) {
      try {
        if (charIterator.nextChar() == '/') {
          // Found another package separator, som not exact package.
          return false;
        }
      } catch (UTFDataFormatException e) {
        assert false
            : "Iterating " + descriptor + " from index " + packageLengthInBytes + " caused " + e;
        return false;
      }
    }
    return true;
  }

  private AndroidApiLevel lookupApiLevel(DexReference reference) {
    // TODO(b/326252366): Assigning all extension items the same "fake" API level results in API
    //  outlines becoming mergable across extensions, which should be prevented.
    if (androidApiExtensionClasses.contains(reference.getContextType())) {
      return AndroidApiLevel.EXTENSION;
    }
    for (int i = 0; i < androidApiExtensionPackages.size(); i++) {
      DexString descriptor = reference.getContextType().getDescriptor();
      DexString extensionPackage = androidApiExtensionPackages.get(i);
      if (descriptor.startsWith(extensionPackage)
          && isPackageOfClass(extensionPackage, descriptor)) {
        return AndroidApiLevel.EXTENSION;
      }
    }
    Optional<AndroidApiLevel> result =
        lookupCache.computeIfAbsent(
            reference,
            ref -> {
              // Prefetch the data access
              if (dataAccess == null) {
                getDataAccess(options, diagnosticsHandler);
              }
              if (dataAccess.isNoBacking()) {
                return Optional.empty();
              }
              ApiDatabaseEntry entry = ApiDatabaseEntry.of(ref);
              byte[] uniqueDescriptorForReference;
              try {
                uniqueDescriptorForReference =
                    entry.getUniqueDescriptor(
                        cpEntry ->
                            constantPoolCache.computeIfAbsent(
                                cpEntry, dataAccess::getConstantPoolIndex));
              } catch (Exception e) {
                uniqueDescriptorForReference = ApiDatabaseEntry.getNonExistingDescriptor();
              }
              if (uniqueDescriptorForReference == ApiDatabaseEntry.getNonExistingDescriptor()) {
                return Optional.empty();
              } else {
                AndroidApiLevel apiLevelForReference =
                    dataAccess.getApiLevelForReference(uniqueDescriptorForReference, entry);
                return Optional.ofNullable(apiLevelForReference);
              }
            });
    return result.orElse(null);
  }
}
