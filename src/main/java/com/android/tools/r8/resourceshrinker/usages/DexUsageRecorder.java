// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.resourceshrinker.usages;

import com.android.ide.common.resources.usage.ResourceUsageModel;
import com.android.ide.common.resources.usage.ResourceUsageModel.Resource;
import com.android.resources.ResourceType;
import com.android.tools.r8.dex.code.CfOrDexStaticFieldRead;
import com.android.tools.r8.dex.code.DexConst;
import com.android.tools.r8.dex.code.DexConst16;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstHigh16;
import com.android.tools.r8.dex.code.DexConstWide16;
import com.android.tools.r8.dex.code.DexConstWide32;
import com.android.tools.r8.dex.code.DexFillArrayData;
import com.android.tools.r8.dex.code.DexFillArrayDataPayload;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNewArray;
import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexAnnotationElement;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexEncodedField;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.graph.DexValue;
import com.android.tools.r8.graph.lens.GraphLens;
import com.android.tools.r8.ir.code.SingleConstant;
import com.android.tools.r8.ir.code.WideConstant;
import com.android.tools.r8.resourceshrinker.ResourceShrinkerModel;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import java.util.ArrayList;
import java.util.List;

public final class DexUsageRecorder {

  private static final String ANDROID_RES = "android_res/";

  private final AppView<AppInfoWithClassHierarchy> appView;
  private final ResourceShrinkerModel model;
  private final DexItemFactory factory;
  private final GraphLens graphLens;
  private final DexMethod getIdentifierMethod;
  private final DexType webViewType;
  private final DexString loadPrefix;

  private DexUsageRecorder(
      AppView<AppInfoWithClassHierarchy> appView, ResourceShrinkerModel model) {
    this.appView = appView;
    this.model = model;
    this.factory = appView.dexItemFactory();
    this.graphLens = appView.graphLens();
    this.getIdentifierMethod =
        factory.createMethod(
            factory.androidResourcesType,
            factory.createProto(
                factory.intType, factory.stringType, factory.stringType, factory.stringType),
            "getIdentifier");
    this.webViewType = factory.createType("Landroid/webkit/WebView;");
    this.loadPrefix = factory.createString("load");
  }

  public static void recordUsages(
      AppView<AppInfoWithClassHierarchy> appView, ResourceShrinkerModel model) {
    new DexUsageRecorder(appView, model).record();
  }

  private void record() {
    InternalOptions options = appView.options();
    boolean skipGlobalSynthetics = options.intermediate && options.hasGlobalSyntheticsConsumer();
    for (DexProgramClass programClass : appView.appInfo().classesWithDeterministicOrder()) {
      if (skipGlobalSynthetics
          && appView.getSyntheticItems().isGlobalSyntheticClassTransitive(programClass)) {
        continue;
      }
      visitClass(programClass);
    }
  }

  private void visitClass(DexProgramClass programClass) {
    DexType originalType =
        graphLens.getOriginalType(programClass.getType(), GraphLens.getIdentityLens());
    String originalClassName = originalType.toSourceString();
    boolean isRClass = getResourceClassType(originalClassName) != null;

    if (!isRClass) {
      for (DexEncodedField field :
          programClass.staticFields(DexEncodedField::hasExplicitStaticValue)) {
        processFieldValue(programClass, field.getStaticValue());
      }
    }

    for (DexEncodedMethod method : programClass.allMethodsSorted()) {
      if (isRClass && method.isClassInitializer()) {
        continue;
      }
      processMethod(programClass, method);
    }

    if (!isRClass && programClass.hasClassOrMemberAnnotations()) {
      processAnnotations(programClass);
    }
  }

  private void processFieldValue(DexProgramClass programClass, DexValue value) {
    switch (value.getValueKind()) {
      case ARRAY:
        for (DexValue elementValue : value.asDexValueArray().getValues()) {
          if (elementValue.isDexValueInt()) {
            referencedInt(programClass, elementValue.asDexValueInt().getValue());
          }
        }
        break;
      case INT:
        referencedInt(programClass, value.asDexValueInt().getValue());
        break;
      case STRING:
        referencedString(value.asDexValueString().getValue());
        break;
      default:
        break;
    }
  }

  private void processMethod(DexProgramClass programClass, DexEncodedMethod method) {
    DexCode code = method.getDexCode();
    if (code == null) {
      assert !method.hasCode() || method.getCode().isSharedCodeObject();
      return;
    }
    DexInstruction[] instructions = code.instructions;
    IntSet methodIntArrayPayloadOffsets = null;
    List<DexFillArrayDataPayload> payloads = null;

    for (int current = 0; current < instructions.length; current++) {
      DexInstruction instruction = instructions[current];
      if (isIntConstInstruction(instruction)) {
        processIntConstInstruction(programClass, instruction);
      } else if (instruction.isConstString()) {
        referencedString(instruction.asConstString().getString());
      } else if (instruction.isConstStringJumbo()) {
        referencedString(instruction.asConstStringJumbo().getString());
      } else if (instruction.isConstString20()) {
        referencedString(instruction.asConstString20().getString());
      } else if (instruction instanceof CfOrDexStaticFieldRead) {
        referencedStaticField(((CfOrDexStaticFieldRead) instruction).getField());
      } else if (instruction.isInvokeMethodOrInvokeMethodRange()) {
        referencedMethod(method, instruction.asInvokeMethodOrInvokeMethodRange().getMethod());
      } else if (instruction instanceof DexFillArrayData) {
        if (current > 0 && instructions[current - 1] instanceof DexNewArray) {
          DexNewArray newArray = (DexNewArray) instructions[current - 1];
          if (newArray.getType().isNotIdenticalTo(factory.intArrayType)) {
            continue;
          }
        }
        DexFillArrayData fillArrayData = (DexFillArrayData) instruction;
        if (methodIntArrayPayloadOffsets == null) {
          methodIntArrayPayloadOffsets = new IntOpenHashSet();
        }
        methodIntArrayPayloadOffsets.add(
            fillArrayData.getPayloadOffset() + fillArrayData.getOffset());
      } else if (instruction instanceof DexFillArrayDataPayload) {
        if (payloads == null) {
          payloads = new ArrayList<>();
        }
        payloads.add((DexFillArrayDataPayload) instruction);
      }
    }

    if (payloads != null && methodIntArrayPayloadOffsets != null) {
      for (DexFillArrayDataPayload payload : payloads) {
        if (methodIntArrayPayloadOffsets.contains(payload.getOffset())) {
          for (int i = 0; i < payload.data.length / 2; i++) {
            int intValue =
                (payload.data[2 * i + 1] << 16) | Short.toUnsignedInt(payload.data[2 * i]);
            referencedInt(programClass, intValue);
          }
        }
      }
    }
  }

