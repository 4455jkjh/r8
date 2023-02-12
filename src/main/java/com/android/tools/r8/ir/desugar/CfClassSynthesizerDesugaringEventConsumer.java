// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.desugaredlibrary.apiconversion.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.desugaredlibrary.retargeter.DesugaredLibraryRetargeterSynthesizerEventConsumer.DesugaredLibraryRetargeterL8SynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer.L8ProgramEmulatedInterfaceSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.records.RecordDesugaringEventConsumer.RecordClassSynthesizerDesugaringEventConsumer;
import com.android.tools.r8.ir.desugar.varhandle.VarHandleDesugaringEventConsumer;
import com.android.tools.r8.profile.art.rewriting.ArtProfileCollectionAdditions;
import com.android.tools.r8.profile.art.rewriting.ArtProfileRewritingCfClassSynthesizerDesugaringEventConsumer;
import com.google.common.collect.Sets;
import java.util.Set;

public abstract class CfClassSynthesizerDesugaringEventConsumer
    implements L8ProgramEmulatedInterfaceSynthesizerEventConsumer,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer,
        DesugaredLibraryRetargeterL8SynthesizerEventConsumer,
        RecordClassSynthesizerDesugaringEventConsumer,
        VarHandleDesugaringEventConsumer {

  protected CfClassSynthesizerDesugaringEventConsumer() {}

  public static CfClassSynthesizerDesugaringEventConsumer create(
      ArtProfileCollectionAdditions artProfileCollectionAdditions) {
    CfClassSynthesizerDesugaringEventConsumer eventConsumer =
        new D8R8CfClassSynthesizerDesugaringEventConsumer();
    return ArtProfileRewritingCfClassSynthesizerDesugaringEventConsumer.attach(
        artProfileCollectionAdditions, eventConsumer);
  }

  public abstract Set<DexProgramClass> getSynthesizedClasses();

  private static class D8R8CfClassSynthesizerDesugaringEventConsumer
      extends CfClassSynthesizerDesugaringEventConsumer {

    private final Set<DexProgramClass> synthesizedClasses = Sets.newConcurrentHashSet();

    @Override
    public void acceptProgramEmulatedInterface(DexProgramClass clazz) {
      synthesizedClasses.add(clazz);
    }

    @Override
    public void acceptWrapperProgramClass(DexProgramClass clazz) {
      synthesizedClasses.add(clazz);
    }

    @Override
    public void acceptEnumConversionProgramClass(DexProgramClass clazz) {
      synthesizedClasses.add(clazz);
    }

    @Override
    public void acceptDesugaredLibraryRetargeterDispatchProgramClass(DexProgramClass clazz) {
      synthesizedClasses.add(clazz);
    }

    @Override
    public void acceptRecordClass(DexProgramClass recordTagClass) {
      synthesizedClasses.add(recordTagClass);
    }

    @Override
    public void acceptRecordClassContext(
        DexProgramClass recordTagClass, DexProgramClass recordClass) {
      // Intentionally empty.
    }

    @Override
    public void acceptVarHandleDesugaringClass(DexProgramClass clazz) {
      synthesizedClasses.add(clazz);
    }

    @Override
    public Set<DexProgramClass> getSynthesizedClasses() {
      return synthesizedClasses;
    }

    @Override
    public void acceptCollectionConversion(ProgramMethod arrayConversion) {
      synthesizedClasses.add(arrayConversion.getHolder());
    }
  }
}
