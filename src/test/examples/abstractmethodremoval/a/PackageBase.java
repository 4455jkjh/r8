// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package abstractmethodremoval.a;

public abstract class PackageBase {
  abstract void foo(int i);

  public static void invokeFoo(PackageBase o) {
    o.foo(0);
  }
}
