// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.androidapi;

import com.android.tools.r8.androidapi.GenerateCovariantReturnTypeMethodsTest.CovariantMethodsInJarResult;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.references.PrimitiveReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.references.TypeReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.internal.TriConsumer;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

public class AndroidApiLevelDatabaseTestHelper {

  public static Set<String> notModeledFields() {
    // The fields below are known not to be modeled by any api-versions.
    Set<String> notModeledFields = new HashSet<>();
    notModeledFields.add("int android.app.appsearch.AppSearchResult.RESULT_DENIED");
    notModeledFields.add("int android.app.appsearch.AppSearchResult.RESULT_RATE_LIMITED");

    notModeledFields.add(
        "int android.adservices.adselection.ReportEventRequest.FLAG_REPORTING_DESTINATION_COMPONENT_SELLER");
    notModeledFields.add(
        "int android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.FEATURE_DISABLED");
    notModeledFields.add(
        "int android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.FEATURE_ENABLED");
    notModeledFields.add(
        "int android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.FEATURE_UNSUPPORTED");
    notModeledFields.add(
        "int android.adservices.ondevicepersonalization.InferenceInput$Params.MODEL_TYPE_EXECUTORCH");
    return notModeledFields;
  }

  public static Set<String> notModeledMethods() {
    // The methods below are known not to be modeled by any api-versions.
    Set<String> notModelledMethods = new HashSet<>();
    notModelledMethods.add(
        "void android.adservices.customaudience.CustomAudienceManager.scheduleCustomAudienceUpdate(android.adservices.customaudience.ScheduleCustomAudienceUpdateRequest,"
            + " java.util.concurrent.Executor,"
            + " android.adservices.common.AdServicesOutcomeReceiver)");
    notModelledMethods.add(
        "android.adservices.ondevicepersonalization.InferenceOutput$Builder"
            + " android.adservices.ondevicepersonalization.InferenceOutput$Builder.setData(byte[])");
    notModelledMethods.add(
        "android.adservices.common.AdTechIdentifier"
            + " android.adservices.adselection.AdSelectionOutcome.getWinningSeller()");
    notModelledMethods.add(
        "void android.adservices.ondevicepersonalization.FederatedComputeScheduler.schedule(android.adservices.ondevicepersonalization.FederatedComputeScheduleRequest,"
            + " java.util.concurrent.Executor, android.os.OutcomeReceiver)");

    notModelledMethods.add(
        "byte[] android.adservices.ondevicepersonalization.InferenceOutput.getData()");
    notModelledMethods.add(
        "byte[] android.adservices.ondevicepersonalization.InferenceInput.getData()");
    notModelledMethods.add(
        "void android.adservices.ondevicepersonalization.OnDevicePersonalizationManager.queryFeatureAvailability(java.lang.String,"
            + " java.util.concurrent.Executor, android.os.OutcomeReceiver)");
    notModelledMethods.add(
        "void android.adservices.ondevicepersonalization.InferenceInput$Builder.<init>(android.adservices.ondevicepersonalization.InferenceInput$Params,"
            + " byte[])");
    notModelledMethods.add(
        "android.adservices.ondevicepersonalization.InferenceInput$Builder"
            + " android.adservices.ondevicepersonalization.InferenceInput$Builder.setInputData(byte[])");
    return notModelledMethods;
  }

  /** These methods are missing from api-versions.xml but present in android.jar. */
  public static void visitAdditionalKnownApiReferences(
      BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    addStringBuilderAndBufferMethods(methodConsumer);
    addConcurrentKeySetViewMethods(methodConsumer);
    addNfcMethods(methodConsumer);
    addWebkitCookieSyncManagerMethods(methodConsumer);
    addChronoTimeMethods(methodConsumer);
  }

  @FunctionalInterface
  public interface ClassConsumer {
    /** If {@code superReference} is null, the class is an interface. */
    void accept(
        ClassReference classReference,
        ClassReference superReference,
        List<ClassReference> interfaces,
        AndroidApiLevel apiLevel);
  }

  /** These entries are present at runtime but absent from api-versions.xml and android.jar. */
  public static void visitHiddenReferences(
      ClassConsumer classConsumer,
      TriConsumer<MethodReference, Boolean, AndroidApiLevel> methodConsumer,
      BiConsumer<FieldReference, AndroidApiLevel> fieldConsumer)
      throws IOException {
    addUnsafeMethods(classConsumer, methodConsumer, fieldConsumer);
    addCovariantMethods(methodConsumer);
  }

