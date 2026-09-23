// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.partial.kotlin

class Inlinee {
  inline fun inlineeMethod(): String {
    return "inlined!"
  }
}

class Caller {
  fun call(): String {
    return Inlinee().inlineeMethod()
  }
}

fun main() {
  println(Caller().call())
}
