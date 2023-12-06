// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.annotations;

public enum KeepItemKind {
  ONLY_CLASS,
  ONLY_MEMBERS,
  ONLY_METHODS,
  ONLY_FIELDS,
  CLASS_AND_MEMBERS,
  CLASS_AND_METHODS,
  CLASS_AND_FIELDS,
  DEFAULT
}
