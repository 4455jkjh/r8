// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.libanalyzer;

import static org.junit.Assert.assertEquals;

import com.android.tools.r8.libanalyzer.proto.ValidateConsumerKeepRulesResult;

public class ValidateConsumerKeepRulesResultInspector
    extends LibraryAnalyzerResultInspector<
        ValidateConsumerKeepRulesResult, ValidateConsumerKeepRulesResultInspector> {

  public ValidateConsumerKeepRulesResultInspector(
      ValidateConsumerKeepRulesResult validateConsumerKeepRulesResult) {
    super(validateConsumerKeepRulesResult);
  }

  public ValidateConsumerKeepRulesResultInspector assertNoBlockedKeepRules() {
    assertEquals(0, result.getBlockedKeepRulesCount());
    return this;
  }

  @Override
  public ValidateConsumerKeepRulesResultInspector self() {
    return this;
  }
}
