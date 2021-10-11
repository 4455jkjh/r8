// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.graph;

import static com.android.tools.r8.graph.GraphLens.getIdentityLens;

import com.android.tools.r8.code.CfOrDexInstruction;
import com.android.tools.r8.ir.code.Invoke;
import com.android.tools.r8.utils.TraversalContinuation;
import java.util.ListIterator;

public abstract class UseRegistry<T extends Definition> {

  private final T context;
  private final DexItemFactory factory;

  private TraversalContinuation continuation = TraversalContinuation.CONTINUE;

  public enum MethodHandleUse {
    ARGUMENT_TO_LAMBDA_METAFACTORY,
    NOT_ARGUMENT_TO_LAMBDA_METAFACTORY
  }

  public UseRegistry(T context, DexItemFactory factory) {
    this.context = context;
    this.factory = factory;
  }

  public final void accept(ProgramMethod method) {
    method.registerCodeReferences(this);
  }

  public DexItemFactory dexItemFactory() {
    return factory;
  }

  public void doBreak() {
    assert continuation.shouldContinue();
    continuation = TraversalContinuation.BREAK;
  }

  public final T getContext() {
    return context;
  }

  public final DexClassAndMethod getMethodContext() {
    assert context.isMethod();
    return context.asMethod();
  }

  public TraversalContinuation getTraversalContinuation() {
    return continuation;
  }

  public abstract void registerInitClass(DexType type);

  public abstract void registerInvokeVirtual(DexMethod method);

  public abstract void registerInvokeDirect(DexMethod method);

  public void registerInvokeSpecial(DexMethod method, boolean itf) {
    registerInvokeSpecial(method);
  }

  public void registerInvokeSpecial(DexMethod method) {
    // TODO(b/201984767, b/202381923): This needs to supply the right graph lens and original
    //  context to produce correct invoke types for invoke-special instructions.
    DexClassAndMethod context = getMethodContext();
    Invoke.Type type =
        Invoke.Type.fromInvokeSpecial(
            method, context, dexItemFactory(), getIdentityLens(), context::getHolderType);
    if (type.isDirect()) {
      registerInvokeDirect(method);
    } else {
      assert type.isSuper();
      registerInvokeSuper(method);
    }
  }

  public abstract void registerInvokeStatic(DexMethod method);

  public abstract void registerInvokeInterface(DexMethod method);

  public abstract void registerInvokeSuper(DexMethod method);

  public abstract void registerInstanceFieldRead(DexField field);

  public void registerInstanceFieldReadFromMethodHandle(DexField field) {
    registerInstanceFieldRead(field);
  }

  public abstract void registerInstanceFieldWrite(DexField field);

  public void registerInstanceFieldWriteFromMethodHandle(DexField field) {
    registerInstanceFieldWrite(field);
  }

  public void registerInvokeStatic(DexMethod method, boolean itf) {
    registerInvokeStatic(method);
  }

  public void registerNewInstance(DexType type) {
    registerTypeReference(type);
  }

  public void registerNewUnboxedEnumInstance(DexType type) {
    registerTypeReference(type);
  }

  public abstract void registerStaticFieldRead(DexField field);

  public void registerStaticFieldReadFromMethodHandle(DexField field) {
    registerStaticFieldRead(field);
  }

  public abstract void registerStaticFieldWrite(DexField field);

  public void registerStaticFieldWriteFromMethodHandle(DexField field) {
    registerStaticFieldWrite(field);
  }

  public abstract void registerTypeReference(DexType type);

  public void registerInstanceOf(DexType type) {
    registerTypeReference(type);
  }

  public void registerConstClass(
      DexType type, ListIterator<? extends CfOrDexInstruction> iterator) {
    registerTypeReference(type);
  }

  public void registerCheckCast(DexType type) {
    registerTypeReference(type);
  }

  public void registerSafeCheckCast(DexType type) {
    registerCheckCast(type);
  }

  public void registerExceptionGuard(DexType guard) {
    registerTypeReference(guard);
  }

  public void registerMethodHandle(DexMethodHandle methodHandle, MethodHandleUse use) {
    switch (methodHandle.type) {
      case INSTANCE_GET:
        registerInstanceFieldReadFromMethodHandle(methodHandle.asField());
        break;
      case INSTANCE_PUT:
        registerInstanceFieldWriteFromMethodHandle(methodHandle.asField());
        break;
      case STATIC_GET:
        registerStaticFieldReadFromMethodHandle(methodHandle.asField());
        break;
      case STATIC_PUT:
        registerStaticFieldWriteFromMethodHandle(methodHandle.asField());
        break;
      case INVOKE_INSTANCE:
        registerInvokeVirtual(methodHandle.asMethod());
        break;
      case INVOKE_STATIC:
        registerInvokeStatic(methodHandle.asMethod());
        break;
      case INVOKE_CONSTRUCTOR:
        DexMethod method = methodHandle.asMethod();
        registerNewInstance(method.holder);
        registerInvokeDirect(method);
        break;
      case INVOKE_INTERFACE:
        registerInvokeInterface(methodHandle.asMethod());
        break;
      case INVOKE_SUPER:
        registerInvokeSuper(methodHandle.asMethod());
        break;
      case INVOKE_DIRECT:
        registerInvokeDirect(methodHandle.asMethod());
        break;
      default:
        throw new AssertionError();
    }
  }

  public void registerCallSite(DexCallSite callSite) {
    boolean isLambdaMetaFactory =
        factory.isLambdaMetafactoryMethod(callSite.bootstrapMethod.asMethod());

    if (!isLambdaMetaFactory) {
      registerMethodHandle(
          callSite.bootstrapMethod, MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY);
    }

    // Lambda metafactory will use this type as the main SAM
    // interface for the dynamically created lambda class.
    registerTypeReference(callSite.methodProto.returnType);

    // Register bootstrap method arguments.
    // Only Type, MethodHandle, and MethodType need to be registered.
    for (DexValue arg : callSite.bootstrapArgs) {
      switch (arg.getValueKind()) {
        case METHOD_HANDLE:
          DexMethodHandle handle = arg.asDexValueMethodHandle().value;
          MethodHandleUse use =
              isLambdaMetaFactory
                  ? MethodHandleUse.ARGUMENT_TO_LAMBDA_METAFACTORY
                  : MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;
          registerMethodHandle(handle, use);
          break;
        case METHOD_TYPE:
          registerProto(arg.asDexValueMethodType().value);
          break;
        case TYPE:
          registerTypeReference(arg.asDexValueType().value);
          break;
        default:
          assert arg.isDexValueInt()
              || arg.isDexValueLong()
              || arg.isDexValueFloat()
              || arg.isDexValueDouble()
              || arg.isDexValueString();
      }
    }
  }

  public void registerProto(DexProto proto) {
    registerTypeReference(proto.returnType);
    for (DexType type : proto.parameters.values) {
      registerTypeReference(type);
    }
  }
}
