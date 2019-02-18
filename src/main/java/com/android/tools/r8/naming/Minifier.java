// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.naming;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItem;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexReference;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.naming.ClassNameMinifier.ClassRenaming;
import com.android.tools.r8.naming.FieldNameMinifier.FieldRenaming;
import com.android.tools.r8.naming.MethodNameMinifier.MethodRenaming;
import com.android.tools.r8.optimize.MemberRebindingAnalysis;
import com.android.tools.r8.shaking.Enqueuer.AppInfoWithLiveness;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Timing;
import com.google.common.collect.ImmutableMap;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

public class Minifier {

  static final char INNER_CLASS_SEPARATOR = '$';

  private final AppView<AppInfoWithLiveness> appView;
  private final AppInfoWithLiveness appInfo;
  private final RootSet rootSet;
  private final Set<DexCallSite> desugaredCallSites;
  private final InternalOptions options;

  public Minifier(
      AppView<AppInfoWithLiveness> appView,
      RootSet rootSet,
      Set<DexCallSite> desugaredCallSites) {
    this.appView = appView;
    this.appInfo = appView.appInfo();
    this.rootSet = rootSet;
    this.desugaredCallSites = desugaredCallSites;
    this.options = appView.options();
  }

  public NamingLens run(Timing timing) {
    assert options.enableMinification;
    timing.begin("MinifyClasses");
    ClassNameMinifier classNameMinifier = new ClassNameMinifier(appView, rootSet);
    ClassRenaming classRenaming = classNameMinifier.computeRenaming(timing);
    timing.end();

    assert new MinifiedRenaming(
            classRenaming, MethodRenaming.empty(), FieldRenaming.empty(), appInfo)
        .verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    timing.begin("MinifyMethods");
    MethodRenaming methodRenaming =
        new MethodNameMinifier(appView, rootSet).computeRenaming(desugaredCallSites, timing);
    timing.end();

    assert new MinifiedRenaming(classRenaming, methodRenaming, FieldRenaming.empty(), appInfo)
        .verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    timing.begin("MinifyFields");
    FieldRenaming fieldRenaming = new FieldNameMinifier(appView, rootSet).computeRenaming(timing);
    timing.end();

    NamingLens lens = new MinifiedRenaming(classRenaming, methodRenaming, fieldRenaming, appInfo);
    assert lens.verifyNoCollisions(appInfo.classes(), appInfo.dexItemFactory);

    timing.begin("MinifyIdentifiers");
    new IdentifierMinifier(
        appInfo, options.getProguardConfiguration().getAdaptClassStrings(), lens).run();
    timing.end();
    return lens;
  }

  static class MinifiedRenaming extends NamingLens {

    private final AppInfo appInfo;
    private final Map<String, String> packageRenaming;
    private final Map<DexItem, DexString> renaming = new IdentityHashMap<>();
    // This set is only used for asserting no duplicated names.
    private final Map<DexString, DexType> renamedTypesForVerification;

    private MinifiedRenaming(
        ClassRenaming classRenaming,
        MethodRenaming methodRenaming,
        FieldRenaming fieldRenaming,
        AppInfo appInfo) {
      this.appInfo = appInfo;
      this.packageRenaming = classRenaming.packageRenaming;
      renaming.putAll(classRenaming.classRenaming);
      renaming.putAll(methodRenaming.renaming);
      renaming.putAll(methodRenaming.callSiteRenaming);
      renaming.putAll(fieldRenaming.renaming);
      renamedTypesForVerification = new HashMap<>();
      for (Map.Entry<DexType, DexString> entry : classRenaming.classRenaming.entrySet()) {
        renamedTypesForVerification.put(entry.getValue(), entry.getKey());
      }
    }

    @Override
    public String lookupPackageName(String packageName) {
      return packageRenaming.getOrDefault(packageName, packageName);
    }

    @Override
    public DexString lookupDescriptor(DexType type) {
      DexString dexString = renaming.get(type);
      if (dexString != null) {
        return dexString;
      }
      assert type.isPrimitiveType()
              || type.isVoidType()
              || !renamedTypesForVerification.containsKey(type.descriptor)
          : "Duplicate minified type '"
              + type.descriptor
              + "' already mapped for: "
              + renamedTypesForVerification.get(type.descriptor);
      return type.descriptor;
    }

