// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.ir.desugar.desugaredlibrary;

import com.android.tools.r8.ir.desugar.desugaredlibrary.humanspecification.HumanDesugaredLibrarySpecification;
import com.android.tools.r8.ir.desugar.desugaredlibrary.legacyspecification.LegacyDesugaredLibrarySpecification;
import java.util.List;

public interface DesugaredLibrarySpecification {

  default boolean isHuman() {
    return false;
  }

  default boolean isLegacy() {
    return false;
  }

  default LegacyDesugaredLibrarySpecification asLegacyDesugaredLibrarySpecification() {
    return null;
  }

  default HumanDesugaredLibrarySpecification asHumanDesugaredLibrarySpecification() {
    return null;
  }

  boolean isEmpty();

  boolean isLibraryCompilation();

  String getJsonSource();

  String getSynthesizedLibraryClassesPackagePrefix();

  List<String> getExtraKeepRules();
}
