// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar;

import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.ir.desugar.desugaredlibrary.DesugaredLibraryWrapperSynthesizerEventConsumer.DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer;
import com.android.tools.r8.ir.desugar.itf.EmulatedInterfaceSynthesizerEventConsumer;
import com.google.common.collect.Sets;
import java.util.Set;

public class CfL8ClassSynthesizerEventConsumer
    implements EmulatedInterfaceSynthesizerEventConsumer,
        DesugaredLibraryL8ProgramWrapperSynthesizerEventConsumer {

  private Set<DexProgramClass> synthesizedClasses = Sets.newConcurrentHashSet();

  @Override
  public void acceptEmulatedInterface(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  @Override
  public void acceptWrapperProgramClass(DexProgramClass clazz) {
    synthesizedClasses.add(clazz);
  }

  public Set<DexProgramClass> getSynthesizedClasses() {
    return synthesizedClasses;
  }

}
