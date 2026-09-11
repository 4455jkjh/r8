// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.dex.container.conststring20;

import com.android.tools.r8.ProgramResource;
import com.android.tools.r8.ProgramResource.Kind;
import com.android.tools.r8.dex.Constants;
import com.android.tools.r8.dex.DexParser;
import com.android.tools.r8.dex.DexReader;
import com.android.tools.r8.dex.container.DexContainerFormatTestBase;
import com.android.tools.r8.graph.ClassKind;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.DexString;
import com.android.tools.r8.jasmin.JasminBuilder;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.InternalOptions;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

public abstract class DexConstString20TestBase extends DexContainerFormatTestBase {

  protected static Collection<byte[]> get32KClassesWith64KStrings() throws Exception {
    return new JasminBuilder()
        .apply(
            jasminBuilder -> {
              for (int i = 0; i < Constants.U16BIT_MAX / 2; i++) {
                jasminBuilder.addClass("C" + i).setSourceFile("SourceFile" + i);
              }
            })
        .buildClasses();
  }

  protected List<DexString> parseStringPool(byte[] dex) throws Exception {
    DexParser<DexProgramClass> parser =
        new DexParser<>(
            new DexReader(ProgramResource.fromBytes(Origin.unknown(), Kind.DEX, dex, null)),
            ClassKind.PROGRAM,
            new InternalOptions());
    parser.populateStrings();
    return Arrays.asList(parser.getIndexedItems().getStringMap());
  }
}
