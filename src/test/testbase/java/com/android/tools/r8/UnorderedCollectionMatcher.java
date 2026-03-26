// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8;

import com.google.common.collect.ImmutableList;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

/** Matcher for a collection of items that matches a collection of matchers one-to-one. */
public class UnorderedCollectionMatcher<T> extends TypeSafeMatcher<Iterable<? extends T>> {

  private final List<Matcher<? super T>> matchers;
  private final Function<? super T, String> toStringFunction;

  private UnorderedCollectionMatcher(
      List<Matcher<? super T>> matchers, Function<? super T, String> toStringFunction) {
    this.matchers = matchers;
    this.toStringFunction = toStringFunction;
  }

  public static <T> Matcher<Iterable<? extends T>> matchesOneToOne(
      Iterable<? extends Matcher<? super T>> matchers) {
    return matchesOneToOne(matchers, null);
  }

  public static <T> Matcher<Iterable<? extends T>> matchesOneToOne(
      Iterable<? extends Matcher<? super T>> matchers,
      Function<? super T, String> toStringFunction) {
    return new UnorderedCollectionMatcher<>(ImmutableList.copyOf(matchers), toStringFunction);
  }

  @Override
  protected boolean matchesSafely(Iterable<? extends T> items) {
    return match(items).isFullMatch();
  }

  @Override
  public void describeTo(Description description) {
    description.appendText("a collection matching matchers one-to-one: ");
    description.appendList("[", ", ", "]", matchers);
  }

  private void appendItem(Description description, T item) {
    if (toStringFunction != null) {
      description.appendText(toStringFunction.apply(item));
    } else {
      description.appendValue(item);
    }
  }

  @Override
  protected void describeMismatchSafely(
      Iterable<? extends T> items, Description mismatchDescription) {
    MatchResult result = match(items);
    mismatchDescription.appendText("was not matched one-to-one:");

    if (!result.unmatchedItems.isEmpty()) {
      mismatchDescription.appendText("\nUnmatched items:");
      for (T item : result.unmatchedItems) {
        mismatchDescription.appendText("\n - ");
        appendItem(mismatchDescription, item);
      }
    }

    if (!result.unmatchedMatchers.isEmpty()) {
      mismatchDescription.appendText("\nUnmatched matchers:");
      for (Matcher<? super T> matcher : result.unmatchedMatchers) {
        mismatchDescription.appendText("\n - ");
        mismatchDescription.appendDescriptionOf(matcher);
      }
    }

    if (!result.matchedItems.isEmpty()) {
      mismatchDescription.appendText("\nMatched pairs:");
      for (int i = 0; i < result.matchedItems.size(); i++) {
        mismatchDescription.appendText("\n - ");
        appendItem(mismatchDescription, result.matchedItems.get(i));
        mismatchDescription.appendText("\n   (Matched by:) ");
        mismatchDescription.appendDescriptionOf(result.matchedMatchers.get(i));
      }
    }
  }

  private MatchResult match(Iterable<? extends T> items) {
    List<T> itemList = new ArrayList<>();
    items.forEach(itemList::add);

    MatchResult result = new MatchResult();
    Set<Integer> availableMatchers = new LinkedHashSet<>();
    for (int i = 0; i < matchers.size(); i++) {
      availableMatchers.add(i);
    }

    // For each item, find the first available matcher that matches it.
    for (T item : itemList) {
      Iterator<Integer> matcherIterator = availableMatchers.iterator();
      boolean found = false;
      while (matcherIterator.hasNext()) {
        int m = matcherIterator.next();
        if (matchers.get(m).matches(item)) {
          matcherIterator.remove();
          result.matchedItems.add(item);
          result.matchedMatchers.add(matchers.get(m));
          found = true;
          break;
        }
      }
      if (!found) {
        result.unmatchedItems.add(item);
      }
    }

    for (int m : availableMatchers) {
      result.unmatchedMatchers.add(matchers.get(m));
    }

    assert (result.matchedItems.size() + result.unmatchedItems.size() == itemList.size());
    assert (result.matchedMatchers.size() + result.unmatchedMatchers.size() == matchers.size());
    assert (result.matchedItems.size() == result.matchedMatchers.size());
    return result;
  }

  private class MatchResult {
    final List<T> matchedItems = new ArrayList<>();
    final List<Matcher<? super T>> matchedMatchers = new ArrayList<>();
    final List<T> unmatchedItems = new ArrayList<>();
    final List<Matcher<? super T>> unmatchedMatchers = new ArrayList<>();

    boolean isFullMatch() {
      return unmatchedItems.isEmpty() && unmatchedMatchers.isEmpty();
    }
  }
}
