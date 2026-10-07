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

  public interface SetOperations<D> {
    boolean isSubsetOf(D a, D b);

    boolean isDisjointWith(D a, D b);
  }

  public static class ApiRangeSetOperations implements SetOperations<ApiRange> {
    @Override
    public boolean isSubsetOf(ApiRange a, ApiRange b) {
      return a.isWithin(b);
    }

    @Override
    public boolean isDisjointWith(ApiRange a, ApiRange b) {
      return !a.isOverlappingWith(b);
    }
  }

  public static <D> void verify(
      Collection<ParsedApiClass<D>> classes, SetOperations<D> setOperations)
      throws ApiDatabaseGeneratorException {
    Map<ClassReference, ParsedApiClass<D>> classMap = new HashMap<>();
    for (ParsedApiClass<D> clazz : classes) {
      ClassReference classReference = clazz.getClassReference();
      if (classMap.containsKey(classReference)) {
        throw new ApiDatabaseGeneratorException("Duplicate API classes for: " + classReference);
      }
      classMap.put(classReference, clazz);
    }

    verify(classMap, setOperations);
  }

  private static <D> void verify(
      Map<ClassReference, ParsedApiClass<D>> classMap, SetOperations<D> setOperations)
      throws ApiDatabaseGeneratorException {
    verifyClassHierarchy(classMap);
    verifyApiRanges(classMap, setOperations);
    verifyClassOrInterface(classMap, setOperations);
  }

  private static <D> void verifyClassHierarchy(Map<ClassReference, ParsedApiClass<D>> classMap)
      throws ApiDatabaseGeneratorException {
    for (ParsedApiClass<D> clazz : classMap.values()) {
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

  private static <D> void verifyApiRanges(
      Map<ClassReference, ParsedApiClass<D>> classMap, SetOperations<D> setOperations)
      throws ApiDatabaseGeneratorException {
    for (ParsedApiClass<D> clazz : classMap.values()) {
      clazz.forEachSupertypeThrowing(
          (supertype, relationRange) -> {
            if (!setOperations.isSubsetOf(relationRange, clazz.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Supertype relation range "
                      + relationRange
                      + " for "
                      + supertype
                      + " is not within class range "
                      + clazz.getData()
                      + " of "
                      + clazz.getClassReference());
            }
            ParsedApiClass<D> superclass = classMap.get(supertype);
            if (!setOperations.isSubsetOf(relationRange, superclass.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Supertype relation range "
                      + relationRange
                      + " for "
                      + supertype
                      + " is not within superclass range "
                      + superclass.getData()
                      + " of "
                      + supertype);
            }
          });

      clazz.forEachInterfaceThrowing(
          (iface, relationRange) -> {
            if (!setOperations.isSubsetOf(relationRange, clazz.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Interface relation range "
                      + relationRange
                      + " for "
                      + iface
                      + " is not within class range "
                      + clazz.getData()
                      + " of "
                      + clazz.getClassReference());
            }
            ParsedApiClass<D> interfaceClass = classMap.get(iface);
            if (!setOperations.isSubsetOf(relationRange, interfaceClass.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Interface relation range "
                      + relationRange
                      + " for "
                      + iface
                      + " is not within interface range "
                      + interfaceClass.getData()
                      + " of "
                      + iface);
            }
          });

      clazz.forEachMethodThrowing(
          (method, methodRange) -> {
            if (!setOperations.isSubsetOf(methodRange, clazz.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Method range "
                      + methodRange
                      + " for "
                      + method
                      + " is not within class range "
                      + clazz.getData()
                      + " of "
                      + clazz.getClassReference());
            }
          });

      clazz.forEachFieldThrowing(
          (field, fieldRange) -> {
            if (!setOperations.isSubsetOf(fieldRange, clazz.getData())) {
              throw new ApiDatabaseGeneratorException(
                  "Field range "
                      + fieldRange
                      + " for "
                      + field
                      + " is not within class range "
                      + clazz.getData()
                      + " of "
                      + clazz.getClassReference());
            }
          });
    }
  }

  private static <D> void verifyClassOrInterface(
      Map<ClassReference, ParsedApiClass<D>> classMap, SetOperations<D> setOperations)
      throws ApiDatabaseGeneratorException {
    ClassInterfaceUnification unifier = new ClassInterfaceUnification();
    var classes = classMap.values();

    for (ParsedApiClass<D> clazz : classes) {
      if (clazz.hasConstructor()) {
        unifier.markAsClass(clazz.getClassReference());
      }
    }

    for (ParsedApiClass<D> clazz : classes) {
      clazz.forEachInterfaceThrowing((iface, range) -> unifier.markAsInterface(iface));
    }

    for (ParsedApiClass<D> clazz : classes) {
      clazz.forEachSupertypeThrowing(
          (supertype, range) -> {
            if (supertype.getDescriptor().equals("Ljava/lang/Object;")) {
              // Both kinds can extend Object.
              return;
            }
            unifier.unify(clazz.getClassReference(), supertype);
          });
    }

    for (ParsedApiClass<D> clazz : classes) {
      List<Pair<ClassReference, D>> supertypes = new ArrayList<>();
      clazz.forEachSupertype(
          (supertype, range) -> {
            if (!supertype.getDescriptor().equals("Ljava/lang/Object;")) {
              supertypes.add(Pair.create(supertype, range));
            }
          });

      for (int i = 0; i < supertypes.size(); i++) {
        for (int j = i + 1; j < supertypes.size(); j++) {
          if (!setOperations.isDisjointWith(
              supertypes.get(i).getSecond(), supertypes.get(j).getSecond())) {
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

    void unify(ClassReference ref1, ClassReference ref2) throws ApiDatabaseGeneratorException {
      unionInternal(ref1, ref2);
    }

    void markAsClass(ClassReference ref) throws ApiDatabaseGeneratorException {
      unionInternal(ref, ClassKind.CLASS);
    }

    void markAsInterface(ClassReference ref) throws ApiDatabaseGeneratorException {
      unionInternal(ref, ClassKind.INTERFACE);
    }

    @SuppressWarnings("ReferenceEquality")
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
