// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.optimize.enums;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;

import com.android.tools.r8.contexts.CompilationContext.ProcessorContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.FieldResolutionResult.SuccessfulFieldResolutionResult;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramField;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.PrunedItems;
import com.android.tools.r8.ir.analysis.inlining.SimpleInliningConstraint;
import com.android.tools.r8.ir.analysis.type.ClassTypeElement;
import com.android.tools.r8.ir.analysis.type.TypeElement;
import com.android.tools.r8.ir.code.BasicBlock;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionListIterator;
import com.android.tools.r8.ir.code.InvokeDirect;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.NewInstance;
import com.android.tools.r8.ir.code.NewUnboxedEnumInstance;
import com.android.tools.r8.ir.code.StaticPut;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.OneTimeMethodProcessor;
import com.android.tools.r8.ir.optimize.enums.EnumDataMap.EnumData;
import com.android.tools.r8.ir.optimize.enums.classification.CheckNotNullEnumUnboxerMethodClassification;
import com.android.tools.r8.ir.optimize.enums.code.CheckNotZeroCode;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedback;
import com.android.tools.r8.ir.optimize.info.OptimizationFeedbackIgnore;
import com.android.tools.r8.ir.optimize.info.field.InstanceFieldInitializationInfo;
import com.android.tools.r8.shaking.AppInfoWithLiveness;
import com.android.tools.r8.synthesis.SyntheticNaming.SyntheticKind;
import com.android.tools.r8.utils.BooleanUtils;
import com.android.tools.r8.utils.ImmutableArrayUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.SetUtils;
import com.android.tools.r8.utils.ThreadUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.utils.collections.ProgramMethodMap;
import com.google.common.collect.Sets;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import it.unimi.dsi.fastutil.ints.IntList;
import java.util.ArrayList;
import java.util.Collection;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.function.Predicate;

class EnumUnboxingTreeFixer {

  private final EnumUnboxingLens.Builder lensBuilder;
  private final AppView<AppInfoWithLiveness> appView;
  private final ProgramMethodMap<Set<DexProgramClass>> checkNotNullMethods;
  private final DexItemFactory factory;
  private final EnumDataMap enumDataMap;
  private final Set<DexProgramClass> unboxedEnums;
  private final EnumUnboxingUtilityClasses utilityClasses;

  EnumUnboxingTreeFixer(
      AppView<AppInfoWithLiveness> appView,
      ProgramMethodMap<Set<DexProgramClass>> checkNotNullMethods,
      EnumDataMap enumDataMap,
      Set<DexProgramClass> unboxedEnums,
      EnumUnboxingUtilityClasses utilityClasses) {
    this.appView = appView;
    this.checkNotNullMethods = checkNotNullMethods;
    this.enumDataMap = enumDataMap;
    this.factory = appView.dexItemFactory();
    this.lensBuilder =
        EnumUnboxingLens.enumUnboxingLensBuilder(appView)
            .mapUnboxedEnums(enumDataMap.getUnboxedEnums());
    this.unboxedEnums = unboxedEnums;
    this.utilityClasses = utilityClasses;
  }

  Result fixupTypeReferences(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    PrunedItems.Builder prunedItemsBuilder = PrunedItems.builder();

    // We do this before so that we can still perform lookup of definitions.
    fixupEnumClassInitializers(converter, executorService);

    // Fix all methods and fields using enums to unbox.
    // TODO(b/191617665): Parallelize this fixup.
    for (DexProgramClass clazz : appView.appInfo().classes()) {
      if (enumDataMap.isUnboxedEnum(clazz)) {
        // Clear the initializers and move the other methods to the new location.
        LocalEnumUnboxingUtilityClass localUtilityClass =
            utilityClasses.getLocalUtilityClass(clazz);
        Collection<DexEncodedField> localUtilityFields =
            createLocalUtilityFields(clazz, localUtilityClass, prunedItemsBuilder);
        Collection<DexEncodedMethod> localUtilityMethods =
            createLocalUtilityMethods(clazz, localUtilityClass, prunedItemsBuilder);

        // Cleanup old class.
        clazz.clearInstanceFields();
        clazz.clearStaticFields();
        clazz.getMethodCollection().clearDirectMethods();
        clazz.getMethodCollection().clearVirtualMethods();

        // Update members on the local utility class.
        localUtilityClass.getDefinition().setDirectMethods(localUtilityMethods);
        localUtilityClass.getDefinition().setStaticFields(localUtilityFields);
      } else {
        clazz.getMethodCollection().replaceMethods(method -> fixupEncodedMethod(clazz, method));
        fixupFields(clazz.staticFields(), clazz::setStaticField);
        fixupFields(clazz.instanceFields(), clazz::setInstanceField);
      }
    }

    // Create mapping from checkNotNull() to checkNotZero() methods.
    Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping =
        duplicateCheckNotNullMethods(converter, executorService);

    return new Result(
        checkNotNullToCheckNotZeroMapping, lensBuilder.build(appView), prunedItemsBuilder.build());
  }

