// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.cf.code.CfInstruction;
import com.android.tools.r8.cf.code.CfInvoke;
import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.dex.ApplicationReader;
import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.CfCode;
import com.android.tools.r8.graph.Code;
import com.android.tools.r8.graph.DexApplication;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.backports.BackportedMethodDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.backports.BackportedMethods;
import com.android.tools.r8.ir.desugar.backports.BooleanMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.CollectionMethodGenerators;
import com.android.tools.r8.ir.desugar.backports.CollectionMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.FloatMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.LongMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.NumericMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.ObjectsMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.OptionalMethodRewrites;
import com.android.tools.r8.ir.desugar.backports.SparseArrayMethodRewrites;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeter;
import com.android.tools.r8.synthesis.SyntheticNaming;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableList;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.function.Consumer;
import org.objectweb.asm.Opcodes;

public final class BackportedMethodRewriter implements CfInstructionDesugaring {

  private final AppView<?> appView;
  private final RewritableMethods rewritableMethods;

  public BackportedMethodRewriter(AppView<?> appView) {
    assert appView.options().desugarState.isOn();
    this.appView = appView;
    this.rewritableMethods = new RewritableMethods(appView.options(), appView);
  }

  public boolean hasBackports() {
    return !rewritableMethods.isEmpty();
  }

  @Override
  public Collection<CfInstruction> desugarInstruction(
      CfInstruction instruction,
      FreshLocalProvider freshLocalProvider,
      LocalStackAllocator localStackAllocator,
      CfInstructionDesugaringEventConsumer eventConsumer,
      ProgramMethod context,
      MethodProcessingContext methodProcessingContext,
      CfInstructionDesugaringCollection desugaringCollection,
      DexItemFactory dexItemFactory) {
    if (!instruction.isInvoke()) {
      return null;
    }

    CfInvoke invoke = instruction.asInvoke();
    MethodProvider methodProvider = getMethodProviderOrNull(invoke.getMethod());
    return methodProvider != null
        ? methodProvider.rewriteInvoke(
            invoke, appView, eventConsumer, methodProcessingContext, localStackAllocator)
        : null;
  }

  @Override
  public boolean needsDesugaring(CfInstruction instruction, ProgramMethod context) {
    return instruction.isInvoke()
        && getMethodProviderOrNull(instruction.asInvoke().getMethod()) != null
        && !appView
            .getSyntheticItems()
            .isSyntheticOfKind(context.getContextType(), SyntheticKind.BACKPORT_WITH_FORWARDING);
  }

  public static List<DexMethod> generateListOfBackportedMethods(
      AndroidApp androidApp, InternalOptions options, ExecutorService executor) throws IOException {
    List<DexMethod> methods = new ArrayList<>();
    TypeRewriter typeRewriter = options.getTypeRewriter();
    AppInfo appInfo = null;
    if (androidApp != null) {
      DexApplication app =
          new ApplicationReader(androidApp, options, Timing.empty()).read(executor);
      appInfo = AppInfo.createInitialAppInfo(app);
    }
    AppView<?> appView = AppView.createForD8(appInfo, typeRewriter);
    BackportedMethodRewriter.RewritableMethods rewritableMethods =
        new BackportedMethodRewriter.RewritableMethods(options, appView);
    rewritableMethods.visit(methods::add);
    if (appInfo != null) {
      DesugaredLibraryRetargeter desugaredLibraryRetargeter =
          new DesugaredLibraryRetargeter(appView);
      desugaredLibraryRetargeter.visit(methods::add);
    }
    return methods;
  }

  public static void registerAssumedLibraryTypes(InternalOptions options) {
    // TODO(b/150693139): Remove the pre-registration once fixed.
    BackportedMethods.registerSynthesizedCodeReferences(options.itemFactory);
  }

  private MethodProvider getMethodProviderOrNull(DexMethod method) {
    DexMethod original = appView.graphLens().getOriginalMethodSignature(method);
    assert original != null;
    MethodProvider provider = rewritableMethods.getProvider(original);
    // Old versions of desugared library have in the jar file pre-desugared code. This is used
    // to undesugar pre-desugared code, then the code is re-desugared with D8/R8. This is
    // maintained for legacy only, recent desugared library should not be shipped with
    // pre-desugared code.
    Map<DexType, DexType> legacyBackport =
        appView.options().machineDesugaredLibrarySpecification.getLegacyBackport();
    if (provider == null
        && appView.options().isDesugaredLibraryCompilation()
        && legacyBackport.containsKey(method.holder)) {
      DexType newHolder = legacyBackport.get(method.holder);
      DexMethod backportedMethod =
          appView.dexItemFactory().createMethod(newHolder, method.proto, method.name);
      provider = rewritableMethods.getProvider(backportedMethod);
    }
    return provider;
  }

  private static final class RewritableMethods {

    private final AppView<?> appView;

    // Map backported method to a provider for creating the actual target method (with code).
    private final Map<DexMethod, MethodProvider> rewritable = new IdentityHashMap<>();

