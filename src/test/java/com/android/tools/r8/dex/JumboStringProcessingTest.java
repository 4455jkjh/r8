// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.DexIndexedConsumer;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestDeps;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.dex.code.DexBase2Format;
import com.android.tools.r8.dex.code.DexConst4;
import com.android.tools.r8.dex.code.DexConstString;
import com.android.tools.r8.dex.code.DexConstStringJumbo;
import com.android.tools.r8.dex.code.DexGoto;
import com.android.tools.r8.dex.code.DexGoto16;
import com.android.tools.r8.dex.code.DexGoto32;
import com.android.tools.r8.dex.code.DexIfEq;
import com.android.tools.r8.dex.code.DexIfEqz;
import com.android.tools.r8.dex.code.DexIfNe;
import com.android.tools.r8.dex.code.DexIfNez;
import com.android.tools.r8.dex.code.DexInstruction;
import com.android.tools.r8.dex.code.DexNop;
import com.android.tools.r8.dex.code.DexPackedSwitch;
import com.android.tools.r8.dex.code.DexPackedSwitchPayload;
import com.android.tools.r8.dex.code.DexReturnVoid;
import com.android.tools.r8.dex.jumbostrings.JumboStringCodeRewriter;
import com.android.tools.r8.graph.DexCode;
import com.android.tools.r8.graph.DexCode.Try;
import com.android.tools.r8.graph.DexCode.TryHandler;
import com.android.tools.r8.graph.DexEncodedMethod;
import com.android.tools.r8.graph.DexItemFactory;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.graph.MethodAccessFlags;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.graph.StringOffsetProvider;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.AndroidApp;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class JumboStringProcessingTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withNoneRuntime().build();
  }

  @Test
  public void branching() {
    DexItemFactory factory = new DexItemFactory();
    DexString string = factory.createString("turn into jumbo");
    DexInstruction[] instructions = buildInstructions(string, false);
    DexCode code = jumboStringProcess(factory, string, instructions);
    DexInstruction[] rewrittenInstructions = code.instructions;
    assert rewrittenInstructions[1] instanceof DexIfEq;
    DexIfEq condition = (DexIfEq) rewrittenInstructions[1];
    assert condition.getOffset() + condition.CCCC == rewrittenInstructions[3].getOffset();
    assert rewrittenInstructions[2] instanceof DexGoto32;
    DexGoto32 jump = (DexGoto32) rewrittenInstructions[2];
    DexInstruction lastInstruction = rewrittenInstructions[rewrittenInstructions.length - 1];
    assert jump.getOffset() + jump.AAAAAAAA == lastInstruction.getOffset();
  }

  @Test
  public void branching2() {
    DexItemFactory factory = new DexItemFactory();
    DexString string = factory.createString("turn into jumbo");
    DexInstruction[] instructions = buildInstructions(string, true);
    DexCode code = jumboStringProcess(factory, string, instructions);
    DexInstruction[] rewrittenInstructions = code.instructions;
    assert rewrittenInstructions[1] instanceof DexIfEqz;
    DexIfEqz condition = (DexIfEqz) rewrittenInstructions[1];
    assert condition.getOffset() + condition.BBBB == rewrittenInstructions[3].getOffset();
    assert rewrittenInstructions[2] instanceof DexGoto32;
    DexGoto32 jump = (DexGoto32) rewrittenInstructions[2];
    DexInstruction lastInstruction = rewrittenInstructions[rewrittenInstructions.length - 1];
    assert jump.getOffset() + jump.AAAAAAAA == lastInstruction.getOffset();
  }

  private DexInstruction[] buildInstructions(DexString string, boolean zeroCondition) {
    List<DexInstruction> instructions = new ArrayList<>();
    int offset = 0;
    DexConst4 zeroInstruction = new DexConst4(0, 0);
    zeroInstruction.setOffset(offset);
    instructions.add(zeroInstruction);
    offset += zeroInstruction.getSize();
    int lastInstructionOffset = 15000 * 2 + 2 + offset;
    DexBase2Format ifInstruction =
        zeroCondition
            ? new DexIfNez(0, lastInstructionOffset - offset)
            : new DexIfNe(0, 0, lastInstructionOffset - offset);
    ifInstruction.setOffset(offset);
    instructions.add(ifInstruction);
    offset += ifInstruction.getSize();
    for (int i = 0; i < 15000; i++) {
      DexConstString stringInstruction = new DexConstString(0, string);
      stringInstruction.setOffset(offset);
      instructions.add(stringInstruction);
      offset += stringInstruction.getSize();
    }
    DexReturnVoid returnInstruction = new DexReturnVoid();
    returnInstruction.setOffset(offset);
    instructions.add(returnInstruction);
    assert returnInstruction.getOffset() == lastInstructionOffset;
    return instructions.toArray(DexInstruction.EMPTY_ARRAY);
  }

  private int countJumboStrings(DexInstruction[] instructions) {
    int count = 0;
    for (DexInstruction instruction : instructions) {
      count += instruction instanceof DexConstStringJumbo ? 1 : 0;
    }
    return count;
  }

  private int countSimpleNops(DexInstruction[] instructions) {
    int count = 0;
    for (DexInstruction instruction : instructions) {
      count += instruction.isSimpleNop() ? 1 : 0;
    }
    return count;
  }

  @Test
  public void regress78072750() throws Exception {
    // This dex file have the baksmali output from the failing class from b/78072750, with all
    // const-string/jumbo replaced with const-string. Also one of the nops before the first
    // payload has been removed to make it valid dex file (correct alignment of the payload
    // instruction).
    Path originalDexFile = TestDeps.getSmaliPath("regression/78072750/78072750.dex");
    AndroidApp application =
        AndroidApp.builder()
            .addDexProgramData(Files.toByteArray(originalDexFile.toFile()), Origin.unknown())
            .build();
    CodeInspector inspector = new CodeInspector(application);
    ProgramMethod method =
        getMethod(
            inspector,
            "android.databinding.DataBinderMapperImpl",
            "android.databinding.ViewDataBinding",
            "getDataBinder",
            ImmutableList.of(
                "android.databinding.DataBindingComponent", "android.view.View", "int"));
    DexInstruction[] instructions = method.getDefinition().getCode().asDexCode().instructions;
    assertEquals(0, countJumboStrings(instructions));
    assertEquals(1, countSimpleNops(instructions));

    DexItemFactory factory = inspector.getFactory();
    DexString string = factory.createString("view must have a tag");
    DexCode code = jumboStringProcess(factory, string, instructions);
    DexInstruction[] rewrittenInstructions = code.instructions;
    assertEquals(289, countJumboStrings(rewrittenInstructions));
    assertEquals(0, countSimpleNops(rewrittenInstructions));
  }

  @Test
  public void gotoExpansionWithPayloadNopRemoval() {
    DexItemFactory factory = new DexItemFactory();
    DexString string = factory.createString("turn into jumbo");
    List<DexInstruction> instructions = new ArrayList<>();
    int offset = 0;

    // Goto at offset 0 targeting ReturnVoid at offset 127 (max 1-byte offset).
    DexGoto gotoInstruction = new DexGoto(127);
    gotoInstruction.setOffset(offset);
    instructions.add(gotoInstruction);
    offset += gotoInstruction.getSize();

    // ConstString at offset 1 that will expand to ConstStringJumbo (+1 code unit),
    // pushing ReturnVoid to offset 128.
    DexConstString stringInstruction = new DexConstString(0, string);
    stringInstruction.setOffset(offset);
    instructions.add(stringInstruction);
    offset += stringInstruction.getSize();

    // PackedSwitch at offset 3 targeting payload at offset 130.
    int switchOffset = offset;
    DexPackedSwitch switchInstruction = new DexPackedSwitch(0);
    switchInstruction.setOffset(offset);
    switchInstruction.setPayloadOffset(130 - switchOffset);
    instructions.add(switchInstruction);
    offset += switchInstruction.getSize();

    while (offset < 127) {
      DexConst4 constInstruction = new DexConst4(0, 0);
      constInstruction.setOffset(offset);
      instructions.add(constInstruction);
      offset += constInstruction.getSize();
    }

    assertEquals(127, offset);
    DexReturnVoid returnInstruction = new DexReturnVoid();
    returnInstruction.setOffset(offset);
    instructions.add(returnInstruction);
    offset += returnInstruction.getSize();

    DexConst4 beforeNop = new DexConst4(0, 0);
    beforeNop.setOffset(offset);
    instructions.add(beforeNop);
    offset += beforeNop.getSize();

    // Simple nop before payload at offset 129; when ConstString expands (+1), the payload
    // alignment removes this nop (-1), resulting in net offsetDelta == 0 at the end of pass 1.
    DexNop nopInstruction = new DexNop();
    nopInstruction.setOffset(offset);
    instructions.add(nopInstruction);
    offset += nopInstruction.getSize();

    assertEquals(130, offset);
    DexPackedSwitchPayload payload = new DexPackedSwitchPayload(0, new int[] {127 - switchOffset});
    payload.setOffset(offset);
    instructions.add(payload);

    DexCode code =
        jumboStringProcess(factory, string, instructions.toArray(DexInstruction.EMPTY_ARRAY));
    DexInstruction[] rewrittenInstructions = code.instructions;
    assertTrue(rewrittenInstructions[0] instanceof DexGoto16);
    DexGoto16 jump = (DexGoto16) rewrittenInstructions[0];
    assertEquals(returnInstruction.getOffset(), jump.getOffset() + jump.AAAA);
  }

  private DexCode jumboStringProcess(
      DexItemFactory factory, DexString string, DexInstruction[] instructions) {
    DexCode code =
        new DexCode(1, 0, 0, instructions, Try.EMPTY_ARRAY, TryHandler.EMPTY_ARRAY, null);
    MethodAccessFlags flags = MethodAccessFlags.fromSharedAccessFlags(Constants.ACC_PUBLIC, false);
    DexEncodedMethod method =
        DexEncodedMethod.builder()
            .setAccessFlags(flags)
            .setCode(code)
            .disableMethodNotNullCheck()
            .disableAndroidApiLevelCheck()
            .build();
    StringOffsetProvider mapping =
        new StringOffsetProvider() {
          @Override
          public int getOffsetFor(DexString str) {
            return str.equals(string) ? 65536 : 0;
          }

          @Override
          public int getLazyDexStringsCount() {
            return 0;
          }

          @Override
          public boolean hasJumboStrings() {
            return true;
          }
        };
    InternalOptions options = new InternalOptions(factory, new Reporter());
    options.programConsumer = DexIndexedConsumer.emptyConsumer();
    return new JumboStringCodeRewriter(method, mapping, () -> false, options).rewrite();
  }
}