  private Map<DexMethod, DexMethod> duplicateCheckNotNullMethods(
      IRConverter converter, ExecutorService executorService) throws ExecutionException {
    Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping = new IdentityHashMap<>();
    ProcessorContext processorContext = appView.createProcessorContext();
    OneTimeMethodProcessor.Builder methodProcessorBuilder =
        OneTimeMethodProcessor.builder(processorContext);

    // Only duplicate checkNotNull() methods that are required for enum unboxing.
    checkNotNullMethods.removeIf(
        (checkNotNullMethod, dependentEnums) ->
            !SetUtils.containsAnyOf(unboxedEnums, dependentEnums));

    // For each checkNotNull() method, synthesize a free flowing static checkNotZero() method that
    // takes an int instead of an Object with the same implementation.
    checkNotNullMethods.forEach(
        (checkNotNullMethod, dependentEnums) -> {
          CheckNotNullEnumUnboxerMethodClassification checkNotNullClassification =
              checkNotNullMethod
                  .getOptimizationInfo()
                  .getEnumUnboxerMethodClassification()
                  .asCheckNotNullClassification();
          DexProto newProto =
              factory.createProto(
                  factory.voidType,
                  ImmutableArrayUtils.set(
                      checkNotNullMethod.getParameters().getBacking(),
                      checkNotNullClassification.getArgumentIndex(),
                      factory.intType));
          ProgramMethod checkNotZeroMethod =
              appView
                  .getSyntheticItems()
                  .createMethod(
                      SyntheticKind.ENUM_UNBOXING_CHECK_NOT_ZERO_METHOD,
                      // Use the context of the checkNotNull() method to ensure the method is placed
                      // in the same feature split.
                      processorContext
                          .createMethodProcessingContext(checkNotNullMethod)
                          .createUniqueContext(),
                      appView,
                      builder ->
                          builder
                              .setAccessFlags(MethodAccessFlags.createPublicStaticSynthetic())
                              .setClassFileVersion(
                                  checkNotNullMethod
                                      .getDefinition()
                                      .getClassFileVersionOrElse(null))
                              .setCode(method -> new CheckNotZeroCode(checkNotNullMethod))
                              .setProto(newProto));
          checkNotNullToCheckNotZeroMapping.put(
              checkNotNullMethod.getReference(), checkNotZeroMethod.getReference());
          lensBuilder.recordCheckNotZeroMethod(checkNotNullMethod, checkNotZeroMethod);
          methodProcessorBuilder.add(checkNotZeroMethod);
        });

    // Convert each of the synthesized methods. These methods are converted eagerly, since their
    // code objects are of type 'CheckNotZeroCode', which implements most methods using throw new
    // Unreachable().
    OneTimeMethodProcessor methodProcessor = methodProcessorBuilder.build();
    methodProcessor.forEachWaveWithExtension(
        (method, methodProcessingContext) ->
            converter.processDesugaredMethod(
                method, OptimizationFeedback.getSimple(), methodProcessor, methodProcessingContext),
        executorService);

    return checkNotNullToCheckNotZeroMapping;
  }


  private void fixupEnumClassInitializers(IRConverter converter, ExecutorService executorService)
      throws ExecutionException {
    DexEncodedField ordinalField =
        appView
            .appInfo()
            .resolveField(appView.dexItemFactory().enumMembers.ordinalField)
            .getResolvedField();
    ThreadUtils.processItems(
        unboxedEnums,
        unboxedEnum -> fixupEnumClassInitializer(converter, unboxedEnum, ordinalField),
        executorService);
  }