    @Override
    public DexString lookupInnerName(InnerClassAttribute attribute, InternalOptions options) {
      if (attribute.getInnerName() == null) {
        return null;
      }
      // The Java reflection library assumes that that inner-class names are separated by a $ and
      // thus we allow the mapping of an inner name to rely on that too. If the dollar is not
      // present after pulling off the original inner-name, then we revert to using the simple name
      // of the inner class as its name.
      DexType innerType = attribute.getInner();
      String inner = DescriptorUtils.descriptorToInternalName(innerType.descriptor.toString());
      String innerName = attribute.getInnerName().toString();
      int lengthOfPrefix = inner.length() - innerName.length();
      if (lengthOfPrefix < 0
          || inner.lastIndexOf(INNER_CLASS_SEPARATOR, lengthOfPrefix - 1) < 0
          || !inner.endsWith(innerName)) {
        return lookupSimpleName(innerType, options.itemFactory);
      }

      // At this point we assume the input was of the form: <OuterType>$<index><InnerName>
      // Find the mapped type and if it remains the same return that, otherwise split at $.
      String innerTypeMapped =
          DescriptorUtils.descriptorToInternalName(lookupDescriptor(innerType).toString());
      if (inner.equals(innerTypeMapped)) {
        return attribute.getInnerName();
      }
      int index = innerTypeMapped.lastIndexOf(INNER_CLASS_SEPARATOR);
      if (index < 0) {
        // TODO(b/120639028): Replace this by "assert false" and remove the testing option.
        // Hitting means we have converted a proper Outer$Inner relationship to an invalid one.
        assert !options.testing.allowFailureOnInnerClassErrors
            : "Outer$Inner class was remapped without keeping the dollar separator";
        return lookupSimpleName(innerType, options.itemFactory);
      }
      return options.itemFactory.createString(innerTypeMapped.substring(index + 1));
    }

    @Override
    public DexString lookupName(DexMethod method) {
      return renaming.getOrDefault(method, method.name);
    }

    @Override
    public DexString lookupMethodName(DexCallSite callSite) {
      return renaming.getOrDefault(callSite, callSite.methodName);
    }

    @Override
    public DexString lookupName(DexField field) {
      return renaming.getOrDefault(field, field.name);
    }

    @Override
    void forAllRenamedTypes(Consumer<DexType> consumer) {
      DexReference.filterDexType(DexReference.filterDexReference(renaming.keySet().stream()))
          .forEach(consumer);
    }

    @Override
    <T extends DexItem> Map<String, T> getRenamedItems(
        Class<T> clazz, Predicate<T> predicate, Function<T, String> namer) {
      return renaming.keySet().stream()
          .filter(item -> (clazz.isInstance(item) && predicate.test(clazz.cast(item))))
          .map(clazz::cast)
          .collect(ImmutableMap.toImmutableMap(namer, i -> i));
    }

    /**
     * Checks whether the target is precise enough to be translated,
     * <p>
     * We only track the renaming of actual definitions, Thus, if we encounter a method id that
     * does not directly point at a definition, we won't find the actual renaming. To avoid
     * dispatching on every lookup, we assume that the tree has been fully dispatched by
     * {@link MemberRebindingAnalysis}.
     * <p>
     * Library methods are excluded from this check, as those are never renamed.
     */
    @Override
    public boolean checkTargetCanBeTranslated(DexMethod item) {
      if (item.holder.isArrayType()) {
        // Array methods are never renamed, so do not bother to check.
        return true;
      }
      DexClass holder = appInfo.definitionFor(item.holder);
      if (holder == null || holder.isLibraryClass()) {
        return true;
      }
      // We don't know which invoke type this method is used for, so checks that it has been
      // rebound either way.
      DexEncodedMethod staticTarget = appInfo.lookupStaticTarget(item);
      DexEncodedMethod directTarget = appInfo.lookupDirectTarget(item);
      DexEncodedMethod virtualTarget = appInfo.lookupVirtualTarget(item.holder, item);
      DexClass staticTargetHolder =
          staticTarget != null ? appInfo.definitionFor(staticTarget.method.getHolder()) : null;
      DexClass directTargetHolder =
          directTarget != null ? appInfo.definitionFor(directTarget.method.getHolder()) : null;
      DexClass virtualTargetHolder =
          virtualTarget != null ? appInfo.definitionFor(virtualTarget.method.getHolder()) : null;
      return (directTarget == null && staticTarget == null && virtualTarget == null)
          || (virtualTarget != null && virtualTarget.method == item)
          || (directTarget != null && directTarget.method == item)
          || (staticTarget != null && staticTarget.method == item)
          || (directTargetHolder != null && directTargetHolder.isLibraryClass())
          || (virtualTargetHolder != null && virtualTargetHolder.isLibraryClass())
          || (staticTargetHolder != null && staticTargetHolder.isLibraryClass());
    }

    @Override
    public String toString() {
      StringBuilder builder = new StringBuilder();
      renaming.forEach((item, str) -> {
        if (item instanceof DexType) {
          builder.append("[c] ");
        } else if (item instanceof DexMethod) {
          builder.append("[m] ");
        } else if (item instanceof DexField) {
          builder.append("[f] ");
        }
        builder.append(item.toSourceString());
        builder.append(" -> ");
        builder.append(str.toSourceString());
        builder.append('\n');
      });
      return builder.toString();
    }
  }
}
