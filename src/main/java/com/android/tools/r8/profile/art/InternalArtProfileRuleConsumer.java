// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.profile.art;

import com.android.tools.r8.references.MethodReference;

public interface InternalArtProfileRuleConsumer extends ArtProfileRuleConsumer {

  void acceptMethodRule(
      MethodReference methodReference,
      ArtProfileMethodRuleInfo methodRuleInfo,
      ArtProfileInlineCacheData inlineCacheData);

  @Override
  default void acceptMethodRule(
      MethodReference methodReference, ArtProfileMethodRuleInfo methodRuleInfo) {
    acceptMethodRule(methodReference, methodRuleInfo, ArtProfileInlineCacheData.empty());
  }
}
