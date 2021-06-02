// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.kotlin;

import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmFieldSignature;
import static com.android.tools.r8.kotlin.KotlinMetadataUtils.toJvmMethodSignature;
import static com.android.tools.r8.utils.FunctionUtils.forEachApply;

import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexDefinitionSupplier;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.naming.NamingLens;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import com.android.tools.r8.utils.Reporter;
import com.google.common.collect.ImmutableList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import kotlinx.metadata.KmClass;
import kotlinx.metadata.KmConstructor;
import kotlinx.metadata.KmType;
import kotlinx.metadata.jvm.JvmClassExtensionVisitor;
import kotlinx.metadata.jvm.JvmExtensionsKt;
import kotlinx.metadata.jvm.JvmMethodSignature;
import kotlinx.metadata.jvm.KotlinClassHeader;
import kotlinx.metadata.jvm.KotlinClassMetadata;

public class KotlinClassInfo implements KotlinClassLevelInfo {

  private final int flags;
  private final String name;
  private final String moduleName;
  private final List<KotlinConstructorInfo> constructorsWithNoBacking;
  private final KotlinDeclarationContainerInfo declarationContainerInfo;
  private final List<KotlinTypeParameterInfo> typeParameters;
  private final List<KotlinTypeInfo> superTypes;
  private final List<KotlinTypeReference> sealedSubClasses;
  private final List<KotlinTypeReference> nestedClasses;
  private final List<String> enumEntries;
  private final KotlinVersionRequirementInfo versionRequirements;
  private final KotlinTypeReference anonymousObjectOrigin;
  private final String packageName;
  private final KotlinLocalDelegatedPropertyInfo localDelegatedProperties;
  private final int[] metadataVersion;

  private KotlinClassInfo(
      int flags,
      String name,
      String moduleName,
      KotlinDeclarationContainerInfo declarationContainerInfo,
      List<KotlinTypeParameterInfo> typeParameters,
      List<KotlinConstructorInfo> constructorsWithNoBacking,
      List<KotlinTypeInfo> superTypes,
      List<KotlinTypeReference> sealedSubClasses,
      List<KotlinTypeReference> nestedClasses,
      List<String> enumEntries,
      KotlinVersionRequirementInfo versionRequirements,
      KotlinTypeReference anonymousObjectOrigin,
      String packageName,
      KotlinLocalDelegatedPropertyInfo localDelegatedProperties,
      int[] metadataVersion) {
    this.flags = flags;
    this.name = name;
    this.moduleName = moduleName;
    this.declarationContainerInfo = declarationContainerInfo;
    this.typeParameters = typeParameters;
    this.constructorsWithNoBacking = constructorsWithNoBacking;
    this.superTypes = superTypes;
    this.sealedSubClasses = sealedSubClasses;
    this.nestedClasses = nestedClasses;
    this.enumEntries = enumEntries;
    this.versionRequirements = versionRequirements;
    this.anonymousObjectOrigin = anonymousObjectOrigin;
    this.packageName = packageName;
    this.localDelegatedProperties = localDelegatedProperties;
    this.metadataVersion = metadataVersion;
  }

  public static KotlinClassInfo create(
      KotlinClassMetadata.Class metadata,
      String packageName,
      int[] metadataVersion,
      DexClass hostClass,
      AppView<?> appView,
      Consumer<DexEncodedMethod> keepByteCode) {
    DexItemFactory factory = appView.dexItemFactory();
    Reporter reporter = appView.reporter();
    KmClass kmClass = metadata.toKmClass();
    KotlinJvmSignatureExtensionInformation extensionInformation =
        KotlinJvmSignatureExtensionInformation.readInformationFromMessage(
            metadata, appView.options());
    Map<String, DexEncodedField> fieldMap = new HashMap<>();
    for (DexEncodedField field : hostClass.fields()) {
      fieldMap.put(toJvmFieldSignature(field.getReference()).asString(), field);
    }
    Map<String, DexEncodedMethod> methodMap = new HashMap<>();
    for (DexEncodedMethod method : hostClass.methods()) {
      methodMap.put(toJvmMethodSignature(method.getReference()).asString(), method);
    }
    ImmutableList.Builder<KotlinConstructorInfo> notBackedConstructors = ImmutableList.builder();
    int constructorIndex = 0;
    for (KmConstructor kmConstructor : kmClass.getConstructors()) {
      boolean readConstructorSignature =
          extensionInformation.hasJvmMethodSignatureExtensionForConstructor(constructorIndex++);
      KotlinConstructorInfo constructorInfo =
          KotlinConstructorInfo.create(kmConstructor, factory, reporter, readConstructorSignature);
      JvmMethodSignature signature = JvmExtensionsKt.getSignature(kmConstructor);
      if (signature != null) {
        DexEncodedMethod method = methodMap.get(signature.asString());
        if (method != null) {
          method.setKotlinMemberInfo(constructorInfo);
          continue;
        }
      }
      // We could not find a definition for the constructor - add it to ensure the same output.
      notBackedConstructors.add(constructorInfo);
    }
    KotlinDeclarationContainerInfo container =
        KotlinDeclarationContainerInfo.create(
            kmClass, methodMap, fieldMap, factory, reporter, keepByteCode, extensionInformation);
    setCompanionObject(kmClass, hostClass, reporter);
    return new KotlinClassInfo(
        kmClass.getFlags(),
        kmClass.name,
        JvmExtensionsKt.getModuleName(kmClass),
        container,
        KotlinTypeParameterInfo.create(kmClass.getTypeParameters(), factory, reporter),
        notBackedConstructors.build(),
        getSuperTypes(kmClass.getSupertypes(), factory, reporter),
        getSealedSubClasses(kmClass.getSealedSubclasses(), factory),
        getNestedClasses(hostClass, kmClass.getNestedClasses(), factory),
        kmClass.getEnumEntries(),
        KotlinVersionRequirementInfo.create(kmClass.getVersionRequirements()),
        getAnonymousObjectOrigin(kmClass, factory),
        packageName,
        KotlinLocalDelegatedPropertyInfo.create(
            JvmExtensionsKt.getLocalDelegatedProperties(kmClass), factory, reporter),
        metadataVersion);
  }

