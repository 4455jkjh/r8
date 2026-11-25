// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.TypeReferenceUtils;
import com.android.tools.r8.utils.internal.ObjectUtils;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.UncheckedIOException;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

public final class ArtProfileInlineCaches {

  private static final ArtProfileInlineCaches EMPTY =
      new ArtProfileInlineCaches(null, Collections.emptyMap());

  private final EntryValue singleInlineCache;
  private final Map<DexType, EntryValue> targetedInlineCaches;

  private ArtProfileInlineCaches(
      EntryValue singleInlineCache, Map<DexType, EntryValue> targetedInlineCaches) {
    assert singleInlineCache == null || targetedInlineCaches.isEmpty();
    this.singleInlineCache = singleInlineCache;
    this.targetedInlineCaches = targetedInlineCaches;
  }

  public static ArtProfileInlineCaches empty() {
    return EMPTY;
  }

  public static ArtProfileInlineCaches fromData(
      ArtProfileInlineCacheData data, DexItemFactory dexItemFactory) {
    if (data.isEmpty()) {
      return EMPTY;
    }
    if (data.hasSingleInlineCache()) {
      return new ArtProfileInlineCaches(
          EntryValue.fromData(data.getSingleInlineCache(), dexItemFactory), Collections.emptyMap());
    }
    Map<DexType, EntryValue> targeted = new LinkedHashMap<>(data.getTargetedInlineCaches().size());
    data.getTargetedInlineCaches()
        .forEach(
            (receiver, value) -> {
              DexType receiverType = TypeReferenceUtils.toDexType(receiver, dexItemFactory);
              assert receiverType.isClassType() || receiverType.isArrayType();
              targeted.merge(
                  receiverType, EntryValue.fromData(value, dexItemFactory), EntryValue::join);
            });
    return new ArtProfileInlineCaches(null, Collections.unmodifiableMap(targeted));
  }

  public ArtProfileInlineCacheData toData() {
    if (isEmpty()) {
      return ArtProfileInlineCacheData.empty();
    }
    if (singleInlineCache != null) {
      return ArtProfileInlineCacheData.createSingle(singleInlineCache.toData());
    }
    ArtProfileInlineCacheData.Builder builder = ArtProfileInlineCacheData.builder();
    targetedInlineCaches.forEach(
        (receiver, value) ->
            builder.addTargetedInlineCache(receiver.asTypeReference(), value.toData()));
    return builder.build();
  }

  public boolean isEmpty() {
    return singleInlineCache == null && targetedInlineCaches.isEmpty();
  }

  public ArtProfileInlineCaches join(ArtProfileInlineCaches other) {
    if (isEmpty()) {
      return other;
    }
    if (other.isEmpty()) {
      return this;
    }
    if (singleInlineCache != null && other.singleInlineCache != null) {
      EntryValue joined = singleInlineCache.join(other.singleInlineCache);
      return joined.equals(singleInlineCache)
          ? this
          : new ArtProfileInlineCaches(joined, Collections.emptyMap());
    }
    if (singleInlineCache != null) {
      return other;
    }
    if (other.singleInlineCache != null) {
      return this;
    }
    Map<DexType, EntryValue> joined = new LinkedHashMap<>(targetedInlineCaches);
    other.targetedInlineCaches.forEach(
        (receiver, value) -> joined.merge(receiver, value, EntryValue::join));
    if (joined.equals(targetedInlineCaches)) {
      return this;
    }
    return new ArtProfileInlineCaches(null, Collections.unmodifiableMap(joined));
  }

  @SuppressWarnings("ReferenceEquality")
  public ArtProfileInlineCaches rewrittenWithLens(GraphLens graphLens, GraphLens codeLens) {
    if (isEmpty() || graphLens == codeLens) {
      return this;
    }
    return rewrittenWithTypeMapper(type -> graphLens.lookupType(type, codeLens));
  }

  public ArtProfileInlineCaches rewrittenWithLens(
      DexItemFactory dexItemFactory, NamingLens namingLens) {
    if (isEmpty() || namingLens.isIdentityLens()) {
      return this;
    }
    return rewrittenWithTypeMapper(type -> namingLens.lookupType(type, dexItemFactory));
  }

  public ArtProfileInlineCaches withoutMissingItems(Predicate<DexType> hasDefinitionFor) {
    if (isEmpty()) {
      return this;
    }
    return rewrittenWithTypeMapper(
        type -> {
          DexType baseType = type.getBaseType();
          assert baseType.isPrimitiveType() || baseType.isClassType();
          return baseType.isPrimitiveType() || hasDefinitionFor.test(baseType) ? type : null;
        });
  }

