// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art.utils;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.profile.art.ArtProfileBuilder;
import com.android.tools.r8.profile.art.ArtProfileClassRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileConsumer;
import com.android.tools.r8.profile.art.ArtProfileInlineCacheData;
import com.android.tools.r8.profile.art.ArtProfileMethodRuleInfo;
import com.android.tools.r8.profile.art.ArtProfileProvider;
import com.android.tools.r8.profile.art.ArtProfileRuleConsumer;
import com.android.tools.r8.profile.art.InternalArtProfileMethodRuleBuilder;
import com.android.tools.r8.profile.art.InternalArtProfileRuleConsumer;
import com.android.tools.r8.profile.art.model.ExternalArtProfile;
import com.android.tools.r8.profile.art.model.ExternalArtProfileClassRule;
import com.android.tools.r8.profile.art.model.ExternalArtProfileMethodRule;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.MethodReference;
import java.util.function.Consumer;

public class ArtProfileTestingUtils {

  // Creates an ArtProfileProvider for passing the given ART profile to a D8/L8/R8 compilation.
  public static ArtProfileProvider createArtProfileProvider(ExternalArtProfile artProfile) {
    return new ArtProfileProvider() {

      @Override
      public void getArtProfile(ArtProfileBuilder profileBuilder) {
        artProfile.forEach(
            classRule ->
                profileBuilder.addClassRule(
                    classRuleBuilder ->
                        classRuleBuilder.setClassReference(classRule.getClassReference())),
            methodRule ->
                profileBuilder.addMethodRule(
                    methodRuleBuilder -> {
                      methodRuleBuilder
                          .setMethodReference(methodRule.getMethodReference())
                          .setMethodRuleInfo(
                              methodRuleInfoBuilder ->
                                  methodRuleInfoBuilder
                                      .setIsHot(methodRule.getMethodRuleInfo().isHot())
                                      .setIsStartup(methodRule.getMethodRuleInfo().isStartup())
                                      .setIsPostStartup(
                                          methodRule.getMethodRuleInfo().isPostStartup()));
                      // TODO(b/590031234): Expose a public API for adding inline cache data.
                      if (!methodRule.getInlineCacheData().isEmpty()
                          && methodRuleBuilder instanceof InternalArtProfileMethodRuleBuilder) {
                        ((InternalArtProfileMethodRuleBuilder) methodRuleBuilder)
                            .setInlineCacheData(methodRule.getInlineCacheData());
                      }
                    }));
      }

      @Override
      public Origin getOrigin() {
        return Origin.unknown();
      }
    };
  }

  // Creates an ArtProfileConsumer for accepting the residual ART profile from the compilation.
  public static ArtProfileConsumer createResidualArtProfileConsumer(
      Consumer<ExternalArtProfile> residualArtProfileInspector) {
    return new ArtProfileConsumer() {

      final ExternalArtProfile.Builder residualArtProfileBuilder = ExternalArtProfile.builder();

      @Override
      public ArtProfileRuleConsumer getRuleConsumer() {
        return new InternalArtProfileRuleConsumer() {

          @Override
          public void acceptClassRule(
              ClassReference classReference, ArtProfileClassRuleInfo classRuleInfo) {
            residualArtProfileBuilder.addRule(
                ExternalArtProfileClassRule.builder().setClassReference(classReference).build());
          }

          @Override
          public void acceptMethodRule(
              MethodReference methodReference,
              ArtProfileMethodRuleInfo methodRuleInfo,
              ArtProfileInlineCacheData inlineCacheData) {
            residualArtProfileBuilder.addRule(
                ExternalArtProfileMethodRule.builder()
                    .setMethodReference(methodReference)
                    .setMethodRuleInfo(methodRuleInfo)
                    .setInlineCacheData(inlineCacheData)
                    .build());
          }
        };
      }

      @Override
      public void finished(DiagnosticsHandler handler) {
        residualArtProfileInspector.accept(residualArtProfileBuilder.build());
      }
    };
  }
}
