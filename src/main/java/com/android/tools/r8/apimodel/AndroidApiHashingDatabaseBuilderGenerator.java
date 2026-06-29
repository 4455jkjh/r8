// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static com.android.tools.r8.androidapi.AndroidApiDataAccess.constantPoolHash;
import static com.android.tools.r8.lightir.ByteUtils.isU2;
import static com.android.tools.r8.lightir.ByteUtils.setBitAtIndex;
import static com.android.tools.r8.utils.internal.MapUtils.ignoreKey;

import com.android.tools.r8.androidapi.AndroidApiDataAccess;
import com.android.tools.r8.androidapi.ApiDatabaseEntry;
import com.android.tools.r8.androidapi.ApiDatabaseEntry.ConstantPoolEntry;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.IntBox;
import com.android.tools.r8.utils.internal.ThrowingBiConsumer;
import com.android.tools.r8.utils.internal.collections.Pair;
import it.unimi.dsi.fastutil.ints.Int2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.objects.Object2IntLinkedOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

public class AndroidApiHashingDatabaseBuilderGenerator {

  public static class GenerationException extends Exception {
    public GenerationException(String message) {
      super(message);
    }

    public GenerationException(String message, Throwable cause) {
      super(message, cause);
    }
  }

  /** The returned map has hash-independent iteration. */
  public static Map<ApiDatabaseEntry, AndroidApiLevel> generateEntries(
      List<ParsedApiClass> apiClasses) throws GenerationException {
    Map<ClassReference, ParsedApiClass> lookupMap = new HashMap<>();
    Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries = new LinkedHashMap<>();

    for (ParsedApiClass apiClass : apiClasses) {
      lookupMap.put(apiClass.getClassReference(), apiClass);
      databaseEntries.put(
          ApiDatabaseEntry.of(apiClass.getClassReference()), apiClass.getApiLevel());
    }

    for (ParsedApiClass apiClass : apiClasses) {
      computeAllReferencesInHierarchy(
          lookupMap, apiClass.getClassReference(), apiClass, AndroidApiLevel.B, databaseEntries);
    }

    return databaseEntries;
  }

  /**
   * This method will generate one single database file where the format is as follows (uX is X
   * number of unsigned bytes):
   *
   * <pre>
   * constant_pool_size: u4
   * constant_pool:      [constant_pool_size * payload_entry]
   * constant_pool_map:  [0..max_hash(ConstantPoolEntry) * payload_entry]
   * api_map:            [0..max_hash(ApiDatabaseEntry) * payload_entry]
   * payload             raw data.
   *
   * payload_entry: u4:relative_offset_from_payload_start_or_tagged_value + u2:length
   * </pre>
   *
   * For hash_definitions and entries see {@link AndroidApiDataAccess}.
   */
  public static void writeEntries(
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries, Path pathToApiLevels)
      throws GenerationException {
    try (FileOutputStream fileOutputStream = new FileOutputStream(pathToApiLevels.toFile())) {
      DataOutputStream dataOutputStream = new DataOutputStream(fileOutputStream);
      generateDatabase(databaseEntries, dataOutputStream);
    } catch (IOException e) {
      throw new GenerationException("Failed to write API database to " + pathToApiLevels, e);
    }
  }

  private static class ConstantPool {

    private final IntBox intBox = new IntBox(0);
    private final Object2IntMap<ConstantPoolEntry> pool = new Object2IntLinkedOpenHashMap<>();

    public int getOrAdd(ConstantPoolEntry entry) {
      return pool.computeIfAbsent(entry, ignored -> intBox.getAndIncrement());
    }

    public void forEach(ThrowingBiConsumer<ConstantPoolEntry, Integer, IOException> consumer)
        throws IOException {
      for (var mapEntry : pool.object2IntEntrySet()) {
        consumer.accept(mapEntry.getKey(), mapEntry.getIntValue());
      }
    }

    public int size() {
      return pool.size();
    }
  }

  private static int setUniqueConstantPoolEntry(int id) {
    return setBitAtIndex(id, 32);
  }

