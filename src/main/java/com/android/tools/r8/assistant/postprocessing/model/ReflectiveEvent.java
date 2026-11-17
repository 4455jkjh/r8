// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.assistant.runtime.ReflectiveOperationReceiver;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.utils.DescriptorUtils;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public abstract class ReflectiveEvent {

  private final ReflectiveEventType eventType;
  private final String[] stack;
  private int id = ReflectiveOperationReceiver.NO_ID;
  private final List<Object> results = new ArrayList<>();

  protected static ClassReference toClassOrNull(String name) {
    return name == null || name.isEmpty() || "null".equals(name)
        ? null
        : Reference.classFromTypeName(name);
  }

  protected static DexType toTypeOrTripleStar(String javaType, DexItemFactory factory) {
    if (javaType == null) {
      return factory.createType("***");
    }
    return toType(javaType, factory);
  }

  protected static DexType toType(String javaType, DexItemFactory factory) {
    if (javaType.startsWith("[")) {
      // Class.getName() writes array types as descriptors with dots, e.g. [Ljava.lang.String;.
      return factory.createType(javaType.replace('.', '/'));
    }
    return factory.createType(DescriptorUtils.javaTypeToDescriptor(javaType));
  }

  protected ReflectiveEvent(ReflectiveEventType eventType, String[] stack) {
    this.eventType = eventType;
    this.stack = stack;
  }

  public ReflectiveEventType getEventType() {
    return eventType;
  }

  public String[] getStack() {
    return stack;
  }

  public int getId() {
    return id;
  }

  public void setId(int id) {
    this.id = id;
  }

  // The distinct results of the normal returns of the operation, as summarized by the logger: null,
  // a Boolean, a String, or a List<String> of member signatures. Empty if it never returned
  // normally, for example because it threw.
  public List<Object> getResults() {
    return results;
  }

  public void addResult(Object result) {
    results.add(result);
  }

  public boolean hasReturned() {
    return !results.isEmpty();
  }

  // True if the log records the outcome of this event (it has an id) and it never returned
  // normally. Logs written without after-call hooks have no ids, and their events are assumed to
  // have succeeded.
  public boolean neverReturned() {
    return id != ReflectiveOperationReceiver.NO_ID && !hasReturned();
  }

  public boolean isAtomicFieldUpdaterNewUpdater() {
    return false;
  }

  public AtomicFieldUpdaterNewUpdater asAtomicFieldUpdaterNewUpdater() {
    return null;
  }

  public boolean isClassGetName() {
    return false;
  }

  public ClassGetName asClassGetName() {
    return null;
  }

  public boolean isServiceLoaderLoad() {
    return false;
  }

  public ServiceLoaderLoad asServiceLoaderLoad() {
    return null;
  }

  public boolean isProxyNewProxyInstance() {
    return false;
  }

  public ProxyNewProxyInstance asProxyNewProxyInstance() {
    return null;
  }

  public boolean isClassGetMember() {
    return false;
  }

  public ClassGetMember asClassGetMember() {
    return null;
  }

  public boolean isClassNewInstance() {
    return false;
  }

  public ClassNewInstance asClassNewInstance() {
    return null;
  }

  public boolean isClassFlagEvent() {
    return false;
  }

  public ClassFlagEvent asClassFlagEvent() {
    return null;
  }

  public boolean isClassGetMembers() {
    return false;
  }

  public ClassGetMembers asClassGetMembers() {
    return null;
  }

  public boolean isClassForName() {
    return false;
  }

  public ClassForName asClassForName() {
    return null;
  }

  public boolean isClassGetGenericSupertypes() {
    return false;
  }

  public ClassGetGenericSupertypes asClassGetGenericSupertypes() {
    return null;
  }

  public boolean isClassLoaderLoadClass() {
    return false;
  }

  public ClassLoaderLoadClass asClassLoaderLoadClass() {
    return null;
  }

  public boolean isResourceBundleGetBundle() {
    return false;
  }

  public ResourceBundleGetBundle asResourceBundleGetBundle() {
    return null;
  }

  public boolean isClassOperation() {
    return false;
  }

  public ClassOperation asClassOperation() {
    return null;
  }

  public abstract String getContentsString();

  public abstract boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported);

  @Override
  public String toString() {
    return eventType + (stack != null ? "[s]" : "") + "(" + getContentsString() + ")";
  }

  public static ReflectiveEvent instantiate(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    switch (eventType) {
      case CLASS_NEW_INSTANCE:
        return new ClassNewInstance(eventType, stack, args, factory);
      case CLASS_GET_DECLARED_METHOD:
      case CLASS_GET_DECLARED_FIELD:
      case CLASS_GET_DECLARED_CONSTRUCTOR:
      case CLASS_GET_METHOD:
      case CLASS_GET_FIELD:
      case CLASS_GET_CONSTRUCTOR:
      case FIELD_GET_ANNOTATION:
      case FIELD_GET_ANNOTATIONS:
      case FIELD_IS_ANNOTATION_PRESENT:
      case FIELD_GET_GENERIC_TYPE:
      case METHOD_GET_ANNOTATION:
      case METHOD_GET_ANNOTATIONS:
      case METHOD_IS_ANNOTATION_PRESENT:
      case METHOD_GET_GENERIC_PARAMETER_TYPES:
      case METHOD_GET_GENERIC_RETURN_TYPE:
      case METHOD_GET_GENERIC_EXCEPTION_TYPES:
      case CONSTRUCTOR_GET_ANNOTATION:
      case CONSTRUCTOR_GET_ANNOTATIONS:
      case CONSTRUCTOR_IS_ANNOTATION_PRESENT:
        return new ClassGetMember(eventType, stack, args, factory);
      case CLASS_GET_DECLARED_METHODS:
      case CLASS_GET_DECLARED_FIELDS:
      case CLASS_GET_DECLARED_CONSTRUCTORS:
      case CLASS_GET_METHODS:
      case CLASS_GET_FIELDS:
      case CLASS_GET_CONSTRUCTORS:
        return new ClassGetMembers(eventType, stack, args, factory);
      case CLASS_GET_NAME:
        return new ClassGetName(eventType, stack, args, factory);
      case CLASS_FOR_NAME:
        return new ClassForName(eventType, stack, args);
      case CLASS_GET_COMPONENT_TYPE:
      case CLASS_GET_PACKAGE:
      case CLASS_IS_ASSIGNABLE_FROM:
      case CLASS_GET_SUPERCLASS:
      case CLASS_AS_SUBCLASS:
      case CLASS_IS_INSTANCE:
      case CLASS_CAST:
      case CLASS_GET_ANNOTATION:
      case CLASS_GET_ANNOTATIONS:
      case CLASS_IS_ANNOTATION_PRESENT:
      case CLASS_GET_DECLARED_CLASSES:
      case CLASS_GET_CLASSES:
      case CLASS_GET_DECLARING_CLASS:
      case CLASS_GET_ENCLOSING_CLASS:
      case ENUM_VALUE_OF:
      case CLASS_GET_ENUM_CONSTANTS:
      case UNSAFE_ALLOCATE_INSTANCE:
        return new ClassOperation(eventType, stack, args, factory);
      case CLASS_FLAG:
        return new ClassFlagEvent(eventType, stack, args, factory);
      case ATOMIC_FIELD_UPDATER_NEW_UPDATER:
        return new AtomicFieldUpdaterNewUpdater(eventType, stack, args, factory);
      case SERVICE_LOADER_LOAD:
        return new ServiceLoaderLoad(eventType, stack, args, factory);
      case PROXY_NEW_PROXY_INSTANCE:
        return new ProxyNewProxyInstance(eventType, stack, args, factory);
      case CLASS_GET_GENERIC_SUPERCLASS:
      case CLASS_GET_GENERIC_INTERFACES:
        return new ClassGetGenericSupertypes(eventType, stack, args, factory);
      case CLASS_LOADER_LOAD_CLASS:
        return new ClassLoaderLoadClass(eventType, stack, args);
      case RESOURCE_BUNDLE_GET_BUNDLE:
        return new ResourceBundleGetBundle(eventType, stack, args);
    }
    return new ReflectiveEvent(eventType, stack) {
      @Override
      public String getContentsString() {
        return Arrays.toString(args);
      }

      @Override
      public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
        return false;
      }
    };
  }
}
