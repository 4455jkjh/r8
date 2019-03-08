// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.ir.optimize.string;

import static com.android.tools.r8.ir.analysis.type.Nullability.definitelyNotNull;
import static com.android.tools.r8.ir.optimize.CodeRewriter.removeOrReplaceByDebugLocalWrite;
import static com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo.ClassNameComputationOption.CANONICAL_NAME;
import static com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo.ClassNameComputationOption.NAME;
import static com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo.ClassNameComputationOption.SIMPLE_NAME;
import static com.android.tools.r8.ir.optimize.ReflectionOptimizer.computeClassName;
import static com.android.tools.r8.utils.DescriptorUtils.INNER_CLASS_SEPARATOR;

import com.android.tools.r8.graph.AppInfo;
import com.android.tools.r8.graph.DexClass;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.ir.analysis.EscapeAnalysis;
import com.android.tools.r8.ir.analysis.type.TypeLatticeElement;
import com.android.tools.r8.ir.code.BasicBlock.ThrowingInfo;
import com.android.tools.r8.ir.code.ConstClass;
import com.android.tools.r8.ir.code.ConstNumber;
import com.android.tools.r8.ir.code.ConstString;
import com.android.tools.r8.ir.code.DexItemBasedConstString;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.code.Instruction;
import com.android.tools.r8.ir.code.InstructionIterator;
import com.android.tools.r8.ir.code.InvokeStatic;
import com.android.tools.r8.ir.code.InvokeVirtual;
import com.android.tools.r8.ir.code.Value;
import com.android.tools.r8.ir.optimize.ReflectionOptimizer.ClassNameComputationInfo;
import com.android.tools.r8.shaking.RootSetBuilder.RootSet;
import com.android.tools.r8.utils.InternalOutputMode;
import com.google.common.annotations.VisibleForTesting;
import java.util.Set;
import java.util.function.BiFunction;
import java.util.function.Function;

public class StringOptimizer {

  private final AppInfo appInfo;
  private final DexItemFactory factory;
  private final ThrowingInfo throwingInfo;

  public StringOptimizer(AppInfo appInfo, InternalOutputMode outputMode) {
    this.appInfo = appInfo;
    this.factory = appInfo.dexItemFactory;
    this.throwingInfo =
        outputMode.isGeneratingClassFiles() ? ThrowingInfo.NO_THROW : ThrowingInfo.CAN_THROW;
  }

