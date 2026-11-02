// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.assistant.cli;

import com.android.tools.r8.graph.DexType;
import com.android.tools.r8.shaking.ProguardClassNameList;
import com.android.tools.r8.shaking.ProguardTypeMatcher;
import com.android.tools.r8.shaking.ProguardTypeMatcher.MatchTypePattern;
import com.android.tools.r8.utils.internal.TraversalContinuation;
import it.unimi.dsi.fastutil.ints.Int2ReferenceMap;
import it.unimi.dsi.fastutil.ints.Int2ReferenceOpenHashMap;
import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;

/**
 * Finds the items, such as keep rules, whose class name patterns can match a class without matching
 * the class against every pattern.
 *
 * <p>Exact class names are recorded at the end of their path in a trie, and wildcard patterns are
 * recorded at their literal prefix, the text before their first wildcard, so the items whose
 * patterns can match a class are found on the path of its name. Items with a pattern that can match
 * any class, such as {@code *} or a list ending in a negated pattern, are recorded under the empty
 * prefix. The candidates include every item whose patterns match the class, so the patterns must
 * still be matched against the class.
 */
final class ClassNameIndex<T> {

  private final List<T> items = new ArrayList<>();
  // A trie of exact class names and literal wildcard prefixes.
  private final Node root = new Node();

  private static final class Node {

    private Int2ReferenceMap<Node> children;
    private BitSet exactItems;
    private BitSet prefixItems;

    Node getChild(char c) {
      return children == null ? null : children.get(c);
    }

    Node getOrCreateChild(char c) {
      if (children == null) {
        children = new Int2ReferenceOpenHashMap<>();
      }
      Node child = children.get(c);
      if (child == null) {
        child = new Node();
        children.put(c, child);
      }
      return child;
    }

    void addExact(int index) {
      if (exactItems == null) {
        exactItems = new BitSet();
      }
      exactItems.set(index);
    }

    void addPrefix(int index) {
      if (prefixItems == null) {
        prefixItems = new BitSet();
      }
      prefixItems.set(index);
    }

    void addPrefixesTo(BitSet candidates) {
      if (prefixItems != null) {
        candidates.or(prefixItems);
      }
    }

    void addExactTo(BitSet candidates) {
      if (exactItems != null) {
        candidates.or(exactItems);
      }
    }
  }

  /** Adds an item that applies to the classes that the class names match. */
  void add(T item, ProguardClassNameList classNames) {
    add(item, classNames, false);
  }

  /**
   * Like {@link #add}, with back references in the class names replaced by what was captured when
   * their precondition was last matched.
   */
  void addMaterialized(T item, ProguardClassNameList classNames) {
    add(item, classNames, true);
  }

  private void add(T item, ProguardClassNameList classNames, boolean materializeBackReferences) {
    int index = items.size();
    items.add(item);
    if (classNames.size() == 0) {
      return;
    }
    if (classNames.getLast().isNegated()) {
      // A list that ends with a negated pattern matches the classes that no pattern matches.
      root.addPrefix(index);
      return;
    }
    classNames.traverseTypeMatchers(
        matcherAndNegation -> {
          // A class that matches a negated pattern first is not matched, so only the positive
          // patterns can match a class.
          if (!matcherAndNegation.isNegated()) {
            recordMatcher(matcherAndNegation.getMatcher(), index, materializeBackReferences);
          }
          return TraversalContinuation.doContinue();
        });
  }

  /** The items whose class names can match the class, in the order they were added. */
  List<T> getCandidateList(String className) {
    BitSet candidates = getCandidates(className);
    List<T> result = new ArrayList<>(candidates.cardinality());
    for (int i = candidates.nextSetBit(0); i >= 0; i = candidates.nextSetBit(i + 1)) {
      result.add(items.get(i));
    }
    return result;
  }

  /** The indices of the items whose class names can match the class. */
  private BitSet getCandidates(String className) {
    BitSet candidates = new BitSet();
    Node node = root;
    node.addPrefixesTo(candidates);
    for (int i = 0; i < className.length(); i++) {
      node = node.getChild(className.charAt(i));
      if (node == null) {
        return candidates;
      }
      node.addPrefixesTo(candidates);
    }
    node.addExactTo(candidates);
    return candidates;
  }

  private void recordMatcher(
      ProguardTypeMatcher matcher, int index, boolean materializeBackReferences) {
    if (matcher.hasSpecificType()) {
      recordExact(matcher.getSpecificType().toSourceString(), index);
    } else if (matcher.hasSpecificTypes()) {
      for (DexType type : matcher.getSpecificTypes()) {
        recordExact(type.toSourceString(), index);
      }
    } else if (matcher instanceof MatchTypePattern) {
      MatchTypePattern patternMatcher = (MatchTypePattern) matcher;
      String pattern =
          materializeBackReferences
              ? patternMatcher.getMaterializedPattern()
              : patternMatcher.getPattern();
      int wildcard = indexOfWildcard(pattern);
      if (wildcard < 0) {
        recordExact(pattern, index);
      } else {
        recordPrefix(pattern, wildcard, index);
      }
    } else {
      // Other matchers, such as *, ** and ***, can match any class.
      root.addPrefix(index);
    }
  }

  private void recordExact(String className, int index) {
    assert indexOfWildcard(className) < 0;
    Node node = root;
    for (int i = 0; i < className.length(); i++) {
      node = node.getOrCreateChild(className.charAt(i));
    }
    node.addExact(index);
  }

  private void recordPrefix(String pattern, int length, int index) {
    Node node = root;
    for (int i = 0; i < length; i++) {
      node = node.getOrCreateChild(pattern.charAt(i));
    }
    node.addPrefix(index);
  }

  /** The index of the first wildcard ({@code *}, {@code ?} or a back reference), or -1. */
  private static int indexOfWildcard(String pattern) {
    for (int i = 0; i < pattern.length(); i++) {
      char c = pattern.charAt(i);
      if (c == '*' || c == '?' || c == '<') {
        return i;
      }
    }
    return -1;
  }
}
