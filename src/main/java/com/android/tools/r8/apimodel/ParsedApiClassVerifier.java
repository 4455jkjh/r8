// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import com.android.tools.r8.ApiDatabaseGeneratorException;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.internal.collections.DisjointSets;
import com.android.tools.r8.utils.internal.collections.Pair;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ParsedApiClassVerifier {

  public static void verify(Collection<ParsedApiClass> classes)
      throws ApiDatabaseGeneratorException {
    Map<ClassReference, ParsedApiClass> classMap = new HashMap<>();
    for (ParsedApiClass clazz : classes) {
      ClassReference classReference = clazz.getClassReference();
      if (classMap.containsKey(classReference)) {
        throw new ApiDatabaseGeneratorException("Duplicate API classes for: " + classReference);
      }
      classMap.put(classReference, clazz);
    }

    verify(classMap);
  }

  private static void verify(Map<ClassReference, ParsedApiClass> classMap)
      throws ApiDatabaseGeneratorException {
    verifyClassHierarchy(classMap);
    verifyApiRanges(classMap);
    verifyClassOrInterface(classMap);
  }

  private static void verifyClassHierarchy(Map<ClassReference, ParsedApiClass> classMap)
      throws ApiDatabaseGeneratorException {
    for (ParsedApiClass clazz : classMap.values()) {
      clazz.forEachSupertypeThrowing(
          (supertype, range) -> {
            if (!classMap.containsKey(supertype)) {
              throw new ApiDatabaseGeneratorException(
                  "Missing supertype " + supertype + " for " + clazz.getClassReference());
            }
          });
      clazz.forEachInterfaceThrowing(
          (iface, range) -> {
            if (!classMap.containsKey(iface)) {
              throw new ApiDatabaseGeneratorException(
                  "Missing interface " + iface + " for " + clazz.getClassReference());
            }
          });
    }
  }

  private static void verifyApiRanges(Map<ClassReference, ParsedApiClass> classMap)
      throws ApiDatabaseGeneratorException {
    for (ParsedApiClass clazz : classMap.values()) {
      clazz.forEachSupertypeThrowing(
          (supertype, relationRange) -> {
            if (!relationRange.isWithin(clazz.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Supertype relation range "
                      + relationRange
                      + " for "
                      + supertype
                      + " is not within class range "
                      + clazz.getRange()
                      + " of "
                      + clazz.getClassReference());
            }
            ParsedApiClass superclass = classMap.get(supertype);
            if (!relationRange.isWithin(superclass.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Supertype relation range "
                      + relationRange
                      + " for "
                      + supertype
                      + " is not within superclass range "
                      + superclass.getRange()
                      + " of "
                      + supertype);
            }
          });

      clazz.forEachInterfaceThrowing(
          (iface, relationRange) -> {
            if (!relationRange.isWithin(clazz.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Interface relation range "
                      + relationRange
                      + " for "
                      + iface
                      + " is not within class range "
                      + clazz.getRange()
                      + " of "
                      + clazz.getClassReference());
            }
            ParsedApiClass interfaceClass = classMap.get(iface);
            if (!relationRange.isWithin(interfaceClass.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Interface relation range "
                      + relationRange
                      + " for "
                      + iface
                      + " is not within interface range "
                      + interfaceClass.getRange()
                      + " of "
                      + iface);
            }
          });

      clazz.forEachMethodThrowing(
          (method, methodRange) -> {
            if (!methodRange.isWithin(clazz.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Method range "
                      + methodRange
                      + " for "
                      + method
                      + " is not within class range "
                      + clazz.getRange()
                      + " of "
                      + clazz.getClassReference());
            }
          });

      clazz.forEachFieldThrowing(
          (field, fieldRange) -> {
            if (!fieldRange.isWithin(clazz.getRange())) {
              throw new ApiDatabaseGeneratorException(
                  "Field range "
                      + fieldRange
                      + " for "
                      + field
                      + " is not within class range "
                      + clazz.getRange()
                      + " of "
                      + clazz.getClassReference());
            }
          });
    }
  }

  private static void verifyClassOrInterface(Map<ClassReference, ParsedApiClass> classMap)
      throws ApiDatabaseGeneratorException {
    ClassInterfaceUnification unifier = new ClassInterfaceUnification();
    var classes = classMap.values();

    for (ParsedApiClass clazz : classes) {
      if (clazz.hasConstructor()) {
        unifier.markAsClass(clazz.getClassReference());
      }
    }

    for (ParsedApiClass clazz : classes) {
      clazz.forEachInterfaceThrowing((iface, range) -> unifier.markAsInterface(iface));
    }

    for (ParsedApiClass clazz : classes) {
      clazz.forEachSupertypeThrowing(
          (supertype, range) -> {
            if (supertype.getDescriptor().equals("Ljava/lang/Object;")) {
              // Both kinds can extend Object.
              return;
            }
            unifier.unify(clazz.getClassReference(), supertype);
          });
    }

    for (ParsedApiClass clazz : classes) {
      List<Pair<ClassReference, ApiRange>> supertypes = new ArrayList<>();
      clazz.forEachSupertype(
          (supertype, range) -> {
            if (!supertype.getDescriptor().equals("Ljava/lang/Object;")) {
              supertypes.add(Pair.create(supertype, range));
            }
          });

      for (int i = 0; i < supertypes.size(); i++) {
        for (int j = i + 1; j < supertypes.size(); j++) {
          if (supertypes.get(i).getSecond().isOverlappingWith(supertypes.get(j).getSecond())) {
            unifier.markAsInterface(clazz.getClassReference());
          }
        }
      }
    }
  }

  private static class ClassInterfaceUnification {
    private final DisjointSets<Object> unifier = new DisjointSets<>();

    private enum ClassKind {
      UNKNOWN,
      CLASS,
      INTERFACE
    }

    ClassInterfaceUnification() {
      unifier.makeSet(ClassKind.CLASS);
      unifier.makeSet(ClassKind.INTERFACE);
    }

    public void unify(ClassReference ref1, ClassReference ref2)
        throws ApiDatabaseGeneratorException {
      unionInternal(ref1, ref2);
    }

    public void markAsClass(ClassReference ref) throws ApiDatabaseGeneratorException {
      unionInternal(ref, ClassKind.CLASS);
    }

    public void markAsInterface(ClassReference ref) throws ApiDatabaseGeneratorException {
      unionInternal(ref, ClassKind.INTERFACE);
    }

    private void unionInternal(Object ref1, Object ref2) throws ApiDatabaseGeneratorException {
      Object root1 = unifier.findOrMakeSet(ref1);
      Object root2 = unifier.findOrMakeSet(ref2);
      if (root1 != root2) {
        unifier.union(root1, root2);
        Object classRoot = unifier.findSet(ClassKind.CLASS);
        assert classRoot != null;
        Object interfaceRoot = unifier.findSet(ClassKind.INTERFACE);
        assert interfaceRoot != null;
        if (classRoot == interfaceRoot) {
          throw new ApiDatabaseGeneratorException(
              "Inconsistent class/interface usage involving " + ref1);
        }
      }
    }
  }
}