  private static void generateDatabase(
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries, DataOutputStream outputStream)
      throws GenerationException, IOException {
    Int2ObjectMap<List<Pair<ApiDatabaseEntry, AndroidApiLevel>>> generationMap =
        new Int2ObjectLinkedOpenHashMap<>();
    ConstantPool constantPool = new ConstantPool();

    int constantPoolHashMapSize = 1 << AndroidApiDataAccess.entrySizeInBitsForConstantPoolMap();
    int apiHashMapSize = 1 << AndroidApiDataAccess.entrySizeInBitsForApiLevelMap();

    for (Entry<ApiDatabaseEntry, AndroidApiLevel> entry : databaseEntries.entrySet()) {
      int newCode = AndroidApiDataAccess.apiLevelHash(entry.getKey());
      if (newCode < 0 || newCode > apiHashMapSize) {
        throw new GenerationException("Hash code out of bounds: " + newCode);
      }
      generationMap
          .computeIfAbsent(newCode, ignoreKey(ArrayList::new))
          .add(Pair.create(entry.getKey(), entry.getValue()));
    }

    Set<String> uniqueHashes = new HashSet<>();
    Int2ObjectMap<Pair<Integer, Integer>> offsetMap = new Int2ObjectLinkedOpenHashMap<>();
    ByteArrayOutputStream payload = new ByteArrayOutputStream();

    // Serialize api map into payload. This will also generate the entire needed constant pool.
    for (Int2ObjectMap.Entry<List<Pair<ApiDatabaseEntry, AndroidApiLevel>>> entry :
        generationMap.int2ObjectEntrySet()) {
      int startingOffset = payload.size();
      int length = serializeIntoPayload(entry.getValue(), payload, constantPool, uniqueHashes);
      offsetMap.put(entry.getIntKey(), Pair.create(startingOffset, length));
    }

    // Write constant pool size <u4:size>.
    outputStream.writeInt(constantPool.size());

    // Write constant pool consisting of <u4:payload_offset><u2:length>.
    if (outputStream.size() != AndroidApiDataAccess.constantPoolOffset()) {
      throw new GenerationException(
          "Unexpected constant pool offset: expected "
              + AndroidApiDataAccess.constantPoolOffset()
              + ", got "
              + outputStream.size());
    }
    IntBox lastReadIndex = new IntBox(-1);
    constantPool.forEach(
        (entry, id) -> {
          if (id <= lastReadIndex.getAndIncrement()) {
            throw new IOException("Constant pool ID out of order");
          }
          outputStream.writeInt(payload.size());
          outputStream.writeShort(entry.getLength());
          entry.writeTo(payload);
        });

    // Serialize hash lookup table for constant pool.
    Map<Integer, List<Integer>> constantPoolLookupTable = new HashMap<>();
    constantPool.forEach(
        (entry, id) -> {
          int constantPoolHash = constantPoolHash(entry);
          assert constantPoolHash >= 0 && constantPoolHash <= constantPoolHashMapSize;
          constantPoolLookupTable
              .computeIfAbsent(constantPoolHash, ignoreKey(ArrayList::new))
              .add(id);
        });

    int[] constantPoolEntries = new int[constantPoolHashMapSize];
    int[] constantPoolEntryLengths = new int[constantPoolHashMapSize];
    for (Entry<Integer, List<Integer>> entry : constantPoolLookupTable.entrySet()) {
      // Tag if we have a unique value.
      if (entry.getValue().size() == 1) {
        int id = entry.getValue().get(0);
        constantPoolEntries[entry.getKey()] = setUniqueConstantPoolEntry(id);
      } else {
        constantPoolEntries[entry.getKey()] = payload.size();
        ByteArrayOutputStream temp = new ByteArrayOutputStream();
        for (Integer id : entry.getValue()) {
          temp.write(intToShortEncodedByteArray(id));
        }
        payload.write(temp.toByteArray());
        constantPoolEntryLengths[entry.getKey()] = temp.size();
      }
    }
    // Write constant pool lookup entries consisting of <u4:payload_offset><u2:length>.
    if (outputStream.size()
        != AndroidApiDataAccess.constantPoolHashMapOffset(constantPool.size())) {
      throw new GenerationException("Unexpected constant pool hash map offset");
    }
    for (int i = 0; i < constantPoolEntries.length; i++) {
      outputStream.writeInt(constantPoolEntries[i]);
      outputStream.writeShort(constantPoolEntryLengths[i]);
    }

    int[] apiOffsets = new int[apiHashMapSize];
    int[] apiOffsetLengths = new int[apiHashMapSize];
    for (Int2ObjectMap.Entry<Pair<Integer, Integer>> hashIndexAndOffset :
        offsetMap.int2ObjectEntrySet()) {
      if (apiOffsets[hashIndexAndOffset.getIntKey()] != 0) {
        throw new GenerationException(
            "Hash collision in API map at index " + hashIndexAndOffset.getIntKey());
      }
      Pair<Integer, Integer> value = hashIndexAndOffset.getValue();
      int offset = value.getFirst();
      int length = value.getSecond();
      apiOffsets[hashIndexAndOffset.getKey()] = offset;
      apiOffsetLengths[hashIndexAndOffset.getKey()] = length;
    }

    // Write api lookup entries consisting of <u4:payload_offset><u2:length>.
    if (outputStream.size() != AndroidApiDataAccess.apiLevelHashMapOffset(constantPool.size())) {
      throw new GenerationException("Unexpected api level hash map offset");
    }
    for (int i = 0; i < apiOffsets.length; i++) {
      outputStream.writeInt(apiOffsets[i]);
      outputStream.writeShort(apiOffsetLengths[i]);
    }

    // Write the payload.
    outputStream.write(payload.toByteArray());
  }

