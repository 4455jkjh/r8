// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.DataResourceProvider;

public class AllEmbeddedRulesExtractor extends EmbeddedRulesExtractor {

  public AllEmbeddedRulesExtractor(DataResourceProvider dataResourceProvider, Reporter reporter) {
    super(SemanticVersion::max, dataResourceProvider, reporter);
  }

  @Override
  protected boolean testFromCompilerVersion(SemanticVersion fromCompilerVersion) {
    return true;
  }

  @Override
  protected boolean testUptoCompilerVersion(SemanticVersion uptoCompilerVersion) {
    return true;
  }
}
