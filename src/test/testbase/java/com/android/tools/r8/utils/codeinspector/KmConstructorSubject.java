// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils.codeinspector;

import java.util.List;
import kotlin.metadata.jvm.JvmMethodSignature;

public abstract class KmConstructorSubject extends Subject {

  public abstract JvmMethodSignature signature();

  public abstract List<KmValueParameterSubject> valueParameters();
}