  private static void addStringBuilderAndBufferMethods(
      BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    TypeReference intType = Reference.primitiveFromDescriptor("I");
    TypeReference charType = Reference.primitiveFromDescriptor("C");
    TypeReference voidType = Reference.returnTypeFromDescriptor("V");
    TypeReference stringType =
        Reference.typeFromClassReference(Reference.classFromClass(String.class));
    TypeReference charArrayType = Reference.typeFromDescriptor("[C");
    // StringBuilder and StringBuffer lack api definitions for the exact same methods in
    // api-versions.xml. See b/216587554 for related error.
    ClassReference[] classes = {
      Reference.classFromClass(StringBuilder.class), Reference.classFromClass(StringBuffer.class)
    };
    for (ClassReference type : classes) {
      methodConsumer.accept(
          Reference.method(type, "capacity", ImmutableList.of(), intType), AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "codePointAt", ImmutableList.of(intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "codePointBefore", ImmutableList.of(intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "codePointCount", ImmutableList.of(intType, intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "ensureCapacity", ImmutableList.of(intType), voidType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(
              type,
              "getChars",
              ImmutableList.of(intType, intType, charArrayType, intType),
              voidType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "indexOf", ImmutableList.of(stringType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "indexOf", ImmutableList.of(stringType, intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "lastIndexOf", ImmutableList.of(stringType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "lastIndexOf", ImmutableList.of(stringType, intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "offsetByCodePoints", ImmutableList.of(intType, intType), intType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "setCharAt", ImmutableList.of(intType, charType), voidType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "setLength", ImmutableList.of(intType), voidType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "substring", ImmutableList.of(intType), stringType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "substring", ImmutableList.of(intType, intType), stringType),
          AndroidApiLevel.B);
      methodConsumer.accept(
          Reference.method(type, "trimToSize", ImmutableList.of(), voidType), AndroidApiLevel.B);
    }
  }

  private static void addConcurrentKeySetViewMethods(
      BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    // KeysetView.getMap was also added in N (24).
    methodConsumer.accept(
        Reference.method(
            Reference.classFromDescriptor("Ljava/util/concurrent/ConcurrentHashMap$KeySetView;"),
            "getMap",
            ImmutableList.of(),
            Reference.typeFromClassReference(Reference.classFromClass(ConcurrentHashMap.class))),
        AndroidApiLevel.N);
  }

  private static void addNfcMethods(BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    String[] nfcClasses =
        new String[] {
          "Landroid/nfc/tech/Ndef;",
          "Landroid/nfc/tech/NfcA;",
          "Landroid/nfc/tech/NfcB;",
          "Landroid/nfc/tech/NfcBarcode;",
          "Landroid/nfc/tech/NfcF;",
          "Landroid/nfc/tech/NdefFormatable;",
          "Landroid/nfc/tech/IsoDep;",
          "Landroid/nfc/tech/MifareClassic;",
          "Landroid/nfc/tech/MifareUltralight;",
          "Landroid/nfc/tech/NfcV;"
        };
    TypeReference tagType = Reference.typeFromDescriptor("Landroid/nfc/Tag;");
    PrimitiveReference boolType = Reference.primitiveFromDescriptor("Z");
    TypeReference voidType = Reference.returnTypeFromDescriptor("V");
    // Seems like all methods are available from api level G_MR1 but we choose K since some of these
    // classes are introduced at 17.
    for (String nfcClass : nfcClasses) {
      ClassReference nfcClassType = Reference.classFromDescriptor(nfcClass);
      methodConsumer.accept(
          Reference.method(nfcClassType, "isConnected", ImmutableList.of(), boolType),
          AndroidApiLevel.K);
      methodConsumer.accept(
          Reference.method(nfcClassType, "getTag", ImmutableList.of(), tagType), AndroidApiLevel.K);
      methodConsumer.accept(
          Reference.method(nfcClassType, "close", ImmutableList.of(), voidType), AndroidApiLevel.K);
      methodConsumer.accept(
          Reference.method(nfcClassType, "connect", ImmutableList.of(), voidType),
          AndroidApiLevel.K);
    }
  }

  private static void addWebkitCookieSyncManagerMethods(
      BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    TypeReference voidType = Reference.returnTypeFromDescriptor("V");
    // All of these are added in android.jar from at least 14.
    ClassReference cookieSyncManager =
        Reference.classFromDescriptor("Landroid/webkit/CookieSyncManager;");
    for (String methodName : new String[] {"sync", "resetSync", "startSync", "stopSync", "run"}) {
      methodConsumer.accept(
          Reference.method(cookieSyncManager, methodName, ImmutableList.of(), voidType),
          AndroidApiLevel.I);
    }
  }

  private static void addChronoTimeMethods(
      BiConsumer<MethodReference, AndroidApiLevel> methodConsumer) {
    TypeReference valueRangeType = Reference.typeFromDescriptor("Ljava/time/temporal/ValueRange;");
    TypeReference chronoLocalDateType =
        Reference.typeFromDescriptor("Ljava/time/chrono/ChronoLocalDate;");
    TypeReference temporalType = Reference.typeFromDescriptor("Ljava/time/temporal/Temporal;");
    TypeReference temporalFieldType =
        Reference.typeFromDescriptor("Ljava/time/temporal/TemporalField;");
    TypeReference temporalUnitType =
        Reference.typeFromDescriptor("Ljava/time/temporal/TemporalUnit;");
    TypeReference temporalAmountType =
        Reference.typeFromDescriptor("Ljava/time/temporal/TemporalAmount;");
    TypeReference temporalAdjusterType =
        Reference.typeFromDescriptor("Ljava/time/temporal/TemporalAdjuster;");
    TypeReference intType = Reference.primitiveFromDescriptor("I");
    TypeReference booleanType = Reference.primitiveFromDescriptor("Z");
    TypeReference longType = Reference.primitiveFromDescriptor("J");

    // All of these classes was added in 26.
    String[] timeClasses =
        new String[] {
          "Ljava/time/chrono/JapaneseDate;",
          "Ljava/time/chrono/MinguoDate;",
          "Ljava/time/chrono/HijrahDate;",
          "Ljava/time/chrono/ThaiBuddhistDate;"
        };
    for (String timeClass : timeClasses) {
      ClassReference timeType = Reference.classFromDescriptor(timeClass);
      // int lengthOfMonth()
      methodConsumer.accept(
          Reference.method(timeType, "lengthOfMonth", ImmutableList.of(), intType),
          AndroidApiLevel.O);
      // int lengthOfYear()
      methodConsumer.accept(
          Reference.method(timeType, "lengthOfYear", ImmutableList.of(), intType),
          AndroidApiLevel.O);
      // boolean isSupported(java.time.temporal.TemporalField)
      methodConsumer.accept(
          Reference.method(
              timeType, "isSupported", ImmutableList.of(temporalFieldType), booleanType),
          AndroidApiLevel.O);
      // java.time.temporal.ValueRange range(java.time.temporal.TemporalField)
      methodConsumer.accept(
          Reference.method(timeType, "range", ImmutableList.of(temporalFieldType), valueRangeType),
          AndroidApiLevel.O);
      // long getLong(java.time.temporal.TemporalField)
      methodConsumer.accept(
          Reference.method(timeType, "getLong", ImmutableList.of(temporalFieldType), longType),
          AndroidApiLevel.O);
      // java.time.chrono.ChronoLocalDateTime atTime(java.time.LocalTime)
      methodConsumer.accept(
          Reference.method(
              timeType,
              "atTime",
              ImmutableList.of(Reference.typeFromDescriptor("Ljava/time/LocalTime;")),
              Reference.typeFromDescriptor("Ljava/time/chrono/ChronoLocalDateTime;")),
          AndroidApiLevel.O);
      // java.time.chrono.ChronoPeriod
      // java.time.chrono.JapaneseDate.until(java.time.chrono.ChronoLocalDate)
      methodConsumer.accept(
          Reference.method(
              timeType,
              "until",
              ImmutableList.of(chronoLocalDateType),
              Reference.typeFromDescriptor("Ljava/time/chrono/ChronoPeriod;")),
          AndroidApiLevel.O);
      // long toEpochDay()
      methodConsumer.accept(
          Reference.method(timeType, "toEpochDay", ImmutableList.of(), longType),
          AndroidApiLevel.O);
      // long until(java.time.temporal.Temporal, java.time.temporal.TemporalUnit)
      methodConsumer.accept(
          Reference.method(
              timeType, "until", ImmutableList.of(temporalType, temporalUnitType), longType),
          AndroidApiLevel.O);

      // java.time.chrono.Era getEra()
      methodConsumer.accept(
          Reference.method(
              timeType,
              "getEra",
              ImmutableList.of(),
              Reference.typeFromDescriptor("Ljava/time/chrono/Era;")),
          AndroidApiLevel.O);
      // java.time.chrono.Chronology getChronology()
      methodConsumer.accept(
          Reference.method(
              timeType,
              "getChronology",
              ImmutableList.of(),
              Reference.typeFromDescriptor("Ljava/time/chrono/Chronology;")),
          AndroidApiLevel.O);
      TypeReference[] returnTypesForModificationMethods =
          new TypeReference[] {chronoLocalDateType, temporalType};
      for (TypeReference returnType : returnTypesForModificationMethods) {
        // [returnType] minus(long, java.time.temporal.TemporalUnit)
        methodConsumer.accept(
            Reference.method(
                timeType, "minus", ImmutableList.of(longType, temporalUnitType), returnType),
            AndroidApiLevel.O);
        // [returnType] minus(java.time.temporal.TemporalAmount)
        methodConsumer.accept(
            Reference.method(timeType, "minus", ImmutableList.of(temporalAmountType), returnType),
            AndroidApiLevel.O);
        // [returnType] plus(long, java.time.temporal.TemporalUnit)
        methodConsumer.accept(
            Reference.method(
                timeType, "plus", ImmutableList.of(longType, temporalUnitType), returnType),
            AndroidApiLevel.O);
        // [returnType] plus(java.time.temporal.TemporalAmount)
        methodConsumer.accept(
            Reference.method(timeType, "plus", ImmutableList.of(temporalAmountType), returnType),
            AndroidApiLevel.O);
        // [returnType] with(java.time.temporal.TemporalField, long)
        methodConsumer.accept(
            Reference.method(
                timeType, "with", ImmutableList.of(temporalFieldType, longType), returnType),
            AndroidApiLevel.O);
        // [returnType] with(java.time.temporal.TemporalAdjuster)
        methodConsumer.accept(
            Reference.method(timeType, "with", ImmutableList.of(temporalAdjusterType), returnType),
            AndroidApiLevel.O);
      }
    }
    // boolean java.time.chrono.HijrahDate.isLeapYear()
    methodConsumer.accept(
        Reference.method(
            Reference.classFromDescriptor("Ljava/time/chrono/HijrahDate;"),
            "isLeapYear",
            ImmutableList.of(),
            booleanType),
        AndroidApiLevel.O);
  }

  public static void addUnsafeMethods(
      ClassConsumer classConsumer,
      TriConsumer<MethodReference, Boolean, AndroidApiLevel> methodConsumer,
      BiConsumer<FieldReference, AndroidApiLevel> fieldConsumer) {
    // If this assert fails then check these things before updating the assert:
    //   * Check if libcore/ojluni/src/main/java/sun/misc/Unsafe.java has new public methods,
    //     including new overloads.
    //     * If so, add the new methods here
    //       (and to SunMiscUnsafeApiTest but it will fail if you don't).
    //   * Verify that no existing methods have been removed.
    assert AndroidApiLevel.LATEST.isEqualTo(AndroidApiLevel.CINNAMON_BUN);

    TypeReference intType = Reference.primitiveFromDescriptor("I");
    TypeReference longType = Reference.primitiveFromDescriptor("J");
    TypeReference doubleType = Reference.primitiveFromDescriptor("D");
    TypeReference floatType = Reference.primitiveFromDescriptor("F");
    TypeReference byteType = Reference.primitiveFromDescriptor("B");
    TypeReference shortType = Reference.primitiveFromDescriptor("S");
    TypeReference charType = Reference.primitiveFromDescriptor("C");
    TypeReference booleanType = Reference.primitiveFromDescriptor("Z");
    TypeReference voidType = Reference.returnTypeFromDescriptor("V");
    TypeReference objectType =
        Reference.typeFromClassReference(Reference.classFromClass(Object.class));
    TypeReference classType =
        Reference.typeFromClassReference(Reference.classFromClass(Class.class));
    TypeReference fieldType =
        Reference.typeFromClassReference(Reference.classFromClass(Field.class));

    AndroidApiLevel always = AndroidApiLevel.B;
    ClassReference sunMiscUnsafeType = Reference.classFromDescriptor("Lsun/misc/Unsafe;");

    classConsumer.accept(
        sunMiscUnsafeType, Reference.classFromClass(Object.class), ImmutableList.of(), always);

    // Fields
    fieldConsumer.accept(
        Reference.field(sunMiscUnsafeType, "INVALID_FIELD_OFFSET", intType), AndroidApiLevel.N);

    // Methods
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "addressSize", ImmutableList.of(), intType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "allocateInstance", ImmutableList.of(classType), objectType),
        false,
        AndroidApiLevel.J);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "allocateMemory", ImmutableList.of(longType), longType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "arrayBaseOffset", ImmutableList.of(classType), intType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "arrayIndexScale", ImmutableList.of(classType), intType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "compareAndSwapInt",
            ImmutableList.of(objectType, longType, intType, intType),
            booleanType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "compareAndSwapLong",
            ImmutableList.of(objectType, longType, longType, longType),
            booleanType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "compareAndSwapObject",
            ImmutableList.of(objectType, longType, objectType, objectType),
            booleanType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "copyMemory",
            ImmutableList.of(longType, longType, longType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "copyMemoryFromPrimitiveArray",
            ImmutableList.of(objectType, longType, longType, longType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "copyMemoryToPrimitiveArray",
            ImmutableList.of(longType, objectType, longType, longType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "freeMemory", ImmutableList.of(longType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "fullFence", ImmutableList.of(), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getAndAddInt",
            ImmutableList.of(objectType, longType, intType),
            intType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getAndAddLong",
            ImmutableList.of(objectType, longType, longType),
            longType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getAndSetInt",
            ImmutableList.of(objectType, longType, intType),
            intType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getAndSetLong",
            ImmutableList.of(objectType, longType, longType),
            longType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getAndSetObject",
            ImmutableList.of(objectType, longType, objectType),
            objectType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getBoolean", ImmutableList.of(objectType, longType), booleanType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getByte", ImmutableList.of(longType), byteType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getByte", ImmutableList.of(objectType, longType), byteType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getChar", ImmutableList.of(longType), charType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getChar", ImmutableList.of(objectType, longType), charType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getDouble", ImmutableList.of(longType), doubleType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getDouble", ImmutableList.of(objectType, longType), doubleType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getFloat", ImmutableList.of(longType), floatType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getFloat", ImmutableList.of(objectType, longType), floatType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getInt", ImmutableList.of(longType), intType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getInt", ImmutableList.of(objectType, longType), intType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getIntVolatile", ImmutableList.of(objectType, longType), intType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getLong", ImmutableList.of(longType), longType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getLong", ImmutableList.of(objectType, longType), longType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getLongVolatile", ImmutableList.of(objectType, longType), longType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getObject", ImmutableList.of(objectType, longType), objectType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "getObjectVolatile",
            ImmutableList.of(objectType, longType),
            objectType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getShort", ImmutableList.of(longType), shortType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "getShort", ImmutableList.of(objectType, longType), shortType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "getUnsafe", ImmutableList.of(), sunMiscUnsafeType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "loadFence", ImmutableList.of(), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "objectFieldOffset", ImmutableList.of(fieldType), longType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "pageSize", ImmutableList.of(), intType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "park", ImmutableList.of(booleanType, longType), voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putBoolean",
            ImmutableList.of(objectType, longType, booleanType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putByte", ImmutableList.of(longType, byteType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putByte",
            ImmutableList.of(objectType, longType, byteType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putChar", ImmutableList.of(longType, charType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putChar",
            ImmutableList.of(objectType, longType, charType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putDouble", ImmutableList.of(longType, doubleType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putDouble",
            ImmutableList.of(objectType, longType, doubleType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putFloat", ImmutableList.of(longType, floatType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putFloat",
            ImmutableList.of(objectType, longType, floatType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putInt", ImmutableList.of(longType, intType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putInt", ImmutableList.of(objectType, longType, intType), voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putIntVolatile",
            ImmutableList.of(objectType, longType, intType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putLong", ImmutableList.of(longType, longType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putLong",
            ImmutableList.of(objectType, longType, longType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putLongVolatile",
            ImmutableList.of(objectType, longType, longType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putObject",
            ImmutableList.of(objectType, longType, objectType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putObjectVolatile",
            ImmutableList.of(objectType, longType, objectType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putOrderedInt",
            ImmutableList.of(objectType, longType, intType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putOrderedLong",
            ImmutableList.of(objectType, longType, longType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putOrderedObject",
            ImmutableList.of(objectType, longType, objectType),
            voidType),
        false,
        always);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType, "putShort", ImmutableList.of(longType, shortType), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "putShort",
            ImmutableList.of(objectType, longType, shortType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(
            sunMiscUnsafeType,
            "setMemory",
            ImmutableList.of(longType, longType, byteType),
            voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "storeFence", ImmutableList.of(), voidType),
        false,
        AndroidApiLevel.N);
    methodConsumer.accept(
        Reference.method(sunMiscUnsafeType, "unpark", ImmutableList.of(objectType), voidType),
        false,
        always);
  }

  private static CovariantMethodsInJarResult covariantResult = null;

  public static void addCovariantMethods(
      TriConsumer<MethodReference, Boolean, AndroidApiLevel> methodConsumer) throws IOException {
    if (covariantResult == null) {
      covariantResult = CovariantMethodsInJarResult.create();
    }
    for (ClassReference classReference : covariantResult.getClasses()) {
      covariantResult.visitCovariantMethodsForHolder(classReference, methodConsumer);
    }
  }
}