  // int String#length()
  // boolean String#isEmpty()
  // boolean String#startsWith(String)
  // boolean String#endsWith(String)
  // boolean String#contains(String)
  // boolean String#equals(String)
  // boolean String#equalsIgnoreCase(String)
  // boolean String#contentEquals(String)
  // int String#indexOf(String)
  // int String#indexOf(int)
  // int String#lastIndexOf(String)
  // int String#lastIndexOf(int)
  // int String#compareTo(String)
  // int String#compareToIgnoreCase(String)
  // String String#substring(int)
  // String String#substring(int, int)
  public void computeTrivialOperationsOnConstString(IRCode code) {
    if (!code.hasConstString) {
      return;
    }
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      if (!invoke.hasOutValue()) {
        continue;
      }
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (invokedMethod.name == appInfo.dexItemFactory.substringName) {
        assert invoke.inValues().size() == 2 || invoke.inValues().size() == 3;
        Value rcv = invoke.getReceiver().getAliasedValue();
        if (rcv.definition == null
            || !rcv.definition.isConstString()
            || rcv.hasLocalInfo()) {
          continue;
        }
        Value beginIndex = invoke.inValues().get(1).getAliasedValue();
        if (beginIndex.definition == null
            || !beginIndex.definition.isConstNumber()
            || beginIndex.hasLocalInfo()) {
          continue;
        }
        int beginIndexValue = beginIndex.definition.asConstNumber().getIntValue();
        Value endIndex = null;
        if (invoke.inValues().size() == 3) {
          endIndex = invoke.inValues().get(2).getAliasedValue();
          if (endIndex.definition == null
              || !endIndex.definition.isConstNumber()
              || endIndex.hasLocalInfo()) {
            continue;
          }
        }
        String rcvString = rcv.definition.asConstString().getValue().toString();
        int endIndexValue =
            endIndex == null
                ? rcvString.length()
                : endIndex.definition.asConstNumber().getIntValue();
        if (beginIndexValue < 0
            || endIndexValue > rcvString.length()
            || beginIndexValue > endIndexValue) {
          // This will raise StringIndexOutOfBoundsException.
          continue;
        }
        String sub = rcvString.substring(beginIndexValue, endIndexValue);
        Value stringValue =
            code.createValue(
                TypeLatticeElement.stringClassType(appInfo, definitelyNotNull()),
                invoke.getLocalInfo());
        it.replaceCurrentInstruction(
            new ConstString(stringValue, factory.createString(sub), throwingInfo));
        continue;
      }

      Function<String, Integer> operatorWithNoArg = null;
      BiFunction<String, String, Integer> operatorWithString = null;
      BiFunction<String, Integer, Integer> operatorWithInt = null;
      if (invokedMethod == factory.stringMethods.length) {
        operatorWithNoArg = String::length;
      } else if (invokedMethod == factory.stringMethods.isEmpty) {
        operatorWithNoArg = rcv -> rcv.isEmpty() ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.contains) {
        operatorWithString = (rcv, arg) -> rcv.contains(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.startsWith) {
        operatorWithString = (rcv, arg) -> rcv.startsWith(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.endsWith) {
        operatorWithString = (rcv, arg) -> rcv.endsWith(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.equals) {
        operatorWithString = (rcv, arg) -> rcv.equals(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.equalsIgnoreCase) {
        operatorWithString = (rcv, arg) -> rcv.equalsIgnoreCase(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.contentEqualsCharSequence) {
        operatorWithString = (rcv, arg) -> rcv.contentEquals(arg) ? 1 : 0;
      } else if (invokedMethod == factory.stringMethods.indexOfInt) {
        operatorWithInt = String::indexOf;
      } else if (invokedMethod == factory.stringMethods.indexOfString) {
        operatorWithString = String::indexOf;
      } else if (invokedMethod == factory.stringMethods.lastIndexOfInt) {
        operatorWithInt = String::lastIndexOf;
      } else if (invokedMethod == factory.stringMethods.lastIndexOfString) {
        operatorWithString = String::lastIndexOf;
      } else if (invokedMethod == factory.stringMethods.compareTo) {
        operatorWithString = String::compareTo;
      } else if (invokedMethod == factory.stringMethods.compareToIgnoreCase) {
        operatorWithString = String::compareToIgnoreCase;
      } else {
        continue;
      }
      Value rcv = invoke.getReceiver().getAliasedValue();
      if (rcv.definition == null
          || !rcv.definition.isConstString()
          || rcv.hasLocalInfo()) {
        continue;
      }
      DexString rcvString = rcv.definition.asConstString().getValue();

      ConstNumber constNumber;
      if (operatorWithNoArg != null) {
        assert invoke.inValues().size() == 1;
        int v = operatorWithNoArg.apply(rcvString.toString());
        constNumber = code.createIntConstant(v);
      } else if (operatorWithString != null) {
        assert invoke.inValues().size() == 2;
        Value arg = invoke.inValues().get(1).getAliasedValue();
        if (arg.definition == null
            || !arg.definition.isConstString()
            || arg.hasLocalInfo()) {
          continue;
        }
        int v = operatorWithString.apply(
            rcvString.toString(),
            arg.definition.asConstString().getValue().toString());
        constNumber = code.createIntConstant(v);
      } else {
        assert operatorWithInt != null;
        assert invoke.inValues().size() == 2;
        Value arg = invoke.inValues().get(1).getAliasedValue();
        if (arg.definition == null
            || !arg.definition.isConstNumber()
            || arg.hasLocalInfo()) {
          continue;
        }
        int v = operatorWithInt.apply(
            rcvString.toString(),
            arg.definition.asConstNumber().getIntValue());
        constNumber = code.createIntConstant(v);
      }

      it.replaceCurrentInstruction(constNumber);
    }
  }

  // Find Class#get*Name() with a constant-class and replace it with a const-string if possible.
  public void rewriteClassGetName(IRCode code, RootSet rootSet) {
    // Conflict with {@link CodeRewriter#collectClassInitializerDefaults}.
    if (code.method.isClassInitializer()) {
      return;
    }
    boolean markUseIdentifierNameString = false;
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (!instr.isInvokeVirtual()) {
        continue;
      }
      InvokeVirtual invoke = instr.asInvokeVirtual();
      DexMethod invokedMethod = invoke.getInvokedMethod();
      if (!factory.classMethods.isReflectiveNameLookup(invokedMethod)) {
        continue;
      }

      Value out = invoke.outValue();
      // Skip the call if the computed name is already discarded or not used anywhere.
      if (out == null || out.numberOfAllUsers() == 0) {
        continue;
      }
      // b/120138731: Filter out local uses, which are likely one-time name computation. In such
      // case, the result of this optimization can lead to a regression if the corresponding class
      // is in a deep package hierarchy.
      if (!code.options.testing.forceNameReflectionOptimization
          && !hasPotentialReadOutside(appInfo, code.method, EscapeAnalysis.escape(code, out))) {
        continue;
      }

      assert invoke.inValues().size() == 1;
      // In case of handling multiple invocations over the same const-string, all the following
      // usages after the initial one will point to non-null IR (a.k.a. alias), e.g.,
      //
      //   rcv <- invoke-virtual instance, ...#getClass() // Can be rewritten to const-class
      //   x <- invoke-virtual rcv, Class#getName()
      //   non_null_rcv <- non-null rcv
      //   y <- invoke-virtual non_null_rcv, Class#getCanonicalName()
      //   z <- invoke-virtual non_null_rcv, Class#getSimpleName()
      //   ... // or some other usages of the same usage.
      //
      // In that case, we should check if the original source is (possibly rewritten) const-class.
      Value in = invoke.getReceiver().getAliasedValue();
      if (in.definition == null
          || !in.definition.isConstClass()
          || in.hasLocalInfo()) {
        continue;
      }

      ConstClass constClass = in.definition.asConstClass();
      DexType type = constClass.getValue();
      int arrayDepth = type.getNumberOfLeadingSquareBrackets();
      DexType baseType = type.toBaseType(factory);
      // Make sure base type is a class type.
      if (!baseType.isClassType()) {
        continue;
      }
      DexClass holder = appInfo.definitionFor(baseType);
      if (holder == null) {
        continue;
      }

      String descriptor = baseType.toDescriptorString();
      boolean assumeTopLevel = descriptor.indexOf(INNER_CLASS_SEPARATOR) < 0;
      DexItemBasedConstString deferred = null;
      DexString name = null;
      if (invokedMethod == factory.classMethods.getName) {
        if (code.options.enableMinification && !rootSet.noObfuscation.contains(holder.type)) {
          deferred =
              new DexItemBasedConstString(
                  invoke.outValue(),
                  baseType,
                  throwingInfo,
                  new ClassNameComputationInfo(NAME, arrayDepth));
        } else {
          name = computeClassName(descriptor, holder, NAME, factory, arrayDepth);
        }
      } else if (invokedMethod == factory.classMethods.getTypeName) {
        // TODO(b/119426668): desugar Type#getTypeName
        continue;
      } else if (invokedMethod == factory.classMethods.getCanonicalName) {
        // Always returns null if the target type is local or anonymous class.
        if (holder.isLocalClass() || holder.isAnonymousClass()) {
          ConstNumber constNull = code.createConstNull();
          it.replaceCurrentInstruction(constNull);
        } else {
          // b/119471127: If an outer class is shrunk, we may compute a wrong canonical name.
          // Leave it as-is so that the class's canonical name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (code.options.enableMinification && !rootSet.noObfuscation.contains(holder.type)) {
            deferred =
                new DexItemBasedConstString(
                    invoke.outValue(),
                    baseType,
                    throwingInfo,
                    new ClassNameComputationInfo(CANONICAL_NAME, arrayDepth));
          } else {
            name = computeClassName(descriptor, holder, CANONICAL_NAME, factory, arrayDepth);
          }
        }
      } else if (invokedMethod == factory.classMethods.getSimpleName) {
        // Always returns an empty string if the target type is an anonymous class.
        if (holder.isAnonymousClass()) {
          name = factory.createString("");
        } else {
          // b/120130435: If an outer class is shrunk, we may compute a wrong simple name.
          // Leave it as-is so that the class's simple name is consistent across the app.
          if (!assumeTopLevel) {
            continue;
          }
          if (code.options.enableMinification && !rootSet.noObfuscation.contains(holder.type)) {
            deferred =
                new DexItemBasedConstString(
                    invoke.outValue(),
                    baseType,
                    throwingInfo,
                    new ClassNameComputationInfo(SIMPLE_NAME, arrayDepth));
          } else {
            name = computeClassName(descriptor, holder, SIMPLE_NAME, factory, arrayDepth);
          }
        }
      }
      if (name != null) {
        Value stringValue =
            code.createValue(
                TypeLatticeElement.stringClassType(appInfo, definitelyNotNull()),
                invoke.getLocalInfo());
        ConstString constString = new ConstString(stringValue, name, throwingInfo);
        it.replaceCurrentInstruction(constString);
      } else if (deferred != null) {
        it.replaceCurrentInstruction(deferred);
        markUseIdentifierNameString = true;
      }
    }
    if (markUseIdentifierNameString) {
      code.method.getMutableOptimizationInfo().markUseIdentifierNameString();
    }
  }

  @VisibleForTesting
  public static boolean hasPotentialReadOutside(
      AppInfo appInfo, DexEncodedMethod invocationContext, Set<Instruction> escapingInstructions) {
    for (Instruction instr : escapingInstructions) {
      if (instr.isReturn() || instr.isThrow() || instr.isStaticPut()) {
        return true;
      }
      if (instr.isInvokeMethod()) {
        DexMethod invokedMethod = instr.asInvokeMethod().getInvokedMethod();
        DexClass holder = appInfo.definitionFor(invokedMethod.getHolder());
        // For most cases, library call is not interesting, e.g.,
        // System.out.println(...), String.valueOf(...), etc.
        // If it's too broad, we can introduce black-list.
        if (holder == null || holder.isLibraryClass()) {
          continue;
        }
        // Heuristic: if the call target has the same method name, it could be still local.
        if (invokedMethod.name == invocationContext.method.name) {
          continue;
        }
        // Add more cases to filter out, if any.
        return true;
      }
      if (instr.isArrayPut()) {
        return instr.asArrayPut().array().isArgument();
      }
    }
    return false;
  }

  // String#valueOf(null) -> "null"
  // String#valueOf(String s) -> s
  // str.toString() -> str
  public void removeTrivialConversions(IRCode code) {
    InstructionIterator it = code.instructionIterator();
    while (it.hasNext()) {
      Instruction instr = it.next();
      if (instr.isInvokeStatic()) {
        InvokeStatic invoke = instr.asInvokeStatic();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != factory.stringMethods.valueOf) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.inValues().get(0);
        if (in.hasLocalInfo()) {
          continue;
        }
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.isNullType()) {
          Value nullStringValue =
              code.createValue(
                  TypeLatticeElement.stringClassType(appInfo, definitelyNotNull()),
                  invoke.getLocalInfo());
          ConstString nullString =
              new ConstString(nullStringValue, factory.createString("null"), throwingInfo);
          it.replaceCurrentInstruction(nullString);
        } else if (inType.nullability().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType().equals(factory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      } else if (instr.isInvokeVirtual()) {
        InvokeVirtual invoke = instr.asInvokeVirtual();
        DexMethod invokedMethod = invoke.getInvokedMethod();
        if (invokedMethod != factory.stringMethods.toString) {
          continue;
        }
        assert invoke.inValues().size() == 1;
        Value in = invoke.getReceiver();
        TypeLatticeElement inType = in.getTypeLattice();
        if (inType.nullability().isDefinitelyNotNull()
            && inType.isClassType()
            && inType.asClassTypeLatticeElement().getClassType().equals(factory.stringType)) {
          Value out = invoke.outValue();
          if (out != null) {
            removeOrReplaceByDebugLocalWrite(invoke, it, in, out);
          } else {
            it.removeOrReplaceByDebugLocalRead();
          }
        }
      }
    }
  }

}
