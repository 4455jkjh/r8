// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.internal.ObjectUtils;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class ArtProfileInlineCacheData {

  public static final char INLINE_CACHE_SEPARATOR = '+';
  public static final char TARGET_SEPARATOR = ']';
  public static final char TYPE_SEPARATOR = ',';
  public static final String MISSING_TYPES_MARKER = "missing_types";
  public static final String MEGAMORPHIC_TYPES_MARKER = "megamorphic_types";

  private static final ArtProfileInlineCacheData EMPTY =
      new ArtProfileInlineCacheData(null, Collections.emptyMap());

  private final EntryValue singleInlineCache;
  private final Map<TypeReference, EntryValue> targetedInlineCaches;

  private ArtProfileInlineCacheData(
      EntryValue singleInlineCache, Map<TypeReference, EntryValue> targetedInlineCaches) {
    assert singleInlineCache == null || targetedInlineCaches.isEmpty();
    this.singleInlineCache = singleInlineCache;
    this.targetedInlineCaches = targetedInlineCaches;
  }

  public static ArtProfileInlineCacheData empty() {
    return EMPTY;
  }

  public static ArtProfileInlineCacheData createSingle(EntryValue singleInlineCache) {
    assert singleInlineCache != null;
    return new ArtProfileInlineCacheData(singleInlineCache, Collections.emptyMap());
  }

  public static Builder builder() {
    return new Builder();
  }

  public boolean isEmpty() {
    return singleInlineCache == null && targetedInlineCaches.isEmpty();
  }

  public boolean hasSingleInlineCache() {
    return singleInlineCache != null;
  }

  public EntryValue getSingleInlineCache() {
    return singleInlineCache;
  }

  public Map<TypeReference, EntryValue> getTargetedInlineCaches() {
    return targetedInlineCaches;
  }

  public void writeHumanReadableInlineCaches(Appendable appendable) throws IOException {
    if (isEmpty()) {
      return;
    }
    appendable.append(INLINE_CACHE_SEPARATOR);
    if (hasSingleInlineCache()) {
      singleInlineCache.writeHumanReadableString(appendable);
    } else {
      for (Map.Entry<TypeReference, EntryValue> entry : targetedInlineCaches.entrySet()) {
        appendable.append(TARGET_SEPARATOR);
        appendable.append(entry.getKey().getDescriptor());
        entry.getValue().writeHumanReadableString(appendable);
      }
    }
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (!(obj instanceof ArtProfileInlineCacheData)) {
      return false;
    }
    ArtProfileInlineCacheData other = (ArtProfileInlineCacheData) obj;
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
      writeHumanReadableInlineCaches(builder);
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
    private final Set<TypeReference> types;

    private EntryValue(boolean isMissingTypes, boolean isMegamorphic, Set<TypeReference> types) {
      assert types != null;
      assert !(isMissingTypes && isMegamorphic);
      assert (isMissingTypes || isMegamorphic) ? types.isEmpty() : !types.isEmpty();
      this.isMissingTypes = isMissingTypes;
      this.isMegamorphic = isMegamorphic;
      this.types = types;
    }

    public static EntryValue missingTypes() {
      return MISSING_TYPES;
    }

    public static EntryValue megamorphicTypes() {
      return MEGAMORPHIC_TYPES;
    }

    public static EntryValue ofTypes(Collection<? extends TypeReference> types) {
      assert types != null && !types.isEmpty();
      assert verifyConcreteTypes(types);
      return new EntryValue(false, false, Collections.unmodifiableSet(new LinkedHashSet<>(types)));
    }

    public static EntryValue ofTypes(TypeReference... types) {
      return ofTypes(Arrays.asList(types));
    }

    private static boolean verifyConcreteTypes(Collection<? extends TypeReference> types) {
      for (TypeReference type : types) {
        assert type != null && (type.isClass() || type.isArray());
      }
      return true;
    }

    public boolean isMissingTypes() {
      return isMissingTypes;
    }

    public boolean isMegamorphic() {
      return isMegamorphic;
    }

    public Set<TypeReference> getTypes() {
      return types;
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
      Set<TypeReference> joined = new LinkedHashSet<>(types.size() + other.types.size());
      joined.addAll(types);
      joined.addAll(other.types);
      return new EntryValue(false, false, Collections.unmodifiableSet(joined));
    }

    public void writeHumanReadableString(Appendable appendable) throws IOException {
      if (isMissingTypes) {
        appendable.append(MISSING_TYPES_MARKER);
      } else if (isMegamorphic) {
        appendable.append(MEGAMORPHIC_TYPES_MARKER);
      } else {
        boolean first = true;
        for (TypeReference type : types) {
          if (!first) {
            appendable.append(TYPE_SEPARATOR);
          }
          appendable.append(type.getDescriptor());
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
        return MISSING_TYPES_MARKER;
      }
      if (isMegamorphic) {
        return MEGAMORPHIC_TYPES_MARKER;
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

  public static final class Builder {

    private EntryValue singleInlineCache;
    private Map<TypeReference, EntryValue> targetedInlineCaches;

    private Builder() {}

    public Builder setSingleInlineCache(EntryValue value) {
      assert value != null;
      assert targetedInlineCaches == null;
      this.singleInlineCache = value;
      return this;
    }

    public Builder addTargetedInlineCache(TypeReference receiverType, EntryValue value) {
      assert receiverType != null;
      assert receiverType.isClass() || receiverType.isArray();
      assert value != null;
      assert singleInlineCache == null;
      if (targetedInlineCaches == null) {
        targetedInlineCaches = new LinkedHashMap<>();
      }
      targetedInlineCaches.merge(receiverType, value, EntryValue::join);
      return this;
    }

    public ArtProfileInlineCacheData build() {
      if (singleInlineCache != null) {
        return createSingle(singleInlineCache);
      }
      if (targetedInlineCaches == null || targetedInlineCaches.isEmpty()) {
        return empty();
      }
      return new ArtProfileInlineCacheData(
          null, Collections.unmodifiableMap(new LinkedHashMap<>(targetedInlineCaches)));
    }
  }
}
