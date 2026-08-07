// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.ir.desugar.LambdaClass.LAMBDA_INSTANCE_FIELD_NAME;
import static com.android.tools.r8.utils.internal.ConsumerUtils.emptyConsumer;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.Marker;
import com.android.tools.r8.graph.DexDebugEvent.AdvanceLine;
import com.android.tools.r8.graph.DexDebugEvent.AdvancePC;
import com.android.tools.r8.graph.DexDebugEvent.Default;
import com.android.tools.r8.graph.DexDebugEvent.EndLocal;
import com.android.tools.r8.graph.DexDebugEvent.RestartLocal;
import com.android.tools.r8.graph.DexDebugEvent.SetEpilogueBegin;
import com.android.tools.r8.graph.DexDebugEvent.SetFile;
import com.android.tools.r8.graph.DexDebugEvent.SetPositionFrame;
import com.android.tools.r8.graph.DexDebugEvent.SetPrologueEnd;
import com.android.tools.r8.graph.DexMethodHandle.MethodHandleType;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.Position;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.desugar.LambdaClass;
import com.android.tools.r8.kotlin.Kotlin;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.internal.ArrayUtils;
import com.android.tools.r8.utils.internal.ListUtils;
import com.android.tools.r8.utils.internal.SetUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.Int2ReferenceArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayDeque;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class DexItemFactory {

  public static final String throwableDescriptorString = "Ljava/lang/Throwable;";
  public static final String dalvikAnnotationSignatureString = "Ldalvik/annotation/Signature;";
  public static final String recordTagDescriptorString = "Lcom/android/tools/r8/RecordTag;";
  public static final String autoCloseableTagString = "Lcom/android/tools/r8/AutoCloseableTag;";
  public static final String recordDescriptorString = "Ljava/lang/Record;";
  public static final String desugarVarHandleDescriptorString =
      "Lcom/android/tools/r8/DesugarVarHandle;";
  public static final String varHandleDescriptorString = "Ljava/lang/invoke/VarHandle;";
  public static final String desugarMethodHandlesLookupDescriptorString =
      "Lcom/android/tools/r8/DesugarMethodHandlesLookup;";
  public static final String methodHandlesLookupDescriptorString =
      "Ljava/lang/invoke/MethodHandles$Lookup;";
  public static final String androidUtilSparseArrayDescriptorString = "Landroid/util/SparseArray;";
  public static final String androidContentResTypedArrayDescriptorString =
      "Landroid/content/res/TypedArray;";
  public static final String androidContentContentProviderClientDescriptorString =
      "Landroid/content/ContentProviderClient;";
  public static final String androidDrmDrmManagerClientDescriptorString =
      "Landroid/drm/DrmManagerClient;";
  public static final String androidMediaMediaDrmDescriptorString = "Landroid/media/MediaDrm;";
  public static final String androidMediaMediaMetadataRetrieverDescriptorString =
      "Landroid/media/MediaMetadataRetriever;";
  public static final String androidResourcesDescriptorString = "Landroid/content/res/Resources;";
  public static final String androidContextDescriptorString = "Landroid/content/Context;";
  public static final String kotlinJvmInternalIntrinsicsDescriptor =
      "Lkotlin/jvm/internal/Intrinsics;";
  public static final String lambdaMethodAnnotationDescriptor =
      "Lcom/android/tools/r8/annotations/LambdaMethod;";

  /** Set of types that may be synthesized during compilation. */
  private final Set<DexType> possibleCompilerSynthesizedTypes = Sets.newIdentityHashSet();

  private Map<DexString, DexString> markers = new ConcurrentHashMap<>();

  private Map<DexMethodHandle, DexMethodHandle> methodHandles = new ConcurrentHashMap<>();
  private Map<DexMethodHandle, DexMethodHandle> committedMethodHandles = new HashMap<>();

  private Map<DexString, DexString> strings = new ConcurrentHashMap<>();
  private Map<DexString, DexString> committedStrings = new HashMap<>();

  private Map<DexString, DexType> types = new ConcurrentHashMap<>();
  private Map<DexString, DexType> committedTypes = new HashMap<>();

  private Map<DexField, DexField> fields = new ConcurrentHashMap<>();
  private Map<DexField, DexField> committedFields = new HashMap<>();

  private Map<DexProto, DexProto> protos = new ConcurrentHashMap<>();
  private Map<DexProto, DexProto> committedProtos = new HashMap<>();

  private Map<DexMethod, DexMethod> methods = new ConcurrentHashMap<>();
  private Map<DexMethod, DexMethod> committedMethods = new HashMap<>();

  // DexDebugEvent Canonicalization.
  private final Int2ReferenceMap<AdvanceLine> advanceLines = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<AdvancePC> advancePCs = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<Default> defaults = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<EndLocal> endLocals = new Int2ReferenceOpenHashMap<>();
  private final Int2ReferenceMap<RestartLocal> restartLocals = new Int2ReferenceOpenHashMap<>();
  private final SetEpilogueBegin setEpilogueBegin = new SetEpilogueBegin();
  private final SetPrologueEnd setPrologueEnd = new SetPrologueEnd();
  private final Map<DexString, SetFile> setFiles = new HashMap<>();
  private final Map<SetPositionFrame, SetPositionFrame> setInlineFrames = new HashMap<>();
  public final DexDebugEvent.Default zeroChangeDefaultEvent = createDefault(0, 0);
  public final DexDebugEvent.Default oneChangeDefaultEvent = createDefault(1, 1);

  // Internal type containing only the null value.
  public static final DexType nullValueType = new DexType(new DexString("NULL"));
  public final DexString emptyString = createString("");
  public final DexString falseString = createString("false");
  public final DexString trueString = createString("true");
  public final DexString nullString = createString("null");

  public static final DexString unknownTypeName = new DexString("UNKNOWN");

  private static final IdentityHashMap<DexItem, DexItem> internalSentinels =
      new IdentityHashMap<>(
          ImmutableMap.of(
              nullValueType, nullValueType,
              unknownTypeName, unknownTypeName));

  public DexItemFactory() {
    this.kotlin = new Kotlin(this);
  }

  public Kotlin kotlin() {
    return kotlin;
  }

  public static boolean isInternalSentinel(DexItem item) {
    return internalSentinels.containsKey(item);
  }

  public final DexString booleanDescriptor = createString("Z");
  public final DexString byteDescriptor = createString("B");
  public final DexString charDescriptor = createString("C");
  public final DexString doubleDescriptor = createString("D");
  public final DexString floatDescriptor = createString("F");
  public final DexString intDescriptor = createString("I");
  public final DexString longDescriptor = createString("J");
  public final DexString shortDescriptor = createString("S");
  public final DexString voidDescriptor = createString("V");
  public final DexString descriptorSeparator = createString("/");
  public final DexString comSunDescriptorPrefix = createString("Lcom/sun/");
  public final DexString javaDescriptorPrefix = createString("Ljava/");
  public final DexString javaxDescriptorPrefix = createString("Ljavax/");
  public final DexString jdkDescriptorPrefix = createString("Ljdk/");
  public final DexString sunDescriptorPrefix = createString("Lsun/");
  public final DexString jDollarDescriptorPrefix = createString("Lj$/");

  private final DexString booleanArrayDescriptor = createString("[Z");
  private final DexString byteArrayDescriptor = createString("[B");
  private final DexString charArrayDescriptor = createString("[C");
  private final DexString doubleArrayDescriptor = createString("[D");
  private final DexString floatArrayDescriptor = createString("[F");
  private final DexString intArrayDescriptor = createString("[I");
  private final DexString longArrayDescriptor = createString("[J");
  private final DexString shortArrayDescriptor = createString("[S");

  public final DexString boxedBooleanDescriptor = createString("Ljava/lang/Boolean;");
  public final DexString boxedByteDescriptor = createString("Ljava/lang/Byte;");
  public final DexString boxedCharDescriptor = createString("Ljava/lang/Character;");
  public final DexString boxedDoubleDescriptor = createString("Ljava/lang/Double;");
  public final DexString boxedFloatDescriptor = createString("Ljava/lang/Float;");
  public final DexString boxedIntDescriptor = createString("Ljava/lang/Integer;");
  public final DexString boxedLongDescriptor = createString("Ljava/lang/Long;");
  public final DexString boxedShortDescriptor = createString("Ljava/lang/Short;");
  public final DexString boxedNumberDescriptor = createString("Ljava/lang/Number;");
  public final DexString boxedVoidDescriptor = createString("Ljava/lang/Void;");

  public final DexString waitMethodName = createString("wait");
  public final DexString notifyMethodName = createString("notify");
  public final DexString notifyAllMethodName = createString("notifyAll");

  public final DexString ofMethodName = createString("of");
  public final DexString unboxBooleanMethodName = createString("booleanValue");
  public final DexString unboxByteMethodName = createString("byteValue");
  public final DexString unboxCharMethodName = createString("charValue");
  public final DexString unboxShortMethodName = createString("shortValue");
  public final DexString unboxIntMethodName = createString("intValue");
  public final DexString unboxLongMethodName = createString("longValue");
  public final DexString unboxFloatMethodName = createString("floatValue");
  public final DexString unboxDoubleMethodName = createString("doubleValue");

  public final DexString isEmptyMethodName = createString("isEmpty");
  public final DexString lengthMethodName = createString("length");

  public final DexString concatMethodName = createString("concat");
  public final DexString containsMethodName = createString("contains");
  public final DexString startsWithMethodName = createString("startsWith");
  public final DexString endsWithMethodName = createString("endsWith");
  public final DexString equalsMethodName = createString("equals");
  public final DexString hashCodeMethodName = createString("hashCode");
  public final DexString identityHashCodeName = createString("identityHashCode");
  public final DexString equalsIgnoreCaseMethodName = createString("equalsIgnoreCase");
  public final DexString contentEqualsMethodName = createString("contentEquals");
  public final DexString indexOfMethodName = createString("indexOf");
  public final DexString lastIndexOfMethodName = createString("lastIndexOf");
  public final DexString compareToMethodName = createString("compareTo");
  public final DexString compareToIgnoreCaseMethodName = createString("compareToIgnoreCase");
  public final DexString cloneMethodName = createString("clone");
  public final DexString formatMethodName = createString("format");
  public final DexString substringName = createString("substring");
  public final DexString trimName = createString("trim");

  public final DexString valueOfMethodName = createString("valueOf");
  public final DexString valuesMethodName = createString("values");
  public final DexString toCharArrayMethodName = createString("toCharArray");
  public final DexString toStringMethodName = createString("toString");
  public final DexString internMethodName = createString("intern");

  public final DexString convertMethodName = createString("convert");
  public final DexString wrapperFieldName = createString("wrappedValue");

  public final DexString iteratorName = createString("iterator");
  public final DexString hasNextName = createString("hasNext");
  public final DexString nextName = createString("next");
  public final DexString getClassMethodName = createString("getClass");
  public final DexString finalizeMethodName = createString("finalize");
  public final DexString ordinalMethodName = createString("ordinal");
  public final DexString nameString = createString("name");
  public final DexString closeMethodName = createString("close");
  public final DexString desiredAssertionStatusMethodName = createString("desiredAssertionStatus");
  public final DexString forNameMethodName = createString("forName");
  public final DexString getNameName = createString("getName");
  public final DexString getCanonicalNameName = createString("getCanonicalName");
  public final DexString getSimpleNameName = createString("getSimpleName");
  public final DexString getTypeNameName = createString("getTypeName");
  public final DexString getDeclaredConstructorName = createString("getDeclaredConstructor");
  public final DexString getFieldName = createString("getField");
  public final DexString getDeclaredFieldName = createString("getDeclaredField");
  public final DexString getMethodName = createString("getMethod");
  public final DexString getDeclaredMethodName = createString("getDeclaredMethod");
  public final DexString newInstanceName = createString("newInstance");
  public final DexString assertionsDisabled = createString("$assertionsDisabled");
  public final DexString invokeMethodName = createString("invoke");
  public final DexString invokeExactMethodName = createString("invokeExact");

  public final DexString charSequenceDescriptor = createString("Ljava/lang/CharSequence;");
  public final DexString charSequenceArrayDescriptor = createString("[Ljava/lang/CharSequence;");
  public final DexString stringDescriptor = createString("Ljava/lang/String;");
  public final DexString stringArrayDescriptor = createString("[Ljava/lang/String;");
  public final DexString objectDescriptor = createString("Ljava/lang/Object;");
  public final DexString recordDescriptor = createString(recordDescriptorString);
  public final DexString recordTagDescriptor = createString(recordTagDescriptorString);
  public final DexString autoCloseableTagDescriptor = createString(autoCloseableTagString);
  public final DexString objectArrayDescriptor = createString("[Ljava/lang/Object;");
  public final DexString classDescriptor = createString("Ljava/lang/Class;");
  public final DexString classLoaderDescriptor = createString("Ljava/lang/ClassLoader;");
  public final DexString autoCloseableDescriptor = createString("Ljava/lang/AutoCloseable;");
  public final DexString classArrayDescriptor = createString("[Ljava/lang/Class;");
  public final DexString classDescDescriptor = createString("Ljava/lang/constant/ClassDesc;");
  public final DexString enumDescDescriptor = createString("Ljava/lang/Enum$EnumDesc;");
  public final DexString constructorDescriptor = createString("Ljava/lang/reflect/Constructor;");
  public final DexString fieldDescriptor = createString("Ljava/lang/reflect/Field;");
  public final DexString methodDescriptor = createString("Ljava/lang/reflect/Method;");
  public final DexString enumDescriptor = createString("Ljava/lang/Enum;");
  public final DexString javaLangSystemDescriptor = createString("Ljava/lang/System;");
  public final DexString annotationDescriptor = createString("Ljava/lang/annotation/Annotation;");
  public final DexString objectsDescriptor = createString("Ljava/util/Objects;");
  public final DexString collectionsDescriptor = createString("Ljava/util/Collections;");
  public final DexString iterableDescriptor = createString("Ljava/lang/Iterable;");
  public final DexString mathDescriptor = createString("Ljava/lang/Math;");
  public final DexString strictMathDescriptor = createString("Ljava/lang/StrictMath;");
  public final DexString closeableDescriptor = createString("Ljava/io/Closeable;");
  public final DexString zipFileDescriptor = createString("Ljava/util/zip/ZipFile;");

  public final DexString bufferDescriptor = createString("Ljava/nio/Buffer;");
  public final DexString byteBufferDescriptor = createString("Ljava/nio/ByteBuffer;");
  public final DexString mappedByteBufferDescriptor = createString("Ljava/nio/MappedByteBuffer;");
  public final DexString charBufferDescriptor = createString("Ljava/nio/CharBuffer;");
  public final DexString shortBufferDescriptor = createString("Ljava/nio/ShortBuffer;");
  public final DexString intBufferDescriptor = createString("Ljava/nio/IntBuffer;");
  public final DexString longBufferDescriptor = createString("Ljava/nio/LongBuffer;");
  public final DexString floatBufferDescriptor = createString("Ljava/nio/FloatBuffer;");
  public final DexString doubleBufferDescriptor = createString("Ljava/nio/DoubleBuffer;");

  public final DexString stringBuilderDescriptor = createString("Ljava/lang/StringBuilder;");
  public final DexString stringBufferDescriptor = createString("Ljava/lang/StringBuffer;");

  public final DexString varHandleDescriptor = createString(varHandleDescriptorString);
  public final DexString methodHandleDescriptor = createString("Ljava/lang/invoke/MethodHandle;");
  public final DexString methodHandlesDescriptor = createString("Ljava/lang/invoke/MethodHandles;");
  public final DexString methodHandlesLookupDescriptor =
      createString(methodHandlesLookupDescriptorString);
  public final DexString methodTypeDescriptor = createString("Ljava/lang/invoke/MethodType;");
  public final DexString invocationHandlerDescriptor =
      createString("Ljava/lang/reflect/InvocationHandler;");
  public final DexString proxyDescriptor = createString("Ljava/lang/reflect/Proxy;");
  public final DexString serviceLoaderDescriptor = createString("Ljava/util/ServiceLoader;");
  public final DexString serviceLoaderConfigurationErrorDescriptor =
      createString("Ljava/util/ServiceConfigurationError;");
  public final DexString localeDescriptor = createString("Ljava/util/Locale;");
  public final DexString listDescriptor = createString("Ljava/util/List;");
  public final DexString setDescriptor = createString("Ljava/util/Set;");
  public final DexString mapDescriptor = createString("Ljava/util/Map;");
  public final DexString mapEntryDescriptor = createString("Ljava/util/Map$Entry;");
  public final DexString collectionDescriptor = createString("Ljava/util/Collection;");
  public final DexString comparatorDescriptor = createString("Ljava/util/Comparator;");
  public final DexString callableDescriptor = createString("Ljava/util/concurrent/Callable;");
  public final DexString supplierDescriptor = createString("Ljava/util/function/Supplier;");
  public final DexString predicateDescriptor = createString("Ljava/util/function/Predicate;");
  public final DexString consumerDescriptor = createString("Ljava/util/function/Consumer;");
  public final DexString runnableDescriptor = createString("Ljava/lang/Runnable;");
  public final DexString optionalDescriptor = createString("Ljava/util/Optional;");
  public final DexString optionalDoubleDescriptor = createString("Ljava/util/OptionalDouble;");
  public final DexString optionalIntDescriptor = createString("Ljava/util/OptionalInt;");
  public final DexString optionalLongDescriptor = createString("Ljava/util/OptionalLong;");
  public final DexString streamDescriptor = createString("Ljava/util/stream/Stream;");
  public final DexString arraysDescriptor = createString("Ljava/util/Arrays;");
  public final DexString threadLocalDescriptor = createString("Ljava/lang/ThreadLocal;");
  public final DexString concurrentHashMapDescriptor =
      createString("Ljava/util/concurrent/ConcurrentHashMap;");
  public final DexString concurrentHaspMapKeySetViewDescriptor =
      createString("Ljava/util/concurrent/ConcurrentHashMap$KeySetView;");

  public final DexString throwableDescriptor = createString(throwableDescriptorString);
  public final DexString kotlinMetadataDescriptor = createString("Lkotlin/Metadata;");
  public final DexString kotlinJvmNameDescriptor = createString("Lkotlin/jvm/JvmName;");

  public final DexString intFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicIntegerFieldUpdater;");
  public final DexString longFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicLongFieldUpdater;");
  public final DexString referenceFieldUpdaterDescriptor =
      createString("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;");

  public final DexType javaUtilConcurrentAtomicAtomicIntegerFieldUpdater =
      createType(intFieldUpdaterDescriptor);
  public final DexType javaUtilConcurrentAtomicAtomicLongFieldUpdater =
      createType(longFieldUpdaterDescriptor);
  public final DexType javaUtilConcurrentAtomicAtomicReferenceFieldUpdater =
      createType(referenceFieldUpdaterDescriptor);

  public final DexString newUpdaterName = createString("newUpdater");
  public final DexString compareAndSetName = createString("compareAndSet");
  public final DexString getName = createString("get");
  public final DexString setName = createString("set");
  public final DexString getAndSetName = createString("getAndSet");

  public final DexString constructorMethodName = createString(Constants.INSTANCE_INITIALIZER_NAME);
  public final DexString classConstructorMethodName =
      createString(Constants.CLASS_INITIALIZER_NAME);

  public final DexString thisName = createString("this");
  public final DexString lambdaInstanceFieldName = createString(LAMBDA_INSTANCE_FIELD_NAME);
  public final DexString javacLambdaMethodPrefix =
      createString(LambdaClass.JAVAC_EXPECTED_LAMBDA_METHOD_PREFIX);
  public final DexString kotlinLambdaMethodIdentifier = createString("$lambda$");

  public final DexString enabledFieldName = createString("ENABLED");

  public final DexString throwableArrayDescriptor = createString("[Ljava/lang/Throwable;");

  public final DexString valueString = createString("value");
  public final DexString kindString = createString("kind");
  public final DexString versionHashString = createString("versionHash");
  public final DexString apiLevelString = createString("apiLevel");
  public final DexString namesString = createString("names");
  public final DexString accessFlagsString = createString("accessFlags");

  // Prefix for runtime affecting yet potential class-retained annotations.
  public final DexString dalvikAnnotationPrefix = createString("Ldalvik/annotation/");
  public final DexString dalvikAnnotationCodegenCovariantReturnTypePrefix =
      createString("Ldalvik/annotation/codegen/CovariantReturnType");
  public final DexString dalvikAnnotationOptimizationPrefix =
      createString("Ldalvik/annotation/optimization/");

  // Method names used on VarHandle.
  public final DexString getString = createString("get");
  public final DexString setString = createString("set");
  public final DexString compareAndSetString = createString("compareAndSet");
  public final DexString weakCompareAndSetString = createString("weakCompareAndSet");
  public final DexString getVolatileString = createString("getVolatile");
  public final DexString setVolatileString = createString("setVolatile");
  public final DexString setReleaseString = createString("setRelease");

  // Method names used on MethodHandles.
  public final DexString lookupString = createString("lookup");
  public final DexString privateLookupInString = createString("privateLookupIn");
  public final DexType booleanType = createStaticallyKnownType(booleanDescriptor);
  public final DexType byteType = createStaticallyKnownType(byteDescriptor);
  public final DexType charType = createStaticallyKnownType(charDescriptor);
  public final DexType doubleType = createStaticallyKnownType(doubleDescriptor);
  public final DexType floatType = createStaticallyKnownType(floatDescriptor);
  public final DexType intType = createStaticallyKnownType(intDescriptor);
  public final DexType longType = createStaticallyKnownType(longDescriptor);
  public final DexType shortType = createStaticallyKnownType(shortDescriptor);
  public final DexType voidType = createStaticallyKnownType(voidDescriptor);

  public final DexType booleanArrayType = createStaticallyKnownType(booleanArrayDescriptor);
  public final DexType byteArrayType = createStaticallyKnownType(byteArrayDescriptor);
  public final DexType charArrayType = createStaticallyKnownType(charArrayDescriptor);
  public final DexType doubleArrayType = createStaticallyKnownType(doubleArrayDescriptor);
  public final DexType floatArrayType = createStaticallyKnownType(floatArrayDescriptor);
  public final DexType intArrayType = createStaticallyKnownType(intArrayDescriptor);
  public final DexType longArrayType = createStaticallyKnownType(longArrayDescriptor);
  public final DexType shortArrayType = createStaticallyKnownType(shortArrayDescriptor);

  public final DexType boxedBooleanType = createStaticallyKnownType(boxedBooleanDescriptor);
  public final DexType boxedByteType = createStaticallyKnownType(boxedByteDescriptor);
  public final DexType boxedCharType = createStaticallyKnownType(boxedCharDescriptor);
  public final DexType boxedDoubleType = createStaticallyKnownType(boxedDoubleDescriptor);
  public final DexType boxedFloatType = createStaticallyKnownType(boxedFloatDescriptor);
  public final DexType boxedIntType = createStaticallyKnownType(boxedIntDescriptor);
  public final DexType boxedLongType = createStaticallyKnownType(boxedLongDescriptor);
  public final DexType boxedShortType = createStaticallyKnownType(boxedShortDescriptor);
  public final DexType boxedNumberType = createStaticallyKnownType(boxedNumberDescriptor);
  public final DexType boxedVoidType = createStaticallyKnownType(boxedVoidDescriptor);

  public final DexType charSequenceType = createStaticallyKnownType(charSequenceDescriptor);
  public final DexType charSequenceArrayType =
      createStaticallyKnownType(charSequenceArrayDescriptor);
  public final DexType stringType = createStaticallyKnownType(stringDescriptor);
  public final DexType stringArrayType = createStaticallyKnownType(stringArrayDescriptor);
  public final DexType objectType = createStaticallyKnownType(objectDescriptor);
  public final DexType recordType = createStaticallyKnownType(recordDescriptor);
  public final DexType recordTagType = createStaticallyKnownType(recordTagDescriptor);
  public final DexType objectArrayType = createStaticallyKnownType(objectArrayDescriptor);
  public final DexType classArrayType = createStaticallyKnownType(classArrayDescriptor);
  public final DexType enumType = createStaticallyKnownType(enumDescriptor);
  public final DexType annotationType = createStaticallyKnownType(annotationDescriptor);
  public final DexType arraysType = createStaticallyKnownType(arraysDescriptor);
  public final DexType objectsType = createStaticallyKnownType(objectsDescriptor);
  public final DexType collectionsType = createStaticallyKnownType(collectionsDescriptor);
  public final DexType iterableType = createStaticallyKnownType(iterableDescriptor);
  public final DexType mathType = createStaticallyKnownType(mathDescriptor);
  public final DexType strictMathType = createStaticallyKnownType(strictMathDescriptor);
  public final DexType referenceFieldUpdaterType =
      createStaticallyKnownType(referenceFieldUpdaterDescriptor);

  public final DexType classType = createStaticallyKnownType(classDescriptor);
  public final DexType packageType = createStaticallyKnownType(Package.class);
  public final DexType classLoaderType = createStaticallyKnownType(classLoaderDescriptor);
  public final DexType constructorType = createStaticallyKnownType(constructorDescriptor);
  public final DexType fieldType = createStaticallyKnownType(fieldDescriptor);
  public final DexType methodType = createStaticallyKnownType(methodDescriptor);
  public final DexType autoCloseableType = createStaticallyKnownType(autoCloseableDescriptor);

  public final DexType closeableType = createStaticallyKnownType(closeableDescriptor);
  public final DexType zipFileType = createStaticallyKnownType(zipFileDescriptor);

  public final DexType stringBuilderType = createStaticallyKnownType(stringBuilderDescriptor);
  public final DexType stringBufferType = createStaticallyKnownType(stringBufferDescriptor);

  public final DexType classDescType = createStaticallyKnownType(classDescDescriptor);
  public final DexType enumDescType = createStaticallyKnownType(enumDescDescriptor);

  public final DexType javaLangAnnotationRetentionPolicyType =
      createStaticallyKnownType("Ljava/lang/annotation/RetentionPolicy;");
  public final DexType javaLangReflectArrayType =
      createStaticallyKnownType("Ljava/lang/reflect/Array;");
  public final DexType javaLangSystemType = createStaticallyKnownType(javaLangSystemDescriptor);
  public final DexType javaIoPrintStreamType = createStaticallyKnownType("Ljava/io/PrintStream;");

  public final DexType javaIoEOFExceptionType = createStaticallyKnownType("Ljava/io/EOFException;");
  public final DexType javaIoFileNotFoundExceptionType =
      createStaticallyKnownType("Ljava/io/FileNotFoundException;");
  public final DexType javaIoInterruptedIOExceptionType =
      createStaticallyKnownType("Ljava/io/InterruptedIOException;");
  public final DexType javaIoInvalidObjectExceptionType =
      createStaticallyKnownType("Ljava/io/InvalidObjectException;");
  public final DexType javaIoIOExceptionType = createStaticallyKnownType("Ljava/io/IOException;");
  public final DexType javaIoNotSerializableExceptionType =
      createStaticallyKnownType("Ljava/io/NotSerializableException;");
  public final DexType javaIoUnsupportedEncodingExceptionType =
      createStaticallyKnownType("Ljava/io/UnsupportedEncodingException;");
  public final DexType javaLangAbstractMethodErrorType =
      createStaticallyKnownType("Ljava/lang/AbstractMethodError;");
  public final DexType javaLangArithmeticExceptionType =
      createStaticallyKnownType("Ljava/lang/ArithmeticException;");
  public final DexType javaLangArrayIndexOutOfBoundsExceptionType =
      createStaticallyKnownType("Ljava/lang/ArrayIndexOutOfBoundsException;");
  public final DexType javaLangAssertionErrorType =
      createStaticallyKnownType("Ljava/lang/AssertionError;");
  public final DexType javaLangClassCastExceptionType =
      createStaticallyKnownType("Ljava/lang/ClassCastException;");
  public final DexType javaLangClassNotFoundExceptionType =
      createStaticallyKnownType("Ljava/lang/ClassNotFoundException;");
  public final DexType javaLangErrorType = createStaticallyKnownType("Ljava/lang/Error;");
  public final DexType javaLangExceptionInInitializerErrorType =
      createStaticallyKnownType("Ljava/lang/ExceptionInInitializerError;");
  public final DexType javaLangIllegalAccessErrorType =
      createStaticallyKnownType("Ljava/lang/IllegalAccessError;");
  public final DexType javaLangIllegalArgumentExceptionType =
      createStaticallyKnownType("Ljava/lang/IllegalArgumentException;");
  public final DexType javaLangIllegalMonitorStateExceptionType =
      createStaticallyKnownType("Ljava/lang/IllegalMonitorStateException;");
  public final DexType javaLangIllegalStateExceptionType =
      createStaticallyKnownType("Ljava/lang/IllegalStateException;");
  public final DexType javaLangIncompatibleClassChangeErrorType =
      createStaticallyKnownType("Ljava/lang/IncompatibleClassChangeError;");
  public final DexType javaLangIndexOutOfBoundsExceptionType =
      createStaticallyKnownType("Ljava/lang/IndexOutOfBoundsException;");
  public final DexType javaLangInterruptedExceptionType =
      createStaticallyKnownType("Ljava/lang/InterruptedException;");
  public final DexType javaLangNoClassDefFoundErrorType =
      createStaticallyKnownType("Ljava/lang/NoClassDefFoundError;");
  public final DexType javaLangNoSuchFieldErrorType =
      createStaticallyKnownType("Ljava/lang/NoSuchFieldError;");
  public final DexType javaLangNoSuchMethodErrorType =
      createStaticallyKnownType("Ljava/lang/NoSuchMethodError;");
  public final DexType javaLangNoSuchMethodExceptionType =
      createStaticallyKnownType("Ljava/lang/NoSuchMethodException;");
  public final DexType javaLangNullPointerExceptionType =
      createStaticallyKnownType("Ljava/lang/NullPointerException;");
  public final DexType javaLangNumberFormatExceptionType =
      createStaticallyKnownType("Ljava/lang/NumberFormatException;");
  public final DexType javaLangOutOfMemoryErrorType =
      createStaticallyKnownType("Ljava/lang/OutOfMemoryError;");
  public final DexType javaLangReflectiveOperationExceptionType =
      createStaticallyKnownType("Ljava/lang/ReflectiveOperationException;");
  public final DexType javaLangRuntimeExceptionType =
      createStaticallyKnownType("Ljava/lang/RuntimeException;");
  public final DexType javaLangSecurityExceptionType =
      createStaticallyKnownType("Ljava/lang/SecurityException;");
  public final DexType javaLangUnsatisfiedLinkErrorType =
      createStaticallyKnownType("Ljava/lang/UnsatisfiedLinkError;");
  public final DexType javaLangUnsupportedOperationExceptionType =
      createStaticallyKnownType("Ljava/lang/UnsupportedOperationException;");
  public final DexType javaNioBufferOverflowExceptionType =
      createStaticallyKnownType("Ljava/nio/BufferOverflowException;");
  public final DexType javaNioFileFileSystemLoopExceptionType =
      createStaticallyKnownType("Ljava/nio/file/FileSystemLoopException;");
  public final DexType javaNioReadOnlyBufferExceptionType =
      createStaticallyKnownType("Ljava/nio/ReadOnlyBufferException;");
  public final DexType javaTextParseExceptionType =
      createStaticallyKnownType("Ljava/text/ParseException;");
  public final DexType javaUtilConcurrentCancellationExceptionType =
      createStaticallyKnownType("Ljava/util/concurrent/CancellationException;");
  public final DexType javaUtilConcurrentExecutionExceptionType =
      createStaticallyKnownType("Ljava/util/concurrent/ExecutionException;");
  public final DexType javaUtilConcurrentRejectedExecutionExceptionType =
      createStaticallyKnownType("Ljava/util/concurrent/RejectedExecutionException;");
  public final DexType javaUtilConcurrentTimeoutExceptionType =
      createStaticallyKnownType("Ljava/util/concurrent/TimeoutException;");
  public final DexType javaUtilConcurrentModificationExceptionType =
      createStaticallyKnownType("Ljava/util/ConcurrentModificationException;");
  public final DexType javaUtilNoSuchElementExceptionType =
      createStaticallyKnownType("Ljava/util/NoSuchElementException;");

  public final DexType varHandleType = createStaticallyKnownType(varHandleDescriptor);
  public final DexType methodHandleType = createStaticallyKnownType(methodHandleDescriptor);
  public final DexType methodHandlesType = createStaticallyKnownType(methodHandlesDescriptor);
  public final DexType methodHandlesLookupType =
      createStaticallyKnownType(methodHandlesLookupDescriptor);
  public final DexType methodTypeType = createStaticallyKnownType(methodTypeDescriptor);
  public final DexType invocationHandlerType =
      createStaticallyKnownType(invocationHandlerDescriptor);
  public final DexType proxyType = createStaticallyKnownType(proxyDescriptor);
  public final DexType serviceLoaderType = createStaticallyKnownType(serviceLoaderDescriptor);
  public final DexType serviceLoaderConfigurationErrorType =
      createStaticallyKnownType(serviceLoaderConfigurationErrorDescriptor);
  public final DexType mapEntryType = createStaticallyKnownType(mapEntryDescriptor);
  public final DexType abstractMapSimpleEntryType =
      createStaticallyKnownType("Ljava/util/AbstractMap$SimpleEntry;");
  public final DexType durationType = createStaticallyKnownType("Ljava/time/Duration;");
  public final DexType collectionType = createStaticallyKnownType(collectionDescriptor);
  public final DexType comparatorType = createStaticallyKnownType(comparatorDescriptor);
  public final DexType callableType = createStaticallyKnownType(callableDescriptor);
  public final DexType supplierType = createStaticallyKnownType(supplierDescriptor);
  public final DexType predicateType = createStaticallyKnownType(predicateDescriptor);
  public final DexType consumerType = createStaticallyKnownType(consumerDescriptor);
  public final DexType runnableType = createStaticallyKnownType(runnableDescriptor);
  public final DexType optionalType = createStaticallyKnownType(optionalDescriptor);
  public final DexType optionalDoubleType = createStaticallyKnownType(optionalDoubleDescriptor);
  public final DexType optionalIntType = createStaticallyKnownType(optionalIntDescriptor);
  public final DexType optionalLongType = createStaticallyKnownType(optionalLongDescriptor);
  public final DexType streamType = createStaticallyKnownType(streamDescriptor);
  public final DexType threadLocalType = createStaticallyKnownType(threadLocalDescriptor);
  public final DexType concurrentHashMapType =
      createStaticallyKnownType(concurrentHashMapDescriptor);
  public final DexType concurrentHashMapKeySetViewType =
      createStaticallyKnownType(concurrentHaspMapKeySetViewDescriptor);

  public final DexType bufferType = createStaticallyKnownType(bufferDescriptor);
  public final DexType byteBufferType = createStaticallyKnownType(byteBufferDescriptor);
  public final DexType mappedByteBufferType = createStaticallyKnownType(mappedByteBufferDescriptor);
  public final DexType charBufferType = createStaticallyKnownType(charBufferDescriptor);
  public final DexType shortBufferType = createStaticallyKnownType(shortBufferDescriptor);
  public final DexType intBufferType = createStaticallyKnownType(intBufferDescriptor);
  public final DexType longBufferType = createStaticallyKnownType(longBufferDescriptor);
  public final DexType floatBufferType = createStaticallyKnownType(floatBufferDescriptor);
  public final DexType doubleBufferType = createStaticallyKnownType(doubleBufferDescriptor);
  public final List<DexType> typeSpecificBuffers =
      ImmutableList.of(
          byteBufferType,
          mappedByteBufferType,
          charBufferType,
          shortBufferType,
          intBufferType,
          longBufferType,
          floatBufferType,
          doubleBufferType);

  private static final List<String> MULTIDEX_PREFIXES =
      ImmutableList.of("androidx/", "android/support/");
  private static final List<String> MULTIDEX_SUFFIXES =
      ImmutableList.of(
          "multidex/MultiDex$V14$ElementConstructor;",
          "multidex/MultiDex$V14$ICSElementConstructor;",
          "multidex/MultiDex$V14$JBMR11ElementConstructor;",
          "multidex/MultiDex$V14$JBMR2ElementConstructor;",
          "multidex/MultiDex$V14;",
          "multidex/MultiDex$V19;",
          "multidex/MultiDex$V21_PLUS;",
          "multidex/MultiDex$V4;",
          "multidex/MultiDexApplication;",
          "multidex/MultiDexExtractor$1;",
          "multidex/MultiDexExtractor$ExtractedDex;",
          "multidex/MultiDexExtractor;",
          "multidex/MultiDex;",
          "multidex/ZipUtil;",
          "multidex/ZipUtil$CentralDirectory;");
  private static final List<String> MULTIDEX_INSTRUMENTATION =
      ImmutableList.of(
          "Landroid/support/multidex/instrumentation/BuildConfig;",
          "Landroid/test/runner/MultiDexTestRunner;");

  private Set<DexType> createMultiDexTypes() {
    ImmutableSet.Builder<DexType> builder = ImmutableSet.builder();
    for (String prefix : MULTIDEX_PREFIXES) {
      for (String suffix : MULTIDEX_SUFFIXES) {
        builder.add(createType("L" + prefix + suffix));
      }
    }
    for (String typeString : MULTIDEX_INSTRUMENTATION) {
      builder.add(createType(typeString));
    }
    return builder.build();
  }

  public Set<DexType> multiDexTypes = createMultiDexTypes();

  public final DexType doubleConsumer =
      createStaticallyKnownType("Ljava/util/function/DoubleConsumer;");
  public final DexType longConsumer =
      createStaticallyKnownType("Ljava/util/function/LongConsumer;");
  public final DexType intConsumer = createStaticallyKnownType("Ljava/util/function/IntConsumer;");

  public final DexType retentionType =
      createStaticallyKnownType("Ljava/lang/annotation/Retention;");
  public final DexType throwableType = createStaticallyKnownType(throwableDescriptor);
  public final DexMethod noSuchElementExceptionInit =
      createInstanceInitializer(javaUtilNoSuchElementExceptionType);

  public final DexType kotlinMetadataType = createStaticallyKnownType(kotlinMetadataDescriptor);
  public final DexType kotlinJvmInlineType = createStaticallyKnownType("Lkotlin/jvm/JvmInline;");
  public final DexType kotlinJvmNameType = createStaticallyKnownType(kotlinJvmNameDescriptor);
  public final DexType kotlinJvmInternalIntrinsicsType =
      createStaticallyKnownType(kotlinJvmInternalIntrinsicsDescriptor);

  public final DexType kotlinEnumEntriesList =
      createStaticallyKnownType("Lkotlin/enums/EnumEntriesList;");
  public final DexMethod kotlinEnumEntriesListInit =
      createInstanceInitializer(kotlinEnumEntriesList, enumType.toArrayType(this));

  public final DexType javaIoFileType = createStaticallyKnownType("Ljava/io/File;");
  public final DexType javaMathBigIntegerType = createStaticallyKnownType("Ljava/math/BigInteger;");
  public final DexType javaNioByteOrderType = createStaticallyKnownType("Ljava/nio/ByteOrder;");
  public final DexType javaUtilCollectionsType =
      createStaticallyKnownType("Ljava/util/Collections;");
  public final DexType javaUtilIteratorType = createStaticallyKnownType("Ljava/util/Iterator;");
  public final DexProto javaUtilIteratorProto = createProto(javaUtilIteratorType);
  public final DexType javaUtilComparatorType = createStaticallyKnownType("Ljava/util/Comparator;");
  public final DexType javaUtilConcurrentTimeUnitType =
      createStaticallyKnownType("Ljava/util/concurrent/TimeUnit;");
  public final DexType javaUtilFormattableType =
      createStaticallyKnownType("Ljava/util/Formattable;");
  public final DexType javaUtilListType = createStaticallyKnownType(listDescriptor);
  public final DexType javaUtilMapType = createStaticallyKnownType(mapDescriptor);
  public final DexType javaUtilSetType = createStaticallyKnownType(setDescriptor);
  public final DexType javaUtilArrayListType = createStaticallyKnownType("Ljava/util/ArrayList;");
  public final DexType javaUtilLinkedListType = createStaticallyKnownType("Ljava/util/LinkedList;");
  public final DexType comGoogleCommonCollectImmutableListType =
      createStaticallyKnownType("Lcom/google/common/collect/ImmutableList;");
  public final DexType javaUtilConcurrentCopyOnWriteArrayListType =
      createStaticallyKnownType("Ljava/util/concurrent/CopyOnWriteArrayList;");
  public final DexType javaUtilLocaleType = createStaticallyKnownType(localeDescriptor);
  public final DexType javaUtilLoggingLevelType =
      createStaticallyKnownType("Ljava/util/logging/Level;");
  public final DexType javaUtilLoggingLoggerType =
      createStaticallyKnownType("Ljava/util/logging/Logger;");
  public final DexType androidAppActivity = createStaticallyKnownType("Landroid/app/Activity;");
  public final DexType androidAppFragment = createStaticallyKnownType("Landroid/app/Fragment;");
  public final DexType androidAppZygotePreload =
      createStaticallyKnownType("Landroid/app/ZygotePreload;");
  public final DexType androidGraphicsColorType =
      createStaticallyKnownType("Landroid/graphics/Color;");
  public final DexType androidGraphicsImageFormatType =
      createStaticallyKnownType("Landroid/graphics/ImageFormat;");
  public final DexType androidNetUriType = createStaticallyKnownType("Landroid/net/Uri;");
  public final DexType androidTextTextUtilsType =
      createStaticallyKnownType("Landroid/text/TextUtils;");
  public final DexType androidViewViewMeasureSpecType =
      createStaticallyKnownType("Landroid/view/View$MeasureSpec;");
  public final DexType javaUtilRegexPatternType =
      createStaticallyKnownType("Ljava/util/regex/Pattern;");
  public final DexType androidOsBuildType = createStaticallyKnownType("Landroid/os/Build;");
  public final DexType androidOsBuildVersionType =
      createStaticallyKnownType("Landroid/os/Build$VERSION;");
  public final DexType androidOsBundleType = createStaticallyKnownType("Landroid/os/Bundle;");
  public final DexType androidOsHandlerType = createStaticallyKnownType("Landroid/os/Handler;");
  public final DexType androidOsParcelableCreatorType =
      createStaticallyKnownType("Landroid/os/Parcelable$Creator;");
  public final DexType androidSystemOsConstantsType =
      createStaticallyKnownType("Landroid/system/OsConstants;");
  public final DexType androidUtilLogType = createStaticallyKnownType("Landroid/util/Log;");
  public final DexType androidUtilPropertyType =
      createStaticallyKnownType("Landroid/util/Property;");
  public final DexType androidViewViewType = createStaticallyKnownType("Landroid/view/View;");
  public final DexType androidUtilAttributeSetType =
      createStaticallyKnownType("Landroid/util/AttributeSet;");
  public final DexType androidPreferencePreferenceType =
      createStaticallyKnownType("Landroid/preference/Preference;");
  public final DexType androidTransitionTransitionType =
      createStaticallyKnownType("Landroid/transition/Transition;");
  public final DexType androidUtilSparseArrayType =
      createStaticallyKnownType(androidUtilSparseArrayDescriptorString);
  public final DexType androidContentResTypedArrayType =
      createStaticallyKnownType(androidContentResTypedArrayDescriptorString);
  public final DexType androidContentContentProviderClientType =
      createStaticallyKnownType(androidContentContentProviderClientDescriptorString);
  public final DexType androidDrmDrmManagerClientType =
      createStaticallyKnownType(androidDrmDrmManagerClientDescriptorString);
  public final DexType androidMediaMediaDrmType =
      createStaticallyKnownType(androidMediaMediaDrmDescriptorString);
  public final DexType androidMediaMediaMetadataRetrieverType =
      createStaticallyKnownType(androidMediaMediaMetadataRetrieverDescriptorString);
  public final DexType androidResourcesType =
      createStaticallyKnownType(androidResourcesDescriptorString);
  public final DexType androidContentContextType =
      createStaticallyKnownType(androidContextDescriptorString);
  public final DexString getColorName = createString("getColor");
  public final DexProto androidGetColorProto = createProto(intType, intType);
  public final DexMethod androidResourcesGetColorMethod =
      createMethod(androidResourcesType, androidGetColorProto, getColorName);
  public final DexMethod androidContextGetColorMethod =
      createMethod(androidContentContextType, androidGetColorProto, getColorName);

  public final DexString getStringName = createString("getString");
  public final DexProto androidGetStringProto = createProto(stringType, intType);
  public final DexMethod androidResourcesGetStringMethod =
      createMethod(androidResourcesType, androidGetStringProto, getStringName);

  public final StringBuildingMethods stringBuilderMethods =
      new StringBuildingMethods(stringBuilderType);
  public final StringBuildingMethods stringBufferMethods =
      new StringBuildingMethods(stringBufferType);
  public final BooleanMembers booleanMembers = new BooleanMembers();
  public final ByteMembers byteMembers = new ByteMembers();
  public final CharMembers charMembers = new CharMembers();
  public final FloatMembers floatMembers = new FloatMembers();
  public final IntegerMembers integerMembers = new IntegerMembers();
  public final LongMembers longMembers = new LongMembers();
  public final VoidMembers voidMembers = new VoidMembers();
  public final ObjectsMethods objectsMethods = new ObjectsMethods();
  public final ObjectMembers objectMembers = new ObjectMembers();
  public final BufferMembers bufferMembers = new BufferMembers();
  public final RecordMembers recordMembers = new RecordMembers();
  public final ShortMembers shortMembers = new ShortMembers();
  public final StringMembers stringMembers = new StringMembers();
  public final SupplierMembers supplierMembers = new SupplierMembers();
  public final ThreadLocalMembers threadLocalMembers = new ThreadLocalMembers();
  public final DoubleMembers doubleMembers = new DoubleMembers();
  public final ThrowableMethods throwableMethods = new ThrowableMethods();
  public final AssertionErrorMethods assertionErrorMethods = new AssertionErrorMethods();
  public final ClassMethods classMethods = new ClassMethods();
  public final ConstructorMethods constructorMethods = new ConstructorMethods();
  public final MethodMethods methodMethods = new MethodMethods();
  public final EnumMembers enumMembers = new EnumMembers();
  public final AndroidUtilLogMembers androidUtilLogMembers = new AndroidUtilLogMembers();
  public final JavaLangReflectArrayMembers javaLangReflectArrayMembers =
      new JavaLangReflectArrayMembers();
  public final JavaLangAnnotationRetentionPolicyMembers javaLangAnnotationRetentionPolicyMembers =
      new JavaLangAnnotationRetentionPolicyMembers();
  public final JavaLangInvokeVarHandleMembers javaLangInvokeVarHandleMembers =
      new JavaLangInvokeVarHandleMembers();
  public final JavaLangSystemMembers javaLangSystemMembers = new JavaLangSystemMembers();
  public final JavaIoPrintStreamMembers javaIoPrintStreamMembers = new JavaIoPrintStreamMembers();
  public final NullPointerExceptionMethods npeMethods = new NullPointerExceptionMethods();
  public final IllegalArgumentExceptionMethods illegalArgumentExceptionMethods =
      new IllegalArgumentExceptionMethods();
  public final PrimitiveTypesBoxedTypeFields primitiveTypesBoxedTypeFields =
      new PrimitiveTypesBoxedTypeFields();
  public final AtomicIntUpdaterMethods atomicIntUpdaterMethods = new AtomicIntUpdaterMethods();
  public final AtomicLongUpdaterMethods atomicLongUpdaterMethods = new AtomicLongUpdaterMethods();
  public final AtomicReferenceUpdaterMethods atomicReferenceUpdaterMethods =
      new AtomicReferenceUpdaterMethods();
  private final Kotlin kotlin;
  public final PolymorphicMethods polymorphicMethods = new PolymorphicMethods();
  public final ProxyMethods proxyMethods = new ProxyMethods();

  // android.**
  public final AndroidGraphicsColorMembers androidGraphicsColorMembers =
      new AndroidGraphicsColorMembers();
  public final AndroidGraphicsImageFormatMembers androidGraphicsImageFormatMembers =
      new AndroidGraphicsImageFormatMembers();
  public final AndroidNetUriMembers androidNetUriMembers = new AndroidNetUriMembers();
  public final AndroidTextTextUtilsMembers androidTextTextUtilsMembers =
      new AndroidTextTextUtilsMembers();
  public final AndroidViewViewMeasureSpecMembers androidViewViewMeasureSpecMembers =
      new AndroidViewViewMeasureSpecMembers();
  public final JavaUtilRegexPatternMembers javaUtilRegexPatternMembers =
      new JavaUtilRegexPatternMembers();
  public final AndroidOsBuildMembers androidOsBuildMembers = new AndroidOsBuildMembers();
  public final AndroidOsBuildVersionMembers androidOsBuildVersionMembers =
      new AndroidOsBuildVersionMembers();
  public final AndroidOsBundleMembers androidOsBundleMembers = new AndroidOsBundleMembers();
  public final AndroidSystemOsConstantsMembers androidSystemOsConstantsMembers =
      new AndroidSystemOsConstantsMembers();
  public final AndroidViewViewMembers androidViewViewMembers = new AndroidViewViewMembers();
  public final AndroidUtilSparseArrayMembers androidUtilSparseArrayMembers =
      new AndroidUtilSparseArrayMembers();
  public final AndroidContentResTypedArrayMembers androidContentResTypedArrayMembers =
      new AndroidContentResTypedArrayMembers();
  public final AndroidContentContentProviderClientMembers
      androidContentContentProviderClientMembers = new AndroidContentContentProviderClientMembers();
  public final AndroidDrmDrmManagerClientMembers androidDrmDrmManagerClientMembers =
      new AndroidDrmDrmManagerClientMembers();
  public final AndroidMediaMediaDrmMembers androidMediaMediaDrmMembers =
      new AndroidMediaMediaDrmMembers();
  public final AndroidMediaMetadataRetrieverMembers androidMediaMetadataRetrieverMembers =
      new AndroidMediaMetadataRetrieverMembers();

  // java.**
  public final JavaIoFileMembers javaIoFileMembers = new JavaIoFileMembers();
  public final JavaMathBigIntegerMembers javaMathBigIntegerMembers =
      new JavaMathBigIntegerMembers();
  public final MathMembers mathMembers = new MathMembers();
  public final JavaNioByteOrderMembers javaNioByteOrderMembers = new JavaNioByteOrderMembers();
  public final JavaUtilArraysMethods javaUtilArraysMethods = new JavaUtilArraysMethods();
  public final JavaUtilCollectionsMembers javaUtilCollectionsMembers =
      new JavaUtilCollectionsMembers();
  public final JavaUtilConcurrentTimeUnitMembers javaUtilConcurrentTimeUnitMembers =
      new JavaUtilConcurrentTimeUnitMembers();

  public final JavaUtilListMembers javaUtilListMembers = new JavaUtilListMembers();
  public final JavaUtilMapMembers javaUtilMapMembers = new JavaUtilMapMembers();
  public final JavaUtilSetMembers javaUtilSetMembers = new JavaUtilSetMembers();
  public final JavaUtilLocaleMembers javaUtilLocaleMembers = new JavaUtilLocaleMembers();
  public final JavaUtilLoggingLevelMembers javaUtilLoggingLevelMembers =
      new JavaUtilLoggingLevelMembers();

  public final List<LibraryMembers> libraryMembersCollection =
      ImmutableList.of(
          booleanMembers,
          floatMembers,
          integerMembers,
          longMembers,
          stringMembers,
          // android.**
          androidGraphicsColorMembers,
          androidGraphicsImageFormatMembers,
          androidNetUriMembers,
          androidOsBuildMembers,
          androidOsBuildVersionMembers,
          androidOsBundleMembers,
          androidSystemOsConstantsMembers,
          androidTextTextUtilsMembers,
          androidViewViewMeasureSpecMembers,
          androidViewViewMembers,
          // java.**
          javaUtilRegexPatternMembers,
          enumMembers,
          javaIoFileMembers,
          javaMathBigIntegerMembers,
          mathMembers,
          javaNioByteOrderMembers,
          javaUtilCollectionsMembers,
          javaUtilConcurrentTimeUnitMembers,
          javaUtilLocaleMembers,
          javaUtilLoggingLevelMembers);

  public final DexString twrCloseResourceMethodName = createString("$closeResource");
  public final DexProto twrCloseResourceMethodProto =
      createProto(voidType, throwableType, autoCloseableType);

  public final DexString deserializeLambdaMethodName = createString("$deserializeLambda$");
  public final DexType serializedLambdaType =
      createStaticallyKnownType("Ljava/lang/invoke/SerializedLambda;");
  public final DexProto deserializeLambdaMethodProto =
      createProto(objectType, serializedLambdaType);

  public final String defaultSourceFileAttributeString = "SourceFile";
  public final DexString defaultSourceFileAttribute =
      createString(defaultSourceFileAttributeString);
  public final String pgSourceFileAttributeString = "PG";
  public final DexString pgSourceFileAttribute = createString(pgSourceFileAttributeString);

  // Dex system annotations.
  // See https://source.android.com/devices/tech/dalvik/dex-format.html#system-annotation
  public final DexType annotationDefault =
      createStaticallyKnownType("Ldalvik/annotation/AnnotationDefault;");
  public final DexType annotationEnclosingClass =
      createStaticallyKnownType("Ldalvik/annotation/EnclosingClass;");
  public final DexType annotationEnclosingMethod =
      createStaticallyKnownType("Ldalvik/annotation/EnclosingMethod;");
  public final DexType annotationInnerClass =
      createStaticallyKnownType("Ldalvik/annotation/InnerClass;");
  public final DexType annotationMemberClasses =
      createStaticallyKnownType("Ldalvik/annotation/MemberClasses;");
  public final DexType annotationMethodParameters =
      createStaticallyKnownType("Ldalvik/annotation/MethodParameters;");
  public final DexType annotationSignature =
      createStaticallyKnownType(dalvikAnnotationSignatureString);
  public final DexType annotationNestHost =
      createStaticallyKnownType("Ldalvik/annotation/NestHost;");
  public final DexType annotationNestMembers =
      createStaticallyKnownType("Ldalvik/annotation/NestMembers;");
  public final DexType annotationNeverCompile =
      createStaticallyKnownType("Ldalvik/annotation/optimization/NeverCompile;");
  public final DexType annotationPermittedSubclasses =
      createStaticallyKnownType("Ldalvik/annotation/PermittedSubclasses;");
  public final DexType annotationRecord = createStaticallyKnownType("Ldalvik/annotation/Record;");
  public final DexString annotationRecordComponentNames = createString("componentNames");
  public final DexString annotationRecordComponentTypes = createString("componentTypes");
  public final DexString annotationRecordComponentSignatures = createString("componentSignatures");
  public final DexString annotationRecordComponentAnnotationVisibilities =
      createString("componentAnnotationVisibilities");
  public final DexString annotationRecordComponentAnnotations =
      createString("componentAnnotations");
  public final DexType annotationSourceDebugExtension =
      createStaticallyKnownType("Ldalvik/annotation/SourceDebugExtension;");
  public final DexType annotationThrows = createStaticallyKnownType("Ldalvik/annotation/Throws;");
  public final DexType annotationSynthesizedClass =
      createStaticallyKnownType("Lcom/android/tools/r8/annotations/SynthesizedClassV2;");
  public final DexType lambdaMethodAnnotation =
      createStaticallyKnownType(lambdaMethodAnnotationDescriptor);

  public final String annotationReachabilitySensitiveDesc =
      "Ldalvik/annotation/optimization/ReachabilitySensitive;";
  public final DexType annotationReachabilitySensitive =
      createStaticallyKnownType(annotationReachabilitySensitiveDesc);

  private static final String METAFACTORY_METHOD_NAME = "metafactory";
  private static final String METAFACTORY_ALT_METHOD_NAME = "altMetafactory";

  public final DexType metafactoryType =
      createStaticallyKnownType("Ljava/lang/invoke/LambdaMetafactory;");
  public final DexType constantBootstrapsType =
      createStaticallyKnownType("Ljava/lang/invoke/ConstantBootstraps;");
  public final ConstantBootstrapsMembers constantBootstrapsMembers =
      new ConstantBootstrapsMembers();
  public final DexType switchBootstrapType = createType("Ljava/lang/runtime/SwitchBootstraps;");
  public final DexType callSiteType = createStaticallyKnownType("Ljava/lang/invoke/CallSite;");
  public final DexType lookupType =
      createStaticallyKnownType("Ljava/lang/invoke/MethodHandles$Lookup;");
  public final DexProto switchBootstrapMethodProto =
      createProto(
          callSiteType, methodHandlesLookupType, stringType, methodTypeType, objectArrayType);
  public final DexMethod typeSwitchMethod =
      createMethod(switchBootstrapType, switchBootstrapMethodProto, createString("typeSwitch"));
  public final DexMethod enumSwitchMethod =
      createMethod(switchBootstrapType, switchBootstrapMethodProto, createString("enumSwitch"));
  public final DexMethod enumDescMethod =
      createMethod(
          enumDescType, createProto(enumDescType, classDescType, stringType), ofMethodName);
  public final DexMethod classDescMethod =
      createMethod(classDescType, createProto(classDescType, stringType), ofMethodName);
  public final DexType objectMethodsType =
      createStaticallyKnownType("Ljava/lang/runtime/ObjectMethods;");
  public final DexType typeDescriptorType =
      createStaticallyKnownType("Ljava/lang/invoke/TypeDescriptor;");
  public final DexType listIteratorType = createStaticallyKnownType("Ljava/util/ListIterator;");
  public final DexType enumerationType = createStaticallyKnownType("Ljava/util/Enumeration;");
  public final DexType serializableType = createStaticallyKnownType("Ljava/io/Serializable;");
  public final DexType externalizableType = createStaticallyKnownType("Ljava/io/Externalizable;");
  public final DexType cloneableType = createStaticallyKnownType("Ljava/lang/Cloneable;");
  public final DexType comparableType = createStaticallyKnownType("Ljava/lang/Comparable;");
  public final DexType stringConcatFactoryType =
      createStaticallyKnownType("Ljava/lang/invoke/StringConcatFactory;");
  public final DexType sunMiscUnsafeType = createStaticallyKnownType("Lsun/misc/Unsafe;");
  public final DexType desugarVarHandleType =
      createStaticallyKnownType(desugarVarHandleDescriptorString);
  public final DexType desugarMethodHandlesLookupType =
      createStaticallyKnownType(desugarMethodHandlesLookupDescriptorString);
  public final DexType javaUtilConcurrentExecutorServiceType =
      createStaticallyKnownType("Ljava/util/concurrent/ExecutorService;");
  public final DexType javaUtilConcurrentForkJoinPoolType =
      createStaticallyKnownType("Ljava/util/concurrent/ForkJoinPool;");
  public final SunMiscUnsafeMethods sunMiscUnsafeMethods = new SunMiscUnsafeMethods();

  public final ObjectMethodsMembers objectMethodsMembers = new ObjectMethodsMembers();
  public final ServiceLoaderMethods serviceLoaderMethods = new ServiceLoaderMethods();
  public final IteratorMethods iteratorMethods = new IteratorMethods();
  public final StringConcatFactoryMembers stringConcatFactoryMembers =
      new StringConcatFactoryMembers();

  private final SyntheticNaming syntheticNaming = new SyntheticNaming();

  public SyntheticNaming getSyntheticNaming() {
    return syntheticNaming;
  }

  public final Map<String, DexType> primitiveDescriptorToType =
      ImmutableMap.of(
          byteDescriptor.toString(), byteType,
          charDescriptor.toString(), charType,
          shortDescriptor.toString(), shortType,
          intDescriptor.toString(), intType,
          longDescriptor.toString(), longType,
          floatDescriptor.toString(), floatType,
          doubleDescriptor.toString(), doubleType,
          booleanDescriptor.toString(), booleanType);

  public final BiMap<DexType, DexType> primitiveToBoxed = HashBiMap.create(
      ImmutableMap.<DexType, DexType>builder()
          .put(booleanType, boxedBooleanType)
          .put(byteType, boxedByteType)
          .put(charType, boxedCharType)
          .put(shortType, boxedShortType)
          .put(intType, boxedIntType)
          .put(longType, boxedLongType)
          .put(floatType, boxedFloatType)
          .put(doubleType, boxedDoubleType)
          .build());

  public final Map<DexType, DexMethod> unboxPrimitiveMethod =
      ImmutableMap.<DexType, DexMethod>builder()
          .put(boxedBooleanType, createUnboxMethod(booleanType, unboxBooleanMethodName))
          .put(boxedByteType, createUnboxMethod(byteType, unboxByteMethodName))
          .put(boxedCharType, createUnboxMethod(charType, unboxCharMethodName))
          .put(boxedShortType, createUnboxMethod(shortType, unboxShortMethodName))
          .put(boxedIntType, createUnboxMethod(intType, unboxIntMethodName))
          .put(boxedLongType, createUnboxMethod(longType, unboxLongMethodName))
          .put(boxedFloatType, createUnboxMethod(floatType, unboxFloatMethodName))
          .put(boxedDoubleType, createUnboxMethod(doubleType, unboxDoubleMethodName))
          .build();

  public final Set<DexMethod> boxPrimitiveMethods =
      SetUtils.newIdentityHashSet(boxedValueOfMethods());
  public final Set<DexMethod> unboxPrimitiveMethods =
      SetUtils.newIdentityHashSet(unboxPrimitiveMethod.values());

  private DexMethod createUnboxMethod(DexType primitiveType, DexString unboxMethodName) {
    DexProto proto = createProto(primitiveType);
    return createMethod(primitiveToBoxed.get(primitiveType), proto, unboxMethodName);
  }

  // Works both with the boxed and unboxed type.
  public DexMethod getUnboxPrimitiveMethod(DexType type) {
    DexType boxType = primitiveToBoxed.getOrDefault(type, type);
    DexMethod unboxMethod = unboxPrimitiveMethod.get(boxType);
    if (unboxMethod == null) {
      throw new Unreachable("Invalid primitive type descriptor: " + type);
    }
    return unboxMethod;
  }

  // Works both with the boxed and unboxed type.
  public DexMethod getBoxPrimitiveMethod(DexType type) {
    DexType boxType = primitiveToBoxed.getOrDefault(type, type);
    DexType primitive = getPrimitiveFromBoxed(boxType);
    if (primitive == null) {
      return null;
    }
    DexProto proto = createProto(boxType, primitive);
    return createMethod(boxType, proto, valueOfMethodName);
  }

  public BoxUnboxPrimitiveMethodRoundtrip getBoxUnboxPrimitiveMethodRoundtrip(DexType type) {
    if (type.isPrimitiveType()) {
      return new BoxUnboxPrimitiveMethodRoundtrip(
          getBoxPrimitiveMethod(type), getUnboxPrimitiveMethod(type));
    } else if (primitiveToBoxed.containsValue(type)) {
      return new BoxUnboxPrimitiveMethodRoundtrip(
          getUnboxPrimitiveMethod(type), getBoxPrimitiveMethod(type));
    } else {
      return null;
    }
  }

  public static class BoxUnboxPrimitiveMethodRoundtrip {

    private final DexMethod boxIfPrimitiveElseUnbox;
    private final DexMethod unboxIfPrimitiveElseBox;

    public BoxUnboxPrimitiveMethodRoundtrip(
        DexMethod boxIfPrimitiveElseUnbox, DexMethod unboxIfPrimitiveElseBox) {
      this.boxIfPrimitiveElseUnbox = boxIfPrimitiveElseUnbox;
      this.unboxIfPrimitiveElseBox = unboxIfPrimitiveElseBox;
    }

    public DexMethod getBoxIfPrimitiveElseUnbox() {
      return boxIfPrimitiveElseUnbox;
    }

    public DexMethod getUnboxIfPrimitiveElseBox() {
      return unboxIfPrimitiveElseBox;
    }
  }

  public DexType getBoxedForPrimitiveType(DexType primitive) {
    assert primitive.isPrimitiveType();
    return primitiveToBoxed.get(primitive);
  }

  public BoxedPrimitiveMembers getBoxedMembersForPrimitiveOrVoidType(DexType type) {
    assert type.isPrimitiveType() || type.isVoidType();
    switch (type.getDescriptor().getFirstByteAsChar()) {
      case 'B':
        return byteMembers;
      case 'C':
        return charMembers;
      case 'D':
        return doubleMembers;
      case 'F':
        return floatMembers;
      case 'I':
        return integerMembers;
      case 'J':
        return longMembers;
      case 'S':
        return shortMembers;
      case 'V':
        return voidMembers;
      case 'Z':
        return booleanMembers;
      default:
        throw new Unreachable("Unknown type " + type);
    }
  }

  public DexType getPrimitiveFromBoxed(DexType boxedPrimitive) {
    return primitiveToBoxed.inverse().get(boxedPrimitive);
  }

  // Boxed Boxed#valueOf(Primitive), e.g., Boolean Boolean#valueOf(B)
  public Set<DexMethod> boxedValueOfMethods() {
    return primitiveToBoxed.entrySet().stream()
        .map(
            entry -> {
              DexType primitive = entry.getKey();
              DexType boxed = entry.getValue();
              return createMethod(
                  boxed.descriptor,
                  valueOfMethodName,
                  boxed.descriptor,
                  new DexString[] {primitive.descriptor});
            })
        .collect(Collectors.toSet());
  }

  public final DexMethod metafactoryMethod =
      createMethod(
          metafactoryType,
          createProto(
              callSiteType,
              lookupType,
              stringType,
              methodTypeType,
              methodTypeType,
              methodHandleType,
              methodTypeType),
          createString(METAFACTORY_METHOD_NAME));

  public final DexMethod metafactoryAltMethod =
      createMethod(
          metafactoryType,
          createProto(callSiteType, lookupType, stringType, methodTypeType, objectArrayType),
          createString(METAFACTORY_ALT_METHOD_NAME));

  public final DexMethod deserializeLambdaMethod =
      createMethod(objectType, deserializeLambdaMethodProto, deserializeLambdaMethodName);

  public Map<DexMethod, int[]> libraryMethodsNonNullParamOrThrow =
      buildLibraryMethodsNonNullParamOrThrow();

  private Map<DexMethod, int[]> buildLibraryMethodsNonNullParamOrThrow() {
    ImmutableMap.Builder<DexMethod, int[]> builder = ImmutableMap.builder();
    for (DexMethod requireNonNullMethod : objectsMethods.requireNonNullMethods()) {
      builder.put(requireNonNullMethod, new int[] {0});
    }
    return builder.build();
  }

  public Set<DexMethod> libraryMethodsReturningReceiver =
      ImmutableSet.<DexMethod>builder()
          .addAll(stringBufferMethods.appendMethods)
          .addAll(stringBuilderMethods.appendMethods)
          .build();

  // Library methods listed here are based on their original implementations. That is, we assume
  // these cannot be overridden.
  public final Set<DexMethod> libraryMethodsReturningNonNull =
      ImmutableSet.<DexMethod>builder()
          .add(
              classMethods.getName,
              classMethods.getSimpleName,
              classMethods.forName,
              objectMembers.getClass,
              objectsMethods.requireNonNull,
              objectsMethods.requireNonNullWithMessage,
              objectsMethods.requireNonNullWithMessageSupplier,
              stringBuilderMethods.toString,
              stringMembers.format,
              stringMembers.substring,
              stringMembers.substringWithEndIndex,
              stringMembers.concat,
              stringMembers.formatWithLocale,
              stringMembers.valueOfObject,
              atomicReferenceUpdaterMethods.newUpdater,
              atomicIntUpdaterMethods.newUpdater,
              atomicLongUpdaterMethods.newUpdater)
          .addAll(javaUtilArraysMethods.copyOfMethods)
          .addAll(boxedValueOfMethods())
          .addAll(stringBufferMethods.appendMethods)
          .addAll(stringBuilderMethods.appendMethods)
          .build();

  // TODO(b/119596718): More idempotent methods? Any singleton accessors? E.g.,
  // java.util.Calendar#getInstance(...) // 4 variants
  // java.util.Locale#getDefault() // returns JVM default locale.
  // android.os.Looper#myLooper() // returns the associated Looper instance.
  // Note that this set is used for canonicalization of method invocations, together with a set of
  // library methods that do not have side effects.
  public Set<DexMethod> libraryMethodsWithReturnValueDependingOnlyOnArguments =
      ImmutableSet.<DexMethod>builder()
          .addAll(boxedValueOfMethods())
          .build();

  public Set<DexType> libraryTypesAssumedToBePresent =
      ImmutableSet.<DexType>builder()
          .add(
              androidAppActivity,
              androidOsHandlerType,
              callableType,
              enumType,
              javaIoEOFExceptionType,
              javaIoFileNotFoundExceptionType,
              javaIoInterruptedIOExceptionType,
              javaIoInvalidObjectExceptionType,
              javaIoIOExceptionType,
              javaIoNotSerializableExceptionType,
              javaIoUnsupportedEncodingExceptionType,
              javaLangAbstractMethodErrorType,
              javaLangArithmeticExceptionType,
              javaLangArrayIndexOutOfBoundsExceptionType,
              javaLangAssertionErrorType,
              javaLangClassCastExceptionType,
              javaLangClassNotFoundExceptionType,
              javaLangErrorType,
              javaLangExceptionInInitializerErrorType,
              javaLangIllegalAccessErrorType,
              javaLangIllegalArgumentExceptionType,
              javaLangIllegalMonitorStateExceptionType,
              javaLangIllegalStateExceptionType,
              javaLangIncompatibleClassChangeErrorType,
              javaLangIndexOutOfBoundsExceptionType,
              javaLangInterruptedExceptionType,
              javaLangNoClassDefFoundErrorType,
              javaLangNoSuchFieldErrorType,
              javaLangNoSuchMethodErrorType,
              javaLangNoSuchMethodExceptionType,
              javaLangNullPointerExceptionType,
              javaLangNumberFormatExceptionType,
              javaLangOutOfMemoryErrorType,
              // javaLangReflectiveOperationExceptionType, // Added in API 19.
              javaLangRuntimeExceptionType,
              javaLangSecurityExceptionType,
              javaLangUnsatisfiedLinkErrorType,
              javaLangUnsupportedOperationExceptionType,
              javaNioBufferOverflowExceptionType,
              // javaNioFileFileSystemLoopExceptionType, // Added in API 26.
              javaNioReadOnlyBufferExceptionType,
              javaTextParseExceptionType,
              javaUtilConcurrentCancellationExceptionType,
              javaUtilConcurrentExecutionExceptionType,
              javaUtilConcurrentRejectedExecutionExceptionType,
              javaUtilConcurrentTimeUnitType,
              javaUtilConcurrentTimeoutExceptionType,
              javaUtilConcurrentModificationExceptionType,
              javaUtilNoSuchElementExceptionType,
              objectType,
              stringBufferType,
              stringBuilderType,
              stringType)
          .addAll(primitiveToBoxed.values())
          .build();

  public Set<DexType> libraryClassesWithoutStaticInitialization =
      ImmutableSet.of(
          boxedBooleanType,
          boxedByteType,
          boxedCharType,
          boxedDoubleType,
          boxedFloatType,
          boxedIntType,
          boxedLongType,
          boxedNumberType,
          boxedShortType,
          boxedVoidType,
          enumType,
          javaIoEOFExceptionType,
          javaIoFileNotFoundExceptionType,
          javaIoInterruptedIOExceptionType,
          javaIoInvalidObjectExceptionType,
          javaIoIOExceptionType,
          javaIoNotSerializableExceptionType,
          javaIoUnsupportedEncodingExceptionType,
          javaLangAbstractMethodErrorType,
          javaLangArithmeticExceptionType,
          javaLangArrayIndexOutOfBoundsExceptionType,
          javaLangAssertionErrorType,
          javaLangClassCastExceptionType,
          javaLangClassNotFoundExceptionType,
          javaLangErrorType,
          javaLangExceptionInInitializerErrorType,
          javaLangIllegalAccessErrorType,
          javaLangIllegalArgumentExceptionType,
          javaLangIllegalMonitorStateExceptionType,
          javaLangIllegalStateExceptionType,
          javaLangIncompatibleClassChangeErrorType,
          javaLangIndexOutOfBoundsExceptionType,
          javaLangInterruptedExceptionType,
          javaLangNoClassDefFoundErrorType,
          javaLangNoSuchFieldErrorType,
          javaLangNoSuchMethodErrorType,
          javaLangNoSuchMethodExceptionType,
          javaLangNullPointerExceptionType,
          javaLangNumberFormatExceptionType,
          javaLangOutOfMemoryErrorType,
          javaLangReflectiveOperationExceptionType,
          javaLangRuntimeExceptionType,
          javaLangSecurityExceptionType,
          javaLangSystemType,
          javaLangUnsatisfiedLinkErrorType,
          javaLangUnsupportedOperationExceptionType,
          javaNioBufferOverflowExceptionType,
          // javaNioFileFileSystemLoopExceptionType, // Added in API 26.
          javaNioReadOnlyBufferExceptionType,
          javaTextParseExceptionType,
          javaUtilConcurrentCancellationExceptionType,
          javaUtilConcurrentExecutionExceptionType,
          javaUtilConcurrentRejectedExecutionExceptionType,
          javaUtilConcurrentTimeUnitType,
          javaUtilConcurrentTimeoutExceptionType,
          javaUtilConcurrentModificationExceptionType,
          javaUtilNoSuchElementExceptionType,
          objectType,
          stringBufferType,
          stringBuilderType,
          stringType);

  private boolean skipNameValidationForTesting = false;

  public void setSkipNameValidationForTesting(boolean skipNameValidationForTesting) {
    this.skipNameValidationForTesting = skipNameValidationForTesting;
  }

  public boolean getSkipNameValidationForTesting() {
    return skipNameValidationForTesting;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isLambdaMetafactoryMethod(DexMethod dexMethod) {
    return dexMethod == metafactoryMethod || dexMethod == metafactoryAltMethod;
  }

  public abstract static class LibraryMembers {

    public void forEachFinalField(Consumer<DexField> consumer) {}
  }

  public abstract static class BoxedPrimitiveMembers extends LibraryMembers {

    public abstract DexField getTypeField();
  }

  public class AndroidGraphicsColorMembers extends LibraryMembers {

    public final DexMethod alphaInt =
        createMethod(androidGraphicsColorType, createProto(intType, intType), "alpha");
    public final DexMethod alphaLong =
        createMethod(androidGraphicsColorType, createProto(floatType, longType), "alpha");
    public final DexMethod argbInt =
        createMethod(
            androidGraphicsColorType,
            createProto(intType, intType, intType, intType, intType),
            "argb");
    public final DexMethod argbFloat =
        createMethod(
            androidGraphicsColorType,
            createProto(intType, floatType, floatType, floatType, floatType),
            "argb");
    public final DexMethod blueInt =
        createMethod(androidGraphicsColorType, createProto(intType, intType), "blue");
    public final DexMethod blueLong =
        createMethod(androidGraphicsColorType, createProto(floatType, longType), "blue");
    public final DexMethod greenInt =
        createMethod(androidGraphicsColorType, createProto(intType, intType), "green");
    public final DexMethod greenLong =
        createMethod(androidGraphicsColorType, createProto(floatType, longType), "green");
    public final DexMethod isSrgb =
        createMethod(androidGraphicsColorType, createProto(booleanType, longType), "isSrgb");
    public final DexMethod isWideGamut =
        createMethod(androidGraphicsColorType, createProto(booleanType, longType), "isWideGamut");
    public final DexMethod luminanceInt =
        createMethod(androidGraphicsColorType, createProto(floatType, intType), "luminance");
    public final DexMethod luminanceLong =
        createMethod(androidGraphicsColorType, createProto(floatType, longType), "luminance");
    public final DexMethod packInt =
        createMethod(androidGraphicsColorType, createProto(longType, intType), "pack");
    public final DexMethod packFloat4 =
        createMethod(
            androidGraphicsColorType,
            createProto(longType, floatType, floatType, floatType, floatType),
            "pack");
    public final DexMethod packFloat3 =
        createMethod(
            androidGraphicsColorType,
            createProto(longType, floatType, floatType, floatType),
            "pack");
    public final DexMethod parseColor =
        createMethod(androidGraphicsColorType, createProto(intType, stringType), "parseColor");
    public final DexMethod redInt =
        createMethod(androidGraphicsColorType, createProto(intType, intType), "red");
    public final DexMethod redLong =
        createMethod(androidGraphicsColorType, createProto(floatType, longType), "red");
    public final DexMethod rgbInt =
        createMethod(
            androidGraphicsColorType, createProto(intType, intType, intType, intType), "rgb");
    public final DexMethod rgbFloat =
        createMethod(
            androidGraphicsColorType, createProto(intType, floatType, floatType, floatType), "rgb");
    public final DexMethod toArgb =
        createMethod(androidGraphicsColorType, createProto(intType, longType), "toArgb");

    private AndroidGraphicsColorMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(alphaInt);
      consumer.accept(alphaLong);
      consumer.accept(argbInt);
      consumer.accept(argbFloat);
      consumer.accept(blueInt);
      consumer.accept(blueLong);
      consumer.accept(greenInt);
      consumer.accept(greenLong);
      consumer.accept(luminanceInt);
      consumer.accept(packInt);
      consumer.accept(packFloat4);
      consumer.accept(packFloat3);
      consumer.accept(redInt);
      consumer.accept(redLong);
      consumer.accept(rgbInt);
      consumer.accept(rgbFloat);
    }
  }

  public class AndroidGraphicsImageFormatMembers extends LibraryMembers {

    public final DexMethod getBitsPerPixel =
        createMethod(
            androidGraphicsImageFormatType, createProto(intType, intType), "getBitsPerPixel");

    private AndroidGraphicsImageFormatMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(getBitsPerPixel);
    }
  }

  public class AndroidNetUriMembers extends LibraryMembers {

    public final DexMethod encode =
        createMethod(androidNetUriType, createProto(stringType, stringType), "encode");
    public final DexMethod encodeWithAllow =
        createMethod(androidNetUriType, createProto(stringType, stringType, stringType), "encode");

    private AndroidNetUriMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(encode);
      consumer.accept(encodeWithAllow);
    }
  }

  public class AndroidTextTextUtilsMembers extends LibraryMembers {

    public final DexMethod equals =
        createMethod(
            androidTextTextUtilsType,
            createProto(booleanType, charSequenceType, charSequenceType),
            "equals");
    public final DexMethod isEmpty =
        createMethod(
            androidTextTextUtilsType, createProto(booleanType, charSequenceType), "isEmpty");

    private AndroidTextTextUtilsMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {}
  }

  public class AndroidViewViewMeasureSpecMembers extends LibraryMembers {

    public final DexMethod makeMeasureSpec =
        createMethod(
            androidViewViewMeasureSpecType,
            createProto(intType, intType, intType),
            "makeMeasureSpec");

    private AndroidViewViewMeasureSpecMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(makeMeasureSpec);
    }
  }

  public class JavaUtilRegexPatternMembers extends LibraryMembers {

    public final DexMethod quote =
        createMethod(javaUtilRegexPatternType, createProto(stringType, stringType), "quote");

    private JavaUtilRegexPatternMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(quote);
    }
  }

  public class AndroidOsBuildMembers extends LibraryMembers {

    public final DexField BOOTLOADER = createField(androidOsBuildType, stringType, "BOOTLOADER");
    public final DexField BRAND = createField(androidOsBuildType, stringType, "BRAND");
    public final DexField CPU_ABI = createField(androidOsBuildType, stringType, "CPU_ABI");
    public final DexField CPU_ABI2 = createField(androidOsBuildType, stringType, "CPU_ABI2");
    public final DexField DEVICE = createField(androidOsBuildType, stringType, "DEVICE");
    public final DexField DISPLAY = createField(androidOsBuildType, stringType, "DISPLAY");
    public final DexField FINGERPRINT = createField(androidOsBuildType, stringType, "FINGERPRINT");
    public final DexField HARDWARE = createField(androidOsBuildType, stringType, "HARDWARE");
    public final DexField MANUFACTURER =
        createField(androidOsBuildType, stringType, "MANUFACTURER");
    public final DexField MODEL = createField(androidOsBuildType, stringType, "MODEL");
    public final DexField PRODUCT = createField(androidOsBuildType, stringType, "PRODUCT");
    public final DexField SERIAL = createField(androidOsBuildType, stringType, "SERIAL");
    public final DexField SUPPORTED_32_BIT_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_32_BIT_ABIS");
    public final DexField SUPPORTED_64_BIT_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_64_BIT_ABIS");
    public final DexField SUPPORTED_ABIS =
        createField(androidOsBuildType, stringArrayType, "SUPPORTED_ABIS");
    public final DexField TIME = createField(androidOsBuildType, longType, "TIME");
    public final DexField TYPE = createField(androidOsBuildType, stringType, "TYPE");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(BOOTLOADER);
      consumer.accept(BRAND);
      consumer.accept(CPU_ABI);
      consumer.accept(CPU_ABI2);
      consumer.accept(DEVICE);
      consumer.accept(DISPLAY);
      consumer.accept(FINGERPRINT);
      consumer.accept(HARDWARE);
      consumer.accept(MANUFACTURER);
      consumer.accept(MODEL);
      consumer.accept(PRODUCT);
      consumer.accept(SERIAL);
      consumer.accept(SUPPORTED_32_BIT_ABIS);
      consumer.accept(SUPPORTED_64_BIT_ABIS);
      consumer.accept(SUPPORTED_ABIS);
      consumer.accept(TIME);
      consumer.accept(TYPE);
    }
  }

  public class AndroidOsBuildVersionMembers extends LibraryMembers {

    public final DexField CODENAME = createField(androidOsBuildVersionType, stringType, "CODENAME");
    public final DexField RELEASE = createField(androidOsBuildVersionType, stringType, "RELEASE");
    public final DexField SDK = createField(androidOsBuildVersionType, stringType, "SDK");
    public final DexField SDK_INT = createField(androidOsBuildVersionType, intType, "SDK_INT");
    public final DexField SDK_INT_FULL =
        createField(androidOsBuildVersionType, intType, "SDK_INT_FULL");
    public final DexField SECURITY_PATCH =
        createField(androidOsBuildVersionType, stringType, "SECURITY_PATCH");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CODENAME);
      consumer.accept(RELEASE);
      consumer.accept(SDK);
      consumer.accept(SDK_INT);
      consumer.accept(SECURITY_PATCH);
    }
  }

  public class AndroidOsBundleMembers extends LibraryMembers {

    public final DexField CREATOR =
        createField(androidOsBundleType, androidOsParcelableCreatorType, "CREATOR");
    public final DexField EMPTY = createField(androidOsBundleType, androidOsBundleType, "EMPTY");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CREATOR);
      consumer.accept(EMPTY);
    }
  }

  public class AndroidSystemOsConstantsMembers extends LibraryMembers {

    public final DexField S_IRUSR = createField(androidSystemOsConstantsType, intType, "S_IRUSR");
    public final DexField S_IXUSR = createField(androidSystemOsConstantsType, intType, "S_IXUSR");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(S_IRUSR);
      consumer.accept(S_IXUSR);
    }
  }

  public class AndroidViewViewMembers extends LibraryMembers {

    public final DexField TRANSLATION_Z =
        createField(androidViewViewType, androidUtilPropertyType, "TRANSLATION_Z");
    public final DexField EMPTY_STATE_SET =
        createField(androidViewViewType, intArrayType, "EMPTY_STATE_SET");
    public final DexField ENABLED_STATE_SET =
        createField(androidViewViewType, intArrayType, "ENABLED_STATE_SET");
    public final DexField PRESSED_ENABLED_STATE_SET =
        createField(androidViewViewType, intArrayType, "PRESSED_ENABLED_STATE_SET");
    public final DexField SELECTED_STATE_SET =
        createField(androidViewViewType, intArrayType, "SELECTED_STATE_SET");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TRANSLATION_Z);
      consumer.accept(EMPTY_STATE_SET);
      consumer.accept(ENABLED_STATE_SET);
      consumer.accept(PRESSED_ENABLED_STATE_SET);
      consumer.accept(SELECTED_STATE_SET);
    }
  }

  // android.util.SparseArray
  public class AndroidUtilSparseArrayMembers extends LibraryMembers {
    public final DexMethod put =
        createMethod(androidUtilSparseArrayType, createProto(voidType, intType, objectType), "put");
    public final DexMethod set =
        createMethod(
            androidUtilSparseArrayType, createProto(voidType, intType, objectType), setString);
  }

  // android.content.res.TypedArray
  public class AndroidContentResTypedArrayMembers extends LibraryMembers {
    public final DexMethod recycle =
        createMethod(androidContentResTypedArrayType, createProto(voidType), "recycle");
    public final DexMethod close =
        createMethod(androidContentResTypedArrayType, createProto(voidType), closeMethodName);
  }

  // android.content.ContentProviderClient
  public class AndroidContentContentProviderClientMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidContentContentProviderClientType, createProto(booleanType), "release");
    public final DexMethod close =
        createMethod(
            androidContentContentProviderClientType, createProto(voidType), closeMethodName);
  }

  // android.drm.DrmManagerClient
  public class AndroidDrmDrmManagerClientMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidDrmDrmManagerClientType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(androidDrmDrmManagerClientType, createProto(voidType), closeMethodName);
  }

  // android.media.MediaDrm
  public class AndroidMediaMediaDrmMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidMediaMediaDrmType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(androidMediaMediaDrmType, createProto(voidType), closeMethodName);
  }

  // android.media.MediaMetadataRetriever
  public class AndroidMediaMetadataRetrieverMembers extends LibraryMembers {
    public final DexMethod release =
        createMethod(androidMediaMediaMetadataRetrieverType, createProto(voidType), "release");
    public final DexMethod close =
        createMethod(
            androidMediaMediaMetadataRetrieverType, createProto(voidType), closeMethodName);
  }

  public class BooleanMembers extends BoxedPrimitiveMembers {

    public final DexField FALSE = createField(boxedBooleanType, boxedBooleanType, "FALSE");
    public final DexField TRUE = createField(boxedBooleanType, boxedBooleanType, "TRUE");
    public final DexField TYPE = createField(boxedBooleanType, classType, "TYPE");

    public final DexMethod booleanValue =
        createMethod(boxedBooleanType, createProto(booleanType), "booleanValue");
    public final DexMethod compare =
        createMethod(boxedBooleanType, createProto(intType, booleanType, booleanType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedBooleanType, createProto(intType, boxedBooleanType), "compareTo");
    public final DexMethod equals =
        createMethod(boxedBooleanType, createProto(booleanType, objectType), "equals");
    public final DexMethod getBoolean =
        createMethod(boxedBooleanType, createProto(booleanType, stringType), "getBoolean");
    public final DexMethod hashCode =
        createMethod(boxedBooleanType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedBooleanType, createProto(intType, booleanType), "hashCode");
    public final DexMethod logicalAnd =
        createMethod(
            boxedBooleanType, createProto(booleanType, booleanType, booleanType), "logicalAnd");
    public final DexMethod logicalOr =
        createMethod(
            boxedBooleanType, createProto(booleanType, booleanType, booleanType), "logicalOr");
    public final DexMethod logicalXor =
        createMethod(
            boxedBooleanType, createProto(booleanType, booleanType, booleanType), "logicalXor");
    public final DexMethod parseBoolean =
        createMethod(boxedBooleanType, createProto(booleanType, stringType), "parseBoolean");
    public final DexMethod toString =
        createMethod(boxedBooleanType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedBooleanType, createProto(stringType, booleanType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedBooleanType, createProto(boxedBooleanType, booleanType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedBooleanType, createProto(boxedBooleanType, stringType), "valueOf");

    private BooleanMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(FALSE);
      consumer.accept(TRUE);
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(booleanValue);
      consumer.accept(compare);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(logicalAnd);
      consumer.accept(logicalOr);
      consumer.accept(logicalXor);
      consumer.accept(parseBoolean);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class ByteMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedByteType, classType, "TYPE");

    public final DexMethod byteValue =
        createMethod(boxedByteType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedByteType, createProto(intType, byteType, byteType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedByteType, createProto(intType, boxedByteType), "compareTo");
    public final DexMethod decode =
        createMethod(boxedByteType, createProto(boxedByteType, stringType), "decode");
    public final DexMethod doubleValue =
        createMethod(boxedByteType, createProto(doubleType), "doubleValue");
    public final DexMethod equals =
        createMethod(boxedByteType, createProto(booleanType, objectType), "equals");
    public final DexMethod floatValue =
        createMethod(boxedByteType, createProto(floatType), "floatValue");
    public final DexMethod hashCode = createMethod(boxedByteType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedByteType, createProto(intType, byteType), "hashCode");
    public final DexMethod intValue = createMethod(boxedByteType, createProto(intType), "intValue");
    public final DexMethod longValue =
        createMethod(boxedByteType, createProto(longType), "longValue");
    public final DexMethod parseByte =
        createMethod(boxedByteType, createProto(byteType, stringType), "parseByte");
    public final DexMethod parseByteWithRadix =
        createMethod(boxedByteType, createProto(byteType, stringType, intType), "parseByte");
    public final DexMethod shortValue =
        createMethod(boxedByteType, createProto(shortType), "shortValue");
    public final DexMethod toString =
        createMethod(boxedByteType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedByteType, createProto(stringType, byteType), "toString");
    public final DexMethod toUnsignedInt =
        createMethod(boxedByteType, createProto(intType, byteType), "toUnsignedInt");
    public final DexMethod toUnsignedLong =
        createMethod(boxedByteType, createProto(longType, byteType), "toUnsignedLong");
    public final DexMethod valueOf =
        createMethod(boxedByteType, createProto(boxedByteType, byteType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedByteType, createProto(boxedByteType, stringType), "valueOf");
    public final DexMethod valueOfStringWithRadix =
        createMethod(boxedByteType, createProto(boxedByteType, stringType, intType), "valueOf");

    private ByteMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(doubleValue);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(intValue);
      consumer.accept(longValue);
      consumer.accept(shortValue);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(toUnsignedInt);
      consumer.accept(toUnsignedLong);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class CharMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedCharType, classType, "TYPE");

    public final DexMethod charCount =
        createMethod(boxedCharType, createProto(intType, intType), "charCount");
    public final DexMethod charValue =
        createMethod(boxedCharType, createProto(charType), "charValue");
    public final DexMethod codePointAt =
        createMethod(boxedCharType, createProto(intType, charSequenceType, intType), "codePointAt");
    public final DexMethod codePointBefore =
        createMethod(
            boxedCharType, createProto(intType, charSequenceType, intType), "codePointBefore");
    public final DexMethod codePointCount =
        createMethod(
            boxedCharType,
            createProto(intType, charSequenceType, intType, intType),
            "codePointCount");
    public final DexMethod compare =
        createMethod(boxedCharType, createProto(intType, charType, charType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedCharType, createProto(intType, boxedCharType), "compareTo");
    public final DexMethod digitWithChar =
        createMethod(boxedCharType, createProto(intType, charType, intType), "digit");
    public final DexMethod digitWithInt =
        createMethod(boxedCharType, createProto(intType, intType, intType), "digit");
    public final DexMethod equals =
        createMethod(boxedCharType, createProto(booleanType, objectType), "equals");
    public final DexMethod forDigit =
        createMethod(boxedCharType, createProto(charType, intType, intType), "forDigit");
    public final DexMethod getDirectionalityWithChar =
        createMethod(boxedCharType, createProto(byteType, charType), "getDirectionality");
    public final DexMethod getDirectionalityWithInt =
        createMethod(boxedCharType, createProto(byteType, intType), "getDirectionality");
    public final DexMethod getName =
        createMethod(boxedCharType, createProto(stringType, intType), "getName");
    public final DexMethod getNumericValueWithChar =
        createMethod(boxedCharType, createProto(intType, charType), "getNumericValue");
    public final DexMethod getNumericValueWithInt =
        createMethod(boxedCharType, createProto(intType, intType), "getNumericValue");
    public final DexMethod getTypeWithChar =
        createMethod(boxedCharType, createProto(intType, charType), "getType");
    public final DexMethod getTypeWithInt =
        createMethod(boxedCharType, createProto(intType, intType), "getType");
    public final DexMethod hashCode = createMethod(boxedCharType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedCharType, createProto(intType, charType), "hashCode");
    public final DexMethod highSurrogate =
        createMethod(boxedCharType, createProto(charType, intType), "highSurrogate");
    public final DexMethod isAlphabetic =
        createMethod(boxedCharType, createProto(booleanType, intType), "isAlphabetic");
    public final DexMethod isBmpCodePoint =
        createMethod(boxedCharType, createProto(booleanType, intType), "isBmpCodePoint");
    public final DexMethod isDefinedWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isDefined");
    public final DexMethod isDefinedWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isDefined");
    public final DexMethod isDigitWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isDigit");
    public final DexMethod isDigitWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isDigit");
    public final DexMethod isHighSurrogate =
        createMethod(boxedCharType, createProto(booleanType, charType), "isHighSurrogate");
    public final DexMethod isIdentifierIgnorableWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isIdentifierIgnorable");
    public final DexMethod isIdentifierIgnorableWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isIdentifierIgnorable");
    public final DexMethod isIdeographic =
        createMethod(boxedCharType, createProto(booleanType, intType), "isIdeographic");
    public final DexMethod isISOControlWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isISOControl");
    public final DexMethod isISOControlWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isISOControl");
    public final DexMethod isJavaIdentifierPartWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isJavaIdentifierPart");
    public final DexMethod isJavaIdentifierPartWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isJavaIdentifierPart");
    public final DexMethod isJavaIdentifierStartWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isJavaIdentifierStart");
    public final DexMethod isJavaIdentifierStartWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isJavaIdentifierStart");
    public final DexMethod isJavaLetter =
        createMethod(boxedCharType, createProto(booleanType, charType), "isJavaLetter");
    public final DexMethod isJavaLetterOrDigit =
        createMethod(boxedCharType, createProto(booleanType, charType), "isJavaLetterOrDigit");
    public final DexMethod isLetterWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isLetter");
    public final DexMethod isLetterWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isLetter");
    public final DexMethod isLetterOrDigitWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isLetterOrDigit");
    public final DexMethod isLetterOrDigitWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isLetterOrDigit");
    public final DexMethod isLowerCaseWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isLowerCase");
    public final DexMethod isLowerCaseWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isLowerCase");
    public final DexMethod isLowSurrogate =
        createMethod(boxedCharType, createProto(booleanType, charType), "isLowSurrogate");
    public final DexMethod isMirroredWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isMirrored");
    public final DexMethod isMirroredWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isMirrored");
    public final DexMethod isSpace =
        createMethod(boxedCharType, createProto(booleanType, charType), "isSpace");
    public final DexMethod isSpaceCharWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isSpaceChar");
    public final DexMethod isSpaceCharWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isSpaceChar");
    public final DexMethod isSupplementaryCodePoint =
        createMethod(boxedCharType, createProto(booleanType, intType), "isSupplementaryCodePoint");
    public final DexMethod isSurrogate =
        createMethod(boxedCharType, createProto(booleanType, charType), "isSurrogate");
    public final DexMethod isSurrogatePair =
        createMethod(
            boxedCharType, createProto(booleanType, charType, charType), "isSurrogatePair");
    public final DexMethod isTitleCaseWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isTitleCase");
    public final DexMethod isTitleCaseWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isTitleCase");
    public final DexMethod isUnicodeIdentifierPartWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isUnicodeIdentifierPart");
    public final DexMethod isUnicodeIdentifierPartWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isUnicodeIdentifierPart");
    public final DexMethod isUnicodeIdentifierStartWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isUnicodeIdentifierStart");
    public final DexMethod isUnicodeIdentifierStartWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isUnicodeIdentifierStart");
    public final DexMethod isUpperCaseWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isUpperCase");
    public final DexMethod isUpperCaseWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isUpperCase");
    public final DexMethod isValidCodePoint =
        createMethod(boxedCharType, createProto(booleanType, intType), "isValidCodePoint");
    public final DexMethod isWhitespaceWithChar =
        createMethod(boxedCharType, createProto(booleanType, charType), "isWhitespace");
    public final DexMethod isWhitespaceWithInt =
        createMethod(boxedCharType, createProto(booleanType, intType), "isWhitespace");
    public final DexMethod lowSurrogate =
        createMethod(boxedCharType, createProto(charType, intType), "lowSurrogate");
    public final DexMethod offsetByCodePoints =
        createMethod(
            boxedCharType,
            createProto(intType, charSequenceType, intType, intType),
            "offsetByCodePoints");
    public final DexMethod reverseBytes =
        createMethod(boxedCharType, createProto(charType, charType), "reverseBytes");
    public final DexMethod toCodePoint =
        createMethod(boxedCharType, createProto(intType, charType, charType), "toCodePoint");
    public final DexMethod toLowerCaseWithChar =
        createMethod(boxedCharType, createProto(charType, charType), "toLowerCase");
    public final DexMethod toLowerCaseWithInt =
        createMethod(boxedCharType, createProto(intType, intType), "toLowerCase");
    public final DexMethod toString =
        createMethod(boxedCharType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedCharType, createProto(stringType, charType), "toString");
    public final DexMethod toTitleCaseWithChar =
        createMethod(boxedCharType, createProto(charType, charType), "toTitleCase");
    public final DexMethod toTitleCaseWithInt =
        createMethod(boxedCharType, createProto(intType, intType), "toTitleCase");
    public final DexMethod toUpperCaseWithChar =
        createMethod(boxedCharType, createProto(charType, charType), "toUpperCase");
    public final DexMethod toUpperCaseWithInt =
        createMethod(boxedCharType, createProto(intType, intType), "toUpperCase");
    public final DexMethod valueOf =
        createMethod(boxedCharType, createProto(boxedCharType, charType), "valueOf");

    private CharMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(charCount);
      consumer.accept(charValue);
      consumer.accept(compare);
      consumer.accept(digitWithChar);
      consumer.accept(digitWithInt);
      consumer.accept(forDigit);
      consumer.accept(getDirectionalityWithChar);
      consumer.accept(getDirectionalityWithInt);
      consumer.accept(getName);
      consumer.accept(getNumericValueWithChar);
      consumer.accept(getNumericValueWithInt);
      consumer.accept(getTypeWithChar);
      consumer.accept(getTypeWithInt);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(highSurrogate);
      consumer.accept(isAlphabetic);
      consumer.accept(isBmpCodePoint);
      consumer.accept(isDefinedWithChar);
      consumer.accept(isDefinedWithInt);
      consumer.accept(isDigitWithChar);
      consumer.accept(isDigitWithInt);
      consumer.accept(isHighSurrogate);
      consumer.accept(isIdentifierIgnorableWithChar);
      consumer.accept(isIdentifierIgnorableWithInt);
      consumer.accept(isIdeographic);
      consumer.accept(isISOControlWithChar);
      consumer.accept(isISOControlWithInt);
      consumer.accept(isJavaIdentifierPartWithChar);
      consumer.accept(isJavaIdentifierPartWithInt);
      consumer.accept(isJavaIdentifierStartWithChar);
      consumer.accept(isJavaIdentifierStartWithInt);
      consumer.accept(isJavaLetter);
      consumer.accept(isJavaLetterOrDigit);
      consumer.accept(isLetterWithChar);
      consumer.accept(isLetterWithInt);
      consumer.accept(isLetterOrDigitWithChar);
      consumer.accept(isLetterOrDigitWithInt);
      consumer.accept(isLowerCaseWithChar);
      consumer.accept(isLowerCaseWithInt);
      consumer.accept(isLowSurrogate);
      consumer.accept(isMirroredWithChar);
      consumer.accept(isMirroredWithInt);
      consumer.accept(isSpace);
      consumer.accept(isSpaceCharWithChar);
      consumer.accept(isSpaceCharWithInt);
      consumer.accept(isSupplementaryCodePoint);
      consumer.accept(isSurrogate);
      consumer.accept(isSurrogatePair);
      consumer.accept(isTitleCaseWithChar);
      consumer.accept(isTitleCaseWithInt);
      consumer.accept(isUnicodeIdentifierPartWithChar);
      consumer.accept(isUnicodeIdentifierPartWithInt);
      consumer.accept(isUnicodeIdentifierStartWithChar);
      consumer.accept(isUnicodeIdentifierStartWithInt);
      consumer.accept(isUpperCaseWithChar);
      consumer.accept(isUpperCaseWithInt);
      consumer.accept(isValidCodePoint);
      consumer.accept(isWhitespaceWithChar);
      consumer.accept(isWhitespaceWithInt);
      consumer.accept(lowSurrogate);
      consumer.accept(reverseBytes);
      consumer.accept(toCodePoint);
      consumer.accept(toLowerCaseWithChar);
      consumer.accept(toLowerCaseWithInt);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(toTitleCaseWithChar);
      consumer.accept(toTitleCaseWithInt);
      consumer.accept(toUpperCaseWithChar);
      consumer.accept(toUpperCaseWithInt);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class FloatMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedFloatType, classType, "TYPE");

    public final DexMethod byteValue =
        createMethod(boxedFloatType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedFloatType, createProto(intType, floatType, floatType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedFloatType, createProto(intType, boxedFloatType), "compareTo");
    public final DexMethod doubleValue =
        createMethod(boxedFloatType, createProto(doubleType), "doubleValue");
    public final DexMethod floatToIntBits =
        createMethod(boxedFloatType, createProto(intType, floatType), "floatToIntBits");
    public final DexMethod floatToRawIntBits =
        createMethod(boxedFloatType, createProto(intType, floatType), "floatToRawIntBits");
    public final DexMethod floatValue =
        createMethod(boxedFloatType, createProto(floatType), "floatValue");
    public final DexMethod hashCode =
        createMethod(boxedFloatType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedFloatType, createProto(intType, floatType), "hashCode");
    public final DexMethod intBitsToFloat =
        createMethod(boxedFloatType, createProto(floatType, intType), "intBitsToFloat");
    public final DexMethod intValue =
        createMethod(boxedFloatType, createProto(intType), "intValue");
    public final DexMethod isFinite =
        createMethod(boxedFloatType, createProto(booleanType, floatType), "isFinite");
    public final DexMethod isInfinite =
        createMethod(boxedFloatType, createProto(booleanType), "isInfinite");
    public final DexMethod staticIsInfinite =
        createMethod(boxedFloatType, createProto(booleanType, floatType), "isInfinite");
    public final DexMethod isNaN = createMethod(boxedFloatType, createProto(booleanType), "isNaN");
    public final DexMethod staticIsNaN =
        createMethod(boxedFloatType, createProto(booleanType, floatType), "isNaN");
    public final DexMethod longValue =
        createMethod(boxedFloatType, createProto(longType), "longValue");
    public final DexMethod max =
        createMethod(boxedFloatType, createProto(floatType, floatType, floatType), "max");
    public final DexMethod min =
        createMethod(boxedFloatType, createProto(floatType, floatType, floatType), "min");
    public final DexMethod parseFloat =
        createMethod(boxedFloatType, createProto(floatType, stringType), "parseFloat");
    public final DexMethod shortValue =
        createMethod(boxedFloatType, createProto(shortType), "shortValue");
    public final DexMethod sum =
        createMethod(boxedFloatType, createProto(floatType, floatType, floatType), "sum");
    public final DexMethod toHexString =
        createMethod(boxedFloatType, createProto(stringType, floatType), "toHexString");
    public final DexMethod toString =
        createMethod(boxedFloatType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedFloatType, createProto(stringType, floatType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedFloatType, createProto(boxedFloatType, floatType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedFloatType, createProto(boxedFloatType, stringType), "valueOf");

    private FloatMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(doubleValue);
      consumer.accept(floatToIntBits);
      consumer.accept(floatToRawIntBits);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(intBitsToFloat);
      consumer.accept(intValue);
      consumer.accept(isFinite);
      consumer.accept(isInfinite);
      consumer.accept(staticIsInfinite);
      consumer.accept(isNaN);
      consumer.accept(staticIsNaN);
      consumer.accept(longValue);
      consumer.accept(min);
      consumer.accept(max);
      consumer.accept(shortValue);
      consumer.accept(sum);
      consumer.accept(toHexString);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class JavaIoFileMembers extends LibraryMembers {

    public final DexField pathSeparator = createField(javaIoFileType, stringType, "pathSeparator");
    public final DexField separator = createField(javaIoFileType, stringType, "separator");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(pathSeparator);
      consumer.accept(separator);
    }
  }

  public class JavaMathBigIntegerMembers extends LibraryMembers {

    public final DexField ONE = createField(javaMathBigIntegerType, javaMathBigIntegerType, "ONE");
    public final DexField ZERO =
        createField(javaMathBigIntegerType, javaMathBigIntegerType, "ZERO");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(ONE);
      consumer.accept(ZERO);
    }
  }

  public class MathMembers extends LibraryMembers {

    public final DexMethod absDouble =
        createMethod(mathType, createProto(doubleType, doubleType), "abs");
    public final DexMethod absFloat =
        createMethod(mathType, createProto(floatType, floatType), "abs");
    public final DexMethod absInt = createMethod(mathType, createProto(intType, intType), "abs");
    public final DexMethod absLong = createMethod(mathType, createProto(longType, longType), "abs");
    public final DexMethod acos =
        createMethod(mathType, createProto(doubleType, doubleType), "acos");
    public final DexMethod addExactInt =
        createMethod(mathType, createProto(intType, intType, intType), "addExact");
    public final DexMethod addExactLong =
        createMethod(mathType, createProto(longType, longType, longType), "addExact");
    public final DexMethod asin =
        createMethod(mathType, createProto(doubleType, doubleType), "asin");
    public final DexMethod atan =
        createMethod(mathType, createProto(doubleType, doubleType), "atan");
    public final DexMethod atan2 =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "atan2");
    public final DexMethod cbrt =
        createMethod(mathType, createProto(doubleType, doubleType), "cbrt");
    public final DexMethod ceil =
        createMethod(mathType, createProto(doubleType, doubleType), "ceil");
    public final DexMethod copySignDouble =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "copySign");
    public final DexMethod copySignFloat =
        createMethod(mathType, createProto(floatType, floatType, floatType), "copySign");
    public final DexMethod cos = createMethod(mathType, createProto(doubleType, doubleType), "cos");
    public final DexMethod cosh =
        createMethod(mathType, createProto(doubleType, doubleType), "cosh");
    public final DexMethod decrementExactInt =
        createMethod(mathType, createProto(intType, intType), "decrementExact");
    public final DexMethod decrementExactLong =
        createMethod(mathType, createProto(longType, longType), "decrementExact");
    public final DexMethod exp = createMethod(mathType, createProto(doubleType, doubleType), "exp");
    public final DexMethod expm1 =
        createMethod(mathType, createProto(doubleType, doubleType), "expm1");
    public final DexMethod floor =
        createMethod(mathType, createProto(doubleType, doubleType), "floor");
    public final DexMethod floorDivInt =
        createMethod(mathType, createProto(intType, intType, intType), "floorDiv");
    public final DexMethod floorDivLong =
        createMethod(mathType, createProto(longType, longType, longType), "floorDiv");
    public final DexMethod floorModInt =
        createMethod(mathType, createProto(intType, intType, intType), "floorMod");
    public final DexMethod floorModLong =
        createMethod(mathType, createProto(longType, longType, longType), "floorMod");
    public final DexMethod getExponentDouble =
        createMethod(mathType, createProto(intType, doubleType), "getExponent");
    public final DexMethod getExponentFloat =
        createMethod(mathType, createProto(intType, floatType), "getExponent");
    public final DexMethod hypot =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "hypot");
    public final DexMethod IEEEremainder =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "IEEEremainder");
    public final DexMethod incrementExactInt =
        createMethod(mathType, createProto(intType, intType), "incrementExact");
    public final DexMethod incrementExactLong =
        createMethod(mathType, createProto(longType, longType), "incrementExact");
    public final DexMethod log = createMethod(mathType, createProto(doubleType, doubleType), "log");
    public final DexMethod log10 =
        createMethod(mathType, createProto(doubleType, doubleType), "log10");
    public final DexMethod log1p =
        createMethod(mathType, createProto(doubleType, doubleType), "log1p");
    public final DexMethod maxDouble =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "max");
    public final DexMethod maxFloat =
        createMethod(mathType, createProto(floatType, floatType, floatType), "max");
    public final DexMethod maxInt =
        createMethod(mathType, createProto(intType, intType, intType), "max");
    public final DexMethod maxLong =
        createMethod(mathType, createProto(longType, longType, longType), "max");
    public final DexMethod minDouble =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "min");
    public final DexMethod minFloat =
        createMethod(mathType, createProto(floatType, floatType, floatType), "min");
    public final DexMethod minInt =
        createMethod(mathType, createProto(intType, intType, intType), "min");
    public final DexMethod minLong =
        createMethod(mathType, createProto(longType, longType, longType), "min");
    public final DexMethod multiplyExactInt =
        createMethod(mathType, createProto(intType, intType, intType), "multiplyExact");
    public final DexMethod multiplyExactLong =
        createMethod(mathType, createProto(longType, longType, longType), "multiplyExact");
    public final DexMethod negateExactInt =
        createMethod(mathType, createProto(intType, intType), "negateExact");
    public final DexMethod negateExactLong =
        createMethod(mathType, createProto(longType, longType), "negateExact");
    public final DexMethod nextAfterDouble =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "nextAfter");
    public final DexMethod nextAfterFloat =
        createMethod(mathType, createProto(floatType, floatType, doubleType), "nextAfter");
    public final DexMethod nextDownDouble =
        createMethod(mathType, createProto(doubleType, doubleType), "nextDown");
    public final DexMethod nextDownFloat =
        createMethod(mathType, createProto(floatType, floatType), "nextDown");
    public final DexMethod nextUpDouble =
        createMethod(mathType, createProto(doubleType, doubleType), "nextUp");
    public final DexMethod nextUpFloat =
        createMethod(mathType, createProto(floatType, floatType), "nextUp");
    public final DexMethod pow =
        createMethod(mathType, createProto(doubleType, doubleType, doubleType), "pow");
    public final DexMethod random = createMethod(mathType, createProto(doubleType), "random");
    public final DexMethod rint =
        createMethod(mathType, createProto(doubleType, doubleType), "rint");
    public final DexMethod roundDouble =
        createMethod(mathType, createProto(longType, doubleType), "round");
    public final DexMethod roundFloat =
        createMethod(mathType, createProto(intType, floatType), "round");
    public final DexMethod scalbDouble =
        createMethod(mathType, createProto(doubleType, doubleType, intType), "scalb");
    public final DexMethod scalbFloat =
        createMethod(mathType, createProto(floatType, floatType, intType), "scalb");
    public final DexMethod signumDouble =
        createMethod(mathType, createProto(doubleType, doubleType), "signum");
    public final DexMethod signumFloat =
        createMethod(mathType, createProto(floatType, floatType), "signum");
    public final DexMethod sin = createMethod(mathType, createProto(doubleType, doubleType), "sin");
    public final DexMethod sinh =
        createMethod(mathType, createProto(doubleType, doubleType), "sinh");
    public final DexMethod sqrt =
        createMethod(mathType, createProto(doubleType, doubleType), "sqrt");
    public final DexMethod subtractExactInt =
        createMethod(mathType, createProto(intType, intType, intType), "subtractExact");
    public final DexMethod subtractExactLong =
        createMethod(mathType, createProto(longType, longType, longType), "subtractExact");
    public final DexMethod tan = createMethod(mathType, createProto(doubleType, doubleType), "tan");
    public final DexMethod tanh =
        createMethod(mathType, createProto(doubleType, doubleType), "tanh");
    public final DexMethod toDegrees =
        createMethod(mathType, createProto(doubleType, doubleType), "toDegrees");
    public final DexMethod toIntExact =
        createMethod(mathType, createProto(intType, longType), "toIntExact");
    public final DexMethod toRadians =
        createMethod(mathType, createProto(doubleType, doubleType), "toRadians");
    public final DexMethod ulpDouble =
        createMethod(mathType, createProto(doubleType, doubleType), "ulp");
    public final DexMethod ulpFloat =
        createMethod(mathType, createProto(floatType, floatType), "ulp");

    private MathMembers() {}

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(absDouble);
      consumer.accept(absFloat);
      consumer.accept(absInt);
      consumer.accept(absLong);
      consumer.accept(acos);
      consumer.accept(asin);
      consumer.accept(atan);
      consumer.accept(atan2);
      consumer.accept(cbrt);
      consumer.accept(ceil);
      consumer.accept(copySignDouble);
      consumer.accept(copySignFloat);
      consumer.accept(cos);
      consumer.accept(cosh);
      consumer.accept(exp);
      consumer.accept(expm1);
      consumer.accept(floor);
      consumer.accept(getExponentDouble);
      consumer.accept(getExponentFloat);
      consumer.accept(hypot);
      consumer.accept(IEEEremainder);
      consumer.accept(log);
      consumer.accept(log10);
      consumer.accept(log1p);
      consumer.accept(maxDouble);
      consumer.accept(maxFloat);
      consumer.accept(maxInt);
      consumer.accept(maxLong);
      consumer.accept(minDouble);
      consumer.accept(minFloat);
      consumer.accept(minInt);
      consumer.accept(minLong);
      consumer.accept(nextAfterDouble);
      consumer.accept(nextAfterFloat);
      consumer.accept(nextDownDouble);
      consumer.accept(nextDownFloat);
      consumer.accept(nextUpDouble);
      consumer.accept(nextUpFloat);
      consumer.accept(pow);
      consumer.accept(rint);
      consumer.accept(roundDouble);
      consumer.accept(roundFloat);
      consumer.accept(scalbDouble);
      consumer.accept(scalbFloat);
      consumer.accept(signumDouble);
      consumer.accept(signumFloat);
      consumer.accept(sin);
      consumer.accept(sinh);
      consumer.accept(sqrt);
      consumer.accept(tan);
      consumer.accept(tanh);
      consumer.accept(toDegrees);
      consumer.accept(toRadians);
      consumer.accept(ulpDouble);
      consumer.accept(ulpFloat);
    }
  }

  public class JavaNioByteOrderMembers extends LibraryMembers {

    public final DexField LITTLE_ENDIAN =
        createField(javaNioByteOrderType, javaNioByteOrderType, "LITTLE_ENDIAN");
    public final DexField BIG_ENDIAN =
        createField(javaNioByteOrderType, javaNioByteOrderType, "BIG_ENDIAN");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(LITTLE_ENDIAN);
      consumer.accept(BIG_ENDIAN);
    }
  }

  public class JavaUtilArraysMethods {

    public final DexMethod asList;
    public final DexMethod hashCode =
        createMethod(arraysType, createProto(intType, objectArrayType), "hashCode");
    public final DexMethod hashCodeIntArray =
        createMethod(arraysType, createProto(intType, intArrayType), "hashCode");
    public final DexMethod equalsObjectArray;
    public final DexMethod equalsByteArray;
    public final Set<DexMethod> copyOfMethods;

    private JavaUtilArraysMethods() {
      asList =
          createMethod(
              arraysType, createProto(javaUtilListType, objectArrayType), createString("asList"));
      equalsObjectArray =
          createMethod(
              arraysType,
              createProto(booleanType, objectArrayType, objectArrayType),
              equalsMethodName);
      equalsByteArray =
          createMethod(
              arraysType, createProto(booleanType, byteArrayType, byteArrayType), equalsMethodName);
      DexString copyOfMethodName = createString("copyOf");
      DexMethod copyOfBoolean =
          createMethod(
              arraysType,
              createProto(booleanArrayType, booleanArrayType, intType),
              copyOfMethodName);
      DexMethod copyOfByte =
          createMethod(
              arraysType, createProto(byteArrayType, byteArrayType, intType), copyOfMethodName);
      DexMethod copyOfChar =
          createMethod(
              arraysType, createProto(charArrayType, charArrayType, intType), copyOfMethodName);
      DexMethod copyOfDouble =
          createMethod(
              arraysType, createProto(doubleArrayType, doubleArrayType, intType), copyOfMethodName);
      DexMethod copyOfFloat =
          createMethod(
              arraysType, createProto(floatArrayType, floatArrayType, intType), copyOfMethodName);
      DexMethod copyOfInt =
          createMethod(
              arraysType, createProto(intArrayType, intArrayType, intType), copyOfMethodName);
      DexMethod copyOfLong =
          createMethod(
              arraysType, createProto(longArrayType, longArrayType, intType), copyOfMethodName);
      DexMethod copyOfShort =
          createMethod(
              arraysType, createProto(shortArrayType, shortArrayType, intType), copyOfMethodName);
      DexMethod copyOfObject =
          createMethod(
              arraysType, createProto(objectArrayType, objectArrayType, intType), copyOfMethodName);
      copyOfMethods =
          ImmutableSet.of(
              copyOfBoolean,
              copyOfByte,
              copyOfChar,
              copyOfDouble,
              copyOfFloat,
              copyOfInt,
              copyOfLong,
              copyOfShort,
              copyOfObject);
    }
  }

  public class JavaUtilCollectionsMembers extends LibraryMembers {

    public final DexField EMPTY_LIST =
        createField(javaUtilCollectionsType, javaUtilListType, "EMPTY_LIST");
    public final DexField EMPTY_MAP =
        createField(javaUtilCollectionsType, javaUtilMapType, "EMPTY_MAP");
    public final DexField EMPTY_SET =
        createField(javaUtilCollectionsType, javaUtilSetType, "EMPTY_SET");

    public final DexMethod emptyList =
        createMethod(javaUtilCollectionsType, createProto(javaUtilListType), "emptyList");
    public final DexMethod emptyMap =
        createMethod(javaUtilCollectionsType, createProto(javaUtilMapType), "emptyMap");
    public final DexMethod emptySet =
        createMethod(javaUtilCollectionsType, createProto(javaUtilSetType), "emptySet");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(EMPTY_LIST);
      consumer.accept(EMPTY_MAP);
      consumer.accept(EMPTY_SET);
    }
  }

  public class JavaUtilConcurrentTimeUnitMembers extends LibraryMembers {

    public final DexField DAYS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "DAYS");
    public final DexField HOURS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "HOURS");
    public final DexField MICROSECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MICROSECONDS");
    public final DexField MILLISECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MILLISECONDS");
    public final DexField MINUTES =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "MINUTES");
    public final DexField NANOSECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "NANOSECONDS");
    public final DexField SECONDS =
        createField(javaUtilConcurrentTimeUnitType, javaUtilConcurrentTimeUnitType, "SECONDS");

    public final DexMethod convert =
        createMethod(
            javaUtilConcurrentTimeUnitType,
            createProto(longType, longType, javaUtilConcurrentTimeUnitType),
            "convert");
    public final DexMethod toDays =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toDays");
    public final DexMethod toHours =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toHours");
    public final DexMethod toMinutes =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toMinutes");
    public final DexMethod toSeconds =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toSeconds");
    public final DexMethod toMillis =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toMillis");
    public final DexMethod toMicros =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toMicros");
    public final DexMethod toNanos =
        createMethod(javaUtilConcurrentTimeUnitType, createProto(longType, longType), "toNanos");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(DAYS);
      consumer.accept(HOURS);
      consumer.accept(MICROSECONDS);
      consumer.accept(MILLISECONDS);
      consumer.accept(MINUTES);
      consumer.accept(NANOSECONDS);
      consumer.accept(SECONDS);
    }

    public boolean isConversionMethod(DexMethod method) {
      return method.isIdenticalTo(convert)
          || method.isIdenticalTo(toDays)
          || method.isIdenticalTo(toHours)
          || method.isIdenticalTo(toMicros)
          || method.isIdenticalTo(toMillis)
          || method.isIdenticalTo(toMinutes)
          || method.isIdenticalTo(toNanos)
          || method.isIdenticalTo(toSeconds);
    }
  }

  public class JavaUtilListMembers {
    public final DexMethod size =
        createMethod(javaUtilListType, createProto(intType), createString("size"));
    public final DexMethod get =
        createMethod(javaUtilListType, createProto(objectType, intType), getString);
    public final DexMethod iterator =
        createMethod(javaUtilListType, createProto(javaUtilIteratorType), iteratorName);
    public final DexMethod of0 =
        createMethod(javaUtilListType, createProto(javaUtilListType), ofMethodName);
  }

  public class JavaUtilMapMembers {
    public final DexMethod of0 =
        createMethod(javaUtilMapType, createProto(javaUtilMapType), ofMethodName);
  }

  public class JavaUtilSetMembers {
    public final DexMethod of0 =
        createMethod(javaUtilSetType, createProto(javaUtilSetType), ofMethodName);
  }

  public class JavaUtilLocaleMembers extends LibraryMembers {

    public final DexField ENGLISH = createField(javaUtilLocaleType, javaUtilLocaleType, "ENGLISH");
    public final DexField ROOT = createField(javaUtilLocaleType, javaUtilLocaleType, "ROOT");
    public final DexField US = createField(javaUtilLocaleType, javaUtilLocaleType, "US");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(ENGLISH);
      consumer.accept(ROOT);
      consumer.accept(US);
    }
  }

  public class JavaUtilLoggingLevelMembers extends LibraryMembers {

    public final DexField CONFIG =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "CONFIG");
    public final DexField FINE =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINE");
    public final DexField FINER =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINER");
    public final DexField FINEST =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "FINEST");
    public final DexField SEVERE =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "SEVERE");
    public final DexField WARNING =
        createField(javaUtilLoggingLevelType, javaUtilLoggingLevelType, "WARNING");

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CONFIG);
      consumer.accept(FINE);
      consumer.accept(FINER);
      consumer.accept(FINEST);
      consumer.accept(SEVERE);
      consumer.accept(WARNING);
    }
  }

  public class LongMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedLongType, classType, "TYPE");

    public final DexMethod bitCount =
        createMethod(boxedLongType, createProto(intType, longType), "bitCount");
    public final DexMethod byteValue =
        createMethod(boxedLongType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedLongType, createProto(intType, longType, longType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedLongType, createProto(intType, boxedLongType), "compareTo");
    public final DexMethod compareUnsigned =
        createMethod(boxedLongType, createProto(intType, longType, longType), "compareUnsigned");
    public final DexMethod decode =
        createMethod(boxedLongType, createProto(boxedLongType, stringType), "decode");
    public final DexMethod divideUnsigned =
        createMethod(boxedLongType, createProto(longType, longType, longType), "divideUnsigned");
    public final DexMethod doubleValue =
        createMethod(boxedLongType, createProto(doubleType), "doubleValue");
    public final DexMethod equals =
        createMethod(boxedLongType, createProto(booleanType, objectType), "equals");
    public final DexMethod floatValue =
        createMethod(boxedLongType, createProto(floatType), "floatValue");
    public final DexMethod getLong =
        createMethod(boxedLongType, createProto(boxedLongType, stringType), "getLong");
    public final DexMethod getLongWithLong =
        createMethod(boxedLongType, createProto(boxedLongType, stringType, longType), "getLong");
    public final DexMethod getLongWithBoxedLong =
        createMethod(
            boxedLongType, createProto(boxedLongType, stringType, boxedLongType), "getLong");
    public final DexMethod hashCode = createMethod(boxedLongType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedLongType, createProto(intType, longType), "hashCode");
    public final DexMethod highestOneBit =
        createMethod(boxedLongType, createProto(longType, longType), "highestOneBit");
    public final DexMethod intValue = createMethod(boxedLongType, createProto(intType), "intValue");
    public final DexMethod longValue =
        createMethod(boxedLongType, createProto(longType), "longValue");
    public final DexMethod lowestOneBit =
        createMethod(boxedLongType, createProto(longType, longType), "lowestOneBit");
    public final DexMethod max =
        createMethod(boxedLongType, createProto(longType, longType, longType), "max");
    public final DexMethod min =
        createMethod(boxedLongType, createProto(longType, longType, longType), "min");
    public final DexMethod numberOfLeadingZeros =
        createMethod(boxedLongType, createProto(intType, longType), "numberOfLeadingZeros");
    public final DexMethod numberOfTrailingZeros =
        createMethod(boxedLongType, createProto(intType, longType), "numberOfTrailingZeros");
    public final DexMethod parseLong =
        createMethod(boxedLongType, createProto(longType, stringType), "parseLong");
    public final DexMethod parseLongWithRadix =
        createMethod(boxedLongType, createProto(longType, stringType, intType), "parseLong");
    public final DexMethod parseUnsignedLong =
        createMethod(boxedLongType, createProto(longType, stringType), "parseUnsignedLong");
    public final DexMethod parseUnsignedLongWithRadix =
        createMethod(
            boxedLongType, createProto(longType, stringType, intType), "parseUnsignedLong");
    public final DexMethod remainderUnsigned =
        createMethod(boxedLongType, createProto(longType, longType, longType), "remainderUnsigned");
    public final DexMethod reverse =
        createMethod(boxedLongType, createProto(longType, longType), "reverse");
    public final DexMethod reverseBytes =
        createMethod(boxedLongType, createProto(longType, longType), "reverseBytes");
    public final DexMethod rotateLeft =
        createMethod(boxedLongType, createProto(longType, longType, intType), "rotateLeft");
    public final DexMethod rotateRight =
        createMethod(boxedLongType, createProto(longType, longType, intType), "rotateRight");
    public final DexMethod shortValue =
        createMethod(boxedLongType, createProto(shortType), "shortValue");
    public final DexMethod signum =
        createMethod(boxedLongType, createProto(intType, longType), "signum");
    public final DexMethod sum =
        createMethod(boxedLongType, createProto(longType, longType, longType), "sum");
    public final DexMethod toBinaryString =
        createMethod(boxedLongType, createProto(stringType, longType), "toBinaryString");
    public final DexMethod toHexString =
        createMethod(boxedLongType, createProto(stringType, longType), "toHexString");
    public final DexMethod toOctalString =
        createMethod(boxedLongType, createProto(stringType, longType), "toOctalString");
    public final DexMethod toString =
        createMethod(boxedLongType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedLongType, createProto(stringType, longType), "toString");
    public final DexMethod toStringWithRadix =
        createMethod(boxedLongType, createProto(stringType, longType, intType), "toString");
    public final DexMethod toUnsignedString =
        createMethod(boxedLongType, createProto(stringType, longType), "toUnsignedString");
    public final DexMethod toUnsignedStringWithRadix =
        createMethod(boxedLongType, createProto(stringType, longType, intType), "toUnsignedString");
    public final DexMethod valueOf =
        createMethod(boxedLongType, createProto(boxedLongType, longType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedLongType, createProto(boxedLongType, stringType), "valueOf");
    public final DexMethod valueOfStringWithRadix =
        createMethod(boxedLongType, createProto(boxedLongType, stringType, intType), "valueOf");

    private LongMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(bitCount);
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(compareUnsigned);
      consumer.accept(doubleValue);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(highestOneBit);
      consumer.accept(intValue);
      consumer.accept(longValue);
      consumer.accept(lowestOneBit);
      consumer.accept(max);
      consumer.accept(min);
      consumer.accept(numberOfLeadingZeros);
      consumer.accept(numberOfTrailingZeros);
      consumer.accept(reverse);
      consumer.accept(reverseBytes);
      consumer.accept(rotateLeft);
      consumer.accept(rotateRight);
      consumer.accept(shortValue);
      // consumer.accept(signum);
      consumer.accept(sum);
      consumer.accept(toBinaryString);
      consumer.accept(toHexString);
      consumer.accept(toOctalString);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(toStringWithRadix);
      consumer.accept(toUnsignedString);
      consumer.accept(toUnsignedStringWithRadix);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class DoubleMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedDoubleType, classType, "TYPE");

    public final DexMethod byteValue =
        createMethod(boxedDoubleType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedDoubleType, createProto(intType, doubleType, doubleType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedDoubleType, createProto(intType, boxedDoubleType), "compareTo");
    public final DexMethod doubleToLongBits =
        createMethod(boxedDoubleType, createProto(longType, doubleType), "doubleToLongBits");
    public final DexMethod doubleToRawLongBits =
        createMethod(boxedDoubleType, createProto(longType, doubleType), "doubleToRawLongBits");
    public final DexMethod doubleValue =
        createMethod(boxedDoubleType, createProto(doubleType), "doubleValue");
    public final DexMethod equals =
        createMethod(boxedDoubleType, createProto(booleanType, objectType), "equals");
    public final DexMethod floatValue =
        createMethod(boxedDoubleType, createProto(floatType), "floatValue");
    public final DexMethod hashCode =
        createMethod(boxedDoubleType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedDoubleType, createProto(intType, doubleType), "hashCode");
    public final DexMethod intValue =
        createMethod(boxedDoubleType, createProto(intType), "intValue");
    public final DexMethod isFinite =
        createMethod(boxedDoubleType, createProto(booleanType, doubleType), "isFinite");
    public final DexMethod isInfinite =
        createMethod(boxedDoubleType, createProto(booleanType), "isInfinite");
    public final DexMethod staticIsInfinite =
        createMethod(boxedDoubleType, createProto(booleanType, doubleType), "isInfinite");
    public final DexMethod isNaN = createMethod(boxedDoubleType, createProto(booleanType), "isNaN");
    public final DexMethod staticIsNaN =
        createMethod(boxedDoubleType, createProto(booleanType, doubleType), "isNaN");
    public final DexMethod longBitsToDouble =
        createMethod(boxedDoubleType, createProto(doubleType, longType), "longBitsToDouble");
    public final DexMethod longValue =
        createMethod(boxedDoubleType, createProto(longType), "longValue");
    public final DexMethod max =
        createMethod(boxedDoubleType, createProto(doubleType, doubleType, doubleType), "max");
    public final DexMethod min =
        createMethod(boxedDoubleType, createProto(doubleType, doubleType, doubleType), "min");
    public final DexMethod parseDouble =
        createMethod(boxedDoubleType, createProto(doubleType, stringType), "parseDouble");
    public final DexMethod shortValue =
        createMethod(boxedDoubleType, createProto(shortType), "shortValue");
    public final DexMethod sum =
        createMethod(boxedDoubleType, createProto(doubleType, doubleType, doubleType), "sum");
    public final DexMethod toHexString =
        createMethod(boxedDoubleType, createProto(stringType, doubleType), "toHexString");
    public final DexMethod toString =
        createMethod(boxedDoubleType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedDoubleType, createProto(stringType, doubleType), "toString");
    public final DexMethod valueOf =
        createMethod(boxedDoubleType, createProto(boxedDoubleType, doubleType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedDoubleType, createProto(boxedDoubleType, stringType), "valueOf");

    private DoubleMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(doubleToLongBits);
      consumer.accept(doubleToRawLongBits);
      consumer.accept(doubleValue);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(intValue);
      consumer.accept(isFinite);
      consumer.accept(isInfinite);
      consumer.accept(staticIsInfinite);
      consumer.accept(isNaN);
      // consumer.accept(staticIsNaN);
      consumer.accept(longBitsToDouble);
      consumer.accept(longValue);
      consumer.accept(min);
      consumer.accept(max);
      consumer.accept(shortValue);
      consumer.accept(sum);
      consumer.accept(toHexString);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class IntegerMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedIntType, classType, "TYPE");

    public final DexMethod bitCount =
        createMethod(boxedIntType, createProto(intType, intType), "bitCount");
    public final DexMethod byteValue =
        createMethod(boxedIntType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedIntType, createProto(intType, intType, intType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedIntType, createProto(intType, boxedIntType), "compareTo");
    public final DexMethod compareUnsigned =
        createMethod(boxedIntType, createProto(intType, intType, intType), "compareUnsigned");
    public final DexMethod decode =
        createMethod(boxedIntType, createProto(boxedIntType, stringType), "decode");
    public final DexMethod divideUnsigned =
        createMethod(boxedIntType, createProto(intType, intType, intType), "divideUnsigned");
    public final DexMethod doubleValue =
        createMethod(boxedIntType, createProto(doubleType), "doubleValue");
    public final DexMethod equals =
        createMethod(boxedIntType, createProto(booleanType, objectType), "equals");
    public final DexMethod floatValue =
        createMethod(boxedIntType, createProto(floatType), "floatValue");
    public final DexMethod getInteger =
        createMethod(boxedIntType, createProto(boxedIntType, stringType), "getInteger");
    public final DexMethod getIntegerWithInt =
        createMethod(boxedIntType, createProto(boxedIntType, stringType, intType), "getInteger");
    public final DexMethod getIntegerWithInteger =
        createMethod(
            boxedIntType, createProto(boxedIntType, stringType, boxedIntType), "getInteger");
    public final DexMethod hashCode = createMethod(boxedIntType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedIntType, createProto(intType, intType), "hashCode");
    public final DexMethod highestOneBit =
        createMethod(boxedIntType, createProto(intType, intType), "highestOneBit");
    public final DexMethod intValue = createMethod(boxedIntType, createProto(intType), "intValue");
    public final DexMethod longValue =
        createMethod(boxedIntType, createProto(longType), "longValue");
    public final DexMethod lowestOneBit =
        createMethod(boxedIntType, createProto(intType, intType), "lowestOneBit");
    public final DexMethod max =
        createMethod(boxedIntType, createProto(intType, intType, intType), "max");
    public final DexMethod min =
        createMethod(boxedIntType, createProto(intType, intType, intType), "min");
    public final DexMethod numberOfLeadingZeros =
        createMethod(boxedIntType, createProto(intType, intType), "numberOfLeadingZeros");
    public final DexMethod numberOfTrailingZeros =
        createMethod(boxedIntType, createProto(intType, intType), "numberOfTrailingZeros");
    public final DexMethod parseInt =
        createMethod(boxedIntType, createProto(intType, stringType), "parseInt");
    public final DexMethod parseIntWithRadix =
        createMethod(boxedIntType, createProto(intType, stringType, intType), "parseInt");
    public final DexMethod parseUnsignedInt =
        createMethod(boxedIntType, createProto(intType, stringType), "parseUnsignedInt");
    public final DexMethod parseUnsignedIntWithRadix =
        createMethod(boxedIntType, createProto(intType, stringType, intType), "parseUnsignedInt");
    public final DexMethod remainderUnsigned =
        createMethod(boxedIntType, createProto(intType, intType, intType), "remainderUnsigned");
    public final DexMethod reverse =
        createMethod(boxedIntType, createProto(intType, intType), "reverse");
    public final DexMethod reverseBytes =
        createMethod(boxedIntType, createProto(intType, intType), "reverseBytes");
    public final DexMethod rotateLeft =
        createMethod(boxedIntType, createProto(intType, intType, intType), "rotateLeft");
    public final DexMethod rotateRight =
        createMethod(boxedIntType, createProto(intType, intType, intType), "rotateRight");
    public final DexMethod shortValue =
        createMethod(boxedIntType, createProto(shortType), "shortValue");
    public final DexMethod signum =
        createMethod(boxedIntType, createProto(intType, intType), "signum");
    public final DexMethod sum =
        createMethod(boxedIntType, createProto(intType, intType, intType), "sum");
    public final DexMethod toBinaryString =
        createMethod(boxedIntType, createProto(stringType, intType), "toBinaryString");
    public final DexMethod toHexString =
        createMethod(boxedIntType, createProto(stringType, intType), "toHexString");
    public final DexMethod toOctalString =
        createMethod(boxedIntType, createProto(stringType, intType), "toOctalString");
    public final DexMethod toString =
        createMethod(boxedIntType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedIntType, createProto(stringType, intType), "toString");
    public final DexMethod toStringWithRadix =
        createMethod(boxedIntType, createProto(stringType, intType, intType), "toString");
    public final DexMethod toUnsignedLong =
        createMethod(boxedIntType, createProto(longType, intType), "toUnsignedLong");
    public final DexMethod toUnsignedString =
        createMethod(boxedIntType, createProto(stringType, intType), "toUnsignedString");
    public final DexMethod toUnsignedStringWithRadix =
        createMethod(boxedIntType, createProto(stringType, intType, intType), "toUnsignedString");
    public final DexMethod valueOf =
        createMethod(boxedIntType, createProto(boxedIntType, intType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedIntType, createProto(boxedIntType, stringType), "valueOf");
    public final DexMethod valueOfStringWithRadix =
        createMethod(boxedIntType, createProto(boxedIntType, stringType, intType), "valueOf");

    private IntegerMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(bitCount);
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(compareUnsigned);
      consumer.accept(doubleValue);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(highestOneBit);
      consumer.accept(intValue);
      consumer.accept(longValue);
      consumer.accept(lowestOneBit);
      consumer.accept(max);
      consumer.accept(min);
      consumer.accept(numberOfLeadingZeros);
      consumer.accept(numberOfTrailingZeros);
      consumer.accept(reverse);
      consumer.accept(reverseBytes);
      consumer.accept(rotateLeft);
      consumer.accept(rotateRight);
      consumer.accept(shortValue);
      consumer.accept(signum);
      consumer.accept(sum);
      consumer.accept(toBinaryString);
      consumer.accept(toHexString);
      consumer.accept(toOctalString);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(toStringWithRadix);
      consumer.accept(toUnsignedLong);
      consumer.accept(toUnsignedString);
      consumer.accept(toUnsignedStringWithRadix);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class StringConcatFactoryMembers {

    public final DexMethod makeConcat =
        createMethod(
            stringConcatFactoryType,
            createProto(callSiteType, lookupType, stringType, methodTypeType),
            createString("makeConcat"));
    public final DexMethod makeConcatWithConstants =
        createMethod(
            stringConcatFactoryType,
            createProto(
                callSiteType, lookupType, stringType, methodTypeType, stringType, objectArrayType),
            createString("makeConcatWithConstants"));
  }

  public class VoidMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(voidType, classType, "TYPE");

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class ThrowableMethods {

    public final DexMethod addSuppressed;
    public final DexMethod getMessage;
    public final DexMethod getSuppressed;
    public final DexMethod initCause;

    private ThrowableMethods() {
      addSuppressed = createMethod(throwableDescriptor,
          createString("addSuppressed"), voidDescriptor, new DexString[]{throwableDescriptor});
      getSuppressed = createMethod(throwableDescriptor,
          createString("getSuppressed"), throwableArrayDescriptor, DexString.EMPTY_ARRAY);
      initCause =
          createMethod(
              throwableDescriptor,
              createString("initCause"),
              throwableDescriptor,
              new DexString[] {throwableDescriptor});
      getMessage =
          createMethod(
              throwableDescriptor,
              createString("getMessage"),
              stringDescriptor,
              DexString.EMPTY_ARRAY);
    }
  }

  public class AssertionErrorMethods {
    public final DexMethod initMessage;
    public final DexMethod initMessageAndCause;

    private AssertionErrorMethods() {
      this.initMessage =
          createMethod(
              javaLangAssertionErrorType, createProto(voidType, objectType), constructorMethodName);
      this.initMessageAndCause =
          createMethod(
              javaLangAssertionErrorType,
              createProto(voidType, stringType, throwableType),
              constructorMethodName);
    }
  }

  public class RecordMembers {
    public final DexMethod constructor = createMethod(recordType, createProto(voidType), "<init>");
    public final DexMethod equals =
        createMethod(recordType, createProto(booleanType, objectType), "equals");
    public final DexMethod hashCode = createMethod(recordType, createProto(intType), "hashCode");
    public final DexMethod toString = createMethod(recordType, createProto(stringType), "toString");
  }

  public class ObjectMethodsMembers {
    public final DexMethod bootstrap =
        createMethod(
            objectMethodsType,
            createProto(
                objectType,
                lookupType,
                stringType,
                typeDescriptorType,
                classType,
                stringType,
                methodHandleType.toArrayType(DexItemFactory.this)),
            "bootstrap");
  }

  public boolean isArrayClone(DexMethod method) {
    return method.getHolderType().isArrayType()
        && isObjectCloneWithoutHolderCheck(method.getProto(), method.getName());
  }

  public boolean isObjectCloneWithoutHolderCheck(DexProto proto, DexString name) {
    return cloneMethodName.isIdenticalTo(name)
        && proto.getParameters().isEmpty()
        && objectType.isIdenticalTo(proto.getReturnType());
  }

  public class ObjectMembers {

    /**
     * This field is not on {@link Object}, but will be synthesized on program classes as a static
     * field, for the compiler to have a principled way to trigger the initialization of a given
     * class.
     */
    public final DexField clinitField = createField(objectType, intType, "$r8$clinit");

    public final DexMethod clone;
    public final DexMethod equals =
        createMethod(objectType, createProto(booleanType, objectType), "equals");
    public final DexMethod getClass;
    public final DexMethod hashCode = createMethod(objectType, createProto(intType), "hashCode");
    public final DexMethod constructor;
    public final DexMethod finalize;
    public final DexMethod toString;
    public final DexMethod notify;
    public final DexMethod notifyAll;
    public final DexMethod wait;
    public final DexMethod waitLong;
    public final DexMethod waitLongInt;

    private ObjectMembers() {
      // The clone method is installed on each array, so one has to use method.match(clone).
      clone = createMethod(objectType, createProto(objectType), cloneMethodName);
      getClass = createMethod(objectDescriptor,
          getClassMethodName, classDescriptor, DexString.EMPTY_ARRAY);
      constructor = createMethod(objectDescriptor,
          constructorMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
      finalize = createMethod(objectDescriptor,
          finalizeMethodName, voidType.descriptor, DexString.EMPTY_ARRAY);
      toString = createMethod(objectDescriptor,
          toStringMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
      notify =
          createMethod(objectDescriptor, notifyMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      notifyAll =
          createMethod(
              objectDescriptor, notifyAllMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      wait = createMethod(objectDescriptor, waitMethodName, voidDescriptor, DexString.EMPTY_ARRAY);
      waitLong =
          createMethod(
              objectDescriptor, waitMethodName, voidDescriptor, new DexString[] {longDescriptor});
      waitLongInt =
          createMethod(
              objectDescriptor,
              waitMethodName,
              voidDescriptor,
              new DexString[] {longDescriptor, intDescriptor});
    }

    public boolean isObjectMember(DexMethod method) {
      return method.match(clone)
          || method.match(getClass)
          || method.match(constructor)
          || method.match(finalize)
          || method.match(toString)
          || method.match(hashCode)
          || method.match(equals)
          || method.match(notify)
          || method.match(notifyAll)
          || method.match(wait)
          || method.match(waitLong)
          || method.match(waitLongInt);
    }

    public DexMethod matchingPublicObjectMember(DexMethod method) {
      switch (method.getName().byteAt(0)) {
        case 't':
          if (method.match(toString)) {
            return toString;
          }
          break;
        case 'h':
          if (method.match(hashCode)) {
            return hashCode;
          }
          break;
        case 'e':
          if (method.match(equals)) {
            return equals;
          }
          break;
        case 'g':
          if (method.match(getClass)) {
            return getClass;
          }
          break;
        case 'n':
          if (method.match(notify)) {
            return notify;
          }
          if (method.match(notifyAll)) {
            return notifyAll;
          }
          break;
        case 'w':
          if (method.match(wait)) {
            return wait;
          }
          if (method.match(waitLong)) {
            return waitLong;
          }
          if (method.match(waitLongInt)) {
            return waitLongInt;
          }
          break;
        default:
          // Methods finalize and clone are not public.
          return null;
      }
      return null;
    }
  }

  public class ConstantBootstrapsMembers {
    public final DexMethod invoke =
        createMethod(
            constantBootstrapsType,
            createProto(
                objectType,
                methodHandlesLookupType,
                stringType,
                classType,
                methodHandleType,
                objectArrayType),
            invokeMethodName);
    public final DexMethod getStaticFinal =
        createMethod(
            constantBootstrapsType,
            createProto(objectType, methodHandlesLookupType, stringType, classType),
            "getStaticFinal");

    public final DexMethod primitiveClass =
        createMethod(
            constantBootstrapsType,
            createProto(classType, methodHandlesLookupType, stringType, classType),
            "primitiveClass");
  }

  public class BufferMembers {
    public final DexMethod positionArg =
        createMethod(bufferType, createProto(bufferType, intType), "position");
    public final DexMethod limitArg =
        createMethod(bufferType, createProto(bufferType, intType), "limit");
    public final DexMethod mark = createMethod(bufferType, createProto(bufferType), "mark");
    public final DexMethod reset = createMethod(bufferType, createProto(bufferType), "reset");
    public final DexMethod clear = createMethod(bufferType, createProto(bufferType), "clear");
    public final DexMethod flip = createMethod(bufferType, createProto(bufferType), "flip");
    public final DexMethod rewind = createMethod(bufferType, createProto(bufferType), "rewind");
    public final List<DexMethod> bufferCovariantMethods =
        ImmutableList.of(positionArg, limitArg, mark, reset, clear, flip, rewind);
  }

  public class ObjectsMethods {

    public final DexMethod equals =
        createMethod(objectsType, createProto(booleanType, objectType, objectType), "equals");
    public final DexMethod hash =
        createMethod(objectsType, createProto(intType, objectArrayType), "hash");
    public final DexMethod hashCode =
        createMethod(objectsType, createProto(intType, objectType), "hashCode");
    public final DexMethod isNull =
        createMethod(objectsType, createProto(booleanType, objectType), "isNull");
    public final DexMethod nonNull =
        createMethod(objectsType, createProto(booleanType, objectType), "nonNull");
    public final DexMethod requireNonNull;
    public final DexMethod requireNonNullWithMessage;
    public final DexMethod requireNonNullWithMessageSupplier;
    public final DexMethod requireNonNullElse =
        createMethod(
            objectsType, createProto(objectType, objectType, objectType), "requireNonNullElse");
    public final DexMethod requireNonNullElseGet =
        createMethod(
            objectsType,
            createProto(objectType, objectType, supplierType),
            "requireNonNullElseGet");
    public final DexMethod toStringWithObject =
        createMethod(objectsType, createProto(stringType, objectType), "toString");
    public final DexMethod toStringWithObjectAndNullDefault =
        createMethod(objectsType, createProto(stringType, objectType, stringType), "toString");

    private ObjectsMethods() {
      DexString requireNonNullMethodName = createString("requireNonNull");
      requireNonNull =
          createMethod(objectsType, createProto(objectType, objectType), requireNonNullMethodName);
      requireNonNullWithMessage =
          createMethod(
              objectsType,
              createProto(objectType, objectType, stringType),
              requireNonNullMethodName);
      requireNonNullWithMessageSupplier =
          createMethod(
              objectsType,
              createProto(objectType, objectType, supplierType),
              requireNonNullMethodName);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isRequireNonNullMethod(DexMethod method) {
      return method == requireNonNull
          || method == requireNonNullWithMessage
          || method == requireNonNullWithMessageSupplier
          || method == requireNonNullElse
          || method == requireNonNullElseGet;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isToStringMethod(DexMethod method) {
      return method == toStringWithObject || method == toStringWithObjectAndNullDefault;
    }

    public Iterable<DexMethod> requireNonNullMethods() {
      return ImmutableList.of(
          requireNonNull, requireNonNullWithMessage, requireNonNullWithMessageSupplier);
    }
  }

  public class ClassMethods {

    public final DexMethod desiredAssertionStatus;
    public final DexMethod isEnum = createMethod(classType, createProto(booleanType), "isEnum");
    public final DexMethod forName;
    public final DexMethod forName3;
    public final DexMethod getClassLoader =
        createMethod(classType, createProto(classLoaderType), "getClassLoader");
    public final DexMethod getName;
    public final DexMethod getCanonicalName;
    public final DexMethod getSimpleName;
    public final DexMethod getTypeName;
    public final DexMethod getConstructor;
    public final DexMethod getDeclaredConstructor;
    public final DexMethod getField;
    public final DexMethod getDeclaredField;
    public final DexMethod getMethod;
    public final DexMethod getDeclaredMethod;
    public final DexMethod getPackage =
        createMethod(classType, createProto(packageType), "getPackage");
    public final DexMethod getSuperclass =
        createMethod(classType, createProto(classType), "getSuperclass");
    public final DexMethod newInstance;
    private final Set<DexMethod> getMembers;
    public final Set<DexMethod> getNames;

    private ClassMethods() {
      desiredAssertionStatus = createMethod(classDescriptor,
          desiredAssertionStatusMethodName, booleanDescriptor, DexString.EMPTY_ARRAY);
      forName =
          createMethod(
              classDescriptor,
              forNameMethodName,
              classDescriptor,
              new DexString[] {stringDescriptor});
      forName3 =
          createMethod(
              classDescriptor,
              forNameMethodName,
              classDescriptor,
              new DexString[] {stringDescriptor, booleanDescriptor, classLoaderDescriptor});
      getName = createMethod(classDescriptor, getNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getCanonicalName = createMethod(
          classDescriptor, getCanonicalNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getSimpleName = createMethod(
          classDescriptor, getSimpleNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getTypeName = createMethod(
          classDescriptor, getTypeNameName, stringDescriptor, DexString.EMPTY_ARRAY);
      getConstructor =
          createMethod(classType, createProto(constructorType, classArrayType), "getConstructor");
      getDeclaredConstructor =
          createMethod(
              classDescriptor,
              getDeclaredConstructorName,
              constructorDescriptor,
              new DexString[] {classArrayDescriptor});
      getField =
          createMethod(
              classDescriptor, getFieldName, fieldDescriptor, new DexString[] {stringDescriptor});
      getDeclaredField =
          createMethod(
              classDescriptor,
              getDeclaredFieldName,
              fieldDescriptor,
              new DexString[] {stringDescriptor});
      getMethod =
          createMethod(
              classDescriptor,
              getMethodName,
              methodDescriptor,
              new DexString[] {stringDescriptor, classArrayDescriptor});
      getDeclaredMethod =
          createMethod(
              classDescriptor,
              getDeclaredMethodName,
              methodDescriptor,
              new DexString[] {stringDescriptor, classArrayDescriptor});
      newInstance =
          createMethod(classDescriptor, newInstanceName, objectDescriptor, DexString.EMPTY_ARRAY);
      getMembers = ImmutableSet.of(getField, getDeclaredField, getMethod, getDeclaredMethod);
      getNames = ImmutableSet.of(getName, getCanonicalName, getSimpleName, getTypeName);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isReflectiveClassLookup(DexMethod method) {
      return method == forName || method == forName3;
    }

    public boolean isReflectiveMemberLookup(DexMethod method) {
      return getMembers.contains(method);
    }

    public boolean isReflectiveNameLookup(DexMethod method) {
      return getNames.contains(method);
    }
  }

  public class ConstructorMethods {

    public final DexMethod newInstance;

    private ConstructorMethods() {
      newInstance =
          createMethod(
              constructorDescriptor,
              newInstanceName,
              objectDescriptor,
              new DexString[] {objectArrayDescriptor});
    }
  }

  public class MethodMethods {

    public final DexMethod invoke =
        createMethod(
            methodType, createProto(objectType, objectType, objectArrayType), invokeMethodName);

    private MethodMethods() {}
  }

  public class AndroidUtilLogMembers {

    public final DexMethod i =
        createMethod(androidUtilLogType, createProto(intType, stringType, stringType), "i");

    private AndroidUtilLogMembers() {}
  }

  public class JavaLangAnnotationRetentionPolicyMembers {

    public final DexField CLASS =
        createField(
            javaLangAnnotationRetentionPolicyType, javaLangAnnotationRetentionPolicyType, "CLASS");
    public final DexField RUNTIME =
        createField(
            javaLangAnnotationRetentionPolicyType,
            javaLangAnnotationRetentionPolicyType,
            "RUNTIME");

    private JavaLangAnnotationRetentionPolicyMembers() {}
  }

  public class JavaLangInvokeVarHandleMembers {

    public final DexMethod storeStoreFence =
        createMethod(varHandleType, createProto(voidType), "storeStoreFence");

    private JavaLangInvokeVarHandleMembers() {}
  }

  public class JavaLangReflectArrayMembers {

    public final DexMethod newInstanceMethodWithDimensions =
        createMethod(
            javaLangReflectArrayType,
            createProto(objectType, classType, intArrayType),
            "newInstance");

    private JavaLangReflectArrayMembers() {}
  }

  public class JavaLangSystemMembers {

    public final DexField out = createField(javaLangSystemType, javaIoPrintStreamType, "out");

    public final DexMethod arraycopy =
        createMethod(
            javaLangSystemType,
            createProto(voidType, objectType, intType, objectType, intType, intType),
            "arraycopy");
    public final DexMethod identityHashCode =
        createMethod(javaLangSystemType, createProto(intType, objectType), identityHashCodeName);

    private JavaLangSystemMembers() {}
  }

  public class JavaIoPrintStreamMembers {

    public final DexMethod printlnWithString =
        createMethod(javaIoPrintStreamType, createProto(voidType, stringType), "println");

    private JavaIoPrintStreamMembers() {}
  }

  public class EnumMembers extends LibraryMembers {

    public final DexField nameField = createField(enumType, stringType, "name");
    public final DexField ordinalField = createField(enumType, intType, "ordinal");

    public final DexMethod valueOf;
    public final DexMethod ordinalMethod;
    public final DexMethod nameMethod;
    public final DexMethod toString;
    public final DexMethod compareTo;
    public final DexMethod compareToWithObject =
        createMethod(enumType, createProto(intType, objectType), "compareTo");
    public final DexMethod equals;
    public final DexMethod hashCode;

    public final DexMethod constructor =
        createMethod(enumType, createProto(voidType, stringType, intType), constructorMethodName);
    public final DexMethod finalize =
        createMethod(enumType, createProto(voidType), finalizeMethodName);

    private EnumMembers() {
      valueOf =
          createMethod(
              enumDescriptor,
              valueOfMethodName,
              enumDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
      ordinalMethod =
          createMethod(enumDescriptor, ordinalMethodName, intDescriptor, DexString.EMPTY_ARRAY);
      nameMethod =
          createMethod(enumDescriptor, nameString, stringDescriptor, DexString.EMPTY_ARRAY);
      toString =
          createMethod(
              enumDescriptor,
              toStringMethodName,
              stringDescriptor,
              DexString.EMPTY_ARRAY);
      compareTo =
          createMethod(
              enumDescriptor, compareToMethodName, intDescriptor, new DexString[] {enumDescriptor});
      equals =
          createMethod(
              enumDescriptor,
              equalsMethodName,
              booleanDescriptor,
              new DexString[] {objectDescriptor});
      hashCode =
          createMethod(enumDescriptor, hashCodeMethodName, intDescriptor, DexString.EMPTY_ARRAY);
    }

    public void forEachField(Consumer<DexField> fn) {
      fn.accept(nameField);
      fn.accept(ordinalField);
    }

    @Override
    public void forEachFinalField(Consumer<DexField> fn) {
      fn.accept(nameField);
      fn.accept(ordinalField);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isNameOrOrdinalField(DexField field) {
      return field == nameField || field == ordinalField;
    }

    public boolean isEnumFieldCandidate(DexClassAndField staticField) {
      FieldAccessFlags accessFlags = staticField.getAccessFlags();
      assert accessFlags.isStatic();
      return accessFlags.isEnum() && accessFlags.isFinal();
    }

    @SuppressWarnings("ReferenceEquality")
    // In some case, the enum field may be respecialized to an enum subtype. In this case, one
    // can pass the encoded field as well as the field with the super enum type for the checks.
    public boolean isEnumField(
        DexClassAndField staticField, DexType enumType, Set<DexType> subtypes) {
      assert staticField.getAccessFlags().isStatic();
      return (staticField.getType() == enumType || subtypes.contains(staticField.getType()))
          && isEnumFieldCandidate(staticField);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isValuesFieldCandidate(DexClassAndField staticField, DexType enumType) {
      FieldAccessFlags accessFlags = staticField.getAccessFlags();
      assert accessFlags.isStatic();
      return staticField.getType().isArrayType()
          && staticField.getType().getArrayElementType() == enumType
          && accessFlags.isSynthetic()
          && accessFlags.isFinal();
    }
  }

  public class NullPointerExceptionMethods {

    public final DexMethod init =
        createMethod(
            javaLangNullPointerExceptionType, createProto(voidType), constructorMethodName);
    public final DexMethod initWithMessage =
        createMethod(
            javaLangNullPointerExceptionType,
            createProto(voidType, stringType),
            constructorMethodName);
  }

  public class IllegalArgumentExceptionMethods {

    public final DexMethod initWithMessage =
        createMethod(
            javaLangIllegalArgumentExceptionType,
            createProto(voidType, stringType),
            constructorMethodName);
  }

  /**
   * All boxed types (Boolean, Byte, ...) have a field named TYPE which contains the Class object
   * for the primitive type.
   *
   * <p>E.g. for Boolean https://docs.oracle.com/javase/8/docs/api/java/lang/Boolean.html#TYPE.
   */
  public class PrimitiveTypesBoxedTypeFields {

    public final DexField byteTYPE;
    public final DexField charTYPE;
    public final DexField shortTYPE;
    public final DexField intTYPE;
    public final DexField longTYPE;
    public final DexField floatTYPE;
    public final DexField doubleTYPE;

    private final Map<DexField, DexType> boxedFieldTypeToPrimitiveType;

    private PrimitiveTypesBoxedTypeFields() {
      byteTYPE = createField(boxedByteType, classType, "TYPE");
      charTYPE = createField(boxedCharType, classType, "TYPE");
      shortTYPE = createField(boxedShortType, classType, "TYPE");
      intTYPE = createField(boxedIntType, classType, "TYPE");
      longTYPE = createField(boxedLongType, classType, "TYPE");
      floatTYPE = createField(boxedFloatType, classType, "TYPE");
      doubleTYPE = createField(boxedDoubleType, classType, "TYPE");

      boxedFieldTypeToPrimitiveType =
          ImmutableMap.<DexField, DexType>builder()
              .put(booleanMembers.TYPE, booleanType)
              .put(byteTYPE, byteType)
              .put(charTYPE, charType)
              .put(shortTYPE, shortType)
              .put(intTYPE, intType)
              .put(longTYPE, longType)
              .put(floatTYPE, floatType)
              .put(doubleTYPE, doubleType)
              .build();
    }

    public DexType boxedFieldTypeToPrimitiveType(DexField field) {
      return boxedFieldTypeToPrimitiveType.get(field);
    }
  }

  public class AtomicIntUpdaterMethods {
    public final DexMethod newUpdater;
    public final DexMethod compareAndSet;
    public final DexMethod get;
    public final DexMethod set;

    private AtomicIntUpdaterMethods() {
      newUpdater =
          createMethod(
              intFieldUpdaterDescriptor,
              newUpdaterName,
              intFieldUpdaterDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
      compareAndSet =
          createMethod(
              intFieldUpdaterDescriptor,
              compareAndSetName,
              booleanDescriptor,
              new DexString[] {objectDescriptor, intDescriptor, intDescriptor});
      get =
          createMethod(
              intFieldUpdaterDescriptor,
              getName,
              intDescriptor,
              new DexString[] {objectDescriptor});
      set =
          createMethod(
              intFieldUpdaterDescriptor,
              setName,
              voidDescriptor,
              new DexString[] {objectDescriptor, intDescriptor});
    }
  }

  public class AtomicLongUpdaterMethods {
    public final DexMethod newUpdater;
    public final DexMethod compareAndSet;
    public final DexMethod get;
    public final DexMethod set;

    private AtomicLongUpdaterMethods() {
      newUpdater =
          createMethod(
              longFieldUpdaterDescriptor,
              newUpdaterName,
              longFieldUpdaterDescriptor,
              new DexString[] {classDescriptor, stringDescriptor});
      compareAndSet =
          createMethod(
              longFieldUpdaterDescriptor,
              compareAndSetName,
              booleanDescriptor,
              new DexString[] {objectDescriptor, longDescriptor, longDescriptor});
      get =
          createMethod(
              longFieldUpdaterDescriptor,
              getName,
              longDescriptor,
              new DexString[] {objectDescriptor});
      set =
          createMethod(
              longFieldUpdaterDescriptor,
              setName,
              voidDescriptor,
              new DexString[] {objectDescriptor, longDescriptor});
    }
  }

  public class AtomicReferenceUpdaterMethods {
    public final DexMethod newUpdater;
    public final DexMethod compareAndSet;
    public final DexMethod get;
    public final DexMethod set;
    public final DexMethod getAndSet;

    private AtomicReferenceUpdaterMethods() {
      newUpdater =
          createMethod(
              referenceFieldUpdaterDescriptor,
              newUpdaterName,
              referenceFieldUpdaterDescriptor,
              new DexString[] {classDescriptor, classDescriptor, stringDescriptor});
      compareAndSet =
          createMethod(
              referenceFieldUpdaterDescriptor,
              compareAndSetName,
              booleanDescriptor,
              new DexString[] {objectDescriptor, objectDescriptor, objectDescriptor});
      get =
          createMethod(
              referenceFieldUpdaterDescriptor,
              getName,
              objectDescriptor,
              new DexString[] {objectDescriptor});
      set =
          createMethod(
              referenceFieldUpdaterDescriptor,
              setName,
              voidDescriptor,
              new DexString[] {objectDescriptor, objectDescriptor});
      getAndSet =
          createMethod(
              referenceFieldUpdaterDescriptor,
              getAndSetName,
              objectDescriptor,
              new DexString[] {objectDescriptor, objectDescriptor});
    }
  }

  public boolean isAtomicFieldUpdaterConstructor(DexMethod method) {
    return method.isIdenticalTo(atomicReferenceUpdaterMethods.newUpdater)
        || method.isIdenticalTo(atomicIntUpdaterMethods.newUpdater)
        || method.isIdenticalTo(atomicLongUpdaterMethods.newUpdater);
  }

  public class SunMiscUnsafeMethods {

    public final DexMethod compareAndSwapInt;
    public final DexMethod compareAndSwapLong;
    public final DexMethod compareAndSwapObject;
    public final DexMethod objectFieldOffset;
    public final DexMethod getObjectVolatile;
    public final DexMethod putObjectVolatile;
    public final DexMethod getIntVolatile;
    public final DexMethod getLongVolatile;
    public final DexMethod putIntVolatile;
    public final DexMethod putLongVolatile;

    private SunMiscUnsafeMethods() {
      this.compareAndSwapInt =
          createMethod(
              sunMiscUnsafeType,
              createProto(booleanType, objectType, longType, intType, intType),
              "compareAndSwapInt");
      this.compareAndSwapLong =
          createMethod(
              sunMiscUnsafeType,
              createProto(booleanType, objectType, longType, longType, longType),
              "compareAndSwapLong");
      this.compareAndSwapObject =
          createMethod(
              sunMiscUnsafeType,
              createProto(booleanType, objectType, longType, objectType, objectType),
              "compareAndSwapObject");
      this.objectFieldOffset =
          createMethod(sunMiscUnsafeType, createProto(longType, fieldType), "objectFieldOffset");
      this.getObjectVolatile =
          createMethod(
              sunMiscUnsafeType,
              createProto(objectType, objectType, longType),
              "getObjectVolatile");
      this.putObjectVolatile =
          createMethod(
              sunMiscUnsafeType,
              createProto(voidType, objectType, longType, objectType),
              "putObjectVolatile");
      this.getIntVolatile =
          createMethod(
              sunMiscUnsafeType, createProto(intType, objectType, longType), "getIntVolatile");
      this.getLongVolatile =
          createMethod(
              sunMiscUnsafeType, createProto(longType, objectType, longType), "getLongVolatile");
      this.putIntVolatile =
          createMethod(
              sunMiscUnsafeType,
              createProto(voidType, objectType, longType, intType),
              "putIntVolatile");
      this.putLongVolatile =
          createMethod(
              sunMiscUnsafeType,
              createProto(voidType, objectType, longType, longType),
              "putLongVolatile");
    }
  }

  public class ShortMembers extends BoxedPrimitiveMembers {

    public final DexField TYPE = createField(boxedShortType, classType, "TYPE");

    public final DexMethod byteValue =
        createMethod(boxedShortType, createProto(byteType), "byteValue");
    public final DexMethod compare =
        createMethod(boxedShortType, createProto(intType, shortType, shortType), "compare");
    public final DexMethod compareTo =
        createMethod(boxedShortType, createProto(intType, boxedShortType), "compareTo");
    public final DexMethod decode =
        createMethod(boxedShortType, createProto(boxedShortType, stringType), "decode");
    public final DexMethod doubleValue =
        createMethod(boxedShortType, createProto(doubleType), "doubleValue");
    public final DexMethod equals =
        createMethod(boxedShortType, createProto(booleanType, objectType), "equals");
    public final DexMethod floatValue =
        createMethod(boxedShortType, createProto(floatType), "floatValue");
    public final DexMethod hashCode =
        createMethod(boxedShortType, createProto(intType), "hashCode");
    public final DexMethod staticHashCode =
        createMethod(boxedShortType, createProto(intType, shortType), "hashCode");
    public final DexMethod intValue =
        createMethod(boxedShortType, createProto(intType), "intValue");
    public final DexMethod longValue =
        createMethod(boxedShortType, createProto(longType), "longValue");
    public final DexMethod parseShort =
        createMethod(boxedShortType, createProto(shortType, stringType), "parseShort");
    public final DexMethod parseShortWithRadix =
        createMethod(boxedShortType, createProto(shortType, stringType, intType), "parseShort");
    public final DexMethod reverseBytes =
        createMethod(boxedShortType, createProto(shortType, shortType), "reverseBytes");
    public final DexMethod shortValue =
        createMethod(boxedShortType, createProto(shortType), "shortValue");
    public final DexMethod toString =
        createMethod(boxedShortType, createProto(stringType), "toString");
    public final DexMethod staticToString =
        createMethod(boxedShortType, createProto(stringType, shortType), "toString");
    public final DexMethod toUnsignedInt =
        createMethod(boxedShortType, createProto(intType, shortType), "toUnsignedInt");
    public final DexMethod toUnsignedLong =
        createMethod(boxedShortType, createProto(longType, shortType), "toUnsignedLong");
    public final DexMethod valueOf =
        createMethod(boxedShortType, createProto(boxedShortType, shortType), "valueOf");
    public final DexMethod valueOfString =
        createMethod(boxedShortType, createProto(boxedShortType, stringType), "valueOf");
    public final DexMethod valueOfStringWithRadix =
        createMethod(boxedShortType, createProto(boxedShortType, stringType, intType), "valueOf");

    private ShortMembers() {}

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(TYPE);
    }

    public void forEachUnconditionalFinalMethodWithoutSideEffects(Consumer<DexMethod> consumer) {
      consumer.accept(byteValue);
      consumer.accept(compare);
      consumer.accept(doubleValue);
      consumer.accept(floatValue);
      consumer.accept(hashCode);
      consumer.accept(staticHashCode);
      consumer.accept(intValue);
      consumer.accept(longValue);
      consumer.accept(reverseBytes);
      consumer.accept(shortValue);
      consumer.accept(toString);
      consumer.accept(staticToString);
      consumer.accept(toUnsignedInt);
      consumer.accept(toUnsignedLong);
      consumer.accept(valueOf);
    }

    @Override
    public DexField getTypeField() {
      return TYPE;
    }
  }

  public class StringMembers extends LibraryMembers {

    public final DexField CASE_INSENSITIVE_ORDER =
        createField(stringType, javaUtilComparatorType, "CASE_INSENSITIVE_ORDER");

    public final DexMethod isEmpty;
    public final DexMethod length;

    public final DexMethod concat;
    public final DexMethod constructor =
        createMethod(stringType, createProto(voidType, stringType), constructorMethodName);
    public final DexMethod contains;
    public final DexMethod startsWith;
    public final DexMethod substring;
    public final DexMethod substringWithEndIndex;
    public final DexMethod endsWith;
    public final DexMethod equals;
    public final DexMethod equalsIgnoreCase;
    public final DexMethod contentEqualsCharSequence;

    public final DexMethod indexOfInt;
    public final DexMethod indexOfIntWithFromIndex;
    public final DexMethod indexOfString;
    public final DexMethod indexOfStringWithFromIndex;
    public final DexMethod lastIndexOfInt;
    public final DexMethod lastIndexOfIntWithFromIndex;
    public final DexMethod lastIndexOfString;
    public final DexMethod lastIndexOfStringWithFromIndex;
    public final DexMethod compareTo;
    public final DexMethod compareToIgnoreCase;

    public final DexMethod hashCode;

    public final DexMethod format;
    public final DexMethod formatWithLocale;
    public final DexMethod valueOfBoolean;
    public final DexMethod valueOfChar;
    public final DexMethod valueOfInt;
    public final DexMethod valueOfLong;
    public final DexMethod valueOfFloat;
    public final DexMethod valueOfDouble;
    public final DexMethod valueOfObject;
    public final DexMethod toString;
    public final DexMethod toCharArray;
    public final DexMethod intern;

    public final DexMethod trim = createMethod(stringType, createProto(stringType), trimName);

    private StringMembers() {
      isEmpty =
          createMethod(
              stringDescriptor, isEmptyMethodName, booleanDescriptor, DexString.EMPTY_ARRAY);
      length =
          createMethod(stringDescriptor, lengthMethodName, intDescriptor, DexString.EMPTY_ARRAY);

      DexString[] charSequenceArgs = {charSequenceDescriptor};
      DexString[] intArgs = {intDescriptor};
      DexString[] intIntArgs = {intDescriptor, intDescriptor};
      DexString[] objectArgs = {objectDescriptor};
      DexString[] stringArgs = {stringDescriptor};
      DexString[] stringIntArgs = {stringDescriptor, intDescriptor};

      concat = createMethod(stringDescriptor, concatMethodName, stringDescriptor, stringArgs);
      contains =
          createMethod(stringDescriptor, containsMethodName, booleanDescriptor, charSequenceArgs);
      startsWith =
          createMethod(stringDescriptor, startsWithMethodName, booleanDescriptor, stringArgs);
      endsWith = createMethod(stringDescriptor, endsWithMethodName, booleanDescriptor, stringArgs);
      substring = createMethod(stringDescriptor, substringName, stringDescriptor, intArgs);
      substringWithEndIndex =
          createMethod(stringDescriptor, substringName, stringDescriptor, intIntArgs);
      equals = createMethod(stringDescriptor, equalsMethodName, booleanDescriptor, objectArgs);
      equalsIgnoreCase =
          createMethod(stringDescriptor, equalsIgnoreCaseMethodName, booleanDescriptor, stringArgs);
      contentEqualsCharSequence =
          createMethod(
              stringDescriptor, contentEqualsMethodName, booleanDescriptor, charSequenceArgs);

      indexOfString = createMethod(stringDescriptor, indexOfMethodName, intDescriptor, stringArgs);
      indexOfStringWithFromIndex =
          createMethod(stringDescriptor, indexOfMethodName, intDescriptor, stringIntArgs);
      indexOfInt = createMethod(stringDescriptor, indexOfMethodName, intDescriptor, intArgs);
      indexOfIntWithFromIndex =
          createMethod(stringDescriptor, indexOfMethodName, intDescriptor, intIntArgs);
      lastIndexOfString =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, stringArgs);
      lastIndexOfStringWithFromIndex =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, stringIntArgs);
      lastIndexOfInt =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, intArgs);
      lastIndexOfIntWithFromIndex =
          createMethod(stringDescriptor, lastIndexOfMethodName, intDescriptor, intIntArgs);
      compareTo = createMethod(stringDescriptor, compareToMethodName, intDescriptor, stringArgs);
      compareToIgnoreCase =
          createMethod(stringDescriptor, compareToIgnoreCaseMethodName, intDescriptor, stringArgs);

      hashCode = createMethod(stringType, createProto(intType), hashCodeMethodName);
      format =
          createMethod(
              stringDescriptor,
              formatMethodName,
              stringDescriptor,
              new DexString[] {stringDescriptor, objectArrayDescriptor});
      formatWithLocale =
          createMethod(
              stringDescriptor,
              formatMethodName,
              stringDescriptor,
              new DexString[] {localeDescriptor, stringDescriptor, objectArrayDescriptor});

      valueOfBoolean =
          createMethod(
              stringDescriptor,
              valueOfMethodName,
              stringDescriptor,
              new DexString[] {booleanDescriptor});
      valueOfChar =
          createMethod(
              stringDescriptor,
              valueOfMethodName,
              stringDescriptor,
              new DexString[] {charDescriptor});
      valueOfInt = createMethod(stringDescriptor, valueOfMethodName, stringDescriptor, intArgs);
      valueOfLong =
          createMethod(
              stringDescriptor,
              valueOfMethodName,
              stringDescriptor,
              new DexString[] {longDescriptor});
      valueOfFloat =
          createMethod(
              stringDescriptor,
              valueOfMethodName,
              stringDescriptor,
              new DexString[] {floatDescriptor});
      valueOfDouble =
          createMethod(
              stringDescriptor,
              valueOfMethodName,
              stringDescriptor,
              new DexString[] {doubleDescriptor});
      valueOfObject =
          createMethod(stringDescriptor, valueOfMethodName, stringDescriptor, objectArgs);

      toCharArray = createMethod(stringType, createProto(charArrayType), toCharArrayMethodName);
      toString =
          createMethod(
              stringDescriptor, toStringMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
      intern =
          createMethod(stringDescriptor, internMethodName, stringDescriptor, DexString.EMPTY_ARRAY);
    }

    @Override
    public void forEachFinalField(Consumer<DexField> consumer) {
      consumer.accept(CASE_INSENSITIVE_ORDER);
    }

    public DexMethod getValueOfForDexType(DexType dexType) {
      switch (dexType.toShorty()) {
        case 'L':
        case '[':
          return valueOfObject;
        case 'Z':
          return valueOfBoolean;
        case 'C':
          return valueOfChar;
        case 'F':
          return valueOfFloat;
        case 'J':
          return valueOfLong;
        case 'D':
          return valueOfDouble;
        case 'B':
        case 'S':
        case 'I':
          return valueOfInt;
        default:
          throw new Unreachable();
      }
    }
  }

  public class StringBuildingMethods {

    public final DexMethod appendBoolean;
    public final DexMethod appendChar;
    public final DexMethod appendCharArray;
    public final DexMethod appendSubCharArray;
    public final DexMethod appendCharSequence;
    public final DexMethod appendSubCharSequence;
    public final DexMethod appendInt;
    public final DexMethod appendDouble;
    public final DexMethod appendFloat;
    public final DexMethod appendLong;
    public final DexMethod appendObject;
    public final DexMethod appendString;
    public final DexMethod appendStringBuffer;
    public final DexMethod capacity;
    public final DexMethod charSequenceConstructor;
    public final DexMethod defaultConstructor;
    public final DexMethod intConstructor;
    public final DexMethod stringConstructor;
    public final DexMethod toString;

    private final Set<DexMethod> appendMethods;
    private final Set<DexMethod> appendPrimitiveMethods;

    private StringBuildingMethods(DexType receiver) {
      DexString append = createString("append");
      appendBoolean = createMethod(receiver, createProto(receiver, booleanType), append);
      appendChar = createMethod(receiver, createProto(receiver, charType), append);
      appendCharArray = createMethod(receiver, createProto(receiver, charArrayType), append);
      appendSubCharArray =
          createMethod(receiver, createProto(receiver, charArrayType, intType, intType), append);
      appendCharSequence = createMethod(receiver, createProto(receiver, charSequenceType), append);
      appendSubCharSequence =
          createMethod(receiver, createProto(receiver, charSequenceType, intType, intType), append);
      appendInt = createMethod(receiver, createProto(receiver, intType), append);
      appendDouble = createMethod(receiver, createProto(receiver, doubleType), append);
      appendFloat = createMethod(receiver, createProto(receiver, floatType), append);
      appendLong = createMethod(receiver, createProto(receiver, longType), append);
      appendObject = createMethod(receiver, createProto(receiver, objectType), append);
      appendString = createMethod(receiver, createProto(receiver, stringType), append);
      appendStringBuffer = createMethod(receiver, createProto(receiver, stringBufferType), append);
      capacity = createMethod(receiver, createProto(intType), createString("capacity"));
      charSequenceConstructor =
          createMethod(receiver, createProto(voidType, charSequenceType), constructorMethodName);
      defaultConstructor = createMethod(receiver, createProto(voidType), constructorMethodName);
      intConstructor =
          createMethod(receiver, createProto(voidType, intType), constructorMethodName);
      stringConstructor =
          createMethod(receiver, createProto(voidType, stringType), constructorMethodName);
      toString = createMethod(receiver, createProto(stringType), toStringMethodName);

      appendMethods =
          ImmutableSet.of(
              appendBoolean,
              appendChar,
              appendCharArray,
              appendSubCharArray,
              appendCharSequence,
              appendSubCharSequence,
              appendInt,
              appendDouble,
              appendFloat,
              appendLong,
              appendObject,
              appendString,
              appendStringBuffer);
      appendPrimitiveMethods =
          ImmutableSet.of(
              appendBoolean, appendChar, appendInt, appendDouble, appendFloat, appendLong);
      constructorMethods =
          ImmutableSet.of(
              charSequenceConstructor, defaultConstructor, intConstructor, stringConstructor);
    }

    public final Set<DexMethod> constructorMethods;

    public boolean isAppendMethod(DexMethod method) {
      return appendMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendObjectMethod(DexMethod method) {
      return method == appendObject;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendCharSequenceMethod(DexMethod method) {
      return method == appendCharSequence || method == appendSubCharSequence;
    }

    public boolean isAppendObjectOrCharSequenceMethod(DexMethod method) {
      return isAppendObjectMethod(method) || isAppendCharSequenceMethod(method);
    }

    public boolean isAppendPrimitiveMethod(DexMethod method) {
      return appendPrimitiveMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendSubArrayMethod(DexMethod method) {
      return appendSubCharArray == method || appendSubCharSequence == method;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendStringMethod(DexMethod method) {
      return method == appendString;
    }

    public boolean isConstructorMethod(DexMethod method) {
      return constructorMethods.contains(method);
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean constructorInvokeIsSideEffectFree(
        DexMethod invokedMethod, List<Value> arguments) {
      if (invokedMethod == defaultConstructor) {
        return true;
      }

      if (invokedMethod == charSequenceConstructor) {
        // Performs callbacks on the given CharSequence, which may have side effects.
        TypeElement charSequenceType = arguments.get(1).getType();
        return charSequenceType.isClassType()
            && charSequenceType.asClassType().getClassType() == stringType;
      }

      if (invokedMethod == intConstructor) {
        // NegativeArraySizeException - if the capacity argument is less than 0.
        Value capacityValue = arguments.get(1);
        if (capacityValue.hasValueRange()) {
          return capacityValue.getValueRange().getMin() >= 0;
        }
        return false;
      }

      if (invokedMethod == stringConstructor) {
        // NullPointerException - if str is null.
        Value strValue = arguments.get(1);
        return !strValue.getType().isNullable();
      }

      assert false : "Unexpected invoke targeting `" + invokedMethod.toSourceString() + "`";
      return false;
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isAppendCharArrayMethod(DexMethod method) {
      return method == appendCharArray || method == appendSubCharArray;
    }

    public DexMethod getAppendMethodForType(DexType argType) {
      switch (argType.toShorty()) {
        case 'L':
          return appendObject;
        case 'Z':
          return appendBoolean;
        case 'C':
          return appendChar;
        case 'F':
          return appendFloat;
        case 'J':
          return appendLong;
        case 'D':
          return appendDouble;
        case 'B':
        case 'S':
        case 'I':
          return appendInt;
        default:
          throw new Unreachable();
      }
    }
  }

  public class SupplierMembers extends LibraryMembers {

    public final DexMethod get = createMethod(supplierType, createProto(objectType), getString);

    private SupplierMembers() {}
  }

  public class ThreadLocalMembers extends LibraryMembers {

    public final DexMethod constructor = createInstanceInitializer(threadLocalType);

    private ThreadLocalMembers() {}
  }

  public class PolymorphicMethods {

    private final DexProto signature = createProto(objectType, objectArrayType);
    private final DexProto setSignature = createProto(voidType, objectArrayType);
    private final DexProto compareAndSetSignature = createProto(booleanType, objectArrayType);

    public final Set<DexString> varHandleMethodsWithPolymorphicReturnType =
        createStrings(
            "compareAndExchange",
            "compareAndExchangeAcquire",
            "compareAndExchangeRelease",
            getString,
            "getAcquire",
            "getAndAdd",
            "getAndAddAcquire",
            "getAndAddRelease",
            "getAndBitwiseAnd",
            "getAndBitwiseAndAcquire",
            "getAndBitwiseAndRelease",
            "getAndBitwiseOr",
            "getAndBitwiseOrAcquire",
            "getAndBitwiseOrRelease",
            "getAndBitwiseXor",
            "getAndBitwiseXorAcquire",
            "getAndBitwiseXorRelease",
            "getAndSet",
            "getAndSetAcquire",
            "getAndSetRelease",
            "getOpaque",
            "getVolatile");

    private final Set<DexString> varHandleSetMethods =
        createStrings(setString, "setOpaque", setReleaseString, setVolatileString);

    public final Set<DexString> varHandleCompareAndSetMethodNames =
        createStrings(
            compareAndSetString,
            weakCompareAndSetString,
            "weakCompareAndSetAcquire",
            "weakCompareAndSetPlain",
            "weakCompareAndSetRelease");

    @SuppressWarnings("ReferenceEquality")
    public DexMethod canonicalize(DexMethod invokeProto) {
      DexMethod result = null;
      if (invokeProto.holder == methodHandleType) {
        if (invokeProto.name == invokeMethodName || invokeProto.name == invokeExactMethodName) {
          result = createMethod(methodHandleType, signature, invokeProto.name);
        }
      } else if (invokeProto.holder == varHandleType) {
        if (varHandleMethodsWithPolymorphicReturnType.contains(invokeProto.name)) {
          result = createMethod(varHandleType, signature, invokeProto.name);
        } else if (varHandleSetMethods.contains(invokeProto.name)) {
          result = createMethod(varHandleType, setSignature, invokeProto.name);
        } else if (varHandleCompareAndSetMethodNames.contains(invokeProto.name)) {
          result = createMethod(varHandleType, compareAndSetSignature, invokeProto.name);
        }
      }
      assert (result != null) == isPolymorphicInvoke(invokeProto);
      return result;
    }

    private Set<DexString> createStrings(Object... strings) {
      IdentityHashMap<DexString, DexString> map = new IdentityHashMap<>();
      for (Object string : strings) {
        DexString dexString =
            string instanceof String ? createString((String) string) : (DexString) string;
        map.put(dexString, dexString);
      }
      return map.keySet();
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isPolymorphicInvoke(DexMethod invokeProto) {
      if (invokeProto.holder == methodHandleType) {
        return invokeProto.name == invokeMethodName || invokeProto.name == invokeExactMethodName;
      }
      if (invokeProto.holder == varHandleType) {
        return varHandleMethodsWithPolymorphicReturnType.contains(invokeProto.name)
            || varHandleSetMethods.contains(invokeProto.name)
            || varHandleCompareAndSetMethodNames.contains(invokeProto.name);
      }
      return false;
    }
  }

  public class ProxyMethods {

    public final DexMethod getProxyClass;
    public final DexMethod newProxyInstance;

    private ProxyMethods() {
      getProxyClass =
          createMethod(
              proxyType,
              createProto(classType, classLoaderType, classArrayType),
              createString("getProxyClass"));
      newProxyInstance =
          createMethod(
              proxyType,
              createProto(objectType, classLoaderType, classArrayType, invocationHandlerType),
              createString("newProxyInstance"));
    }
  }

  public class ServiceLoaderMethods {

    public final DexMethod load;
    public final DexMethod loadWithClassLoader;
    public final DexMethod loadInstalled;
    public final DexMethod iterator;

    private ServiceLoaderMethods() {
      DexString loadName = createString("load");
      load = createMethod(serviceLoaderType, createProto(serviceLoaderType, classType), loadName);
      loadWithClassLoader =
          createMethod(
              serviceLoaderType,
              createProto(serviceLoaderType, classType, classLoaderType),
              loadName);
      loadInstalled =
          createMethod(
              serviceLoaderType,
              createProto(serviceLoaderType, classType),
              createString("loadInstalled"));
      iterator =
          createMethod(
              serviceLoaderType, createProto(javaUtilIteratorType), createString("iterator"));
    }

    @SuppressWarnings("ReferenceEquality")
    public boolean isLoadMethod(DexMethod method) {
      return method == load || method == loadWithClassLoader || method == loadInstalled;
    }
  }

  public class IteratorMethods {
    public final DexMethod hasNext =
        createMethod(javaUtilIteratorType, createProto(booleanType), hasNextName);
    public final DexMethod next =
        createMethod(javaUtilIteratorType, createProto(objectType), nextName);
  }

  private static <T extends DexItem> T canonicalize(
      Map<T, T> committedMap, Map<T, T> pendingMap, T item) {
    assert item != null;
    assert !DexItemFactory.isInternalSentinel(item);
    // Avoid synchronization for committed items.
    T committed = committedMap.get(item);
    if (committed != null) {
      return committed;
    }
    T previous = pendingMap.putIfAbsent(item, item);
    if (previous != null) {
      return previous;
    }
    return item;
  }

  public DexString createMarkerString(int size, byte[] content) {
    DexString potentialMarker = createString(size, content);
    if (Marker.hasMarkerPrefix(potentialMarker.content)) {
      markers.put(potentialMarker, potentialMarker);
    }
    return potentialMarker;
  }

  public DexString createMarkerString(String marker) {
    DexString potentialMarker = createString(marker);
    if (Marker.hasMarkerPrefix(potentialMarker.content)) {
      markers.put(potentialMarker, potentialMarker);
    }
    return potentialMarker;
  }

  public DexString createString(int size, byte[] content) {
    return canonicalize(committedStrings, strings, new DexString(size, content));
  }

  public DexString createString(String source) {
    return canonicalize(committedStrings, strings, new DexString(source));
  }

  public static String escapeMemberString(String str) {
    return str.replace('.', '$');
  }

  public String createMemberString(String baseName, DexType holder, int index) {
    StringBuilder sb = new StringBuilder().append(baseName);
    if (holder != null) {
      sb.append('$').append(escapeMemberString(holder.toSourceString()));
    }

    if (index > 0) {
      sb.append("$").append(index);
    }

    return sb.toString();
  }

  public <T> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName, DexType holder) {
    return createFreshMember(tryString, baseName, holder, 0);
  }

  /**
   * Find a fresh method name that is not used by any other method. The method name takes the form
   * "basename$holdername" or "basename$holdername$index".
   *
   * @param tryString callback to check if the method name is in use.
   */
  public <T> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName, DexType holder, int index) {
    int offset = 0;
    while (true) {
      assert offset < 1000;
      DexString name = createString(createMemberString(baseName, holder, index + offset));
      Optional<T> result = tryString.apply(name);
      if (result.isPresent()) {
        return result.get();
      }
      offset++;
    }
  }

  /**
   * Find a fresh method name that is not used by any other method. The method name takes the form
   * "basename" or "basename$index".
   *
   * @param tryString callback to check if the method name is in use.
   */
  public <T extends DexMember<?, ?>> T createFreshMember(
      Function<DexString, Optional<T>> tryString, String baseName) {
    return createFreshMember(tryString, baseName, null);
  }

  /**
   * Find a fresh method name that is not in the string pool. The name takes the form
   * "basename$holdername" or "basename$holdername$index".
   */
  public DexString createGloballyFreshMemberString(String baseName, DexType holder) {
    int index = 0;
    while (true) {
      String name = createMemberString(baseName, holder, index++);
      DexString dexName = lookupString(name);
      if (dexName == null) {
        return createString(name);
      }
    }
  }

  /**
   * Find a fresh method name that is not in the string pool. The name takes the form "basename" or
   * "basename$index".
   */
  public DexString createGloballyFreshMemberString(String baseName) {
    return createGloballyFreshMemberString(baseName, null);
  }

  public DexType createFreshTypeName(DexType type, Predicate<DexType> isFresh) {
    return createFreshTypeName(type, isFresh, 0);
  }

  public DexType createFreshTypeName(DexType type, Predicate<DexType> isFresh, int index) {
    while (true) {
      DexType newType = type.addSuffixId(index++, this);
      if (isFresh.test(newType)) {
        return newType;
      }
    }
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form
   * baseName$holder$n, where {@code baseName} and {@code holder} are supplied by the user, and
   * {@code n} is picked to be the first number so that {@code isFresh.apply(method)} returns {@code
   * true}.
   *
   * @param holder indicates where the method originates from.
   */
  public DexMethod createFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh) {
    assert holder != null;
    return internalCreateFreshMethodNameWithHolder(baseName, holder, proto, target, isFresh);
  }

  public DexMethod createFreshMethodNameWithoutHolder(
      String baseName, DexProto proto, DexType target, Predicate<DexMethod> isFresh) {
    return createFreshMethodNameWithoutHolder(baseName, proto, target, isFresh, 0);
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form baseName$n,
   * where {@code baseName} is supplied by the user, and {@code n} is picked to be the first number
   * starting from {@param index} so that {@code isFresh.apply(method)} returns {@code true}.
   */
  public DexMethod createFreshMethodNameWithoutHolder(
      String baseName, DexProto proto, DexType target, Predicate<DexMethod> isFresh, int index) {
    return internalCreateFreshMethodNameWithHolder(baseName, null, proto, target, isFresh, index);
  }

  private DexMethod internalCreateFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh) {
    return internalCreateFreshMethodNameWithHolder(baseName, holder, proto, target, isFresh, 0);
  }

  /**
   * Used to find a fresh method name of the from {@code baseName$n}, or {@code baseName$holder$n}
   * if {@param holder} is non-null.
   */
  private DexMethod internalCreateFreshMethodNameWithHolder(
      String baseName,
      DexType holder,
      DexProto proto,
      DexType target,
      Predicate<DexMethod> isFresh,
      int index) {
    return createFreshMember(
        name -> {
          DexMethod tryMethod = createMethod(target, proto, name);
          if (isFresh.test(tryMethod)) {
            return Optional.of(tryMethod);
          } else {
            return Optional.empty();
          }
        },
        baseName,
        holder,
        index);
  }

  /**
   * Tries to find a method name for insertion into the class {@code target} of the form
   * baseName$holder$n, where {@code baseName} and {@code holder} are supplied by the user, and
   * {@code n} is picked to be the first number so that {@code isFresh.apply(method)} returns {@code
   * true}.
   *
   * @param holder indicates where the method originates from.
   */
  public DexMethodSignature createFreshMethodSignatureName(
      String baseName, DexType holder, DexProto proto, Predicate<DexMethodSignature> isFresh) {
    return createFreshMember(
        name -> {
          DexMethodSignature trySignature = DexMethodSignature.create(name, proto);
          if (isFresh.test(trySignature)) {
            return Optional.of(trySignature);
          } else {
            return Optional.empty();
          }
        },
        baseName,
        holder);
  }

  /**
   * Tries to find a method name for insertion into the class {@code holder} of the form baseName$n,
   * where {@code baseName} is supplied by the user, and {@code n} is picked to be the first number
   * so that {@code isFresh.apply(method)} returns {@code true}.
   */
  public DexField createFreshFieldNameWithoutHolder(
      DexType holder, DexType type, String baseName, Predicate<DexField> isFresh) {
    return internalCreateFreshFieldName(null, holder, type, baseName, isFresh);
  }

  private DexField internalCreateFreshFieldName(
      DexType originalHolder,
      DexType newHolder,
      DexType type,
      String baseName,
      Predicate<DexField> isFresh) {
    return createFreshMember(
        name -> {
          DexField candidate = createField(newHolder, type, name);
          return isFresh.test(candidate) ? Optional.of(candidate) : Optional.empty();
        },
        baseName,
        originalHolder);
  }

  public DexMethod createClassInitializer(DexType holder) {
    return createMethod(holder, createProto(voidType), classConstructorMethodName);
  }

  public DexMethod createInstanceInitializer(DexType holder, DexType... parameters) {
    return createMethod(holder, createProto(voidType, parameters), constructorMethodName);
  }

  public DexMethod createInstanceInitializer(DexType holder, DexTypeList parameters) {
    return createInstanceInitializer(holder, parameters.values);
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method, List<Supplier<DexType>> extraTypes, Predicate<DexMethod> isFresh) {
    return createInstanceInitializerWithFreshProto(method, extraTypes, isFresh, emptyConsumer());
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method,
      List<Supplier<DexType>> extraTypes,
      Predicate<DexMethod> isFresh,
      Consumer<Set<DexType>> usedExtraTypesConsumer) {
    assert method.isInstanceInitializer(this);
    return createInstanceInitializerWithFreshProto(
        method.proto,
        extraTypes,
        proto -> Optional.of(method.withProto(proto, this)).filter(isFresh),
        usedExtraTypesConsumer);
  }

  public DexMethod createInstanceInitializerWithFreshProto(
      DexMethod method, DexType extraType, Predicate<DexMethod> isFresh) {
    assert method.isInstanceInitializer(this);
    return createInstanceInitializerWithFreshProto(
        method.proto,
        ImmutableList.of(() -> extraType),
        proto -> Optional.of(method.withProto(proto, this)).filter(isFresh),
        emptyConsumer());
  }

  private class FreshInstanceInitializerCandidate {

    DexProto protoWithoutExtraType;
    Supplier<DexType> extraTypeSupplier;
    Set<DexType> usedExtraTypes;

    FreshInstanceInitializerCandidate(
        DexProto protoWithoutExtraType,
        Supplier<DexType> extraTypeSupplier,
        Set<DexType> usedExtraTypes) {
      this.protoWithoutExtraType = protoWithoutExtraType;
      this.extraTypeSupplier = extraTypeSupplier;
      this.usedExtraTypes = SetUtils.newIdentityHashSet(usedExtraTypes);
    }

    DexProto createProto() {
      DexType extraType = extraTypeSupplier.get();
      usedExtraTypes.add(extraType);
      return appendTypeToProto(protoWithoutExtraType, extraType);
    }
  }

  private DexMethod createInstanceInitializerWithFreshProto(
      DexProto proto,
      List<Supplier<DexType>> extraTypes,
      Function<DexProto, Optional<DexMethod>> isFresh,
      Consumer<Set<DexType>> usedExtraTypesConsumer) {
    Optional<DexMethod> resultWithNoExtraTypes = isFresh.apply(proto);
    if (resultWithNoExtraTypes.isPresent()) {
      return resultWithNoExtraTypes.get();
    }
    assert !extraTypes.isEmpty();
    Deque<FreshInstanceInitializerCandidate> worklist = new ArrayDeque<>(extraTypes.size());
    for (Supplier<DexType> extraTypeSupplier : extraTypes) {
      worklist.addLast(
          new FreshInstanceInitializerCandidate(proto, extraTypeSupplier, Collections.emptySet()));
    }
    int count = 0;
    while (true) {
      assert count++ < 100;
      assert !worklist.isEmpty();
      FreshInstanceInitializerCandidate candidate = worklist.removeFirst();
      DexProto tryProto = candidate.createProto();
      Optional<DexMethod> object = isFresh.apply(tryProto);
      if (object.isPresent()) {
        assert !candidate.usedExtraTypes.isEmpty();
        usedExtraTypesConsumer.accept(candidate.usedExtraTypes);
        return object.get();
      }
      for (Supplier<DexType> extraTypeSupplier : extraTypes) {
        worklist.addLast(
            new FreshInstanceInitializerCandidate(
                tryProto, extraTypeSupplier, candidate.usedExtraTypes));
      }
    }
  }

  public DexString lookupString(String source) {
    DexString key = new DexString(source);
    DexString committed = committedStrings.get(key);
    if (committed != null) {
      return committed;
    }
    return strings.get(key);
  }

  // Debugging support to extract marking string.
  // Find all markers.
  public synchronized Collection<Marker> extractMarkers() {
    Set<Marker> markers = new HashSet<>();
    for (DexString dexString : this.markers.keySet()) {
      Marker marker = Marker.parse(dexString);
      if (marker != null) {
        markers.add(marker);
      }
    }
    return markers;
  }

  private DexType createStaticallyKnownType(String descriptor) {
    return createStaticallyKnownType(createString(descriptor));
  }

  private DexType createStaticallyKnownType(Class<?> clazz) {
    // This uses Class.getName() and not Class.getTypeName(), as the compilers are also
    // running on Art versions where Class.getTypeName() is not present (7.0 and before).
    return createStaticallyKnownType(
        createString(DescriptorUtils.javaTypeToDescriptor(clazz.getName())));
  }

  private DexType createStaticallyKnownType(DexString descriptor) {
    DexType type = createType(descriptor);
    // Conservatively add all statically known types to "compiler synthesized types set".
    addPossiblySynthesizedType(type);
    return type;
  }

  // Safe synchronized external create. May be used for statically known types in synthetic code.
  // See the generated BackportedMethods.java for reference.
  public synchronized DexType createSynthesizedType(String descriptor) {
    DexType type = createType(createString(descriptor));
    addPossiblySynthesizedType(type);
    return type;
  }

  // Registration of a type that is only dynamically known (eg, in the desugared lib spec), but
  // will be referenced during desugaring.
  public void registerTypeNeededForDesugaring(DexType type) {
    addPossiblySynthesizedType(type);
  }

  private void addPossiblySynthesizedType(DexType type) {
    if (type.isArrayType()) {
      type = type.getBaseType();
    }
    if (type.isClassType()) {
      possibleCompilerSynthesizedTypes.add(type);
    }
  }

  public boolean isPossiblyCompilerSynthesizedType(DexType type) {
    return possibleCompilerSynthesizedTypes.contains(type);
  }

  public void forEachPossiblyCompilerSynthesizedType(Consumer<DexType> fn) {
    possibleCompilerSynthesizedTypes.forEach(fn);
  }

  public DexType createType(DexString descriptor) {
    assert descriptor != null;
    DexType committed = committedTypes.get(descriptor);
    if (committed != null) {
      return committed;
    }
    if (descriptor.getFirstByteAsChar() != '[') {
      return types.computeIfAbsent(descriptor, DexType::new);
    }
    DexType pending = types.get(descriptor);
    if (pending != null) {
      return pending;
    }
    DexType elementType = createType(descriptor.toArrayElementDescriptor(this));
    return types.computeIfAbsent(descriptor, d -> new DexArrayType(d, elementType));
  }

  public DexType createType(String descriptor) {
    return createType(createString(descriptor));
  }

  public DexType createType(ClassReference clazz) {
    return createType(clazz.getDescriptor());
  }

  public DexType lookupType(DexString descriptor) {
    DexType committed = committedTypes.get(descriptor);
    if (committed != null) {
      return committed;
    }
    return types.get(descriptor);
  }

  public DexField createField(DexType clazz, DexType type, DexString name) {
    DexField field = new DexField(clazz, type, name, skipNameValidationForTesting);
    return canonicalize(committedFields, fields, field);
  }

  public DexField createField(DexType clazz, DexType type, String name) {
    return createField(clazz, type, createString(name));
  }

  public DexField createField(FieldReference fieldReference) {
    return createField(
        createType(fieldReference.getHolderClass().getDescriptor()),
        createType(fieldReference.getFieldType().getDescriptor()),
        fieldReference.getFieldName());
  }

  public DexProto createProto(DexType returnType, DexTypeList parameters) {
    DexProto proto = new DexProto(returnType, parameters);
    return canonicalize(committedProtos, protos, proto);
  }

  public DexProto createProto(DexType returnType, DexType... parameters) {
    return createProto(
        returnType, parameters.length == 0 ? DexTypeList.empty() : new DexTypeList(parameters));
  }

  public DexProto createProto(DexType returnType, List<DexType> parameters) {
    return createProto(returnType, parameters.toArray(DexType.EMPTY_ARRAY));
  }

  public DexProto prependHolderToProto(DexMethod method) {
    return method.getProto().prependParameter(method.getHolderType(), this);
  }

  public DexProto prependHolderToProtoIf(DexMethod method, boolean condition) {
    return condition ? prependHolderToProto(method) : method.getProto();
  }

  public DexProto appendTypeToProto(DexProto initialProto, DexType extraLastType) {
    DexType[] parameterTypes = new DexType[initialProto.parameters.size() + 1];
    System.arraycopy(
        initialProto.parameters.values, 0, parameterTypes, 0, initialProto.parameters.size());
    parameterTypes[parameterTypes.length - 1] = extraLastType;
    return createProto(initialProto.returnType, parameterTypes);
  }

  public DexMethod appendTypeToMethod(DexMethod initialMethod, DexType extraLastType) {
    DexProto newProto = appendTypeToProto(initialMethod.proto, extraLastType);
    return createMethod(initialMethod.holder, newProto, initialMethod.name);
  }

  @SuppressWarnings("ReferenceEquality")
  public DexProto applyClassMappingToProto(
      DexProto proto, Function<DexType, DexType> mapping, Map<DexProto, DexProto> cache) {
    assert cache != null;
    DexProto result = cache.get(proto);
    if (result == null) {
      DexType returnType = mapping.apply(proto.returnType);
      DexType[] parameters = applyClassMappingToDexTypes(proto.parameters.values, mapping);
      if (returnType == proto.returnType && parameters == proto.parameters.values) {
        result = proto;
      } else {
        // Should be different if reference has changed.
        assert returnType == proto.returnType || !returnType.equals(proto.returnType);
        assert parameters == proto.parameters.values
            || !Arrays.equals(parameters, proto.parameters.values);
        result = createProto(returnType, parameters);
      }
      cache.put(proto, result);
    }
    return result;
  }

  @SuppressWarnings("ReferenceEquality")
  private static DexType[] applyClassMappingToDexTypes(
      DexType[] types, Function<DexType, DexType> mapping) {
    Map<Integer, DexType> changed = new Int2ReferenceArrayMap<>();
    for (int i = 0; i < types.length; i++) {
      DexType applied = mapping.apply(types[i]);
      if (applied != types[i]) {
        changed.put(i, applied);
      }
    }
    return changed.isEmpty()
        ? types
        : ArrayUtils.copyWithSparseChanges(DexType[].class, types, changed);
  }

  public DexMethod createMethod(DexType holder, DexProto proto, DexString name) {
    DexMethod method = new DexMethod(holder, proto, name, skipNameValidationForTesting);
    return canonicalize(committedMethods, methods, method);
  }

  public DexMethod createMethod(DexType holder, DexProto proto, String name) {
    return createMethod(holder, proto, createString(name));
  }

  public DexMethod createMethod(MethodReference methodReference) {
    DexString[] formals = new DexString[methodReference.getFormalTypes().size()];
    ListUtils.forEachWithIndex(
        methodReference.getFormalTypes(),
        (formal, index) -> {
          formals[index] = createString(formal.getDescriptor());
        });
    return createMethod(
        createString(methodReference.getHolderClass().getDescriptor()),
        createString(methodReference.getMethodName()),
        methodReference.getReturnType() == null
            ? voidDescriptor
            : createString(methodReference.getReturnType().getDescriptor()),
        formals);
  }

  public DexMethodHandle createMethodHandle(
      MethodHandleType type,
      DexMember<? extends DexItem, ? extends DexMember<?, ?>> fieldOrMethod,
      boolean isInterface) {
    return createMethodHandle(type, fieldOrMethod, isInterface, null);
  }

  public DexMethodHandle createMethodHandle(
      MethodHandleType type,
      DexMember<? extends DexItem, ? extends DexMember<?, ?>> fieldOrMethod,
      boolean isInterface,
      DexMethod rewrittenTarget) {
    DexMethodHandle methodHandle =
        new DexMethodHandle(type, fieldOrMethod, isInterface, rewrittenTarget);
    return canonicalize(committedMethodHandles, methodHandles, methodHandle);
  }

  public DexCallSite createCallSite(
      DexString methodName,
      DexProto methodProto,
      DexMethodHandle bootstrapMethod,
      List<DexValue> bootstrapArgs) {
    // Call sites are never equal and therefore we do not canonicalize.
    return new DexCallSite(methodName, methodProto, bootstrapMethod, bootstrapArgs);
  }

  public DexMethod createMethod(
      DexString clazzDescriptor,
      DexString name,
      DexString returnTypeDescriptor,
      DexString[] parameterDescriptors) {
    DexType clazz = createType(clazzDescriptor);
    DexType returnType = createType(returnTypeDescriptor);
    DexType[] parameterTypes = new DexType[parameterDescriptors.length];
    for (int i = 0; i < parameterDescriptors.length; i++) {
      parameterTypes[i] = createType(parameterDescriptors[i]);
    }
    DexProto proto = createProto(returnType, parameterTypes);

    return createMethod(clazz, proto, name);
  }

  public DexMethod createClinitMethod(DexType holder) {
    return createMethod(holder, createProto(voidType), classConstructorMethodName);
  }

  public AdvanceLine createAdvanceLine(int delta) {
    synchronized (advanceLines) {
      return advanceLines.computeIfAbsent(delta, AdvanceLine::new);
    }
  }

  public AdvancePC createAdvancePC(int delta) {
    synchronized (advancePCs) {
      return advancePCs.computeIfAbsent(delta, AdvancePC::new);
    }
  }

  public Default createDefault(int value) {
    synchronized (defaults) {
      return defaults.computeIfAbsent(value, Default::new);
    }
  }

  public Default createDefault(int lineDelta, int pcDelta) {
    return createDefault(Default.create(lineDelta, pcDelta).value);
  }

  public EndLocal createEndLocal(int registerNum) {
    synchronized (endLocals) {
      return endLocals.computeIfAbsent(registerNum, EndLocal::new);
    }
  }

  public RestartLocal createRestartLocal(int registerNum) {
    synchronized (restartLocals) {
      return restartLocals.computeIfAbsent(registerNum, RestartLocal::new);
    }
  }

  public SetEpilogueBegin createSetEpilogueBegin() {
    return setEpilogueBegin;
  }

  public SetPrologueEnd createSetPrologueEnd() {
    return setPrologueEnd;
  }

  public SetFile createSetFile(DexString fileName) {
    synchronized (setFiles) {
      return setFiles.computeIfAbsent(fileName, SetFile::new);
    }
  }

  // TODO(tamaskenez) b/69024229 Measure if canonicalization is worth it.
  public SetPositionFrame createPositionFrame(Position position) {
    synchronized (setInlineFrames) {
      return setInlineFrames.computeIfAbsent(new SetPositionFrame(position), p -> p);
    }
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isConstructor(DexMethod method) {
    return method.name == constructorMethodName;
  }

  @SuppressWarnings("ReferenceEquality")
  public boolean isClassConstructor(DexMethod method) {
    return method.name == classConstructorMethodName;
  }

  public Collection<DexType> getCommittedTypes() {
    return committedTypes.values();
  }

  public void commitPendingItems() {
    commitPendingItems(committedMethodHandles, methodHandles);
    commitPendingItems(committedStrings, strings);
    commitPendingItems(committedTypes, types);
    commitPendingItems(committedFields, fields);
    commitPendingItems(committedProtos, protos);
    commitPendingItems(committedMethods, methods);
  }

  public int getNumberOfUncommittedItems() {
    return methodHandles.size()
        + strings.size()
        + types.size()
        + fields.size()
        + protos.size()
        + methods.size();
  }

  private static <K, V> void commitPendingItems(Map<K, V> committed, Map<K, V> pending) {
    committed.putAll(pending);
    pending.clear();
  }
}
