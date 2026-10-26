// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.enumunboxing.kotlin

enum class UnboxableEnum {
  A,
  B,
  C,
}

enum class EnumWithEntries {
  ONE,
  TWO,
  THREE,
}

fun switchOnEnumWithEntries(e: EnumWithEntries): String =
  when (e) {
    EnumWithEntries.ONE -> "1"
    EnumWithEntries.TWO -> "2"
    EnumWithEntries.THREE -> "3"
  }

fun main() {
  enumValues<UnboxableEnum>().forEach { println(it.name) }
  EnumWithEntries.entries.forEach { println("${it.name}:${it.ordinal}") }
  println(EnumWithEntries.values().size)
  println(EnumWithEntries.ONE.ordinal)
  println(EnumWithEntries.TWO.name)
  println(switchOnEnumWithEntries(EnumWithEntries.entries[0]))
}
