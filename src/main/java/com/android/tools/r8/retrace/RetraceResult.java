// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.retrace;

import com.android.tools.r8.Keep;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Base interface for any retrace result.
 *
 * <p>The retracing of any given item may result in ambiguous results. This base type provides the
 * contract for representing the ambiguity. Concretely an ambiguous result can be mapped over with
 * each "element" representing one of the non-ambiguous possible retracings. The retrace result can
 * itself provide methods for providing contextual information to further restrict the ambiguity of
 * the result.
 */
@Keep
public interface RetraceResult<E> {

  /** Basic operation over 'elements' which represent a possible non-ambiguous retracing. */
  Stream<E> stream();

  /** A result is ambiguous iff the stream has two or more elements. */
  default boolean isAmbiguous() {
    return stream().findFirst().isPresent() && stream().skip(1).findFirst().isPresent();
  }

  /** Short-hand for iterating the elements. */
  default void forEach(Consumer<E> action) {
    stream().forEach(action);
  }
}
