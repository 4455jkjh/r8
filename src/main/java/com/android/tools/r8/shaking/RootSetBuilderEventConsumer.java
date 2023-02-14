// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.AppInfoWithClassHierarchy;
import com.android.tools.r8.graph.AppView;
import com.android.tools.r8.graph.ProgramMethod;
import com.android.tools.r8.ir.desugar.itf.InterfaceMethodDesugaringBaseEventConsumer;
import com.android.tools.r8.profile.art.rewriting.ArtProfileRewritingRootSetBuilderEventConsumer;
import com.android.tools.r8.shaking.RootSetUtils.RootSet;

public interface RootSetBuilderEventConsumer extends InterfaceMethodDesugaringBaseEventConsumer {

  static RootSetBuilderEventConsumer create(AppView<? extends AppInfoWithClassHierarchy> appView) {
    return ArtProfileRewritingRootSetBuilderEventConsumer.attach(appView, empty());
  }

  static EmptyRootSetBuilderEventConsumer empty() {
    return EmptyRootSetBuilderEventConsumer.getInstance();
  }

  default void finished(AppView<? extends AppInfoWithClassHierarchy> appView, RootSet rootSet) {}

  class EmptyRootSetBuilderEventConsumer implements RootSetBuilderEventConsumer {

    private static final EmptyRootSetBuilderEventConsumer INSTANCE =
        new EmptyRootSetBuilderEventConsumer();

    private EmptyRootSetBuilderEventConsumer() {}

    static EmptyRootSetBuilderEventConsumer getInstance() {
      return INSTANCE;
    }

    @Override
    public void acceptCompanionClassClinit(ProgramMethod method) {
      // Intentionally empty.
    }

    @Override
    public void acceptDefaultAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }

    @Override
    public void acceptPrivateAsCompanionMethod(
        ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }

    @Override
    public void acceptStaticAsCompanionMethod(ProgramMethod method, ProgramMethod companionMethod) {
      // Intentionally empty.
    }
  }
}