  private static KotlinTypeReference getAnonymousObjectOrigin(
      KmClass kmClass, DexItemFactory factory) {
    String anonymousObjectOriginName = JvmExtensionsKt.getAnonymousObjectOriginName(kmClass);
    if (anonymousObjectOriginName != null) {
      return KotlinTypeReference.fromBinaryName(anonymousObjectOriginName, factory);
    }
    return null;
  }

  private static List<KotlinTypeReference> getNestedClasses(
      DexClass clazz, List<String> nestedClasses, DexItemFactory factory) {
    ImmutableList.Builder<KotlinTypeReference> nestedTypes = ImmutableList.builder();
    for (String nestedClass : nestedClasses) {
      String binaryName =
          clazz.type.toBinaryName() + DescriptorUtils.INNER_CLASS_SEPARATOR + nestedClass;
      nestedTypes.add(KotlinTypeReference.fromBinaryName(binaryName, factory));
    }
    return nestedTypes.build();
  }

  private static List<KotlinTypeReference> getSealedSubClasses(
      List<String> sealedSubclasses, DexItemFactory factory) {
    ImmutableList.Builder<KotlinTypeReference> sealedTypes = ImmutableList.builder();
    for (String sealedSubClass : sealedSubclasses) {
      String binaryName =
          sealedSubClass.replace(
              DescriptorUtils.JAVA_PACKAGE_SEPARATOR, DescriptorUtils.INNER_CLASS_SEPARATOR);
      sealedTypes.add(KotlinTypeReference.fromBinaryName(binaryName, factory));
    }
    return sealedTypes.build();
  }

  private static List<KotlinTypeInfo> getSuperTypes(
      List<KmType> superTypes, DexItemFactory factory, Reporter reporter) {
    ImmutableList.Builder<KotlinTypeInfo> superTypeInfos = ImmutableList.builder();
    for (KmType superType : superTypes) {
      superTypeInfos.add(KotlinTypeInfo.create(superType, factory, reporter));
    }
    return superTypeInfos.build();
  }

  private static void setCompanionObject(KmClass kmClass, DexClass hostClass, Reporter reporter) {
    String companionObjectName = kmClass.getCompanionObject();
    if (companionObjectName == null) {
      return;
    }
    for (DexEncodedField field : hostClass.fields()) {
      if (field.getReference().name.toString().equals(companionObjectName)) {
        field.setKotlinMemberInfo(new KotlinCompanionInfo(companionObjectName));
        return;
      }
    }
    reporter.warning(
        KotlinMetadataDiagnostic.missingCompanionObject(hostClass, companionObjectName));
  }

  @Override
  public boolean isClass() {
    return true;
  }

  @Override
  public KotlinClassInfo asClass() {
    return this;
  }

