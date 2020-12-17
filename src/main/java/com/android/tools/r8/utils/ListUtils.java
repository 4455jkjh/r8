// Copyright (c) 2016, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Predicate;

public class ListUtils {

  public static <T> T first(List<T> list) {
    return list.get(0);
  }

  public static <T> int firstIndexMatching(List<T> list, Predicate<T> tester) {
    for (int i = 0; i < list.size(); i++) {
      if (tester.test(list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public static <T> T last(List<T> list) {
    return list.get(list.size() - 1);
  }

  public static <T> int lastIndexMatching(List<T> list, Predicate<T> tester) {
    for (int i = list.size() - 1; i >= 0; i--) {
      if (tester.test(list.get(i))) {
        return i;
      }
    }
    return -1;
  }

  public static <S, T> List<T> map(Iterable<S> list, Function<S, T> fn) {
    List<T> result = new ArrayList<>();
    for (S element : list) {
      result.add(fn.apply(element));
    }
    return result;
  }

  public static <S, T> List<T> map(Collection<S> list, Function<S, T> fn) {
    List<T> result = new ArrayList<>(list.size());
    for (S element : list) {
      result.add(fn.apply(element));
    }
    return result;
  }

  public static <T> Optional<T> removeFirstMatch(List<T> list, Predicate<T> element) {
    int index = firstIndexMatching(list, element);
    if (index >= 0) {
      return Optional.of(list.remove(index));
    }
    return Optional.empty();
  }

  public static <T extends Comparable<T>> boolean verifyListIsOrdered(List<T> list) {
    for (int i = list.size() - 1; i > 0; i--) {
      if (list.get(i).compareTo(list.get(i - 1)) < 0) {
        return false;
      }
    }
    return true;
  }

  public static <T, R> R fold(Collection<T> items, R identity, BiFunction<R, T, R> acc) {
    R result = identity;
    for (T item : items) {
      result = acc.apply(result, item);
    }
    return result;
  }
}
