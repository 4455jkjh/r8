// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.shaking.reflectiveidentification;

import static com.android.tools.r8.graph.DexClassAndMethod.asProgramMethodOrNull;
import static com.android.tools.r8.graph.DexProgramClass.asProgramClassOrNull;
import static com.android.tools.r8.graph.ProgramField.asProgramFieldOrNull;
import static com.android.tools.r8.naming.IdentifierNameStringUtils.identifyIdentifier;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexItemFactory.ClassMethods;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.code.ArrayPut;
import com.android.tools.r8.ir.code.ConstantValueUtils;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InvokeMethod;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewArrayEmpty;
import com.android.tools.r8.ir.code.NewArrayFilled;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.naming.identifiernamestring.IdentifierNameStringLookupResult;
import com.android.tools.r8.shaking.Enqueuer;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.collections.ProgramMethodSet;
import com.android.tools.r8.utils.timing.Timing;
import com.google.common.collect.Sets;
import java.lang.reflect.InvocationHandler;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

public class EnqueuerReflectiveIdentificationAnalysis {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final DexItemFactory factory;
  private final Enqueuer enqueuer;
  private final ReflectiveIdentificationEventConsumer eventConsumer;

  private final Set<DexMember<?, ?>> identifierNameStrings = Sets.newIdentityHashSet();
  private final ProgramMethodSet worklist = ProgramMethodSet.create();

  public EnqueuerReflectiveIdentificationAnalysis(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      Enqueuer enqueuer,
      ReflectiveIdentificationEventConsumer eventConsumer) {
    this.appView = appView;
    this.factory = appView.dexItemFactory();
    this.enqueuer = enqueuer;
    this.eventConsumer = eventConsumer;
  }

  public Set<DexMember<?, ?>> getIdentifierNameStrings() {
    return identifierNameStrings;
  }

