// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.resourceshrinker;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.regex.Pattern;
import org.junit.Test;

public class PossibleResourcesMarkerTest {

  @Test
  public void testSimpleConversion() {
    String regex = PossibleResourcesMarker.convertFormatStringToRegexp("ic_foo_%d");
    assertEquals("\\Qic_foo_\\E\\p{Digit}++", regex);
    Pattern pattern = Pattern.compile(regex);
    assertTrue(pattern.matcher("ic_foo_123").matches());
    assertFalse(pattern.matcher("ic_foo_").matches());
    assertFalse(pattern.matcher("ic_foo_abc").matches());
  }

  @Test
  public void testMultipleDifferentConversions() {
    String regex = PossibleResourcesMarker.convertFormatStringToRegexp("ic_%d_foo_%s");
    assertEquals("\\Qic_\\E\\p{Digit}++\\Q_foo_\\E.*", regex);
    Pattern pattern = Pattern.compile(regex);
    assertTrue(pattern.matcher("ic_123_foo_bar").matches());
    assertTrue(pattern.matcher("ic_1_foo_").matches());
  }

  @Test
  public void testCollapseIdenticalAdjacentConversions() {
    String regex = PossibleResourcesMarker.convertFormatStringToRegexp("ic_%d%d%d");
    assertEquals("\\Qic_\\E\\p{Digit}++", regex);

    // Fixed-length placeholders like %c or %% should not collapse
    assertEquals("\\Qic_\\E..", PossibleResourcesMarker.convertFormatStringToRegexp("ic_%c%c"));
    assertEquals("\\Qic_\\E%%", PossibleResourcesMarker.convertFormatStringToRegexp("ic_%%%%"));
  }

  @Test(timeout = 1000)
  public void testComplexFormatStringMitigated() {
    // A long format string containing many adjacent digit conversions
    String complexFormat = "a%d%d%d%d%d%d%d%d%d%d%d%d%d%d%d%d%d%d%d%dz";
    String regex = PossibleResourcesMarker.convertFormatStringToRegexp(complexFormat);

    Pattern pattern = Pattern.compile(regex);
    String subject =
        "a000000000000000000000000000000000000000000000000000000000000"; // no 'z' at the end

    // Should return false almost instantly.
    // Without the fix, this call would hang/take extremely long due to backtracking on the multiple
    // adjacent specs.
    assertFalse(pattern.matcher(subject).matches());
  }

  @Test
  public void testPossessiveQuantifiersForAllTypes() {
    assertEquals(
        "\\Qprefix\\E\\p{XDigit}++",
        PossibleResourcesMarker.convertFormatStringToRegexp("prefix%x"));
    assertEquals(
        "\\Qprefix\\E\\p{Digit}++",
        PossibleResourcesMarker.convertFormatStringToRegexp("prefix%o"));
    assertEquals(
        "\\Qprefix\\E(?>null|\\p{XDigit}++)",
        PossibleResourcesMarker.convertFormatStringToRegexp("prefix%h"));
    assertEquals(
        "\\Qprefix\\E-?[\\p{XDigit},.]++",
        PossibleResourcesMarker.convertFormatStringToRegexp("prefix%f"));
  }

  @Test
  public void testMatchingEquivalence() {
    // Test Case 1: Simple single conversion
    // Old: \Qic_foo_\E\p{Digit}+
    // New: \Qic_foo_\E\p{Digit}++
    verifyEquivalence(
        "\\Qic_foo_\\E\\p{Digit}+",
        "\\Qic_foo_\\E\\p{Digit}++",
        "ic_foo_1",
        "ic_foo_123",
        "ic_foo_",
        "ic_foo_abc",
        "ic_foo_123a");

    // Test Case 2: Multiple conversions with separator
    // Old: \Qic_\E\p{Digit}+\Q_bar_\E.*
    // New: \Qic_\E\p{Digit}++\Q_bar_\E.*
    verifyEquivalence(
        "\\Qic_\\E\\p{Digit}+\\Q_bar_\\E.*",
        "\\Qic_\\E\\p{Digit}++\\Q_bar_\\E.*",
        "ic_1_bar_a",
        "ic_123_bar_abc",
        "ic__bar_a",
        "ic_1a_bar_a");

    // Test Case 3: Collapsed adjacent conversions
    // Old: \Qic_\E\p{Digit}+\p{Digit}+\p{Digit}+
    // New: \Qic_\E\p{Digit}++
    // Note: The new collapsed regex is a conservative superset. Any string matching the old
    // (at least 3 digits) will also match the new (at least 1 digit). Let's verify this.
    verifySuperset(
        "\\Qic_\\E\\p{Digit}+\\p{Digit}+\\p{Digit}+",
        "\\Qic_\\E\\p{Digit}++",
        "ic_1",
        "ic_12",
        "ic_123",
        "ic_1234",
        "ic_abc");
  }

  private void verifyEquivalence(String oldRegex, String newRegex, String... subjects) {
    Pattern oldPattern = Pattern.compile(oldRegex);
    Pattern newPattern = Pattern.compile(newRegex);
    for (String subject : subjects) {
      boolean oldMatches = oldPattern.matcher(subject).matches();
      boolean newMatches = newPattern.matcher(subject).matches();
      assertEquals("Mismatch for subject: " + subject, oldMatches, newMatches);
    }
  }

  private void verifySuperset(String oldRegex, String newRegex, String... subjects) {
    Pattern oldPattern = Pattern.compile(oldRegex);
    Pattern newPattern = Pattern.compile(newRegex);
    for (String subject : subjects) {
      boolean oldMatches = oldPattern.matcher(subject).matches();
      boolean newMatches = newPattern.matcher(subject).matches();
      if (oldMatches) {
        assertTrue(
            "New pattern must match since old pattern matched for subject: " + subject, newMatches);
      }
    }
  }
}