  public ArtProfileInlineCaches withoutPrunedItems(PrunedItems prunedItems) {
    if (isEmpty() || !prunedItems.hasRemovedClasses()) {
      return this;
    }
    return rewrittenWithTypeMapper(type -> prunedItems.isRemoved(type.getBaseType()) ? null : type);
  }

  @SuppressWarnings("ReferenceEquality")
  private ArtProfileInlineCaches rewrittenWithTypeMapper(Function<DexType, DexType> typeMapper) {
    if (isEmpty()) {
      return this;
    }
    if (singleInlineCache != null) {
      EntryValue newSingle = singleInlineCache.rewrittenWithTypeMapper(typeMapper);
      if (newSingle == null) {
        return empty();
      }
      if (newSingle == singleInlineCache) {
        return this;
      }
      return new ArtProfileInlineCaches(newSingle, Collections.emptyMap());
    }
    Map<DexType, EntryValue> newTargeted = null;
    for (Map.Entry<DexType, EntryValue> entry : targetedInlineCaches.entrySet()) {
      DexType receiver = entry.getKey();
      EntryValue value = entry.getValue();
      DexType newReceiver = typeMapper.apply(receiver);
      boolean keepReceiver =
          newReceiver != null && (newReceiver.isClassType() || newReceiver.isArrayType());
      EntryValue newValue = keepReceiver ? value.rewrittenWithTypeMapper(typeMapper) : null;
      if (newTargeted != null) {
        if (newValue != null) {
          newTargeted.merge(newReceiver, newValue, EntryValue::join);
        }
      } else if (newValue == null || newReceiver.isNotIdenticalTo(receiver) || newValue != value) {
        newTargeted = new LinkedHashMap<>(targetedInlineCaches.size());
        for (Map.Entry<DexType, EntryValue> previous : targetedInlineCaches.entrySet()) {
          if (previous.getKey().isIdenticalTo(receiver)) {
            break;
          }
          newTargeted.put(previous.getKey(), previous.getValue());
        }
        if (newValue != null) {
          newTargeted.merge(newReceiver, newValue, EntryValue::join);
        }
      }
    }
    if (newTargeted == null) {
      return this;
    }
    if (newTargeted.isEmpty()) {
      return empty();
    }
    return new ArtProfileInlineCaches(null, Collections.unmodifiableMap(newTargeted));
  }

  public void writeHumanReadableInlineCaches(OutputStreamWriter writer) throws IOException {
    writeHumanReadableInlineCachesInternal(writer);
  }

  private void writeHumanReadableInlineCachesInternal(Appendable appendable) throws IOException {
    if (isEmpty()) {
      return;
    }
    appendable.append(ArtProfileInlineCacheData.INLINE_CACHE_SEPARATOR);
    if (singleInlineCache != null) {
      singleInlineCache.writeHumanReadableString(appendable);
    } else {
      for (Map.Entry<DexType, EntryValue> entry : targetedInlineCaches.entrySet()) {
        appendable.append(ArtProfileInlineCacheData.TARGET_SEPARATOR);
        appendable.append(entry.getKey().toDescriptorString());
        entry.getValue().writeHumanReadableString(appendable);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ArtProfileInlineCaches)) {
      return false;
    }
    ArtProfileInlineCaches other = (ArtProfileInlineCaches) obj;
    return Objects.equals(singleInlineCache, other.singleInlineCache)
        && targetedInlineCaches.equals(other.targetedInlineCaches);
  }

  @Override
  public int hashCode() {
    return ObjectUtils.hashLL(singleInlineCache, targetedInlineCaches);
  }

  @Override
  public String toString() {
    if (isEmpty()) {
      return "";
    }
    StringBuilder builder = new StringBuilder();
    try {
      writeHumanReadableInlineCachesInternal(builder);
    } catch (IOException e) {
      throw new UncheckedIOException(e);
    }
    return builder.toString();
  }

  public static final class EntryValue {

    private static final EntryValue MISSING_TYPES =
        new EntryValue(true, false, Collections.emptySet());
    private static final EntryValue MEGAMORPHIC_TYPES =
        new EntryValue(false, true, Collections.emptySet());

    private final boolean isMissingTypes;
    private final boolean isMegamorphic;
    private final Set<DexType> types;

    private EntryValue(boolean isMissingTypes, boolean isMegamorphic, Set<DexType> types) {
      assert types != null;
      assert !(isMissingTypes && isMegamorphic);
      assert (isMissingTypes || isMegamorphic) ? types.isEmpty() : !types.isEmpty();
      this.isMissingTypes = isMissingTypes;
      this.isMegamorphic = isMegamorphic;
      this.types = types;
    }

