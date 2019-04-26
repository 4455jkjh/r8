// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexApplication.Builder;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.type.Nullability;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeCustom;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.StaticGet;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.ImmutableSet;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;

/**
 * Lambda desugaring rewriter.
 *
 * Performs lambda instantiation point matching,
 * lambda class generation, and instruction patching.
 */
public class LambdaRewriter {

  // Public for testing.
  public static final String LAMBDA_CLASS_NAME_PREFIX = "-$$Lambda$";
  public static final String LAMBDA_GROUP_CLASS_NAME_PREFIX = "-$$LambdaGroup$";
  static final String EXPECTED_LAMBDA_METHOD_PREFIX = "lambda$";
  static final String LAMBDA_INSTANCE_FIELD_NAME = "INSTANCE";
  static final String LAMBDA_CREATE_INSTANCE_METHOD_NAME = "$$createInstance";

  private final AppView<?> appView;
  final IRConverter converter;
  final DexItemFactory factory;

  final DexMethod objectInitMethod;

  final DexString constructorName;
  final DexString classConstructorName;
  final DexString instanceFieldName;
  final DexString createInstanceMethodName;

  final BiMap<DexMethod, DexMethod> methodMapping = HashBiMap.create();

  // Maps call sites seen so far to inferred lambda descriptor. It is intended
  // to help avoid re-matching call sites we already seen. Note that same call
  // site may match one or several lambda classes.
  //
  // NOTE: synchronize concurrent access on `knownCallSites`.
  private final Map<DexCallSite, LambdaDescriptor> knownCallSites = new IdentityHashMap<>();
  // Maps lambda class type into lambda class representation. Since lambda class
  // type uniquely defines lambda class, effectively canonicalizes lambda classes.
  // NOTE: synchronize concurrent access on `knownLambdaClasses`.
  private final Map<DexType, LambdaClass> knownLambdaClasses = new IdentityHashMap<>();

  // Checks if the type starts with lambda-class prefix.
  public static boolean hasLambdaClassPrefix(DexType clazz) {
    return clazz.getName().startsWith(LAMBDA_CLASS_NAME_PREFIX);
  }

  public LambdaRewriter(AppView<?> appView, IRConverter converter) {
    assert converter != null;
    this.appView = appView;
    this.converter = converter;
    this.factory = appView.dexItemFactory();

    this.constructorName = factory.createString(Constants.INSTANCE_INITIALIZER_NAME);
    DexProto initProto = factory.createProto(factory.voidType);
    this.objectInitMethod = factory.createMethod(factory.objectType, initProto, constructorName);
    this.classConstructorName = factory.createString(Constants.CLASS_INITIALIZER_NAME);
    this.instanceFieldName = factory.createString(LAMBDA_INSTANCE_FIELD_NAME);
    this.createInstanceMethodName = factory.createString(LAMBDA_CREATE_INSTANCE_METHOD_NAME);
  }

  /**
   * Detect and desugar lambdas and method references found in the code.
   *
   * NOTE: this method can be called concurrently for several different methods.
   */
  public void desugarLambdas(DexEncodedMethod encodedMethod, IRCode code) {
    DexType currentType = encodedMethod.method.holder;
    ListIterator<BasicBlock> blocks = code.listIterator();
    while (blocks.hasNext()) {
      BasicBlock block = blocks.next();
      InstructionListIterator instructions = block.listIterator();
      while (instructions.hasNext()) {
        Instruction instruction = instructions.next();
        if (instruction.isInvokeCustom()) {
          LambdaDescriptor descriptor = inferLambdaDescriptor(
              instruction.asInvokeCustom().getCallSite());
          if (descriptor == LambdaDescriptor.MATCH_FAILED) {
            continue;
          }

          // We have a descriptor, get or create lambda class.
          LambdaClass lambdaClass = getOrCreateLambdaClass(descriptor, currentType);
          assert lambdaClass != null;

          // We rely on patch performing its work in a way which
          // keeps both `instructions` and `blocks` iterators in
          // valid state so that we can continue iteration.
          patchInstruction(lambdaClass, code, blocks, instructions);
        }
      }
    }
  }

