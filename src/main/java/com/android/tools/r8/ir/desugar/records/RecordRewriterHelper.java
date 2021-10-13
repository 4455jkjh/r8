// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.records;

import com.android.tools.r8.cf.code.CfInvokeDynamic;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.DexCallSite;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethodHandle;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexProto;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexValue.DexValueMethodHandle;
import com.android.tools.r8.graph.DexValue.DexValueString;
import com.android.tools.r8.graph.DexValue.DexValueType;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.naming.dexitembasedstring.RecordFieldNamesComputationInfo;

public class RecordRewriterHelper {

  public static boolean isInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic, AppView<?> appView, ProgramMethod context) {
    DexItemFactory factory = appView.dexItemFactory();
    DexCallSite callSite = invokeDynamic.getCallSite();
    // 1. Validates this is an invoke-static to ObjectMethods#bootstrap.
    DexMethodHandle bootstrapMethod = callSite.bootstrapMethod;
    if (!bootstrapMethod.type.isInvokeStatic()) {
      return false;
    }
    if (bootstrapMethod.member != factory.objectMethodsMembers.bootstrap) {
      return false;
    }
    // From there on we assume in the assertions that the invoke to the library method is
    // well-formed. If the invoke is not well formed assertions will fail but the execution is
    // correct.
    if (bootstrapMethod.isInterface) {
      assert false
          : "Invoke-dynamic invoking non interface method ObjectMethods#bootstrap as an interface"
              + " method.";
      return false;
    }
    // 2. Validate the bootstrapArgs include the record type, the instance field names and
    // the corresponding instance getters.
    if (callSite.bootstrapArgs.size() < 2) {
      assert false
          : "Invoke-dynamic invoking method ObjectMethods#bootstrap with less than 2 parameters.";
      return false;
    }
    DexValueType recordType = callSite.bootstrapArgs.get(0).asDexValueType();
    if (recordType == null) {
      assert false : "Invoke-dynamic invoking method ObjectMethods#bootstrap with an invalid type.";
      return false;
    }
    DexClass recordClass = appView.definitionFor(recordType.getValue(), context);
    if (recordClass == null || recordClass.isNotProgramClass()) {
      return false;
    }
    DexValueString valueString = callSite.bootstrapArgs.get(1).asDexValueString();
    if (valueString == null) {
      assert false
          : "Invoke-dynamic invoking method ObjectMethods#bootstrap with invalid field names.";
      return false;
    }
    DexString fieldNames = valueString.getValue();
    assert fieldNames.toString().isEmpty()
        || (fieldNames.toString().split(";").length == callSite.bootstrapArgs.size() - 2);
    assert recordClass.instanceFields().size() == callSite.bootstrapArgs.size() - 2;
    for (int i = 2; i < callSite.bootstrapArgs.size(); i++) {
      DexValueMethodHandle handle = callSite.bootstrapArgs.get(i).asDexValueMethodHandle();
      if (handle == null
          || !handle.value.type.isInstanceGet()
          || !handle.value.member.isDexField()) {
        assert false
            : "Invoke-dynamic invoking method ObjectMethods#bootstrap with invalid getters.";
        return false;
      }
    }
    // 3. Check it matches one of the 3 invokeDynamicOnRecord instruction.
    if (callSite.methodName == factory.toStringMethodName) {
      assert callSite.methodProto == factory.createProto(factory.stringType, recordClass.getType());
      return true;
    }
    if (callSite.methodName == factory.hashCodeMethodName) {
      assert callSite.methodProto == factory.createProto(factory.intType, recordClass.getType());
      return true;
    }
    if (callSite.methodName == factory.equalsMethodName) {
      assert callSite.methodProto
          == factory.createProto(factory.booleanType, recordClass.getType(), factory.objectType);
      return true;
    }
    return false;
  }

  public static RecordInvokeDynamic parseInvokeDynamicOnRecord(
      CfInvokeDynamic invokeDynamic, AppView<?> appView, ProgramMethod context) {
    assert isInvokeDynamicOnRecord(invokeDynamic, appView, context);
    DexCallSite callSite = invokeDynamic.getCallSite();
    DexValueType recordValueType = callSite.bootstrapArgs.get(0).asDexValueType();
    DexValueString valueString = callSite.bootstrapArgs.get(1).asDexValueString();
    DexString fieldNames = valueString.getValue();
    DexField[] fields = new DexField[callSite.bootstrapArgs.size() - 2];
    for (int i = 2; i < callSite.bootstrapArgs.size(); i++) {
      DexValueMethodHandle handle = callSite.bootstrapArgs.get(i).asDexValueMethodHandle();
      fields[i - 2] = handle.value.member.asDexField();
    }
    DexProgramClass recordClass =
        appView.definitionFor(recordValueType.getValue()).asProgramClass();
    return new RecordInvokeDynamic(
        callSite.methodName, callSite.methodProto, fieldNames, fields, recordClass);
  }

  static class RecordInvokeDynamic {

    private final DexString methodName;
    private final DexProto methodProto;
    private final DexString fieldNames;
    private final DexField[] fields;
    private final DexProgramClass recordClass;

    private RecordInvokeDynamic(
        DexString methodName,
        DexProto methodProto,
        DexString fieldNames,
        DexField[] fields,
        DexProgramClass recordClass) {
      this.methodName = methodName;
      this.methodProto = methodProto;
      this.fieldNames = fieldNames;
      this.fields = fields;
      this.recordClass = recordClass;
    }

    RecordInvokeDynamic withFieldNamesAndFields(DexString fieldNames, DexField[] fields) {
      return new RecordInvokeDynamic(methodName, methodProto, fieldNames, fields, recordClass);
    }

    DexField[] getFields() {
      return fields;
    }

    DexProgramClass getRecordClass() {
      return recordClass;
    }

    DexString getFieldNames() {
      return fieldNames;
    }

    DexString getMethodName() {
      return methodName;
    }

    DexProto getMethodProto() {
      return methodProto;
    }

    RecordFieldNamesComputationInfo computeRecordFieldNamesComputationInfo() {
      return RecordFieldNamesComputationInfo.forFieldNamesAndFields(getFieldNames(), getFields());
    }
  }
}
