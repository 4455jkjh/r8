// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.retrace.RetracePartitionException;
import com.android.tools.r8.utils.StringUtils;
import com.android.tools.r8.utils.internal.SerializationUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.hash.Hashing;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public class MetadataAdditionalInfo {

  private static final int NUMBER_OF_ELEMENTS = 2;

  public enum AdditionalInfoTypes {
    UNKNOWN(-1),
    PREAMBLE(0),
    OBFUSCATED_PACKAGES(1);

    private final int serializedKey;

    AdditionalInfoTypes(int serializedKey) {
      this.serializedKey = serializedKey;
    }

    static AdditionalInfoTypes getByKey(int serializedKey) {
      if (serializedKey == 0) {
        return PREAMBLE;
      } else if (serializedKey == 1) {
        return OBFUSCATED_PACKAGES;
      }
      return UNKNOWN;
    }
  }

  protected final List<String> preamble;
  protected final Set<String> obfuscatedPackages;

  private MetadataAdditionalInfo(List<String> preamble, Set<String> obfuscatedPackages) {
    this.preamble = preamble;
    this.obfuscatedPackages = obfuscatedPackages;
  }

  public boolean hasPreamble() {
    return preamble != null;
  }

  public List<String> getPreamble() {
    return preamble;
  }

  public boolean hasObfuscatedPackages() {
    return obfuscatedPackages != null;
  }

  public Set<String> getObfuscatedPackages() {
    return obfuscatedPackages;
  }

  // The serialized format is an extensible list where we first record the offsets for each data
  // section and then emit the data.
  // <total-size:int><number-of-elements:short>[<type-i:short><length-i:int><data-i>]
  public void serialize(DataOutputStream dataOutputStream) throws IOException {
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    DataOutputStream additionalInfoStream = new DataOutputStream(temp);
    additionalInfoStream.writeShort(NUMBER_OF_ELEMENTS);
    additionalInfoStream.writeShort(AdditionalInfoTypes.PREAMBLE.serializedKey);
    SerializationUtils.writeUTFOfIntSize(additionalInfoStream, StringUtils.unixLines(preamble));
    additionalInfoStream.writeShort(AdditionalInfoTypes.OBFUSCATED_PACKAGES.serializedKey);
    List<String> sortedPackages = new ArrayList<>(obfuscatedPackages);
    Collections.sort(sortedPackages);
    SerializationUtils.writeUTFOfIntSize(
        additionalInfoStream, StringUtils.unixLines(sortedPackages));
    byte[] payload = temp.toByteArray();
    dataOutputStream.writeInt(payload.length);
    dataOutputStream.write(payload);
  }

  private static MetadataAdditionalInfo deserialize(
      byte[] bytes, Predicate<AdditionalInfoTypes> serializeSection) {
    CompatByteBuffer compatByteBuffer = CompatByteBuffer.wrap(bytes);
    int numberOfElements = compatByteBuffer.getShort();
    List<String> preamble = null;
    Set<String> packages = null;
    for (int i = 0; i < numberOfElements; i++) {
      // We are parsing <type:short><length:int><bytes>
      int additionInfoTypeKey = compatByteBuffer.getShort();
      AdditionalInfoTypes additionalInfoType = AdditionalInfoTypes.getByKey(additionInfoTypeKey);
      if (additionalInfoType == AdditionalInfoTypes.UNKNOWN) {
        throw new RetracePartitionException(
            "Could not additional info from key: " + additionInfoTypeKey);
      }
      if (serializeSection.test(additionalInfoType)) {
        switch (additionalInfoType) {
          case PREAMBLE:
            preamble = StringUtils.splitLines(compatByteBuffer.getUTFOfIntSize());
            break;
          case OBFUSCATED_PACKAGES:
            packages = StringUtils.splitLinesIntoSet(compatByteBuffer.getUTFOfIntSize());
            break;
          default:
            throw new Unreachable("Unreachable since we already checked for UNKNOWN");
        }
      } else {
        int length = compatByteBuffer.getInt();
        compatByteBuffer.position(compatByteBuffer.position() + length);
      }
    }
    return new MetadataAdditionalInfo(preamble, packages);
  }

  public static MetadataAdditionalInfo create(
      List<String> preamble, Set<String> obfuscatedPackages) {
    return new MetadataAdditionalInfo(preamble, obfuscatedPackages);
  }

  public MetadataAdditionalInfo combine(MetadataAdditionalInfo other, String newMapId) {
    Set<String> combinedPackages = new LinkedHashSet<>();
    Set<String> thisPackages = getObfuscatedPackages();
    if (thisPackages != null) {
      combinedPackages.addAll(thisPackages);
    }
    Set<String> otherPackages = other.getObfuscatedPackages();
    if (otherPackages != null) {
      combinedPackages.addAll(otherPackages);
    }
    return create(combinePreambles(getPreamble(), other.getPreamble(), newMapId), combinedPackages);
  }

  private static List<String> combinePreambles(
      List<String> preamble, List<String> otherPreamble, String newMapId) {
    if (preamble == null || otherPreamble == null) {
      throw new RetracePartitionException("Preamble is missing");
    }
    if (preamble.size() < 8 || otherPreamble.size() < 8) {
      throw new RetracePartitionException("Preamble is too short to be combined");
    }

    // Build up the new preamble with the new map id.
    List<String> combinedPreamble = new ArrayList<>();
    for (int i = 0; i <= 5; i++) {
      if (!Objects.equals(preamble.get(i), otherPreamble.get(i))) {
        throw new RetracePartitionException("Preamble lines 0-5 are not identical");
      }
      combinedPreamble.add(preamble.get(i));
    }
    combinedPreamble.add("# pg_map_id: " + newMapId);

    // Create a new hash.
    String pgMapHashLine = preamble.get(7);
    String otherPgMapHashLine = otherPreamble.get(7);
    if (!pgMapHashLine.startsWith("# pg_map_hash: SHA-256 ")
        || !otherPgMapHashLine.startsWith("# pg_map_hash: SHA-256 ")) {
      throw new RetracePartitionException(
          "Expected last preamble line to start with '# pg_map_hash: SHA-256 '");
    }

    String pgMapHash = pgMapHashLine.substring("# pg_map_hash: SHA-256 ".length());
    String otherPgMapHash = otherPgMapHashLine.substring("# pg_map_hash: SHA-256 ".length());
    String combinedPgMapHash =
        Hashing.sha256()
            .newHasher()
            .putString(pgMapHash, StandardCharsets.UTF_8)
            .putString(otherPgMapHash, StandardCharsets.UTF_8)
            .hash()
            .toString();
    combinedPreamble.add("# pg_map_hash: SHA-256 " + combinedPgMapHash);
    return combinedPreamble;
  }

  public static class LazyMetadataAdditionalInfo extends MetadataAdditionalInfo {

    private final byte[] bytes;
    private final Map<Integer, MetadataAdditionalInfo> metadataAdditionalInfo =
        new ConcurrentHashMap<>();

    public LazyMetadataAdditionalInfo(byte[] bytes) {
      super(null, null);
      this.bytes = bytes;
    }

    @Override
    public boolean hasPreamble() {
      MetadataAdditionalInfo metadataAdditionalInfo =
          getMetadataAdditionalInfo(AdditionalInfoTypes.PREAMBLE);
      return metadataAdditionalInfo != null && metadataAdditionalInfo.hasPreamble();
    }

    @Override
    public List<String> getPreamble() {
      MetadataAdditionalInfo metadataAdditionalInfo =
          getMetadataAdditionalInfo(AdditionalInfoTypes.PREAMBLE);
      return metadataAdditionalInfo == null ? null : metadataAdditionalInfo.getPreamble();
    }

    @Override
    public boolean hasObfuscatedPackages() {
      MetadataAdditionalInfo metadataAdditionalInfo =
          getMetadataAdditionalInfo(AdditionalInfoTypes.OBFUSCATED_PACKAGES);
      return metadataAdditionalInfo != null && metadataAdditionalInfo.hasObfuscatedPackages();
    }

    @Override
    public Set<String> getObfuscatedPackages() {
      MetadataAdditionalInfo metadataAdditionalInfo =
          getMetadataAdditionalInfo(AdditionalInfoTypes.OBFUSCATED_PACKAGES);
      return metadataAdditionalInfo == null ? null : metadataAdditionalInfo.getObfuscatedPackages();
    }

    private MetadataAdditionalInfo getMetadataAdditionalInfo(AdditionalInfoTypes infoType) {
      return metadataAdditionalInfo.computeIfAbsent(
          infoType.serializedKey,
          ignored ->
              MetadataAdditionalInfo.deserialize(
                  bytes, deserializeType -> deserializeType == infoType));
    }

    public static LazyMetadataAdditionalInfo create(CompatByteBuffer buffer) {
      return new LazyMetadataAdditionalInfo(buffer.getBytesOfIntSize());
    }
  }
}