  @Override
  public Pair<KotlinClassHeader, Boolean> rewrite(
      DexClass clazz, AppView<?> appView, NamingLens namingLens) {
    KmClass kmClass = new KmClass();
    // TODO(b/154348683): Set flags.
    kmClass.setFlags(flags);
    // Set potentially renamed class name.
    DexString originalDescriptor = clazz.type.descriptor;
    DexString rewrittenDescriptor = namingLens.lookupDescriptor(clazz.type);
    // If the original descriptor equals the rewritten descriptor, we pick the original name
    // to preserve potential errors in the original name. As an example, the kotlin stdlib has
    // name: .kotlin/collections/CollectionsKt___CollectionsKt$groupingBy$1, which seems incorrect.
    boolean rewritten = !originalDescriptor.equals(rewrittenDescriptor);
    kmClass.setName(
        !rewritten
            ? this.name
            : DescriptorUtils.getBinaryNameFromDescriptor(rewrittenDescriptor.toString()));
    // Find a companion object.
    for (DexEncodedField field : clazz.fields()) {
      if (field.getKotlinInfo().isCompanion()) {
        rewritten |=
            field.getKotlinInfo().asCompanion().rewrite(kmClass, field.getReference(), namingLens);
      }
    }
    // Take all not backed constructors because we will never find them in definitions.
    for (KotlinConstructorInfo constructorInfo : constructorsWithNoBacking) {
      rewritten |= constructorInfo.rewrite(kmClass, null, appView, namingLens);
    }
    // Find all constructors.
    for (DexEncodedMethod method : clazz.methods()) {
      if (method.getKotlinInfo().isConstructor()) {
        KotlinConstructorInfo constructorInfo = method.getKotlinInfo().asConstructor();
        rewritten |= constructorInfo.rewrite(kmClass, method, appView, namingLens);
      }
    }
    // Rewrite functions, type-aliases and type-parameters.
    rewritten |=
        declarationContainerInfo.rewrite(
            kmClass::visitFunction,
            kmClass::visitProperty,
            kmClass::visitTypeAlias,
            clazz,
            appView,
            namingLens);
    // Rewrite type parameters.
    for (KotlinTypeParameterInfo typeParameter : typeParameters) {
      rewritten |= typeParameter.rewrite(kmClass::visitTypeParameter, appView, namingLens);
    }
    // Rewrite super types.
    for (KotlinTypeInfo superType : superTypes) {
      // Ensure the rewritten super type is not this type.
      if (clazz.getType() != superType.rewriteType(appView.graphLens())) {
        rewritten |= superType.rewrite(kmClass::visitSupertype, appView, namingLens);
      } else {
        rewritten = true;
      }
    }
    // Rewrite nested classes.
    for (KotlinTypeReference nestedClass : nestedClasses) {
      rewritten |=
          nestedClass.toRenamedBinaryNameOrDefault(
              nestedDescriptor -> {
                if (nestedDescriptor != null) {
                  // If the class is a nested class, it should be on the form Foo.Bar$Baz, where Baz
                  // is the
                  // name we should record.
                  int innerClassIndex =
                      nestedDescriptor.lastIndexOf(DescriptorUtils.INNER_CLASS_SEPARATOR);
                  kmClass.visitNestedClass(nestedDescriptor.substring(innerClassIndex + 1));
                }
              },
              appView,
              namingLens,
              null);
    }
    // Rewrite sealed sub classes.
    for (KotlinTypeReference sealedSubClass : sealedSubClasses) {
      rewritten |=
          sealedSubClass.toRenamedBinaryNameOrDefault(
              sealedDescriptor -> {
                if (sealedDescriptor != null) {
                  kmClass.visitSealedSubclass(
                      sealedDescriptor.replace(
                          DescriptorUtils.INNER_CLASS_SEPARATOR,
                          DescriptorUtils.JAVA_PACKAGE_SEPARATOR));
                }
              },
              appView,
              namingLens,
              null);
    }
    // TODO(b/154347404): Understand enum entries.
    kmClass.getEnumEntries().addAll(enumEntries);
    rewritten |= versionRequirements.rewrite(kmClass::visitVersionRequirement);
    JvmClassExtensionVisitor extensionVisitor =
        (JvmClassExtensionVisitor) kmClass.visitExtensions(JvmClassExtensionVisitor.TYPE);
    extensionVisitor.visitModuleName(moduleName);
    if (anonymousObjectOrigin != null) {
      rewritten |=
          anonymousObjectOrigin.toRenamedBinaryNameOrDefault(
              renamedAnon -> {
                if (renamedAnon != null) {
                  extensionVisitor.visitAnonymousObjectOriginName(renamedAnon);
                }
              },
              appView,
              namingLens,
              null);
    }
    rewritten |=
        localDelegatedProperties.rewrite(
            extensionVisitor::visitLocalDelegatedProperty, appView, namingLens);
    extensionVisitor.visitEnd();
    KotlinClassMetadata.Class.Writer writer = new KotlinClassMetadata.Class.Writer();
    kmClass.accept(writer);
    return Pair.create(writer.write().getHeader(), rewritten);
  }

  @Override
  public String getPackageName() {
    return packageName;
  }

  @Override
  public int[] getMetadataVersion() {
    return metadataVersion;
  }

  @Override
  public void trace(DexDefinitionSupplier definitionSupplier) {
    forEachApply(constructorsWithNoBacking, constructor -> constructor::trace, definitionSupplier);
    declarationContainerInfo.trace(definitionSupplier);
    forEachApply(typeParameters, param -> param::trace, definitionSupplier);
    forEachApply(superTypes, type -> type::trace, definitionSupplier);
    forEachApply(sealedSubClasses, sealed -> sealed::trace, definitionSupplier);
    forEachApply(nestedClasses, nested -> nested::trace, definitionSupplier);
    localDelegatedProperties.trace(definitionSupplier);
    // TODO(b/154347404): trace enum entries.
    if (anonymousObjectOrigin != null) {
      anonymousObjectOrigin.trace(definitionSupplier);
    }
  }
}