  /** Remove lambda deserialization methods. */
  public boolean removeLambdaDeserializationMethods(Iterable<DexProgramClass> classes) {
    for (DexProgramClass clazz : classes) {
      // Search for a lambda deserialization method and remove it if found.
      List<DexEncodedMethod> directMethods = clazz.directMethods();
      if (directMethods != null) {
        int methodCount = directMethods.size();
        for (int i = 0; i < methodCount; i++) {
          DexEncodedMethod encoded = directMethods.get(i);
          DexMethod method = encoded.method;
          if (method.isLambdaDeserializeMethod(appView.dexItemFactory())) {
            assert encoded.accessFlags.isStatic();
            assert encoded.accessFlags.isSynthetic();
            clazz.removeDirectMethod(i);

            // We assume there is only one such method in the class.
            return true;
          }
        }
      }
    }
    return false;
  }

  /**
   * Adjust accessibility of referenced application symbols or
   * creates necessary accessors.
   */
  public void adjustAccessibility() {
    // For each lambda class perform necessary adjustment of the
    // referenced symbols to make them accessible. This can result in
    // method access relaxation or creation of accessor method.
    for (LambdaClass lambdaClass : knownLambdaClasses.values()) {
      // This call may cause methodMapping to be updated.
      lambdaClass.target.ensureAccessibility();
    }
    if (appView.enableWholeProgramOptimizations() && !methodMapping.isEmpty()) {
      appView.setGraphLense(
          new LambdaRewriterGraphLense(methodMapping, appView.graphLense(), factory));
    }
  }

  /**
   * Returns a synthetic class for desugared lambda or `null` if the `type`
   * does not represent one. Method can be called concurrently.
   */
  public DexProgramClass getLambdaClass(DexType type) {
    LambdaClass lambdaClass = getKnown(knownLambdaClasses, type);
    return lambdaClass == null ? null : lambdaClass.getOrCreateLambdaClass();
  }

  /** Generates lambda classes and adds them to the builder. */
  public void synthesizeLambdaClasses(Builder<?> builder, ExecutorService executorService)
      throws ExecutionException {
    AppInfo appInfo = appView.appInfo();
    for (LambdaClass lambdaClass : knownLambdaClasses.values()) {
      DexProgramClass synthesizedClass = lambdaClass.getOrCreateLambdaClass();
      appInfo.addSynthesizedClass(synthesizedClass);
      builder.addSynthesizedClass(synthesizedClass, lambdaClass.addToMainDexList.get());
    }
    converter.optimizeSynthesizedClasses(
        knownLambdaClasses.values().stream()
            .map(LambdaClass::getOrCreateLambdaClass)
            .collect(ImmutableSet.toImmutableSet()),
        executorService);
  }

  public Set<DexCallSite> getDesugaredCallSites() {
    synchronized (knownCallSites) {
      return knownCallSites.keySet();
    }
  }

  // Matches invoke-custom instruction operands to infer lambda descriptor
  // corresponding to this lambda invocation point.
  //
  // Returns the lambda descriptor or `MATCH_FAILED`.
  private LambdaDescriptor inferLambdaDescriptor(DexCallSite callSite) {
    // We check the map before and after inferring lambda descriptor to minimize time
    // spent in synchronized block. As a result we may throw away calculated descriptor
    // in rare case when another thread has same call site processed concurrently,
    // but this is a low price to pay comparing to making whole method synchronous.
    LambdaDescriptor descriptor = getKnown(knownCallSites, callSite);
    return descriptor != null
        ? descriptor
        : putIfAbsent(
            knownCallSites, callSite, LambdaDescriptor.infer(callSite, appView.appInfo()));
  }

  private boolean isInMainDexList(DexType type) {
    return appView.appInfo().isInMainDexList(type);
  }

  // Returns a lambda class corresponding to the lambda descriptor and context,
  // creates the class if it does not yet exist.
  private LambdaClass getOrCreateLambdaClass(LambdaDescriptor descriptor, DexType accessedFrom) {
    DexType lambdaClassType = LambdaClass.createLambdaClassType(this, accessedFrom, descriptor);
    // We check the map twice to to minimize time spent in synchronized block.
    LambdaClass lambdaClass = getKnown(knownLambdaClasses, lambdaClassType);
    if (lambdaClass == null) {
      lambdaClass =
          putIfAbsent(
              knownLambdaClasses,
              lambdaClassType,
              new LambdaClass(this, accessedFrom, lambdaClassType, descriptor));
    }
    lambdaClass.addSynthesizedFrom(appView.definitionFor(accessedFrom).asProgramClass());
    if (isInMainDexList(accessedFrom)) {
      lambdaClass.addToMainDexList.set(true);
    }
    return lambdaClass;
  }

