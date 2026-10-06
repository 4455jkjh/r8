// Copyright (c) 2025, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.assistant.postprocessing.model;

import com.android.tools.r8.assistant.runtime.ReflectiveEventType;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMember;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.KeepFieldInfo;
import com.android.tools.r8.shaking.KeepInfoCollectionExported;
import com.android.tools.r8.shaking.KeepMethodInfo;

public class ClassGetMember extends ReflectiveEvent {

  private final DexMember<?, ?> member;
  private final boolean hasMemberType;

  protected ClassGetMember(
      ReflectiveEventType eventType, String[] stack, String[] args, DexItemFactory factory) {
    super(eventType, stack);
    hasMemberType = args[0] != null;
    DexType type = toTypeOrTripleStar(args[0], factory);
    DexType holder = toType(args[1], factory);
    String name = args[2];
    if (eventType == ReflectiveEventType.CLASS_GET_FIELD
        || eventType == ReflectiveEventType.CLASS_GET_DECLARED_FIELD) {
      member = factory.createField(holder, type, name);
      return;
    }
    assert eventType == ReflectiveEventType.CLASS_GET_METHOD
        || eventType == ReflectiveEventType.CLASS_GET_DECLARED_METHOD
        || eventType == ReflectiveEventType.CLASS_GET_CONSTRUCTOR
        || eventType == ReflectiveEventType.CLASS_GET_DECLARED_CONSTRUCTOR;
    DexType[] typeArgs = new DexType[args.length - 3];
    for (int i = 3; i < args.length; i++) {
      typeArgs[i - 3] = toType(args[i], factory);
    }
    member = factory.createMethod(holder, factory.createProto(type, typeArgs), name);
  }

  public DexMember<?, ?> getMember() {
    return member;
  }

  // False if the field type or return type was not logged, which is the case for field and method
  // lookups that failed. The member then has the type ***. Failed constructor lookups have the
  // type void, so use ReflectiveEvent.hasReturned() to check whether a lookup succeeded.
  public boolean hasMemberType() {
    return hasMemberType;
  }

  @Override
  public boolean isClassGetMember() {
    return true;
  }

  @Override
  public ClassGetMember asClassGetMember() {
    return this;
  }

  @Override
  public String getContentsString() {
    if (hasMemberType) {
      return member.toSourceString();
    }
    // The type *** has no source form, so print the member without it.
    if (member.isDexMethod()) {
      return member.asDexMethod().toSourceStringWithoutReturnType();
    }
    return member.getHolderType().toSourceString() + "." + member.getName();
  }

  @Override
  public boolean isKeptBy(KeepInfoCollectionExported keepInfoCollectionExported) {
    if (!hasMemberType) {
      // The lookup failed, so there is no member to keep.
      return false;
    }
    if (member.isDexField()) {
      KeepFieldInfo keepFieldInfo =
          keepInfoCollectionExported.getKeepFieldInfo(member.asDexField().asFieldReference());
      // TODO(b/428836085): Check inner properties of the keep rules, holder, type and name may have
      //  to be preserved.
      return keepFieldInfo != null;
    }
    KeepMethodInfo keepMethodInfo =
        keepInfoCollectionExported.getKeepMethodInfo(member.asDexMethod().asMethodReference());
    // TODO(b/428836085): Check inner properties of the keep rules, holder, type and name may have
    //  to be preserved.
    return keepMethodInfo != null;
  }
}