  /** This will serialize a collection of DexReferences and apis into a byte stream. */
  private static int serializeIntoPayload(
      List<Pair<ApiDatabaseEntry, AndroidApiLevel>> pairs,
      ByteArrayOutputStream payload,
      ConstantPool constantPool,
      Set<String> seen)
      throws GenerationException, IOException {
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    for (Pair<ApiDatabaseEntry, AndroidApiLevel> pair : pairs) {
      byte[] uniqueDescriptorForReference =
          pair.getFirst().getUniqueDescriptor(constantPool::getOrAdd);
      if (uniqueDescriptorForReference == ApiDatabaseEntry.getNonExistingDescriptor()) {
        throw new GenerationException("Reference descriptor does not exist: " + pair.getFirst());
      }
      if (!seen.add(Arrays.toString(uniqueDescriptorForReference))) {
        throw new GenerationException(
            "Duplicate reference descriptor in payload: " + pair.getFirst());
      }
      temp.write(intToShortEncodedByteArray(uniqueDescriptorForReference.length));
      temp.write(uniqueDescriptorForReference);
      if (pair.getSecond() == AndroidApiLevel.MAIN
          || pair.getSecond() == AndroidApiLevel.EXTENSION) {
        throw new GenerationException("Invalid API level for database entry: " + pair.getSecond());
      }
      temp.write(pair.getSecond().serializeAsByte());
    }
    byte[] tempArray = temp.toByteArray();
    payload.write(tempArray);
    return tempArray.length;
  }

  public static byte[] intToShortEncodedByteArray(int value) {
    assert isU2(value);
    byte[] bytes = new byte[2];
    bytes[0] = (byte) (value >> 8);
    bytes[1] = (byte) value;
    return bytes;
  }

  private static final String OBJECT_DESCRIPTOR = "Ljava/lang/Object;";

  private static void computeAllReferencesInHierarchy(
      Map<ClassReference, ParsedApiClass> lookupMap,
      ClassReference holder,
      ParsedApiClass apiClass,
      AndroidApiLevel linkLevel,
      Map<ApiDatabaseEntry, AndroidApiLevel> databaseEntries)
      throws GenerationException {
    if (!apiClass.getClassReference().getDescriptor().equals(OBJECT_DESCRIPTOR)) {
      apiClass.forEachMethod(
          (methodReference, apiLevel) -> {
            MethodReference methodWithNewHolder =
                Reference.method(
                    holder,
                    methodReference.getMethodName(),
                    methodReference.getFormalTypes(),
                    methodReference.getReturnType());
            addIfNewOrApiLevelIsLower(
                linkLevel, databaseEntries, apiLevel, ApiDatabaseEntry.of(methodWithNewHolder));
          });
      apiClass.forEachField(
          (fieldReference, apiLevel) -> {
            FieldTypelessReference fieldWithNewHolder =
                new FieldTypelessReference(holder, fieldReference.getFieldName());
            addIfNewOrApiLevelIsLower(
                linkLevel, databaseEntries, apiLevel, ApiDatabaseEntry.of(fieldWithNewHolder));
          });

      apiClass.forEachSupertypeThrowing(
          (superType, apiLevel) -> {
            ParsedApiClass superApiClass = lookupMap.get(superType);
            if (superApiClass == null) {
              if (!superType.getDescriptor().equals(OBJECT_DESCRIPTOR)) {
                throw new GenerationException(
                    "Supertype "
                        + superType.getTypeName()
                        + " of "
                        + apiClass.getClassReference().getTypeName()
                        + " is missing from the parsed API classes.");
              }
            } else {
              computeAllReferencesInHierarchy(
                  lookupMap, holder, superApiClass, linkLevel.max(apiLevel), databaseEntries);
            }
          });

      apiClass.forEachInterfaceThrowing(
          (interfaceReference, apiLevel) -> {
            ParsedApiClass interfaceApiClass = lookupMap.get(interfaceReference);
            if (interfaceApiClass == null) {
              throw new GenerationException(
                  "Interface "
                      + interfaceReference.getTypeName()
                      + " implemented by "
                      + apiClass.getClassReference().getTypeName()
                      + " is missing from the parsed API classes.");
            }
            computeAllReferencesInHierarchy(
                lookupMap, holder, interfaceApiClass, linkLevel.max(apiLevel), databaseEntries);
          });
    }
  }

  private static void addIfNewOrApiLevelIsLower(
      AndroidApiLevel linkLevel,
      Map<ApiDatabaseEntry, AndroidApiLevel> additionMap,
      AndroidApiLevel apiLevel,
      ApiDatabaseEntry member) {
    AndroidApiLevel currentApiLevel = apiLevel.max(linkLevel);
    AndroidApiLevel existingApiLevel = additionMap.get(member);
    if (existingApiLevel == null || currentApiLevel.isLessThanOrEqualTo(existingApiLevel)) {
      additionMap.put(member, currentApiLevel);
    }
  }
}
