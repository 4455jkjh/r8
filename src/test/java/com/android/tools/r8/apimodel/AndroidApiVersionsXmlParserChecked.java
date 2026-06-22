// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.apimodel;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;

import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingException;
import com.android.tools.r8.apimodel.AndroidApiVersionsXmlParser.ParsingListener;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.utils.AndroidApiLevel;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeInspector;
import com.android.tools.r8.utils.codeinspector.FieldSubject;
import com.google.common.collect.ImmutableMap;
import java.io.IOException;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AndroidApiVersionsXmlParserChecked {

  /**
   * Wrapper of {@link AndroidApiVersionsXmlParser} with sanity checks against an android.jar and
   * max API.
   */
  public static List<ParsedApiClass> parse(
      Path xmlPath, Path androidJar, AndroidApiLevel maxApiLevel, boolean ignoreExemptionList)
      throws ParsingException, IOException {
    return new AndroidApiVersionsXmlParserChecked(androidJar, maxApiLevel, ignoreExemptionList)
        .parse(xmlPath);
  }

  private final AndroidApiLevel maxApiLevel;
  private final ParsingListener listener;
  private final CodeInspector inspector;
  private final Map<String, Integer> knownMissingClassesInJar;

  private AndroidApiVersionsXmlParserChecked(
      Path androidJar, AndroidApiLevel maxApiLevel, boolean ignoreExemptionList)
      throws IOException {
    this.maxApiLevel = maxApiLevel;
    this.listener = createListener();
    this.inspector = new CodeInspector(androidJar);
    this.knownMissingClassesInJar = getKnownMissingClasses(ignoreExemptionList);
  }

  /** Also updates the hit count for exemptions. */
  private boolean isKnownMissingInJar(String className) {
    if (knownMissingClassesInJar.containsKey(className)) {
      knownMissingClassesInJar.put(className, knownMissingClassesInJar.get(className) + 1);
      return true;
    } else {
      return false;
    }
  }

  private AndroidApiVersionsXmlParser.ParsingListener createListener() {
    return new ParsingListener() {
      @Override
      public void startedProcessingClass(ClassReference reference) {
        ClassSubject clazz = inspector.clazz(reference);
        String className = clazz.getOriginalTypeName();
        if (!clazz.isPresent()) {
          assertTrue(
              className + " should either be present in jar or marked as an exemption",
              isKnownMissingInJar(className));
        }
      }

      @Override
      public void skippingRemovedClass(ClassReference reference, AndroidApiLevel removedAt) {
        ClassSubject clazz = inspector.clazz(reference);
        assumeFalse(reference + " not expected in jar", clazz.isPresent());
        assert removedAt.isLessThanOrEqualTo(maxApiLevel)
            : removedAt + " is newer than max (" + maxApiLevel + ")";
      }

      @Override
      public void startedProcessingField(FieldTypelessReference reference) {
        ClassReference classReference = reference.getHolderClass();
        ClassSubject clazz = inspector.clazz(classReference);
        String className = clazz.getOriginalTypeName();
        if (clazz.isPresent()) {
          FieldSubject field = clazz.uniqueFieldWithOriginalName(reference.getFieldName());
          assertTrue(field + " should be present", field.isPresent());
        } else {
          assertTrue(
              className + " should either be present in jar or marked as an exemption",
              isKnownMissingInJar(className));
        }
      }

      @Override
      public void skippingRemovedField(
          FieldTypelessReference reference, AndroidApiLevel removedAt) {
        ClassReference classReference = reference.getHolderClass();
        ClassSubject clazz = inspector.clazz(classReference);
        assertTrue(classReference + " should be present", clazz.isPresent());
        FieldSubject field = clazz.uniqueFieldWithOriginalName(reference.getFieldName());
        assertFalse(field + " should be present", field.isPresent());
        assert removedAt.isLessThanOrEqualTo(maxApiLevel)
            : removedAt + " is newer than max (" + maxApiLevel + ")";
      }
    };
  }

  private List<ParsedApiClass> parse(Path xmlPath) throws ParsingException {
    List<ParsedApiClass> parsedClasses = AndroidApiVersionsXmlParser.parse(xmlPath, listener);
    knownMissingClassesInJar.forEach(
        (exemption, hitCount) ->
            assertTrue("Unused exemption: " + exemption + "(" + hitCount + ")", hitCount > 0));
    return parsedClasses;
  }

  private Map<String, Integer> getKnownMissingClasses(boolean ignoreExemptionList) {
    if (ignoreExemptionList) {
      return ImmutableMap.of();
    }
    Map<String, Integer> classes = new LinkedHashMap<>();
    classes.put("android.test.ActivityInstrumentationTestCase", 0);
    classes.put("android.test.ActivityInstrumentationTestCase2", 0);
    classes.put("android.test.ActivityTestCase", 0);
    classes.put("android.test.ActivityUnitTestCase", 0);
    classes.put("android.test.AndroidTestCase", 0);
    classes.put("android.test.AndroidTestRunner", 0);
    classes.put("android.test.ApplicationTestCase", 0);
    classes.put("android.test.AssertionFailedError", 0);
    classes.put("android.test.ComparisonFailure", 0);
    classes.put("android.test.FlakyTest", 0);
    classes.put("android.test.InstrumentationTestCase", 0);
    classes.put("android.test.InstrumentationTestRunner", 0);
    classes.put("android.test.InstrumentationTestSuite", 0);
    classes.put("android.test.IsolatedContext", 0);
    classes.put("android.test.LaunchPerformanceBase", 0);
    classes.put("android.test.LoaderTestCase", 0);
    classes.put("android.test.MoreAsserts", 0);
    classes.put("android.test.PerformanceTestCase", 0);
    classes.put("android.test.PerformanceTestCase$Intermediates", 0);
    classes.put("android.test.ProviderTestCase", 0);
    classes.put("android.test.ProviderTestCase2", 0);
    classes.put("android.test.RenamingDelegatingContext", 0);
    classes.put("android.test.RepetitiveTest", 0);
    classes.put("android.test.ServiceTestCase", 0);
    classes.put("android.test.SingleLaunchActivityTestCase", 0);
    classes.put("android.test.SyncBaseInstrumentation", 0);
    classes.put("android.test.TestSuiteProvider", 0);
    classes.put("android.test.TouchUtils", 0);
    classes.put("android.test.UiThreadTest", 0);
    classes.put("android.test.ViewAsserts", 0);
    classes.put("android.test.mock.MockAccountManager", 0);
    classes.put("android.test.mock.MockApplication", 0);
    classes.put("android.test.mock.MockContentProvider", 0);
    classes.put("android.test.mock.MockContentResolver", 0);
    classes.put("android.test.mock.MockContext", 0);
    classes.put("android.test.mock.MockCursor", 0);
    classes.put("android.test.mock.MockDialogInterface", 0);
    classes.put("android.test.mock.MockPackageManager", 0);
    classes.put("android.test.mock.MockResources", 0);
    classes.put("android.test.mock.MockService", 0);
    classes.put("android.test.suitebuilder.TestMethod", 0);
    classes.put("android.test.suitebuilder.TestSuiteBuilder", 0);
    classes.put("android.test.suitebuilder.TestSuiteBuilder$FailedToCreateTests", 0);
    classes.put("android.test.suitebuilder.UnitTestSuiteBuilder", 0);
    classes.put("android.test.suitebuilder.annotation.LargeTest", 0);
    classes.put("android.test.suitebuilder.annotation.MediumTest", 0);
    classes.put("android.test.suitebuilder.annotation.SmallTest", 0);
    classes.put("android.test.suitebuilder.annotation.Smoke", 0);
    classes.put("android.test.suitebuilder.annotation.Suppress", 0);
    classes.put("com.android.internal.util.Predicate", 0);
    classes.put("junit.framework.Assert", 0);
    classes.put("junit.framework.AssertionFailedError", 0);
    classes.put("junit.framework.ComparisonFailure", 0);
    classes.put("junit.framework.Protectable", 0);
    classes.put("junit.framework.Test", 0);
    classes.put("junit.framework.TestCase", 0);
    classes.put("junit.framework.TestFailure", 0);
    classes.put("junit.framework.TestListener", 0);
    classes.put("junit.framework.TestResult", 0);
    classes.put("junit.framework.TestSuite", 0);
    classes.put("junit.runner.BaseTestRunner", 0);
    classes.put("junit.runner.TestSuiteLoader", 0);
    classes.put("junit.runner.Version", 0);
    return classes;
  }
}
