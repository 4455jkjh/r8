// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepradius.ui;

import static com.microsoft.playwright.assertions.PlaywrightAssertions.assertThat;

import com.microsoft.playwright.Locator;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.options.BoundingBox;

public class KeepRadiusInspector {

  private final Page page;

  public KeepRadiusInspector(Page page) {
    this.page = page;
  }

  public KeepRadiusInspector assertTitle(String expected) {
    assertThat(page).hasTitle(expected);
    return this;
  }

  public KeepRadiusInspector assertTotalObfuscationNotEmpty() {
    assertThat(page.locator("#total-obfuscation")).not().isEmpty();
    return this;
  }

  public KeepRadiusInspector clickLensTab(String lens) {
    page.click("#lens-tabs button[data-lens=\"" + lens + "\"]");
    return this;
  }

  public KeepRadiusInspector search(String term) {
    BoundingBox box = page.locator("#search-input").boundingBox();
    if (box == null || box.width == 0) {
      page.click("#search-icon-btn");
    }
    page.fill("#search-input", term);
    return this;
  }

  public KeepRadiusInspector clickRowWithText(String text) {
    page.locator("#table-data td")
        .filter(new Locator.FilterOptions().setHasText(text))
        .first()
        .click();
    return this;
  }

  public KeepRadiusInspector assertTableContains(String text) {
    assertThat(page.locator("#table-data")).containsText(text);
    return this;
  }

  public KeepRadiusInspector assertTableNotContains(String text) {
    assertThat(page.locator("#table-data")).not().containsText(text);
    return this;
  }

  public KeepRadiusInspector assertVisible(String selector) {
    assertThat(page.locator(selector)).isVisible();
    return this;
  }

  public KeepRadiusInspector assertHidden(String selector) {
    assertThat(page.locator(selector)).isHidden();
    return this;
  }

  public KeepRadiusInspector assertTableRowsCount(int expectedCount) {
    assertThat(page.locator("#table-data tr")).hasCount(expectedCount);
    return this;
  }

  public KeepRadiusInspector assertDetailsClassesContains(String className) {
    assertThat(page.locator("#details-classes-content")).containsText(className);
    return this;
  }

  public KeepRadiusInspector clickDetailsBackToSummary() {
    page.click("#details-back-to-summary");
    return this;
  }
}