  private void processAnnotations(DexProgramClass programClass) {
    programClass.forEachAnnotation(
        annotation -> {
          for (DexAnnotationElement element : annotation.annotation.elements) {
            processAnnotationValue(programClass, element.value);
          }
        });
  }

  private void processAnnotationValue(DexProgramClass programClass, DexValue value) {
    switch (value.getValueKind()) {
      case ANNOTATION:
        for (DexAnnotationElement element : value.asDexValueAnnotation().value.elements) {
          processAnnotationValue(programClass, element.value);
        }
        break;
      case ARRAY:
        for (DexValue elementValue : value.asDexValueArray().getValues()) {
          processAnnotationValue(programClass, elementValue);
        }
        break;
      case INT:
        referencedInt(programClass, value.asDexValueInt().getValue());
        break;
      case STRING:
        referencedString(value.asDexValueString().getValue());
        break;
      default:
        break;
    }
  }

  private static boolean isIntConstInstruction(DexInstruction instruction) {
    int opcode = instruction.getOpcode();
    return opcode == DexConst4.OPCODE
        || opcode == DexConst16.OPCODE
        || opcode == DexConst.OPCODE
        || opcode == DexConstWide32.OPCODE
        || opcode == DexConstHigh16.OPCODE
        || opcode == DexConstWide16.OPCODE;
  }

  private void processIntConstInstruction(
      DexProgramClass programClass, DexInstruction instruction) {
    int constantValue;
    if (instruction instanceof SingleConstant) {
      constantValue = ((SingleConstant) instruction).decodedValue();
    } else if (instruction instanceof WideConstant) {
      long wideValue = ((WideConstant) instruction).decodedValue();
      if ((int) wideValue != wideValue) {
        return;
      }
      constantValue = (int) wideValue;
    } else {
      throw new Unreachable("Not an int const instruction.");
    }
    referencedInt(programClass, constantValue);
  }

  private void referencedInt(DexProgramClass programClass, int value) {
    Resource resource = model.getResourceStore().getResource(value);
    if (ResourceUsageModel.markReachable(resource)) {
      model
          .getDebugReporter()
          .debug(
              () ->
                  "Marking "
                      + resource
                      + " reachable: referenced from "
                      + programClass.getOrigin());
    }
  }

  private void referencedStaticField(DexField field) {
    DexField originalField =
        graphLens.getOriginalFieldSignature(field, GraphLens.getIdentityLens());
    ResourceType type = getResourceClassType(originalField.getHolderType().toSourceString());
    if (type != null) {
      for (Resource resource :
          model.getResourceStore().getResources(type, originalField.getName().toString())) {
        ResourceUsageModel.markReachable(resource);
      }
    }
  }

  private void referencedString(DexString dexString) {
    if (dexString.isEmpty() || dexString.length() > 80) {
      return;
    }
    String value = dexString.toString();
    boolean allValid = true;
    boolean hasIdentifierPart = false;
    for (int i = 0; i < value.length(); i++) {
      char c = value.charAt(i);
      if (Character.isJavaIdentifierPart(c) || c == '.' || c == ':' || c == '/' || c == '%') {
        if (Character.isJavaIdentifierPart(c)) {
          hasIdentifierPart = true;
        }
      } else {
        allValid = false;
        break;
      }
    }
    if (allValid && hasIdentifierPart) {
      model.addStringConstant(value);
      model.setFoundWebContent(model.isFoundWebContent() || value.contains(ANDROID_RES));
    }
  }

  private void referencedMethod(DexEncodedMethod caller, DexMethod method) {
    if (method.isIdenticalTo(getIdentifierMethod)) {
      DexType originalHolder =
          graphLens
              .getOriginalMethodSignature(caller.getReference(), GraphLens.getIdentityLens())
              .getHolderType();
      if (AppCompat.isAppCompatClass(originalHolder.toSourceString())) {
        return;
      }
      model.setFoundGetIdentifier(true);
    }
    if (method.getHolderType().isIdenticalTo(webViewType)
        && method.getName().startsWith(loadPrefix)) {
      model.setFoundWebContent(true);
    }
  }

  private static ResourceType getResourceClassType(String className) {
    int lastDot = className.lastIndexOf('.');
    String lastPart = lastDot == -1 ? className : className.substring(lastDot + 1);
    if (lastPart.startsWith("R$")) {
      return ResourceType.fromClassName(lastPart.substring(2));
    }
    return null;
  }
}
