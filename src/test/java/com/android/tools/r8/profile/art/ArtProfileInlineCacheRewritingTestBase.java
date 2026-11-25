// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.TextOutputStream;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.utils.ArtProfileTestingUtils;
import com.android.tools.r8.utils.internal.Box;
import java.nio.file.Path;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public abstract class ArtProfileInlineCacheRewritingTestBase extends TestBase {

  protected static final ArtProfileMethodRuleInfo HOT_RULE =
      ArtProfileMethodRuleInfoImpl.builder().setIsHot().build();
  protected static final ArtProfileMethodRuleInfo POST_STARTUP_RULE =
      ArtProfileMethodRuleInfoImpl.builder().setIsPostStartup().build();

  protected final TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  protected ArtProfileInlineCacheRewritingTestBase(TestParameters parameters) {
    this.parameters = parameters;
  }

  protected static ArtProfileConsumer createCombinedArtProfileConsumer(
      Path outputProfile, Box<ExternalArtProfile> residualProfileBox) {
    ArtProfileConsumer humanReadableConsumer = ArtProfileConsumerUtils.create(outputProfile);
    ArtProfileConsumer ruleConsumerDelegate =
        ArtProfileTestingUtils.createResidualArtProfileConsumer(residualProfileBox::set);
    return new ArtProfileConsumer() {

      @Override
      public TextOutputStream getHumanReadableArtProfileConsumer() {
        return humanReadableConsumer.getHumanReadableArtProfileConsumer();
      }

      @Override
      public ArtProfileRuleConsumer getRuleConsumer() {
        return ruleConsumerDelegate.getRuleConsumer();
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        humanReadableConsumer.finished(handler);
        ruleConsumerDelegate.finished(handler);
      }
    };
  }
}