  private void fixupEnumClassInitializer(
      IRConverter converter, DexProgramClass unboxedEnum, DexEncodedField ordinalField) {
    if (!unboxedEnum.hasClassInitializer()) {
      assert unboxedEnum.staticFields().isEmpty();
      return;
    }

    ProgramMethod classInitializer = unboxedEnum.getProgramClassInitializer();
    EnumData enumData = enumDataMap.get(unboxedEnum);
    LocalEnumUnboxingUtilityClass localUtilityClass =
        utilityClasses.getLocalUtilityClass(unboxedEnum);

    // Rewrite enum instantiations + remove static-puts to pruned fields.
    IRCode code = classInitializer.buildIR(appView);
    ListIterator<BasicBlock> blockIterator = code.listIterator();
    Set<Instruction> instructionsToRemove = Sets.newIdentityHashSet();
    while (blockIterator.hasNext()) {
      BasicBlock block = blockIterator.next();
      InstructionListIterator instructionIterator = block.listIterator(code);
      while (instructionIterator.hasNext()) {
        Instruction instruction = instructionIterator.next();
        if (instructionsToRemove.remove(instruction)) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
          continue;
        }

        if (instruction.isConstClass()) {
          // Rewrite MyEnum.class.desiredAssertionStatus() to
          // LocalEnumUtility.class.desiredAssertionStatus() instead of
          // int.class.desiredAssertionStatus().
          ConstClass constClass = instruction.asConstClass();
          if (constClass.getType() != unboxedEnum.getType()) {
            continue;
          }

          List<InvokeVirtual> desiredAssertionStatusUsers = new ArrayList<>();
          for (Instruction user : constClass.outValue().aliasedUsers()) {
            if (user.isInvokeVirtual()) {
              InvokeVirtual invoke = user.asInvokeVirtual();
              if (invoke.getInvokedMethod()
                  == appView.dexItemFactory().classMethods.desiredAssertionStatus) {
                desiredAssertionStatusUsers.add(invoke);
              }
            }
          }

          if (!desiredAssertionStatusUsers.isEmpty()) {
            ConstClass newConstClass =
                ConstClass.builder()
                    .setType(localUtilityClass.getType())
                    .setFreshOutValue(
                        code, TypeElement.classClassType(appView, definitelyNotNull()))
                    .setPosition(constClass.getPosition())
                    .build();
            instructionIterator.add(newConstClass);
            constClass
                .outValue()
                .replaceSelectiveInstructionUsers(
                    newConstClass.outValue(), desiredAssertionStatusUsers::contains);
          }
        } else if (instruction.isNewInstance()) {
          NewInstance newInstance = instruction.asNewInstance();
          DexType rewrittenType = appView.graphLens().lookupType(newInstance.getType());
          if (rewrittenType == unboxedEnum.getType()) {
            InvokeDirect constructorInvoke =
                newInstance.getUniqueConstructorInvoke(appView.dexItemFactory());
            assert constructorInvoke != null;

            ProgramMethod constructor =
                unboxedEnum.lookupProgramMethod(constructorInvoke.getInvokedMethod());
            assert constructor != null;

            InstanceFieldInitializationInfo ordinalInitializationInfo =
                constructor
                    .getDefinition()
                    .getOptimizationInfo()
                    .getInstanceInitializerInfo(constructorInvoke)
                    .fieldInitializationInfos()
                    .get(ordinalField);

            int ordinal;
            if (ordinalInitializationInfo.isArgumentInitializationInfo()) {
              Value ordinalValue =
                  constructorInvoke
                      .getArgument(
                          ordinalInitializationInfo
                              .asArgumentInitializationInfo()
                              .getArgumentIndex())
                      .getAliasedValue();
              assert ordinalValue.isDefinedByInstructionSatisfying(Instruction::isConstNumber);
              ordinal = ordinalValue.getDefinition().asConstNumber().getIntValue();
            } else {
              assert ordinalInitializationInfo.isSingleValue();
              assert ordinalInitializationInfo.asSingleValue().isSingleNumberValue();
              ordinal =
                  ordinalInitializationInfo.asSingleValue().asSingleNumberValue().getIntValue();
            }

            // Replace by an instruction that produces a value of class type UnboxedEnum (for the
            // code to type check), which can easily be rewritten to a const-number instruction in
            // the enum unboxing rewriter.
            instructionIterator.replaceCurrentInstruction(
                new NewUnboxedEnumInstance(
                    unboxedEnum.getType(),
                    ordinal,
                    code.createValue(
                        ClassTypeElement.create(
                            unboxedEnum.getType(), definitelyNotNull(), appView))));

            instructionsToRemove.add(constructorInvoke);
          }
        } else if (instruction.isStaticPut()) {
          StaticPut staticPut = instruction.asStaticPut();
          DexField rewrittenField = appView.graphLens().lookupField(staticPut.getField());
          if (rewrittenField.getHolderType() != unboxedEnum.getType()) {
            continue;
          }

          SuccessfulFieldResolutionResult resolutionResult =
              appView.appInfo().resolveField(rewrittenField).asSuccessfulResolution();
          if (resolutionResult != null
              && resolutionResult.getResolvedHolder().isProgramClass()
              && isPrunedAfterEnumUnboxing(resolutionResult.getProgramField(), enumData)) {
            instructionIterator.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }

    if (!instructionsToRemove.isEmpty()) {
      InstructionListIterator instructionIterator = code.instructionListIterator();
      while (instructionIterator.hasNext()) {
        if (instructionsToRemove.remove(instructionIterator.next())) {
          instructionIterator.removeOrReplaceByDebugLocalRead();
        }
      }
    }

    converter.removeDeadCodeAndFinalizeIR(
        code, OptimizationFeedbackIgnore.getInstance(), Timing.empty());
  }

  private Collection<DexEncodedField> createLocalUtilityFields(
      DexProgramClass unboxedEnum,
      LocalEnumUnboxingUtilityClass localUtilityClass,
      PrunedItems.Builder prunedItemsBuilder) {
    EnumData enumData = enumDataMap.get(unboxedEnum);
    Map<DexField, DexEncodedField> localUtilityFields =
        new LinkedHashMap<>(unboxedEnum.staticFields().size());
    assert localUtilityClass.getDefinition().staticFields().isEmpty();

    unboxedEnum.forEachProgramField(
        field -> {
          if (isPrunedAfterEnumUnboxing(field, enumData)) {
            prunedItemsBuilder.addRemovedField(field.getReference());
            return;
          }

          DexEncodedField newLocalUtilityField =
              createLocalUtilityField(
                  field,
                  localUtilityClass,
                  newFieldSignature -> !localUtilityFields.containsKey(newFieldSignature));
          assert !localUtilityFields.containsKey(newLocalUtilityField.getReference());
          localUtilityFields.put(newLocalUtilityField.getReference(), newLocalUtilityField);
        });
    return localUtilityFields.values();
  }

  private DexEncodedField createLocalUtilityField(
      ProgramField field,
      LocalEnumUnboxingUtilityClass localUtilityClass,
      Predicate<DexField> availableFieldSignatures) {
    // Create a new, fresh field signature on the local utility class.
    DexField newFieldSignature =
        factory.createFreshFieldNameWithoutHolder(
            localUtilityClass.getType(),
            fixupType(field.getType()),
            field.getName().toString(),
            availableFieldSignatures);

    // Record the move.
    lensBuilder.move(field.getReference(), newFieldSignature);

    // Clear annotations and publicize.
    return field
        .getDefinition()
        .toTypeSubstitutedField(
            newFieldSignature,
            builder ->
                builder
                    .clearAnnotations()
                    .modifyAccessFlags(
                        accessFlags -> {
                          assert accessFlags.isStatic();
                          accessFlags.promoteToPublic();
                        }));
  }

  private Collection<DexEncodedMethod> createLocalUtilityMethods(
      DexProgramClass unboxedEnum,
      LocalEnumUnboxingUtilityClass localUtilityClass,
      PrunedItems.Builder prunedItemsBuilder) {
    Map<DexMethod, DexEncodedMethod> localUtilityMethods =
        new LinkedHashMap<>(
            localUtilityClass.getDefinition().getMethodCollection().size()
                + unboxedEnum.getMethodCollection().size());
    localUtilityClass
        .getDefinition()
        .forEachMethod(method -> localUtilityMethods.put(method.getReference(), method));

    unboxedEnum.forEachProgramMethod(
        method -> {
          if (method.getDefinition().isInstanceInitializer()) {
            prunedItemsBuilder.addRemovedMethod(method.getReference());
          } else {
            DexEncodedMethod newLocalUtilityMethod =
                createLocalUtilityMethod(
                    method,
                    localUtilityClass,
                    newMethodSignature -> !localUtilityMethods.containsKey(newMethodSignature));
            assert !localUtilityMethods.containsKey(newLocalUtilityMethod.getReference());
            localUtilityMethods.put(newLocalUtilityMethod.getReference(), newLocalUtilityMethod);
          }
        });
    return localUtilityMethods.values();
  }

  private DexEncodedMethod createLocalUtilityMethod(
      ProgramMethod method,
      LocalEnumUnboxingUtilityClass localUtilityClass,
      Predicate<DexMethod> availableMethodSignatures) {
    DexMethod methodReference = method.getReference();

    // Create a new, fresh method signature on the local utility class. We prefix the method by "_"
    // such that this does not collide with the utility methods we synthesize for unboxing.
    DexMethod newMethod =
        method.getDefinition().isClassInitializer()
            ? factory.createClassInitializer(localUtilityClass.getType())
            : factory.createFreshMethodNameWithoutHolder(
                "_" + method.getName().toString(),
                fixupProto(
                    method.getAccessFlags().isStatic()
                        ? method.getProto()
                        : factory.prependHolderToProto(methodReference)),
                localUtilityClass.getType(),
                availableMethodSignatures);

    // Record the move.
    lensBuilder.move(methodReference, newMethod, method.getDefinition().isStatic(), true);

    return method
        .getDefinition()
        .toTypeSubstitutedMethod(
            newMethod,
            builder ->
                builder
                    .clearAllAnnotations()
                    .modifyAccessFlags(
                        accessFlags -> {
                          if (method.getDefinition().isClassInitializer()) {
                            assert accessFlags.isStatic();
                          } else {
                            accessFlags.promoteToPublic();
                            accessFlags.promoteToStatic();
                          }
                        })
                    .setCompilationState(method.getDefinition().getCompilationState())
                    .unsetIsLibraryMethodOverride());
  }

  private boolean isPrunedAfterEnumUnboxing(ProgramField field, EnumData enumData) {
    return !field.getAccessFlags().isStatic()
        || ((enumData.hasUnboxedValueFor(field) || enumData.matchesValuesField(field))
            && !field.getDefinition().getOptimizationInfo().isDead());
  }

  private DexEncodedMethod fixupEncodedMethod(DexProgramClass holder, DexEncodedMethod method) {
    DexProto oldProto = method.getProto();
    DexProto newProto = fixupProto(oldProto);
    if (newProto == method.getProto()) {
      return method;
    }
    assert !method.isClassInitializer();
    assert !method.isLibraryMethodOverride().isTrue()
        : "Enum unboxing is changing the signature of a library override in a non unboxed class.";
    // We add the $enumunboxing$ suffix to make sure we do not create a library override.
    String newMethodName =
        method.getName().toString() + (method.isNonPrivateVirtualMethod() ? "$enumunboxing$" : "");
    DexMethod newMethod = factory.createMethod(method.getHolderType(), newProto, newMethodName);
    newMethod = ensureUniqueMethod(method, newMethod);
    int numberOfExtraNullParameters = newMethod.getArity() - method.getReference().getArity();
    boolean isStatic = method.isStatic();
    lensBuilder.move(
        method.getReference(), newMethod, isStatic, isStatic, numberOfExtraNullParameters);
    return method.toTypeSubstitutedMethod(
        newMethod,
        builder ->
            builder
                .fixupCallSiteOptimizationInfo(
                    callSiteOptimizationInfo ->
                        callSiteOptimizationInfo.fixupAfterExtraNullParameters(
                            numberOfExtraNullParameters))
                .setCompilationState(method.getCompilationState())
                .setIsLibraryMethodOverrideIf(
                    method.isNonPrivateVirtualMethod(), OptionalBool.FALSE)
                .setSimpleInliningConstraint(
                    holder, getRewrittenSimpleInliningConstraint(method, oldProto, newProto)));
  }

  private SimpleInliningConstraint getRewrittenSimpleInliningConstraint(
      DexEncodedMethod method, DexProto oldProto, DexProto newProto) {
    IntList unboxedArgumentIndices = new IntArrayList();
    int offset = BooleanUtils.intValue(method.isInstance());
    for (int i = 0; i < method.getReference().getArity(); i++) {
      if (oldProto.getParameter(i).isReferenceType()
          && newProto.getParameter(i).isPrimitiveType()) {
        unboxedArgumentIndices.add(i + offset);
      }
    }
    return method
        .getOptimizationInfo()
        .getSimpleInliningConstraint()
        .rewrittenWithUnboxedArguments(
            unboxedArgumentIndices, appView.simpleInliningConstraintFactory());
  }

  private DexMethod ensureUniqueMethod(DexEncodedMethod encodedMethod, DexMethod newMethod) {
    DexClass holder = appView.definitionFor(encodedMethod.getHolderType());
    assert holder != null;
    if (newMethod.isInstanceInitializer(appView.dexItemFactory())) {
      newMethod =
          factory.createInstanceInitializerWithFreshProto(
              newMethod,
              utilityClasses.getSharedUtilityClass().getType(),
              tryMethod -> holder.lookupMethod(tryMethod) == null);
    } else {
      int index = 0;
      while (holder.lookupMethod(newMethod) != null) {
        newMethod =
            newMethod.withName(
                encodedMethod.getName().toString() + "$enumunboxing$" + index++,
                appView.dexItemFactory());
      }
    }
    return newMethod;
  }

  private void fixupFields(List<DexEncodedField> fields, DexClass.FieldSetter setter) {
    if (fields == null) {
      return;
    }
    for (int i = 0; i < fields.size(); i++) {
      DexEncodedField encodedField = fields.get(i);
      DexField field = encodedField.getReference();
      DexType newType = fixupType(field.type);
      if (newType != field.type) {
        DexField newField = field.withType(newType, factory);
        lensBuilder.move(field, newField);
        DexEncodedField newEncodedField =
            encodedField.toTypeSubstitutedField(
                newField,
                builder ->
                    builder.setAbstractValue(
                        encodedField.getOptimizationInfo().getAbstractValue(), appView));
        setter.setField(i, newEncodedField);
        if (encodedField.isStatic() && encodedField.hasExplicitStaticValue()) {
          assert encodedField.getStaticValue() == DexValue.DexValueNull.NULL;
          newEncodedField.setStaticValue(DexValue.DexValueInt.DEFAULT);
          // TODO(b/150593449): Support conversion from DexValueEnum to DexValueInt.
        }
      }
    }
  }

  private DexProto fixupProto(DexProto proto) {
    DexType returnType = fixupType(proto.returnType);
    DexType[] arguments = fixupTypes(proto.parameters.values);
    return factory.createProto(returnType, arguments);
  }

  private DexType fixupType(DexType type) {
    if (type.isArrayType()) {
      DexType base = type.toBaseType(factory);
      DexType fixed = fixupType(base);
      if (base == fixed) {
        return type;
      }
      return type.replaceBaseType(fixed, factory);
    }
    return type.isClassType() && enumDataMap.isUnboxedEnum(type) ? factory.intType : type;
  }

  private DexType[] fixupTypes(DexType[] types) {
    DexType[] result = new DexType[types.length];
    for (int i = 0; i < result.length; i++) {
      result[i] = fixupType(types[i]);
    }
    return result;
  }

  public static class Result {

    private final Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping;
    private final EnumUnboxingLens lens;
    private final PrunedItems prunedItems;

    Result(
        Map<DexMethod, DexMethod> checkNotNullToCheckNotZeroMapping,
        EnumUnboxingLens lens,
        PrunedItems prunedItems) {
      this.checkNotNullToCheckNotZeroMapping = checkNotNullToCheckNotZeroMapping;
      this.lens = lens;
      this.prunedItems = prunedItems;
    }

    Map<DexMethod, DexMethod> getCheckNotNullToCheckNotZeroMapping() {
      return checkNotNullToCheckNotZeroMapping;
    }

    EnumUnboxingLens getLens() {
      return lens;
    }

    PrunedItems getPrunedItems() {
      return prunedItems;
    }
  }
}