  private static <K, V> V getKnown(Map<K, V> map, K key) {
    synchronized (map) {
      return map.get(key);
    }
  }

  private static <K, V> V putIfAbsent(Map<K, V> map, K key, V value) {
    synchronized (map) {
      V known = map.get(key);
      if (known != null) {
        return known;
      }
      map.put(key, value);
      return value;
    }
  }

  // Patches invoke-custom instruction to create or get an instance
  // of the generated lambda class.
  private void patchInstruction(LambdaClass lambdaClass, IRCode code,
      ListIterator<BasicBlock> blocks, InstructionListIterator instructions) {
    assert lambdaClass != null;
    assert instructions != null;
    assert instructions.peekPrevious().isInvokeCustom();

    // Move to the previous instruction, must be InvokeCustom
    InvokeCustom invoke = instructions.previous().asInvokeCustom();

    // The value representing new lambda instance: we reuse the
    // value from the original invoke-custom instruction, and thus
    // all its usages.
    Value lambdaInstanceValue = invoke.outValue();
    if (lambdaInstanceValue == null) {
      // The out value might be empty in case it was optimized out.
      lambdaInstanceValue =
          code.createValue(
              TypeLatticeElement.fromDexType(lambdaClass.type, Nullability.maybeNull(), appView));
    }

    // For stateless lambdas we replace InvokeCustom instruction with StaticGet
    // reading the value of INSTANCE field created for singleton lambda class.
    if (lambdaClass.isStateless()) {
      instructions.replaceCurrentInstruction(
          new StaticGet(lambdaInstanceValue, lambdaClass.instanceField));
      // Note that since we replace one throwing operation with another we don't need
      // to have any special handling for catch handlers.
      return;
    }

    if (!converter.appView.options().testing.enableStatefulLambdaCreateInstanceMethod) {
      // For stateful lambdas we always create a new instance since we need to pass
      // captured values to the constructor.
      //
      // We replace InvokeCustom instruction with a new NewInstance instruction
      // instantiating lambda followed by InvokeDirect instruction calling a
      // constructor on it.
      //
      //    original:
      //      Invoke-Custom rResult <- { rArg0, rArg1, ... }; call site: ...
      //
      //    result:
      //      NewInstance   rResult <-  LambdaClass
      //      Invoke-Direct { rResult, rArg0, rArg1, ... }; method: void LambdaClass.<init>(...)
      NewInstance newInstance = new NewInstance(lambdaClass.type, lambdaInstanceValue);
      instructions.replaceCurrentInstruction(newInstance);

      List<Value> arguments = new ArrayList<>();
      arguments.add(lambdaInstanceValue);
      arguments.addAll(invoke.arguments()); // Optional captures.
      InvokeDirect constructorCall =
          new InvokeDirect(lambdaClass.constructor, null /* no return value */, arguments);
      instructions.add(constructorCall);
      constructorCall.setPosition(newInstance.getPosition());

      // If we don't have catch handlers we are done.
      if (!constructorCall.getBlock().hasCatchHandlers()) {
        return;
      }

      // Move the iterator back to position it between the two instructions, split
      // the block between the two instructions, and copy the catch handlers.
      instructions.previous();
      assert instructions.peekNext().isInvokeDirect();
      BasicBlock currentBlock = newInstance.getBlock();
      BasicBlock nextBlock = instructions.split(code, blocks);
      assert !instructions.hasNext();
      nextBlock.copyCatchHandlers(code, blocks, currentBlock, appView.options());
    } else {
      // For stateful lambdas we call the createInstance method.
      //
      //    original:
      //      Invoke-Custom rResult <- { rArg0, rArg1, ... }; call site: ...
      //
      //    result:
      //      Invoke-Static rResult <- { rArg0, rArg1, ... }; method void
      // LambdaClass.createInstance(...)
      InvokeStatic invokeStatic =
          new InvokeStatic(
              lambdaClass.getCreateInstanceMethod(), lambdaInstanceValue, invoke.arguments());
      instructions.replaceCurrentInstruction(invokeStatic);
    }
  }
}