    static EntryValue fromData(
        ArtProfileInlineCacheData.EntryValue data, DexItemFactory dexItemFactory) {
      if (data.isMissingTypes()) {
        return MISSING_TYPES;
      }
      if (data.isMegamorphic()) {
        return MEGAMORPHIC_TYPES;
      }
      Set<DexType> types = new LinkedHashSet<>(data.getTypes().size());
      for (TypeReference typeReference : data.getTypes()) {
        DexType type = TypeReferenceUtils.toDexType(typeReference, dexItemFactory);
        assert type.isClassType() || type.isArrayType();
        types.add(type);
      }
      return new EntryValue(false, false, Collections.unmodifiableSet(types));
    }

    ArtProfileInlineCacheData.EntryValue toData() {
      if (isMissingTypes) {
        return ArtProfileInlineCacheData.EntryValue.missingTypes();
      }
      if (isMegamorphic) {
        return ArtProfileInlineCacheData.EntryValue.megamorphicTypes();
      }
      Set<TypeReference> typeReferences = new LinkedHashSet<>(types.size());
      for (DexType type : types) {
        typeReferences.add(type.asTypeReference());
      }
      return ArtProfileInlineCacheData.EntryValue.ofTypes(typeReferences);
    }

    private boolean hasConcreteTypes() {
      return !isMissingTypes && !isMegamorphic;
    }

    public EntryValue join(EntryValue other) {
      if (isMissingTypes || other.isMissingTypes) {
        return MISSING_TYPES;
      }
      if (isMegamorphic || other.isMegamorphic) {
        return MEGAMORPHIC_TYPES;
      }
      if (types.containsAll(other.types)) {
        return this;
      }
      Set<DexType> joined = new LinkedHashSet<>(types.size() + other.types.size());
      joined.addAll(types);
      joined.addAll(other.types);
      return new EntryValue(false, false, Collections.unmodifiableSet(joined));
    }

    private EntryValue rewrittenWithTypeMapper(Function<DexType, DexType> typeMapper) {
      if (!hasConcreteTypes()) {
        return this;
      }
      Set<DexType> newTypes = null;
      for (DexType type : types) {
        DexType newType = typeMapper.apply(type);
        boolean keep = newType != null && (newType.isClassType() || newType.isArrayType());
        if (newTypes != null) {
          if (keep) {
            newTypes.add(newType);
          }
        } else if (!keep || newType.isNotIdenticalTo(type)) {
          newTypes = new LinkedHashSet<>(types.size());
          for (DexType previous : types) {
            if (previous.isIdenticalTo(type)) {
              break;
            }
            newTypes.add(previous);
          }
          if (keep) {
            newTypes.add(newType);
          }
        }
      }
      if (newTypes == null) {
        return this;
      }
      if (newTypes.isEmpty()) {
        return null;
      }
      return new EntryValue(false, false, Collections.unmodifiableSet(newTypes));
    }

    public void writeHumanReadableString(Appendable appendable) throws IOException {
      if (isMissingTypes) {
        appendable.append(ArtProfileInlineCacheData.MISSING_TYPES_MARKER);
      } else if (isMegamorphic) {
        appendable.append(ArtProfileInlineCacheData.MEGAMORPHIC_TYPES_MARKER);
      } else {
        boolean first = true;
        for (DexType type : types) {
          if (!first) {
            appendable.append(ArtProfileInlineCacheData.TYPE_SEPARATOR);
          }
          appendable.append(type.toDescriptorString());
          first = false;
        }
      }
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj) {
        return true;
      }
      if (!(obj instanceof EntryValue)) {
        return false;
      }
      EntryValue other = (EntryValue) obj;
      return isMissingTypes == other.isMissingTypes
          && isMegamorphic == other.isMegamorphic
          && types.equals(other.types);
    }

    @Override
    public int hashCode() {
      return ObjectUtils.hashZZL(isMissingTypes, isMegamorphic, types);
    }

    @Override
    public String toString() {
      if (isMissingTypes) {
        return ArtProfileInlineCacheData.MISSING_TYPES_MARKER;
      }
      if (isMegamorphic) {
        return ArtProfileInlineCacheData.MEGAMORPHIC_TYPES_MARKER;
      }
      StringBuilder builder = new StringBuilder();
      try {
        writeHumanReadableString(builder);
      } catch (IOException e) {
        throw new UncheckedIOException(e);
      }
      return builder.toString();
    }
  }
}