    RewritableMethods(InternalOptions options, AppView<?> appView) {
      this.appView = appView;

      if (!options.shouldBackportMethods()) {
        return;
      }

      DexItemFactory factory = options.itemFactory;

      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.K)) {
        initializeAndroidKMethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.N)) {
        initializeAndroidNMethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.O)) {
        initializeAndroidOMethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.R)) {
        initializeAndroidRMethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.S)) {
        initializeAndroidSMethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.Sv2)) {
        initializeAndroidSv2MethodProviders(factory);
      }
      if (options.getMinApiLevel().isLessThan(AndroidApiLevel.T)) {
        initializeAndroidTMethodProviders(factory);
      }

      // The following providers are implemented at API level T. For backporting they require
      // the java.util.Optional class to be present, either through library desugaring or natively.
      // If the java.util.Optional class is not present, we do not backport to avoid confusion in
      // error messages.
      if (appView.typeRewriter.hasRewrittenType(factory.optionalType, appView)
          || options.getMinApiLevel().betweenBothIncluded(AndroidApiLevel.N, AndroidApiLevel.Sv2)) {
        initializeAndroidOptionalTMethodProviders(factory);
      }

      // The following providers are currently not implemented at any API level in Android. For
      // backporting they require the java.util.stream.Stream class to be present, either through
      // library desugaring or natively. If the java.util.stream.Stream class is not present, we do
      // not desugar to avoid confusion in error messages.
      if (appView.typeRewriter.hasRewrittenType(factory.streamType, appView)
          || options.getMinApiLevel().isGreaterThanOrEqualTo(AndroidApiLevel.N)) {
        initializeStreamMethodProviders(factory);
      }

      if (appView.typeRewriter.hasRewrittenType(factory.supplierType, appView)) {
        // TODO(b/191188594): Consider adding the Objects method from R here, or instead
        //  rely on desugared library to support them.
        initializeObjectsMethodProviders(factory);
      }

      // These are currently not implemented at any API level in Android.
      initializeJava9MethodProviders(factory);
      initializeJava10MethodProviders(factory);
      initializeJava11MethodProviders(factory);
    }

    boolean isEmpty() {
      return rewritable.isEmpty();
    }

    public void visit(Consumer<DexMethod> consumer) {
      rewritable.keySet().forEach(consumer);
    }

    private void initializeAndroidKMethodProviders(DexItemFactory factory) {
      // Byte
      DexType type = factory.boxedByteType;
      // int Byte.compare(byte a, byte b)
      DexString name = factory.createString("compare");
      DexProto proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      DexMethod method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ByteMethods_compare));

      // Short
      type = factory.boxedShortType;
      // int Short.compare(short a, short b)
      name = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ShortMethods_compare));

      // Integer
      type = factory.boxedIntType;
      // int Integer.compare(int a, int b)
      name = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_compare));

      // Long
      type = factory.boxedLongType;
      // int Long.compare(long a, long b)
      name = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, LongMethodRewrites.rewriteCompare()));

      // Boolean
      type = factory.boxedBooleanType;
      // int Boolean.compare(boolean a, boolean b)
      name = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.booleanType, factory.booleanType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::BooleanMethods_compare));

      // Character
      type = factory.boxedCharType;
      // int Character.compare(char a, char b)
      name = factory.createString("compare");
      proto = factory.createProto(factory.intType, factory.charType, factory.charType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::CharacterMethods_compare));

      // Objects
      type = factory.objectsType;

      // int Objects.compare(T a, T b, Comparator<? super T> c)
      name = factory.createString("compare");
      proto =
          factory.createProto(
              factory.intType, factory.objectType, factory.objectType, factory.comparatorType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_compare));

      // boolean Objects.deepEquals(Object a, Object b)
      name = factory.createString("deepEquals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_deepEquals));

      // boolean Objects.equals(Object a, Object b)
      name = factory.createString("equals");
      proto = factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_equals));

      // int Objects.hash(Object... o)
      name = factory.createString("hash");
      proto = factory.createProto(factory.intType, factory.objectArrayType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, ObjectsMethodRewrites.rewriteToArraysHashCode()));

      // int Objects.hashCode(Object o)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_hashCode));

      // T Objects.requireNonNull(T obj)
      method = factory.objectsMethods.requireNonNull;
      addProvider(new InvokeRewriter(method, ObjectsMethodRewrites.rewriteRequireNonNull()));

      // T Objects.requireNonNull(T obj, String message)
      name = factory.createString("requireNonNull");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.stringType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method,
              BackportedMethods::ObjectsMethods_requireNonNullMessage,
              "requireNonNullMessage"));

      // String Objects.toString(Object o)
      name = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_toString));

      // String Objects.toString(Object o, String nullDefault);
      name = factory.createString("toString");
      proto = factory.createProto(factory.stringType, factory.objectType, factory.stringType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::ObjectsMethods_toStringDefault, "toStringDefault"));

      // Collections
      type = factory.collectionsType;

      // Enumeration<T> Collections.emptyEnumeration();
      name = factory.createString("emptyEnumeration");
      proto = factory.createProto(factory.enumerationType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::CollectionsMethods_emptyEnumeration));

      // Iterator<T> Collections.emptyIterator();
      name = factory.createString("emptyIterator");
      proto = factory.createProto(factory.iteratorType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::CollectionsMethods_emptyIterator));

      // ListIterator<T> Collections.emptyListIterator();
      name = factory.createString("emptyListIterator");
      proto = factory.createProto(factory.listIteratorType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::CollectionsMethods_emptyListIterator));
    }

    private void initializeAndroidNMethodProviders(DexItemFactory factory) {
      // Byte
      DexType type = factory.boxedByteType;
      // int Byte.hashCode(byte i)
      DexString name = factory.createString("hashCode");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      DexMethod method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteAsIdentity()));

      // Short
      type = factory.boxedShortType;
      // int Short.hashCode(short i)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.shortType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteAsIdentity()));

      // Integer
      type = factory.boxedIntType;

      // int Integer.hashCode(int i)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteAsIdentity()));

      // int Integer.max(int a, int b)
      name = factory.createString("max");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // int Integer.min(int a, int b)
      name = factory.createString("min");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // int Integer.sum(int a, int b)
      name = factory.createString("sum");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToAddInstruction()));

      // Double
      type = factory.boxedDoubleType;

      // int Double.hashCode(double d)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.doubleType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::DoubleMethods_hashCode));

      // double Double.max(double a, double b)
      name = factory.createString("max");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // double Double.min(double a, double b)
      name = factory.createString("min");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // double Double.sum(double a, double b)
      name = factory.createString("sum");
      proto = factory.createProto(factory.doubleType, factory.doubleType, factory.doubleType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToAddInstruction()));

      // boolean Double.isFinite(double a)
      name = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.doubleType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::DoubleMethods_isFinite));

      // Float
      type = factory.boxedFloatType;

      // int Float.hashCode(float d)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.floatType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, FloatMethodRewrites.rewriteHashCode()));

      // float Float.max(float a, float b)
      name = factory.createString("max");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // float Float.min(float a, float b)
      name = factory.createString("min");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // float Float.sum(float a, float b)
      name = factory.createString("sum");
      proto = factory.createProto(factory.floatType, factory.floatType, factory.floatType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToAddInstruction()));

      // boolean Float.isFinite(float a)
      name = factory.createString("isFinite");
      proto = factory.createProto(factory.booleanType, factory.floatType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::FloatMethods_isFinite));

      // Boolean
      type = factory.boxedBooleanType;

      // int Boolean.hashCode(boolean b)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.booleanType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::BooleanMethods_hashCode));

      // boolean Boolean.logicalAnd(boolean a, boolean b)
      name = factory.createString("logicalAnd");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, BooleanMethodRewrites.rewriteLogicalAnd()));

      // boolean Boolean.logicalOr(boolean a, boolean b)
      name = factory.createString("logicalOr");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, BooleanMethodRewrites.rewriteLogicalOr()));

      // boolean Boolean.logicalXor(boolean a, boolean b)
      name = factory.createString("logicalXor");
      proto = factory.createProto(factory.booleanType, factory.booleanType, factory.booleanType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, BooleanMethodRewrites.rewriteLogicalXor()));

      // Long
      type = factory.boxedLongType;

      // int Long.hashCode(long i)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_hashCode));

      // long Long.max(long a, long b)
      name = factory.createString("max");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // long Long.min(long a, long b)
      name = factory.createString("min");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToInvokeMath()));

      // long Long.sum(long a, long b)
      name = factory.createString("sum");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteToAddInstruction()));

      // Character
      type = factory.boxedCharType;

      // int Character.hashCode(char i)
      name = factory.createString("hashCode");
      proto = factory.createProto(factory.intType, factory.charType);
      method = factory.createMethod(type, proto, name);
      addProvider(new InvokeRewriter(method, NumericMethodRewrites.rewriteAsIdentity()));

      // Objects
      type = factory.objectsType;

      // boolean Objects.isNull(Object o)
      name = factory.createString("isNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_isNull));

      // boolean Objects.nonNull(Object a)
      name = factory.createString("nonNull");
      proto = factory.createProto(factory.booleanType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_nonNull));

      // Math & StrictMath, which have some symmetric, binary-compatible APIs
      DexType[] mathTypes = {factory.mathType, factory.strictMathType};
      for (DexType mathType : mathTypes) {

        // int {Math,StrictMath}.addExact(int, int)
        name = factory.createString("addExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(method, BackportedMethods::MathMethods_addExactInt, "addExactInt"));

        // long {Math,StrictMath}.addExact(long, long)
        name = factory.createString("addExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_addExactLong, "addExactLong"));

        // int {Math,StrictMath}.floorDiv(int, int)
        name = factory.createString("floorDiv");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(method, BackportedMethods::MathMethods_floorDivInt, "floorDivInt"));

        // long {Math,StrictMath}.floorDiv(long, long)
        name = factory.createString("floorDiv");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_floorDivLong, "floorDivLong"));

        // int {Math,StrictMath}.floorMod(int, int)
        name = factory.createString("floorMod");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(method, BackportedMethods::MathMethods_floorModInt, "floorModInt"));

        // long {Math,StrictMath}.floorMod(long, long)
        name = factory.createString("floorMod");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_floorModLong, "floorModLong"));

        // int {Math,StrictMath}.multiplyExact(int, int)
        name = factory.createString("multiplyExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_multiplyExactInt, "multiplyExactInt"));

        // long {Math,StrictMath}.multiplyExact(long, long)
        name = factory.createString("multiplyExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_multiplyExactLong, "multiplyExactLong"));

        // double {Math,StrictMath}.nextDown(double)
        name = factory.createString("nextDown");
        proto = factory.createProto(factory.doubleType, factory.doubleType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_nextDownDouble, "nextDownDouble"));

        // float {Math,StrictMath}.nextDown(float)
        name = factory.createString("nextDown");
        proto = factory.createProto(factory.floatType, factory.floatType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_nextDownFloat, "nextDownFloat"));

        // int {Math,StrictMath}.subtractExact(int, int)
        name = factory.createString("subtractExact");
        proto = factory.createProto(factory.intType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_subtractExactInt, "subtractExactInt"));

        // long {Math,StrictMath}.subtractExact(long, long)
        name = factory.createString("subtractExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_subtractExactLong, "subtractExactLong"));

        // int {Math,StrictMath}.toIntExact(long)
        name = factory.createString("toIntExact");
        proto = factory.createProto(factory.intType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(new MethodGenerator(method, BackportedMethods::MathMethods_toIntExact));
      }

      // Math (APIs which are not mirrored by StrictMath)
      type = factory.mathType;

      // int Math.decrementExact(int)
      name = factory.createString("decrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_decrementExactInt, "decrementExactInt"));

      // long Math.decrementExact(long)
      name = factory.createString("decrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_decrementExactLong, "decrementExactLong"));

      // int Math.incrementExact(int)
      name = factory.createString("incrementExact");
      proto = factory.createProto(factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_incrementExactInt, "incrementExactInt"));

      // long Math.incrementExact(long)
      name = factory.createString("incrementExact");
      proto = factory.createProto(factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_incrementExactLong, "incrementExactLong"));

      // int Math.negateExact(int)
      name = factory.createString("negateExact");
      proto = factory.createProto(factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_negateExactInt, "negateExactInt"));

      // long Math.negateExact(long)
      name = factory.createString("negateExact");
      proto = factory.createProto(factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::MathMethods_negateExactLong, "negateExactLong"));
    }

    private void initializeAndroidOMethodProviders(DexItemFactory factory) {
      // Byte
      DexType type = factory.boxedByteType;

      // int Byte.toUnsignedInt(byte value)
      DexString name = factory.createString("toUnsignedInt");
      DexProto proto = factory.createProto(factory.intType, factory.byteType);
      DexMethod method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ByteMethods_toUnsignedInt));

      // long Byte.toUnsignedLong(byte value)
      name = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.byteType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ByteMethods_toUnsignedLong));

      // Short
      type = factory.boxedShortType;

      // int Short.toUnsignedInt(short value)
      name = factory.createString("toUnsignedInt");
      proto = factory.createProto(factory.intType, factory.shortType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ShortMethods_toUnsignedInt));

      // long Short.toUnsignedLong(short value)
      name = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.shortType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ShortMethods_toUnsignedLong));

      // Integer
      type = factory.boxedIntType;

      // int Integer.divideUnsigned(int a, int b)
      name = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_divideUnsigned));

      // int Integer.remainderUnsigned(int a, int b)
      name = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_remainderUnsigned));

      // int Integer.compareUnsigned(int a, int b)
      name = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_compareUnsigned));

      // long Integer.toUnsignedLong(int value)
      name = factory.createString("toUnsignedLong");
      proto = factory.createProto(factory.longType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_toUnsignedLong));

      // int Integer.parseUnsignedInt(String value)
      name = factory.createString("parseUnsignedInt");
      proto = factory.createProto(factory.intType, factory.stringType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_parseUnsignedInt));

      // int Integer.parseUnsignedInt(String value, int radix)
      name = factory.createString("parseUnsignedInt");
      proto = factory.createProto(factory.intType, factory.stringType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method,
              BackportedMethods::IntegerMethods_parseUnsignedIntWithRadix,
              "parseUnsignedIntWithRadix"));

      // String Integer.toUnsignedString(int value)
      name = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::IntegerMethods_toUnsignedString));

      // String Integer.toUnsignedString(int value, int radix)
      name = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method,
              BackportedMethods::IntegerMethods_toUnsignedStringWithRadix,
              "toUnsignedStringWithRadix"));

      // Long
      type = factory.boxedLongType;

      // long Long.divideUnsigned(long a, long b)
      name = factory.createString("divideUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_divideUnsigned));

      // long Long.remainderUnsigned(long a, long b)
      name = factory.createString("remainderUnsigned");
      proto = factory.createProto(factory.longType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_remainderUnsigned));

      // int Long.compareUnsigned(long a, long b)
      name = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.longType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_compareUnsigned));

      // long Long.parseUnsignedLong(String value)
      name = factory.createString("parseUnsignedLong");
      proto = factory.createProto(factory.longType, factory.stringType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_parseUnsignedLong));

      // long Long.parseUnsignedLong(String value, int radix)
      name = factory.createString("parseUnsignedLong");
      proto = factory.createProto(factory.longType, factory.stringType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method,
              BackportedMethods::LongMethods_parseUnsignedLongWithRadix,
              "parseUnsignedLongWithRadix"));

      // String Long.toUnsignedString(long value)
      name = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.longType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::LongMethods_toUnsignedString));

      // String Long.toUnsignedString(long value, int radix)
      name = factory.createString("toUnsignedString");
      proto = factory.createProto(factory.stringType, factory.longType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method,
              BackportedMethods::LongMethods_toUnsignedStringWithRadix,
              "toUnsignedStringWithRadix"));

      // String
      type = factory.stringType;

      // String String.join(CharSequence, CharSequence...)
      name = factory.createString("join");
      proto =
          factory.createProto(
              factory.stringType, factory.charSequenceType, factory.charSequenceArrayType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::StringMethods_joinArray, "joinArray"));

      // String String.join(CharSequence, Iterable<? extends CharSequence>)
      name = factory.createString("join");
      proto =
          factory.createProto(factory.stringType, factory.charSequenceType, factory.iterableType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::StringMethods_joinIterable, "joinIterable"));
    }

    private void initializeAndroidRMethodProviders(DexItemFactory factory) {
      DexType type;
      DexString name;
      DexProto proto;
      DexMethod method;

      // Objects
      type = factory.objectsType;

      // T Objects.requireNonNullElse(T, T)
      name = factory.createString("requireNonNullElse");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.objectType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::ObjectsMethods_requireNonNullElse));

      // T Objects.requireNonNullElseGet(T, Supplier<? extends T>)
      name = factory.createString("requireNonNullElseGet");
      proto = factory.createProto(factory.objectType, factory.objectType, factory.supplierType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::ObjectsMethods_requireNonNullElseGet));

      // int Objects.checkIndex(int, int)
      name = factory.createString("checkIndex");
      proto = factory.createProto(factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_checkIndex));

      // int Objects.checkFromToIndex(int, int, int)
      name = factory.createString("checkFromToIndex");
      proto =
          factory.createProto(factory.intType, factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ObjectsMethods_checkFromToIndex));

      // int Objects.checkFromIndexSize(int, int, int)
      name = factory.createString("checkFromIndexSize");
      proto =
          factory.createProto(factory.intType, factory.intType, factory.intType, factory.intType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::ObjectsMethods_checkFromIndexSize));

      // List<E> List.of(<args>) for 0 to 10 arguments and List.of(E[])
      type = factory.listType;
      name = factory.createString("of");
      for (int i = 0; i <= 10; i++) {
        final int formalCount = i;
        proto = factory.createProto(type, Collections.nCopies(i, factory.objectType));
        method = factory.createMethod(type, proto, name);
        addProvider(
            i == 0
                ? new InvokeRewriter(method, CollectionMethodRewrites.rewriteListOfEmpty())
                : new MethodGenerator(
                    method,
                    (options, methodArg) ->
                        CollectionMethodGenerators.generateListOf(
                            options, methodArg, formalCount)));
      }
      proto = factory.createProto(type, factory.objectArrayType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::CollectionMethods_listOfArray, "ofArray"));

      // Set<E> Set.of(<args>) for 0 to 10 arguments and Set.of(E[])
      type = factory.setType;
      name = factory.createString("of");
      for (int i = 0; i <= 10; i++) {
        final int formalCount = i;
        proto = factory.createProto(type, Collections.nCopies(i, factory.objectType));
        method = factory.createMethod(type, proto, name);
        addProvider(
            i == 0
                ? new InvokeRewriter(method, CollectionMethodRewrites.rewriteSetOfEmpty())
                : new MethodGenerator(
                    method,
                    (options, methodArg) ->
                        CollectionMethodGenerators.generateSetOf(options, methodArg, formalCount)));
      }
      proto = factory.createProto(type, factory.objectArrayType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::CollectionMethods_setOfArray, "ofArray"));

      // Map<K, V> Map.of(<K, V args>) for 0 to 10 pairs and Map.ofEntries(Map.Entry<K, V>[])
      type = factory.mapType;
      name = factory.createString("of");
      for (int i = 0; i <= 10; i++) {
        final int formalCount = i;
        proto = factory.createProto(type, Collections.nCopies(i * 2, factory.objectType));
        method = factory.createMethod(type, proto, name);
        addProvider(
            i == 0
                ? new InvokeRewriter(method, CollectionMethodRewrites.rewriteMapOfEmpty())
                : new MethodGenerator(
                    method,
                    (options, methodArg) ->
                        CollectionMethodGenerators.generateMapOf(options, methodArg, formalCount)));
      }
      proto = factory.createProto(type, factory.createArrayType(1, factory.mapEntryType));
      method = factory.createMethod(type, proto, "ofEntries");
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::CollectionMethods_mapOfEntries, "ofEntries"));

      // Map.Entry<K, V> Map.entry(K, V)
      type = factory.mapType;
      proto = factory.createProto(factory.mapEntryType, factory.objectType, factory.objectType);
      method = factory.createMethod(type, proto, "entry");
      addProvider(new MethodGenerator(method, BackportedMethods::CollectionMethods_mapEntry));
    }

    private void initializeAndroidSMethodProviders(DexItemFactory factory) {
      DexType type;
      DexString name;
      DexProto proto;
      DexMethod method;

      // List
      type = factory.listType;

      // List List.copyOf(Collection)
      name = factory.createString("copyOf");
      proto = factory.createProto(factory.listType, factory.collectionType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::CollectionsMethods_copyOfList, "copyOfList"));

      // Set
      type = factory.setType;

      // Set Set.copyOf(Collection)
      name = factory.createString("copyOf");
      proto = factory.createProto(factory.setType, factory.collectionType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::CollectionsMethods_copyOfSet, "copyOfSet"));

      // Map
      type = factory.mapType;

      // Map Map.copyOf(Map)
      name = factory.createString("copyOf");
      proto = factory.createProto(factory.mapType, factory.mapType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::CollectionsMethods_copyOfMap, "copyOfMap"));

      // Byte
      type = factory.boxedByteType;

      // int Byte.compareUnsigned(byte, byte)
      name = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.byteType, factory.byteType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ByteMethods_compareUnsigned));

      // Short
      type = factory.boxedShortType;

      // int Short.compareUnsigned(short, short)
      name = factory.createString("compareUnsigned");
      proto = factory.createProto(factory.intType, factory.shortType, factory.shortType);
      method = factory.createMethod(type, proto, name);
      addProvider(new MethodGenerator(method, BackportedMethods::ShortMethods_compareUnsigned));

      // Math & StrictMath, which have some symmetric, binary-compatible APIs
      DexType[] mathTypes = {factory.mathType, factory.strictMathType};
      for (DexType mathType : mathTypes) {

        // long {Math,StrictMath}.multiplyExact(long, int)
        name = factory.createString("multiplyExact");
        proto = factory.createProto(factory.longType, factory.longType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method,
                BackportedMethods::MathMethods_multiplyExactLongInt,
                "multiplyExactLongInt"));

        // long {Math,StrictMath}.multiplyFull(int, int)
        name = factory.createString("multiplyFull");
        proto = factory.createProto(factory.longType, factory.intType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(new MethodGenerator(method, BackportedMethods::MathMethods_multiplyFull));

        // long {Math,StrictMath}.multiplyHigh(long, long)
        name = factory.createString("multiplyHigh");
        proto = factory.createProto(factory.longType, factory.longType, factory.longType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(new MethodGenerator(method, BackportedMethods::MathMethods_multiplyHigh));

        // long {Math,StrictMath}.floorDiv(long, int)
        name = factory.createString("floorDiv");
        proto = factory.createProto(factory.longType, factory.longType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_floorDivLongInt, "floorDivLongInt"));

        // int {Math,StrictMath}.floorMod(long, int)
        name = factory.createString("floorMod");
        proto = factory.createProto(factory.intType, factory.longType, factory.intType);
        method = factory.createMethod(mathType, proto, name);
        addProvider(
            new MethodGenerator(
                method, BackportedMethods::MathMethods_floorModLongInt, "floorModLongInt"));
      }

      // android.util.SparseArray

      // void android.util.SparseArray.set(int, Object))
      addProvider(
          new InvokeRewriter(
              factory.androidUtilSparseArrayMembers.set, SparseArrayMethodRewrites.rewriteSet()));
    }

    private void initializeAndroidSv2MethodProviders(DexItemFactory factory) {
      // sun.misc.Unsafe
      {
        // compareAndSwapObject(Object receiver, long offset, Object expect, Object update)
        DexType type = factory.unsafeType;
        DexString name = factory.createString("compareAndSwapObject");
        DexProto proto =
            factory.createProto(
                factory.booleanType,
                factory.objectType,
                factory.longType,
                factory.objectType,
                factory.objectType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodWithForwardingGenerator(
                method,
                BackportedMethods::UnsafeMethods_compareAndSwapObject,
                "compareAndSwapObject",
                type));
      }

      // java.util.concurrent.atomic.AtomicReference
      {
        // compareAndSet(Object expect, Object update)
        DexType type = factory.createType("Ljava/util/concurrent/atomic/AtomicReference;");
        DexString name = factory.createString("compareAndSet");
        DexProto proto =
            factory.createProto(factory.booleanType, factory.objectType, factory.objectType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodWithForwardingGenerator(
                method,
                BackportedMethods::AtomicReferenceMethods_compareAndSet,
                "compareAndSet",
                type));
      }

      // java.util.concurrent.atomic.AtomicReferenceArray
      {
        // compareAndSet(int index, Object expect, Object update)
        DexType type = factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceArray;");
        DexString name = factory.createString("compareAndSet");
        DexProto proto =
            factory.createProto(
                factory.booleanType, factory.intType, factory.objectType, factory.objectType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodWithForwardingGenerator(
                method,
                BackportedMethods::AtomicReferenceArrayMethods_compareAndSet,
                "compareAndSet",
                type));
      }

      // java.util.concurrent.atomic.AtomicReferenceFieldUpdater
      {
        // compareAndSet(Object object, Object expect, Object update)
        DexType type =
            factory.createType("Ljava/util/concurrent/atomic/AtomicReferenceFieldUpdater;");
        DexString name = factory.createString("compareAndSet");
        DexProto proto =
            factory.createProto(
                factory.booleanType, factory.objectType, factory.objectType, factory.objectType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodWithForwardingGenerator(
                method,
                BackportedMethods::AtomicReferenceFieldUpdaterMethods_compareAndSet,
                "compareAndSet",
                type));
      }
    }

    private void initializeAndroidTMethodProviders(DexItemFactory factory) {
      // java.lang.Integer.
      {
        // int Integer.parseInt(CharSequence s, int beginIndex, int endIndex, int radix)
        DexType type = factory.boxedIntType;
        DexString name = factory.createString("parseInt");
        DexProto proto =
            factory.createProto(
                factory.intType,
                factory.charSequenceType,
                factory.intType,
                factory.intType,
                factory.intType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            appView.options().canParseNumbersWithPlusPrefix()
                ? new MethodGenerator(
                    method,
                    BackportedMethods::IntegerMethods_parseIntSubsequenceWithRadix,
                    "parseIntSubsequenceWithRadix")
                : new MethodGenerator(
                    method,
                    BackportedMethods::IntegerMethods_parseIntSubsequenceWithRadixDalvik,
                    "parseIntSubsequenceWithRadix"));
      }
      {
        // int Integer.parseUnsignedInt(CharSequence s, int beginIndex, int endIndex, int radix)
        DexType type = factory.boxedIntType;
        DexString name = factory.createString("parseUnsignedInt");
        DexProto proto =
            factory.createProto(
                factory.intType,
                factory.charSequenceType,
                factory.intType,
                factory.intType,
                factory.intType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new MethodGenerator(
                method,
                BackportedMethods::IntegerMethods_parseUnsignedIntSubsequenceWithRadix,
                "parseIntSubsequenceWithRadix"));
      }

      // java.lang.Long.
      {
        // long Long.parseLong(CharSequence s, int beginIndex, int endIndex, int radix)
        DexType type = factory.boxedLongType;
        DexString name = factory.createString("parseLong");
        DexProto proto =
            factory.createProto(
                factory.longType,
                factory.charSequenceType,
                factory.intType,
                factory.intType,
                factory.intType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            appView.options().canParseNumbersWithPlusPrefix()
                ? new MethodGenerator(
                    method,
                    BackportedMethods::LongMethods_parseLongSubsequenceWithRadix,
                    "parseLongSubsequenceWithRadix")
                : new MethodGenerator(
                    method,
                    BackportedMethods::LongMethods_parseLongSubsequenceWithRadixDalvik,
                    "parseLongSubsequenceWithRadix"));
      }
      {
        // long Long.parseUnsignedLong(CharSequence s, int beginIndex, int endIndex, int radix)
        DexType type = factory.boxedLongType;
        DexString name = factory.createString("parseUnsignedLong");
        DexProto proto =
            factory.createProto(
                factory.longType,
                factory.charSequenceType,
                factory.intType,
                factory.intType,
                factory.intType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new MethodGenerator(
                method,
                BackportedMethods::LongMethods_parseUnsignedLongSubsequenceWithRadix,
                "parseUnsignedLongSubsequenceWithRadix"));
      }
      // java.lang.String.
      {
        // String String.repeat(int)
        DexType type = factory.stringType;
        DexString name = factory.createString("repeat");
        DexProto proto = factory.createProto(factory.stringType, factory.intType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::StringMethods_repeat, "repeat", type));
      }
      {
        // boolean String.isBlank()
        DexType type = factory.stringType;
        DexString name = factory.createString("isBlank");
        DexProto proto = factory.createProto(factory.booleanType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::StringMethods_isBlank, "isBlank", type));
      }
      {
        // String String.strip()
        DexType type = factory.stringType;
        DexString name = factory.createString("strip");
        DexProto proto = factory.createProto(factory.stringType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::StringMethods_strip, "strip", type));
      }
      {
        // String String.stripLeading()
        DexType type = factory.stringType;
        DexString name = factory.createString("stripLeading");
        DexProto proto = factory.createProto(factory.stringType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::StringMethods_stripLeading, "stripLeading", type));
      }
      {
        // String String.stripTrailing()
        DexType type = factory.stringType;
        DexString name = factory.createString("stripTrailing");
        DexProto proto = factory.createProto(factory.stringType);
        DexMethod method = factory.createMethod(type, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::StringMethods_stripTrailing, "stripTrailing", type));
      }
    }

    private void initializeAndroidOptionalTMethodProviders(DexItemFactory factory) {
      DexType optionalType = factory.optionalType;
      DexType[] optionalTypes =
          new DexType[] {
            factory.optionalType,
            factory.optionalDoubleType,
            factory.optionalLongType,
            factory.optionalIntType,
          };

      // or (added in Java 9).
      {
        DexString name = factory.createString("or");
        DexProto proto = factory.createProto(optionalType, factory.supplierType);
        DexMethod method = factory.createMethod(optionalType, proto, name);
        addProvider(
            new StatifyingMethodGenerator(
                method, BackportedMethods::OptionalMethods_or, "or", optionalType));
      }

      // stream (added in Java 9).
      {
        DexType[] streamReturnTypes =
            new DexType[] {
              factory.streamType,
              factory.createType(factory.createString("Ljava/util/stream/DoubleStream;")),
              factory.createType(factory.createString("Ljava/util/stream/LongStream;")),
              factory.createType(factory.createString("Ljava/util/stream/IntStream;")),
            };
        TemplateMethodFactory[] streamMethodFactories =
            new TemplateMethodFactory[] {
              BackportedMethods::OptionalMethods_stream,
              BackportedMethods::OptionalMethods_streamDouble,
              BackportedMethods::OptionalMethods_streamLong,
              BackportedMethods::OptionalMethods_streamInt,
            };
        DexString name = factory.createString("stream");
        for (int i = 0; i < optionalTypes.length; i++) {
          DexType optional = optionalTypes[i];
          DexType streamReturnType = streamReturnTypes[i];
          DexProto proto = factory.createProto(streamReturnType);
          DexMethod method = factory.createMethod(optional, proto, name);
          addProvider(
              new StatifyingMethodGenerator(method, streamMethodFactories[i], "stream", optional));
        }
      }

      // ifPresentOrElse (added in Java 9).
      {
        DexType[] consumerTypes =
            new DexType[] {
              factory.consumerType,
              factory.doubleConsumer,
              factory.longConsumer,
              factory.intConsumer
            };
        TemplateMethodFactory[] methodFactories =
            new TemplateMethodFactory[] {
              BackportedMethods::OptionalMethods_ifPresentOrElse,
              BackportedMethods::OptionalMethods_ifPresentOrElseDouble,
              BackportedMethods::OptionalMethods_ifPresentOrElseLong,
              BackportedMethods::OptionalMethods_ifPresentOrElseInt
            };
        for (int i = 0; i < optionalTypes.length; i++) {
          DexType optional = optionalTypes[i];
          DexType consumer = consumerTypes[i];
          DexString name = factory.createString("ifPresentOrElse");
          DexProto proto = factory.createProto(factory.voidType, consumer, factory.runnableType);
          DexMethod method = factory.createMethod(optional, proto, name);
          addProvider(
              new StatifyingMethodGenerator(
                  method, methodFactories[i], "ifPresentOrElse", optional));
        }
      }

      // orElseThrow (added in Java 10).
      {
        DexType[] returnTypes =
            new DexType[] {
              factory.objectType, factory.doubleType, factory.longType, factory.intType,
            };
        MethodInvokeRewriter[] rewriters =
            new MethodInvokeRewriter[] {
              OptionalMethodRewrites.rewriteOrElseGet(),
              OptionalMethodRewrites.rewriteDoubleOrElseGet(),
              OptionalMethodRewrites.rewriteLongOrElseGet(),
              OptionalMethodRewrites.rewriteIntOrElseGet(),
            };
        DexString name = factory.createString("orElseThrow");
        for (int i = 0; i < optionalTypes.length; i++) {
          DexProto proto = factory.createProto(returnTypes[i]);
          DexMethod method = factory.createMethod(optionalTypes[i], proto, name);
          addProvider(new InvokeRewriter(method, rewriters[i]));
        }
      }

      // isEmpty (added in Java 11).
      {
        TemplateMethodFactory[] methodFactories =
            new TemplateMethodFactory[] {
              BackportedMethods::OptionalMethods_isEmpty,
              BackportedMethods::OptionalMethods_isEmptyDouble,
              BackportedMethods::OptionalMethods_isEmptyLong,
              BackportedMethods::OptionalMethods_isEmptyInt
            };
        DexString name = factory.createString("isEmpty");
        for (int i = 0; i < optionalTypes.length; i++) {
          DexProto proto = factory.createProto(factory.booleanType);
          DexMethod method = factory.createMethod(optionalTypes[i], proto, name);
          addProvider(
              new StatifyingMethodGenerator(
                  method, methodFactories[i], "isEmpty", optionalTypes[i]));
        }
      }
    }

    private void initializeJava9MethodProviders(DexItemFactory factory) {
      // Nothing right now.
    }

    private void initializeJava10MethodProviders(DexItemFactory factory) {
      // Nothing right now.
    }

    private void initializeJava11MethodProviders(DexItemFactory factory) {
      // Character
      DexType type = factory.boxedCharType;

      // String Character.toString(int)
      DexString name = factory.createString("toString");
      DexProto proto = factory.createProto(factory.stringType, factory.intType);
      DexMethod method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(
              method, BackportedMethods::CharacterMethods_toStringCodepoint, "toStringCodepoint"));

      // CharSequence
      type = factory.charSequenceType;

      // int CharSequence.compare(CharSequence, CharSequence)
      name = factory.createString("compare");
      proto =
          factory.createProto(factory.intType, factory.charSequenceType, factory.charSequenceType);
      method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::CharSequenceMethods_compare, "compare"));
    }

    private void initializeStreamMethodProviders(DexItemFactory factory) {
      // Stream
      DexType streamType = factory.streamType;

      // Stream.ofNullable(object)
      DexString name = factory.createString("ofNullable");
      DexProto proto = factory.createProto(factory.streamType, factory.objectType);
      DexMethod method = factory.createMethod(streamType, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::StreamMethods_ofNullable, "ofNullable"));
    }

    private void initializeObjectsMethodProviders(DexItemFactory factory) {
      // Objects
      DexType type = factory.objectsType;

      // Objects.requireNonNull(Object, Supplier)
      DexString name = factory.createString("requireNonNull");
      DexProto proto =
          factory.createProto(factory.objectType, factory.objectType, factory.supplierType);
      DexMethod method = factory.createMethod(type, proto, name);
      addProvider(
          new MethodGenerator(method, BackportedMethods::ObjectsMethods_requireNonNullSupplier));
    }

    private void addProvider(MethodProvider generator) {
      if (appView.options().machineDesugaredLibrarySpecification.isSupported(generator.method)) {
        // TODO(b/174453232): Remove this after the configuration file format has bee updated
        // with the "rewrite_method" section.
        if (generator.method.getHolderType() == appView.dexItemFactory().objectsType) {
          // Still backport the new API level 30 methods and Objects.requireNonNull taking
          // one argument.
          String methodName = generator.method.getName().toString();
          if (!methodName.equals("requireNonNull")
              && !methodName.equals("requireNonNullElse")
              && !methodName.equals("requireNonNullElseGet")
              && !methodName.equals("checkIndex")
              && !methodName.equals("checkFromToIndex")
              && !methodName.equals("checkFromIndexSize")) {
            return;
          }
          if (methodName.equals("requireNonNull") && generator.method.getArity() != 1) {
            return;
          }
        }
      }
      MethodProvider replaced = rewritable.put(generator.method, generator);
      assert replaced == null;
    }

    MethodProvider getProvider(DexMethod method) {
      return rewritable.get(method);
    }
  }

  public abstract static class MethodProvider {

    final DexMethod method;

    public MethodProvider(DexMethod method) {
      this.method = method;
    }

    public abstract Collection<CfInstruction> rewriteInvoke(
        CfInvoke invoke,
        AppView<?> appView,
        BackportedMethodDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext,
        LocalStackAllocator localStackAllocator);
  }

  private static final class InvokeRewriter extends MethodProvider {

    private final MethodInvokeRewriter rewriter;

    InvokeRewriter(DexMethod method, MethodInvokeRewriter rewriter) {
      super(method);
      this.rewriter = rewriter;
    }

    @Override
    public Collection<CfInstruction> rewriteInvoke(
        CfInvoke invoke,
        AppView<?> appView,
        BackportedMethodDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext,
        LocalStackAllocator localStackAllocator) {
      return rewriter.rewrite(invoke, appView.dexItemFactory(), localStackAllocator);
    }
  }

  private static class MethodGenerator extends MethodProvider {

    private final TemplateMethodFactory factory;
    private final String methodName;

    MethodGenerator(DexMethod method, TemplateMethodFactory factory) {
      this(method, factory, method.name.toString());
    }

    MethodGenerator(DexMethod method, TemplateMethodFactory factory, String methodName) {
      super(method);
      this.factory = factory;
      this.methodName = methodName;
    }

    protected SyntheticKind getSyntheticKind() {
      return SyntheticNaming.SyntheticKind.BACKPORT;
    }

    @Override
    public Collection<CfInstruction> rewriteInvoke(
        CfInvoke invoke,
        AppView<?> appView,
        BackportedMethodDesugaringEventConsumer eventConsumer,
        MethodProcessingContext methodProcessingContext,
        LocalStackAllocator localStackAllocator) {
      ProgramMethod method = getSyntheticMethod(appView, methodProcessingContext);
      eventConsumer.acceptBackportedMethod(method, methodProcessingContext.getMethodContext());
      return ImmutableList.of(new CfInvoke(Opcodes.INVOKESTATIC, method.getReference(), false));
    }

    private ProgramMethod getSyntheticMethod(
        AppView<?> appView, MethodProcessingContext methodProcessingContext) {
      return appView
          .getSyntheticItems()
          .createMethod(
              getSyntheticKind(),
              methodProcessingContext.createUniqueContext(),
              appView,
              builder ->
                  builder
                      .disableAndroidApiLevelCheck()
                      .setProto(getProto(appView.dexItemFactory()))
                      .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                      .setCode(methodSig -> generateTemplateMethod(appView.options(), methodSig)));
    }

    public DexProto getProto(DexItemFactory itemFactory) {
      return method.proto;
    }

    public Code generateTemplateMethod(InternalOptions options, DexMethod method) {
      return factory.create(options, method);
    }
  }

  // Specific subclass to transform virtual methods into static desugared methods.
  // To be correct, the method has to be on a final class or be a final method, and to be
  // implemented directly on the class (no overrides).
  private static class StatifyingMethodGenerator extends MethodGenerator {

    private final DexType receiverType;

    StatifyingMethodGenerator(
        DexMethod method, TemplateMethodFactory factory, String methodName, DexType receiverType) {
      super(method, factory, methodName);
      this.receiverType = receiverType;
    }

    @Override
    public DexProto getProto(DexItemFactory itemFactory) {
      return itemFactory.prependTypeToProto(receiverType, super.getProto(itemFactory));
    }
  }

  // Version of StatifyingMethodGenerator for backports which will call the method they backport.
  // Such backports will not go through backporting again as that would cause infinite recursion.
  private static class StatifyingMethodWithForwardingGenerator extends StatifyingMethodGenerator {
    StatifyingMethodWithForwardingGenerator(
        DexMethod method, TemplateMethodFactory factory, String methodName, DexType receiverType) {
      super(method, factory, methodName, receiverType);
    }

    @Override
    protected SyntheticKind getSyntheticKind() {
      return SyntheticKind.BACKPORT_WITH_FORWARDING;
    }
  }

  private interface TemplateMethodFactory {

    CfCode create(InternalOptions options, DexMethod method);
  }

  public interface MethodInvokeRewriter {

    CfInstruction rewriteSingle(CfInvoke invoke, DexItemFactory factory);

    // Convenience wrapper since most rewrites are to a single instruction.
    default Collection<CfInstruction> rewrite(
        CfInvoke invoke, DexItemFactory factory, LocalStackAllocator localStackAllocator) {
      return ImmutableList.of(rewriteSingle(invoke, factory));
    }
  }

  public abstract static class FullMethodInvokeRewriter implements MethodInvokeRewriter {

    @Override
    public final CfInstruction rewriteSingle(CfInvoke invoke, DexItemFactory factory) {
      throw new Unreachable();
    }

    @Override
    public abstract Collection<CfInstruction> rewrite(
        CfInvoke invoke, DexItemFactory factory, LocalStackAllocator localStackAllocator);
  }
}