  public void scanInvoke(DexMethod invokedMethod, ProgramMethod method) {
    ClassMethods classMethods = factory.classMethods;
    DexType holder = invokedMethod.getHolderType();
    if (holder.isIdenticalTo(factory.classType)) {
      // java.lang.Class
      if (invokedMethod.isIdenticalTo(classMethods.newInstance)) {
        enqueue(method);
      } else if (classMethods.isReflectiveClassLookup(invokedMethod)
          || classMethods.isReflectiveMemberLookup(invokedMethod)) {
        // Implicitly add -identifiernamestring rule for the Java reflection in use.
        identifierNameStrings.add(invokedMethod);
        enqueue(method);
      }
    } else if (holder.isIdenticalTo(factory.constructorType)) {
      // java.lang.reflect.Constructor
      if (invokedMethod.isIdenticalTo(factory.constructorMethods.newInstance)) {
        enqueue(method);
      }
    } else if (holder.isIdenticalTo(factory.proxyType)) {
      // java.lang.reflect.Proxy
      if (invokedMethod.isIdenticalTo(factory.proxyMethods.newProxyInstance)) {
        enqueue(method);
      }
    } else if (holder.isIdenticalTo(factory.serviceLoaderType)) {
      // java.util.ServiceLoader
      if (factory.serviceLoaderMethods.isLoadMethod(invokedMethod)) {
        enqueue(method);
      }
    } else if (holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicIntegerFieldUpdater)
        || holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicLongFieldUpdater)
        || holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)) {
      // java.util.concurrent.atomic.AtomicIntegerFieldUpdater
      // java.util.concurrent.atomic.AtomicLongFieldUpdater
      // java.util.concurrent.atomic.AtomicReferenceFieldUpdater
      if (factory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod)) {
        identifierNameStrings.add(invokedMethod);
        enqueue(method);
      }
    }
  }

  public void enqueue(ProgramMethod method) {
    worklist.add(method);
  }

  public void processWorklist(Timing timing) {
    if (worklist.isEmpty()) {
      return;
    }
    timing.begin("Reflective identification");
    // TODO(b/414944282): Parallelize reflective identification.
    for (ProgramMethod method : worklist) {
      processMethod(method);
    }
    worklist.clear();
    timing.end();
  }

  private void processMethod(ProgramMethod method) {
    IRCode code = method.buildIR(appView, MethodConversionOptions.nonConverting());
    for (InvokeMethod invoke : code.<InvokeMethod>instructions(Instruction::isInvokeMethod)) {
      processInvoke(method, invoke);
    }
  }

  private void processInvoke(ProgramMethod method, InvokeMethod invoke) {
    DexMethod invokedMethod = invoke.getInvokedMethod();
    DexType holder = invokedMethod.getHolderType();
    if (holder.isIdenticalTo(factory.classType)) {
      // java.lang.Class
      if (invokedMethod.isIdenticalTo(factory.classMethods.forName)
          || invokedMethod.isIdenticalTo(factory.classMethods.forName3)) {
        handleJavaLangClassForName(method, invoke);
      } else if (invokedMethod.isIdenticalTo(factory.classMethods.getField)
          || invokedMethod.isIdenticalTo(factory.classMethods.getDeclaredField)) {
        handleJavaLangClassGetField(method, invoke);
      } else if (invokedMethod.isIdenticalTo(factory.classMethods.getMethod)
          || invokedMethod.isIdenticalTo(factory.classMethods.getDeclaredMethod)) {
        handleJavaLangClassGetMethod(method, invoke);
      } else if (invokedMethod.isIdenticalTo(factory.classMethods.newInstance)) {
        handleJavaLangClassNewInstance(method, invoke);
      }
    } else if (holder.isIdenticalTo(factory.constructorType)) {
      // java.lang.reflect.Constructor
      if (invokedMethod.isIdenticalTo(factory.constructorMethods.newInstance)) {
        handleJavaLangReflectConstructorNewInstance(method, invoke);
      }
    } else if (holder.isIdenticalTo(factory.proxyType)) {
      // java.lang.reflect.Proxy
      if (invokedMethod.isIdenticalTo(factory.proxyMethods.newProxyInstance)) {
        handleJavaLangReflectProxyNewProxyInstance(method, invoke);
      }
    } else if (holder.isIdenticalTo(factory.serviceLoaderType)) {
      // java.util.ServiceLoader
      if (factory.serviceLoaderMethods.isLoadMethod(invokedMethod)) {
        handleJavaUtilServiceLoaderLoad(method, invoke);
      }
    } else if (holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicIntegerFieldUpdater)) {
      // java.util.concurrent.atomic.AtomicIntegerFieldUpdater
      if (factory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod)) {
        handleJavaUtilConcurrentAtomicAtomicFieldUpdater(
            method,
            invoke,
            field ->
                eventConsumer.onJavaUtilConcurrentAtomicAtomicIntegerFieldUpdaterNewUpdater(
                    field, method));
      }
    } else if (holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicLongFieldUpdater)) {
      // java.util.concurrent.atomic.AtomicLongFieldUpdater
      if (factory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod)) {
        handleJavaUtilConcurrentAtomicAtomicFieldUpdater(
            method,
            invoke,
            field ->
                eventConsumer.onJavaUtilConcurrentAtomicAtomicLongFieldUpdaterNewUpdater(
                    field, method));
      }
    } else if (holder.isIdenticalTo(factory.javaUtilConcurrentAtomicAtomicReferenceFieldUpdater)) {
      // java.util.concurrent.atomic.AtomicReferenceFieldUpdater
      if (factory.atomicFieldUpdaterMethods.isFieldUpdater(invokedMethod)) {
        handleJavaUtilConcurrentAtomicAtomicFieldUpdater(
            method,
            invoke,
            field ->
                eventConsumer.onJavaUtilConcurrentAtomicAtomicReferenceFieldUpdaterNewUpdater(
                    field, method));
      }
    } else if (enqueuer.getAnalyses().handleReflectiveInvoke(method, invoke)) {
      // Intentionally empty.
    }
  }

  private void handleJavaLangClassForName(ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeStatic()) {
      return;
    }
    DexClass clazz = ConstantValueUtils.getClassFromClassForName(invoke.asInvokeStatic(), appView);
    if (clazz != null) {
      eventConsumer.onJavaLangClassForName(clazz, method);
    }
  }

  private void handleJavaLangClassGetField(ProgramMethod method, InvokeMethod invoke) {
    IdentifierNameStringLookupResult<?> identifierLookupResult =
        identifyIdentifier(invoke, appView, method);
    if (identifierLookupResult == null) {
      return;
    }

    DexField fieldReference = identifierLookupResult.getReference().asDexField();
    assert fieldReference != null;

    ProgramField field = asProgramFieldOrNull(appView.definitionFor(fieldReference));
    if (field != null) {
      eventConsumer.onJavaLangClassGetField(field, method);
    }
  }

  private void handleJavaLangClassGetMethod(ProgramMethod method, InvokeMethod invoke) {
    IdentifierNameStringLookupResult<?> identifierLookupResult =
        identifyIdentifier(invoke, appView, method);
    if (identifierLookupResult == null) {
      return;
    }

    DexMethod referencedMethodReference = identifierLookupResult.getReference().asDexMethod();
    assert referencedMethodReference != null;

    ProgramMethod referencedMethod =
        asProgramMethodOrNull(appView.definitionFor(referencedMethodReference));
    if (referencedMethod != null) {
      eventConsumer.onJavaLangClassGetMethod(referencedMethod, method);
    }
  }

  /** Handles reflective uses of {@link Class#newInstance()}. */
  private void handleJavaLangClassNewInstance(ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
            invoke.asInvokeVirtual().getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which class is being instantiated, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz =
        enqueuer.getProgramClassOrNullFromReflectiveAccess(instantiatedType, method);
    if (clazz != null) {
      eventConsumer.onJavaLangClassNewInstance(clazz, method);
    }
  }

  /** Handles reflective uses of {@link java.lang.reflect.Constructor#newInstance(Object...)}. */
  private void handleJavaLangReflectConstructorNewInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeVirtual()) {
      assert false;
      return;
    }

    Value constructorValue = invoke.asInvokeVirtual().getReceiver().getAliasedValue();
    if (constructorValue.isPhi() || !constructorValue.definition.isInvokeVirtual()) {
      // Give up, we can't tell which class is being instantiated.
      return;
    }

    InvokeVirtual constructorDefinition = constructorValue.definition.asInvokeVirtual();
    DexMethod invokedMethod = constructorDefinition.getInvokedMethod();
    if (invokedMethod.isNotIdenticalTo(factory.classMethods.getConstructor)
        && invokedMethod.isNotIdenticalTo(factory.classMethods.getDeclaredConstructor)) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }

    DexType instantiatedType =
        ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
            constructorDefinition.getReceiver(), appView);
    if (instantiatedType == null || !instantiatedType.isClassType()) {
      // Give up, we can't tell which constructor is being invoked, or the type is not a class type.
      // The latter should not happen in practice.
      return;
    }

    DexProgramClass clazz =
        asProgramClassOrNull(
            appView.appInfo().definitionForWithoutExistenceAssert(instantiatedType));
    if (clazz == null) {
      return;
    }
    Value parametersValue = constructorDefinition.inValues().get(1);
    if (parametersValue.isPhi()) {
      // Give up, we can't tell which constructor is being invoked.
      return;
    }
    NewArrayEmpty newArrayEmpty = parametersValue.definition.asNewArrayEmpty();
    NewArrayFilled newArrayFilled = parametersValue.definition.asNewArrayFilled();
    int parametersSize =
        newArrayEmpty != null
            ? newArrayEmpty.sizeIfConst()
            : newArrayFilled != null
                ? newArrayFilled.size()
                : parametersValue.isAlwaysNull(appView) ? 0 : -1;
    if (parametersSize < 0) {
      return;
    }

    ProgramMethod initializer = null;
    if (parametersSize == 0) {
      initializer = clazz.getProgramDefaultInitializer();
    } else {
      DexType[] parameterTypes = new DexType[parametersSize];
      int missingIndices;

      if (newArrayEmpty != null) {
        missingIndices = parametersSize;
      } else {
        missingIndices = 0;
        List<Value> values = newArrayFilled.inValues();
        for (int i = 0; i < parametersSize; ++i) {
          DexType type =
              ConstantValueUtils.getDexTypeRepresentedByValueForTracing(values.get(i), appView);
          if (type == null) {
            return;
          }
          parameterTypes[i] = type;
        }
      }

      for (Instruction user : parametersValue.uniqueUsers()) {
        if (user.isArrayPut()) {
          ArrayPut arrayPutInstruction = user.asArrayPut();
          if (arrayPutInstruction.array() != parametersValue) {
            return;
          }

          int index = arrayPutInstruction.indexIfConstAndInBounds(parametersSize);
          if (index < 0) {
            return;
          }

          DexType type =
              ConstantValueUtils.getDexTypeRepresentedByValueForTracing(
                  arrayPutInstruction.value(), appView);
          if (type == null) {
            return;
          }

          if (type.isIdenticalTo(parameterTypes[index])) {
            continue;
          }
          if (parameterTypes[index] != null) {
            return;
          }
          parameterTypes[index] = type;
          missingIndices--;
        }
      }

      if (missingIndices == 0) {
        initializer = clazz.getProgramInitializer(parameterTypes);
      }
    }

    if (initializer != null) {
      eventConsumer.onJavaLangReflectConstructorNewInstance(initializer, method);
    }
  }

  /**
   * Handles reflective uses of {@link java.lang.reflect.Proxy#newProxyInstance(ClassLoader,
   * Class[], InvocationHandler)}.
   */
  private void handleJavaLangReflectProxyNewProxyInstance(
      ProgramMethod method, InvokeMethod invoke) {
    if (!invoke.isInvokeStatic()) {
      assert false;
      return;
    }

    Value interfacesValue = invoke.getArgument(1);
    if (interfacesValue.isPhi()) {
      // Give up, we can't tell which interfaces the proxy implements.
      return;
    }

    NewArrayFilled newArrayFilled = interfacesValue.getDefinition().asNewArrayFilled();
    NewArrayEmpty newArrayEmpty = interfacesValue.getDefinition().asNewArrayEmpty();
    List<Value> values;
    if (newArrayFilled != null) {
      values = newArrayFilled.inValues();
    } else if (newArrayEmpty != null) {
      values = new ArrayList<>(interfacesValue.uniqueUsers().size());
      for (Instruction user : interfacesValue.uniqueUsers()) {
        ArrayPut arrayPut = user.asArrayPut();
        if (arrayPut != null) {
          values.add(arrayPut.value());
        }
      }
    } else {
      return;
    }

    Set<DexProgramClass> classes = Sets.newIdentityHashSet();
    for (Value value : values) {
      DexType type = ConstantValueUtils.getDexTypeRepresentedByValueForTracing(value, appView);
      if (type == null || !type.isClassType()) {
        continue;
      }

      DexProgramClass clazz = enqueuer.getProgramClassOrNullFromReflectiveAccess(type, method);
      if (clazz != null && clazz.isInterface()) {
        classes.add(clazz);
      }
    }
    if (!classes.isEmpty()) {
      eventConsumer.onJavaLangReflectProxyNewProxyInstance(classes, method);
    }
  }

  private void handleJavaUtilConcurrentAtomicAtomicFieldUpdater(
      ProgramMethod method, InvokeMethod invoke, Consumer<ProgramField> notifier) {
    IdentifierNameStringLookupResult<?> identifierLookupResult =
        identifyIdentifier(invoke, appView, method);
    if (identifierLookupResult == null) {
      return;
    }
    DexField fieldReference = identifierLookupResult.getReference().asDexField();
    assert fieldReference != null;
    ProgramField field = asProgramFieldOrNull(appView.definitionFor(fieldReference));
    if (field != null) {
      notifier.accept(field);
    }
  }

  private void handleJavaUtilServiceLoaderLoad(ProgramMethod method, InvokeMethod invoke) {
    if (invoke.inValues().isEmpty()) {
      // Should never happen.
      return;
    }

    Value argument = invoke.getFirstArgument().getAliasedValue();
    if (argument.isDefinedByInstructionSatisfying(Instruction::isConstClass)) {
      DexType serviceType = argument.getDefinition().asConstClass().getType();
      if (appView.appServices().allServiceTypes().contains(serviceType)) {
        notifyOnJavaUtilServiceLoaderLoad(serviceType, method);
      }
    } else {
      for (DexType serviceType : appView.appServices().allServiceTypes()) {
        notifyOnJavaUtilServiceLoaderLoad(serviceType, method);
      }
    }
  }

  private void notifyOnJavaUtilServiceLoaderLoad(DexType serviceType, ProgramMethod context) {
    DexProgramClass serviceClass =
        enqueuer.getProgramClassOrNullFromReflectiveAccess(serviceType, context);
    List<DexProgramClass> implementationClasses =
        ListUtils.mapNotNull(
            appView.appServices().serviceImplementationsFor(serviceType),
            implementationType ->
                implementationType.isClassType()
                    ? enqueuer.getProgramClassOrNull(implementationType, context)
                    : null);
    if (serviceClass != null || !implementationClasses.isEmpty()) {
      eventConsumer.onJavaUtilServiceLoaderLoad(serviceClass, implementationClasses, context);
    }
  }
}
