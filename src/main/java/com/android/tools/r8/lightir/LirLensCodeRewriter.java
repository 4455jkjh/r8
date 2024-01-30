// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.lightir;

import static com.android.tools.r8.graph.UseRegistry.MethodHandleUse.NOT_ARGUMENT_TO_LAMBDA_METAFACTORY;

import com.android.tools.r8.errors.Unreachable;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.bytecodemetadata.BytecodeMetadataProvider;
import com.android.tools.r8.graph.lens.FieldLookupResult;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.graph.lens.MethodLookupResult;
import com.android.tools.r8.graph.proto.RewrittenPrototypeDescription;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.IRMetadata;
import com.android.tools.r8.ir.code.IROpcodeUtils;
import com.android.tools.r8.ir.code.InvokeType;
import com.android.tools.r8.ir.conversion.IRToLirFinalizer;
import com.android.tools.r8.ir.conversion.LensCodeRewriter;
import com.android.tools.r8.ir.conversion.LensCodeRewriterUtils;
import com.android.tools.r8.ir.conversion.MethodConversionOptions;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.optimize.AffectedValues;
import com.android.tools.r8.ir.optimize.DeadCodeRemover;
import com.android.tools.r8.lightir.LirBuilder.RecordFieldValuesPayload;
import com.android.tools.r8.lightir.LirCode.TryCatchTable;
import com.android.tools.r8.utils.ArrayUtils;
import com.android.tools.r8.utils.Timing;
import com.android.tools.r8.verticalclassmerging.VerticalClassMergerGraphLens;
import it.unimi.dsi.fastutil.objects.Reference2IntMap;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

public class LirLensCodeRewriter<EV> extends LirParsedInstructionCallback<EV> {

  private final AppView<? extends AppInfoWithClassHierarchy> appView;
  private final ProgramMethod context;
  private final DexMethod contextReference;
  private final GraphLens graphLens;
  private final GraphLens codeLens;
  private final LensCodeRewriterUtils helper;

  private int numberOfInvokeOpcodeChanges = 0;
  private Map<LirConstant, LirConstant> constantPoolMapping = null;

  private boolean hasNonTrivialRewritings = false;

  public LirLensCodeRewriter(
      AppView<? extends AppInfoWithClassHierarchy> appView,
      LirCode<EV> code,
      ProgramMethod context,
      LensCodeRewriterUtils helper) {
    super(code);
    this.appView = appView;
    this.context = context;
    this.contextReference = context.getReference();
    this.graphLens = appView.graphLens();
    this.codeLens = context.getDefinition().getCode().getCodeLens(appView);
    this.helper = helper;
  }

  @Override
  public int getCurrentValueIndex() {
    // We do not need to interpret values.
    return -1;
  }

  public void onTypeReference(DexType type) {
    addRewrittenMapping(type, graphLens.lookupType(type, codeLens));
  }

  public void onFieldReference(DexField field) {
    FieldLookupResult result = graphLens.lookupFieldResult(field, codeLens);
    assert !result.hasReadCastType();
    assert !result.hasWriteCastType();
    addRewrittenMapping(field, result.getReference());
  }

  public void onCallSiteReference(DexCallSite callSite) {
    addRewrittenMapping(callSite, helper.rewriteCallSite(callSite, context));
  }

  public void onMethodHandleReference(DexMethodHandle methodHandle) {
    addRewrittenMapping(
        methodHandle,
        helper.rewriteDexMethodHandle(methodHandle, NOT_ARGUMENT_TO_LAMBDA_METAFACTORY, context));
  }

  public void onProtoReference(DexProto proto) {
    addRewrittenMapping(proto, helper.rewriteProto(proto));
  }

  private void onInvoke(DexMethod method, InvokeType type, boolean isInterface) {
    MethodLookupResult result = graphLens.lookupMethod(method, contextReference, type, codeLens);
    if (hasPotentialNonTrivialInvokeRewriting(method, type, result)) {
      hasNonTrivialRewritings = true;
      return;
    }
    int opcode = type.getLirOpcode(isInterface);
    DexMethod newMethod = result.getReference();
    InvokeType newType = result.getType();
    boolean newIsInterface = lookupIsInterface(method, opcode, result);
    int newOpcode = newType.getLirOpcode(newIsInterface);
    assert newMethod.getArity() == method.getArity();
    if (newOpcode != opcode) {
      assert type == newType
              || (type.isVirtual() && newType.isInterface())
              || (type.isInterface() && newType.isVirtual())
              || (type.isSuper() && newType.isVirtual())
          : type + " -> " + newType;
      numberOfInvokeOpcodeChanges++;
    } else {
      // All non-type dependent mappings are just rewritten in the content pool.
      addRewrittenMapping(method, newMethod);
    }
  }

