// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.conversion.passes;

import com.android.tools.r8.contexts.CompilationContext.MethodProcessingContext;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.ir.code.IRCode;
import com.android.tools.r8.ir.conversion.IRConverter;
import com.android.tools.r8.ir.conversion.MethodProcessor;
import com.android.tools.r8.ir.conversion.passes.result.CodeRewriterResult;
import com.android.tools.r8.ir.optimize.ListIterationRewriter;
import com.android.tools.r8.utils.InternalOptions;
import com.android.tools.r8.utils.internal.ArrayUtils;
import com.android.tools.r8.utils.internal.collections.Pair;
import com.android.tools.r8.utils.timing.Timing;
import java.util.Arrays;
import java.util.List;

public class CodeRewriterPassCollection {

  private final List<CodeRewriterPass<?>> passes;

  private CodeRewriterPassCollection(List<CodeRewriterPass<?>> passes) {
    this.passes = passes;
  }

  public static CodeRewriterPassCollection create(List<CodeRewriterPass<?>> passes) {
    return new CodeRewriterPassCollection(passes);
  }

  public static CodeRewriterPassCollection create(CodeRewriterPass<?>... passes) {
    return create(Arrays.asList(passes));
  }

  public static CodeRewriterPassCollection createFromNullable(CodeRewriterPass<?>... passes) {
    return CodeRewriterPassCollection.create(
        ArrayUtils.filterNulls(passes, CodeRewriterPass.EMPTY_ARRAY));
  }

  public Pair<Boolean, String> run(
      IRCode code,
      MethodProcessor methodProcessor,
      MethodProcessingContext methodProcessingContext,
      Timing timing,
      String previousMethodPrinting,
      InternalOptions options) {
    boolean changed = false;
    for (CodeRewriterPass<?> pass : passes) {
      // TODO(b/286345542): Run printMethod after each run.
      CodeRewriterResult result = pass.run(code, methodProcessor, methodProcessingContext, timing);
      changed |= result.hasChanged().isTrue();
      previousMethodPrinting =
          IRConverter.printMethodIR(
              code, "IR after " + pass.getRewriterId(), previousMethodPrinting, options);
    }
    return new Pair<>(changed, previousMethodPrinting);
  }

  public void enableListIterationRewriter(AppView<?> appView) {
    passes.add(new ListIterationRewriter(appView));
  }
}