  private boolean hasPotentialNonTrivialInvokeRewriting(
      DexMethod method, InvokeType type, MethodLookupResult result) {
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null) {
      if (!result.getPrototypeChanges().isEmpty()) {
        return true;
      }
      for (int argumentIndex = 0;
          argumentIndex < method.getNumberOfArguments(type.isStatic());
          argumentIndex++) {
        DexType argumentType = method.getArgumentType(argumentIndex, type.isStatic());
        if (verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(argumentType)) {
          return true;
        }
      }
    }
    assert result.getPrototypeChanges().isEmpty();
    return false;
  }

  private void addRewrittenMapping(LirConstant item, LirConstant rewrittenItem) {
    if (item == rewrittenItem) {
      return;
    }
    if (constantPoolMapping == null) {
      constantPoolMapping =
          new IdentityHashMap<>(
              // Avoid using initial capacity larger than the number of actual constants.
              Math.min(getCode().getConstantPool().length, 32));
    }
    LirConstant old = constantPoolMapping.put(item, rewrittenItem);
    if (old != null && old != rewrittenItem) {
      throw new Unreachable(
          "Unexpected rewriting of item: "
              + item
              + " to two distinct items: "
              + rewrittenItem
              + " and "
              + old);
    }
  }

  @Override
  public void onInstancePut(DexField field, EV object, EV value) {
    onFieldPut(field);
  }

  @Override
  public void onStaticPut(DexField field, EV value) {
    onFieldPut(field);
  }

  private void onFieldPut(DexField field) {
    if (hasPotentialNonTrivialFieldPutRewriting(field)) {
      hasNonTrivialRewritings = true;
    }
  }

  private boolean hasPotentialNonTrivialFieldPutRewriting(DexField field) {
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null
        && verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(field.getType())) {
      return true;
    }
    return false;
  }

  @Override
  public void onInvokeDirect(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.DIRECT, isInterface);
  }

  @Override
  public void onInvokeSuper(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.SUPER, isInterface);
  }

  @Override
  public void onInvokeVirtual(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.VIRTUAL, false);
  }

  @Override
  public void onInvokeStatic(DexMethod method, List<EV> arguments, boolean isInterface) {
    onInvoke(method, InvokeType.STATIC, isInterface);
  }

  @Override
  public void onInvokeInterface(DexMethod method, List<EV> arguments) {
    onInvoke(method, InvokeType.INTERFACE, true);
  }

  private InvokeType getInvokeTypeThatMayChange(int opcode) {
    if (opcode == LirOpcodes.INVOKEVIRTUAL) {
      return InvokeType.VIRTUAL;
    }
    if (opcode == LirOpcodes.INVOKEINTERFACE) {
      return InvokeType.INTERFACE;
    }
    if (graphLens.isVerticalClassMergerLens()) {
      if (opcode == LirOpcodes.INVOKESTATIC_ITF) {
        return InvokeType.STATIC;
      }
      if (opcode == LirOpcodes.INVOKESUPER) {
        return InvokeType.SUPER;
      }
    }
    return null;
  }

  public LirCode<EV> rewrite() {
    if (hasNonTrivialMethodChanges()) {
      return rewriteWithLensCodeRewriter();
    }
    assert !hasNonTrivialRewritings;
    LirCode<EV> rewritten = rewriteConstantPoolAndScanForTypeChanges(getCode());
    if (hasNonTrivialRewritings) {
      return rewriteWithLensCodeRewriter();
    }
    rewritten = rewriteInstructionsWithInvokeTypeChanges(rewritten);
    rewritten = rewriteTryCatchTable(rewritten);
    // In the unusual case where a catch handler has been eliminated as a result of class merging
    // we remove the unreachable blocks.
    if (hasPrunedCatchHandlers(rewritten)) {
      rewritten = removeUnreachableBlocks(rewritten);
    }
    return rewritten;
  }

  private boolean hasNonTrivialMethodChanges() {
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null) {
      DexMethod previousReference =
          verticalClassMergerLens.getPreviousMethodSignature(contextReference);
      if (verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(
          previousReference.getReturnType())) {
        return true;
      }
      RewrittenPrototypeDescription prototypeChanges =
          graphLens.lookupPrototypeChangesForMethodDefinition(context.getReference(), codeLens);
      if (!prototypeChanges.isEmpty()) {
        return true;
      }
    }
    return false;
  }

  private boolean hasPrunedCatchHandlers(LirCode<EV> rewritten) {
    if (!getCode().hasTryCatchTable()) {
      return false;
    }
    if (!appView.graphLens().isClassMergerLens()) {
      assert !internalHasPrunedCatchHandlers(rewritten);
      return false;
    }
    return internalHasPrunedCatchHandlers(rewritten);
  }

  private boolean internalHasPrunedCatchHandlers(LirCode<EV> rewritten) {
    TryCatchTable tryCatchTable = getCode().getTryCatchTable();
    TryCatchTable rewrittenTryCatchTable = rewritten.getTryCatchTable();
    return tryCatchTable.hasHandlerThatMatches(
        (blockIndex, handlers) ->
            handlers.size() > rewrittenTryCatchTable.getHandlersForBlock(blockIndex).size());
  }

  @SuppressWarnings("unchecked")
  private LirCode<EV> removeUnreachableBlocks(LirCode<EV> rewritten) {
    IRCode code =
        rewritten.buildIR(
            context,
            appView,
            MethodConversionOptions.forLirPhase(appView).disableStringSwitchConversion());
    AffectedValues affectedValues = code.removeUnreachableBlocks();
    affectedValues.narrowingWithAssumeRemoval(appView, code);
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
    deadCodeRemover.run(code, Timing.empty());
    LirCode<Integer> result =
        new IRToLirFinalizer(appView, deadCodeRemover)
            .finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    return (LirCode<EV>) result;
  }

  @SuppressWarnings("unchecked")
  private LirCode<EV> rewriteWithLensCodeRewriter() {
    IRCode code =
        context.buildIR(
            appView,
            MethodConversionOptions.forLirPhase(appView)
                .disableStringSwitchConversion()
                .setFinalizeAfterLensCodeRewriter());
    // MethodProcessor argument is only used by unboxing lenses.
    MethodProcessor methodProcessor = null;
    new LensCodeRewriter(appView).rewrite(code, context, methodProcessor);
    DeadCodeRemover deadCodeRemover = new DeadCodeRemover(appView);
    deadCodeRemover.run(code, Timing.empty());
    IRToLirFinalizer finalizer = new IRToLirFinalizer(appView, deadCodeRemover);
    LirCode<?> rewritten =
        finalizer.finalizeCode(code, BytecodeMetadataProvider.empty(), Timing.empty());
    return (LirCode<EV>) rewritten;
  }

  private LirCode<EV> rewriteConstantPoolAndScanForTypeChanges(LirCode<EV> code) {
    // The code may need to be rewritten by the lens.
    // First pass scans just the constant pool to see if any types change or if there are any
    // fields/methods that need to be examined.
    boolean hasFieldReference = false;
    boolean hasPotentialRewrittenMethod = false;
    for (LirConstant constant : code.getConstantPool()) {
      // RecordFieldValuesPayload is lowered to NewArrayEmpty before lens code rewriting any LIR.
      assert !(constant instanceof RecordFieldValuesPayload);
      if (constant instanceof DexType) {
        onTypeReference((DexType) constant);
      } else if (constant instanceof DexField) {
        onFieldReference((DexField) constant);
        hasFieldReference = true;
      } else if (constant instanceof DexCallSite) {
        onCallSiteReference((DexCallSite) constant);
      } else if (constant instanceof DexMethodHandle) {
        onMethodHandleReference((DexMethodHandle) constant);
      } else if (constant instanceof DexProto) {
        onProtoReference((DexProto) constant);
      } else if (!hasPotentialRewrittenMethod && constant instanceof DexMethod) {
        // We might be able to still fast-case this if we can guarantee the method is never
        // rewritten. Say it is an java.lang.Object reference or if the lens can fast-check it.
        hasPotentialRewrittenMethod = true;
      }
    }

    // If there are potential method rewritings then we need to iterate the instructions as the
    // rewriting is instruction-sensitive (i.e., may be dependent on the invoke type).
    boolean hasPotentialNonTrivialFieldPutRewriting =
        hasFieldReference && graphLens.isVerticalClassMergerLens();
    if (hasPotentialNonTrivialFieldPutRewriting || hasPotentialRewrittenMethod) {
      for (LirInstructionView view : code) {
        view.accept(this);
        if (hasNonTrivialRewritings) {
          return null;
        }
      }
    }

    if (constantPoolMapping == null) {
      return code;
    }

    return code.newCodeWithRewrittenConstantPool(
        item -> constantPoolMapping.getOrDefault(item, item));
  }

  private LirCode<EV> rewriteInstructionsWithInvokeTypeChanges(LirCode<EV> code) {
    if (numberOfInvokeOpcodeChanges == 0) {
      return code;
    }
    // Build a small map from method refs to index in case the type-dependent methods are already
    // in the constant pool.
    Reference2IntMap<DexMethod> methodIndices = new Reference2IntOpenHashMap<>();
    LirConstant[] rewrittenConstants = code.getConstantPool();
    for (int i = 0, length = rewrittenConstants.length; i < length; i++) {
      LirConstant constant = rewrittenConstants[i];
      if (constant instanceof DexMethod) {
        methodIndices.put((DexMethod) constant, i);
      }
    }

    IRMetadata irMetadata = code.getMetadataForIR();
    ByteArrayWriter byteWriter = new ByteArrayWriter();
    LirWriter lirWriter = new LirWriter(byteWriter);
    List<LirConstant> methodsToAppend = new ArrayList<>(numberOfInvokeOpcodeChanges);
    for (LirInstructionView view : code) {
      int opcode = view.getOpcode();
      // Instructions that do not have an invoke-type change are just mapped via identity.
      if (LirOpcodes.isOneByteInstruction(opcode)) {
        lirWriter.writeOneByteInstruction(opcode);
        continue;
      }
      InvokeType type = getInvokeTypeThatMayChange(opcode);
      if (type == null) {
        int size = view.getRemainingOperandSizeInBytes();
        lirWriter.writeInstruction(opcode, size);
        while (size-- > 0) {
          lirWriter.writeOperand(view.getNextU1());
        }
        continue;
      }
      // This is potentially an invoke with a type change, in such cases the method is mapped with
      // the instruction updated to the new type. The constant pool is amended with the mapped
      // method if needed.
      int constantIndex = view.getNextConstantOperand();
      DexMethod method = (DexMethod) code.getConstantItem(constantIndex);
      MethodLookupResult result =
          graphLens.lookupMethod(method, context.getReference(), type, codeLens);
      boolean newIsInterface = lookupIsInterface(method, opcode, result);
      InvokeType newType = result.getType();
      int newOpcode = newType.getLirOpcode(newIsInterface);
      if (newOpcode != opcode) {
        --numberOfInvokeOpcodeChanges;
        if (newType != type) {
          irMetadata.record(IROpcodeUtils.fromLirInvokeOpcode(newOpcode));
        }
        constantIndex =
            methodIndices.computeIfAbsent(
                result.getReference(),
                ref -> {
                  methodsToAppend.add(ref);
                  return rewrittenConstants.length + methodsToAppend.size() - 1;
                });
      }
      int constantIndexSize = ByteUtils.intEncodingSize(constantIndex);
      int remainingSize = view.getRemainingOperandSizeInBytes();
      lirWriter.writeInstruction(newOpcode, constantIndexSize + remainingSize);
      ByteUtils.writeEncodedInt(constantIndex, lirWriter::writeOperand);
      while (remainingSize-- > 0) {
        lirWriter.writeOperand(view.getNextU1());
      }
    }
    assert numberOfInvokeOpcodeChanges == 0;
    // Note that since we assume 'null' in the mapping is identity this may end up with a stale
    // reference to a no longer used method. That is not an issue as it will be pruned when
    // building IR again, it is just a small and size overhead.
    LirCode<EV> newCode =
        code.copyWithNewConstantsAndInstructions(
            irMetadata,
            ArrayUtils.appendElements(code.getConstantPool(), methodsToAppend),
            byteWriter.toByteArray());
    return newCode;
  }

  // TODO(b/157111832): This should be part of the graph lens lookup result.
  private boolean lookupIsInterface(DexMethod method, int opcode, MethodLookupResult result) {
    VerticalClassMergerGraphLens verticalClassMergerLens = graphLens.asVerticalClassMergerLens();
    if (verticalClassMergerLens != null
        && verticalClassMergerLens.hasInterfaceBeenMergedIntoClass(method.getHolderType())) {
      DexClass clazz = appView.definitionFor(result.getReference().getHolderType());
      if (clazz != null) {
        return clazz.isInterface();
      }
    }
    return LirOpcodeUtils.getInterfaceBitFromInvokeOpcode(opcode);
  }

  private LirCode<EV> rewriteTryCatchTable(LirCode<EV> code) {
    TryCatchTable tryCatchTable = code.getTryCatchTable();
    if (tryCatchTable == null) {
      return code;
    }
    TryCatchTable newTryCatchTable = tryCatchTable.rewriteWithLens(graphLens, codeLens);
    return code.newCodeWithRewrittenTryCatchTable(newTryCatchTable);
  }
}
