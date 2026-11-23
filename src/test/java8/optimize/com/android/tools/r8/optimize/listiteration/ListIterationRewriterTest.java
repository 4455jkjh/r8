// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.optimize.listiteration;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoFieldTypeStrengthening;
import com.android.tools.r8.NoParameterTypeStrengthening;
import com.android.tools.r8.NoReturnTypeStrengthening;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.CodeMatchers;
import com.android.tools.r8.utils.codeinspector.FoundClassSubject;
import com.android.tools.r8.utils.codeinspector.InstructionSubject;
import com.android.tools.r8.utils.codeinspector.Matchers;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import com.android.tools.r8.utils.internal.ListUtils;
import java.util.AbstractList;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ListIterationRewriterTest extends TestBase {
  interface TestCase {
    String[] expectedOutput();

    void run();

    default String getName() {
      // Some runtimes prefix the outer class, and some do not.
      return getClass().getSimpleName().replaceFirst(".*\\$", "");
    }
  }

  public static class ArrayListMethodWithTryCatch implements TestCase {
    @NeverInline
    private static void helper(ArrayList<Integer> list) {
      try {
        for (Integer x : list) {
          System.out.println(x);
        }
      } catch (Throwable t) {
        System.out.println("error");
      }
    }

    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethodWithSubsequentLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1", "1", "2", "3"};
    }

    // Use ArrayList<Object> to avoid check-cast instructions.
    @NeverInline
    private static void helper(ArrayList<Object> list) {
      long now = System.currentTimeMillis();
      int i = 0;
      for (Object x : list) {
        for (; i < Math.min(3, now); ++i) {
          System.out.println(x);
        }
        System.out.println(x);
      }
    }

    @Override
    public void run() {
      ArrayList<Object> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethodWithSubsequentLoopAndTryCatch implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1", "1", "2", "3"};
    }

    // Use ArrayList<Object> to avoid check-cast instructions.
    @NeverInline
    private static void helper(ArrayList<Object> list) {
      long now = System.currentTimeMillis();
      int i = 0;
      try {
        for (Object x : list) {
          for (; i < Math.min(3, now); ++i) {
            System.out.println(x);
          }
          System.out.println(x);
        }
      } catch (Throwable t) {
        System.out.println("error");
      }
    }

    @Override
    public void run() {
      ArrayList<Object> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListMethod implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @NeverInline
    private static void helper(ArrayList<Integer> list) {
      for (Integer x : list) {
        System.out.println(x);
      }
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      helper(list);
    }
  }

  public static class ArrayListLocal implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (Integer x : list) {
        System.out.println(x);
      }
    }
  }

  public static class ArrayListLocalNoOutValue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "1", "1"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (Integer x : list) {
        System.out.println("1");
      }
    }
  }

  public static class NestedLocalArrayList implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"7", "8", "9", "1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        for (int y : list) {
          x += y;
        }
        System.out.println(x);
      }
      // Also test sibling loop.
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class LocalArrayListWithExtraConstInstr implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3", "true"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      boolean found = false;
      while (it.hasNext()) {
        Integer x = it.next();
        if (x == 3) {
          found = true;
        }
        System.out.println(x);
      }
      System.out.println(found);
    }
  }

  public static class LocalArrayListWithInvertedIfCondition implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (true) {
        // This will be an "if NEZ" rather than the normal "if EQZ".
        if (!it.hasNext()) {
          return;
        }
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class LoopWithContinue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @NeverInline
    private static List<Integer> getList() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      return list;
    }

    @Override
    public void run() {
      Collection<Integer> list = getList();
      for (Integer x : list) {
        if (x == null) {
          continue;
        }
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeCopyOnWrite implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      CopyOnWriteArrayList<Integer> list = new CopyOnWriteArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeLinkedList implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      LinkedList<Integer> list = new LinkedList<>();
      Collections.addAll(list, 1, 2, 3);
      for (int x : list) {
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeNonLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      if (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeCustomExtraUsers implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
      if (it.hasNext()) {
        throw new RuntimeException();
      }
    }
  }

  public static class DoNotOptimizeCustomLoopWithExtraInstruction implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"01", "02", "03"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        System.out.print("0");
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizePhiIterator implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = System.currentTimeMillis() <= 0 ? null : list.iterator();
      while (it.hasNext()) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeExtraConditionLoop implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext() && System.currentTimeMillis() > 0) {
        Integer x = it.next();
        System.out.println(x);
      }
    }
  }

  public static class DoNotOptimizeOnlyNoOutValue implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2"};
    }

    @NeverInline
    private static void noIteratorOutValue(ArrayList<Integer> list) {
      list.iterator();
      System.out.println("1");
    }

    @NeverInline
    private static void noHasNextOutValue(ArrayList<Integer> list) {
      Iterator<Integer> it = list.iterator();
      it.hasNext();
      System.out.println("2");
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      noIteratorOutValue(list);
      noHasNextOutValue(list);
    }
  }

  public static class DoNotOptimizeWrongReceiver implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3", "4"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it0 = list.iterator();
      Iterator<Integer> it1 = list.iterator();
      while (it0.hasNext()) {
        Integer x = it0.next();
        System.out.println(x);
      }
      if (it1.hasNext()) {
        System.out.println(4);
      }
    }
  }

  public static class DoNotOptimizeExtraNextPredecessors implements TestCase {
    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 1, 2, 3);
      Iterator<Integer> it = list.iterator();
      while (it.hasNext()) {
        Integer x;
        for (; ; ) {
          x = it.next();
          if (x != null) {
            break;
          }
        }
        System.out.println(x);
      }
    }
  }

  public static class CustomArrayListNoIterator implements TestCase {
    static class CustomArrayList extends ArrayList<Integer> {}

    @Override
    public String[] expectedOutput() {
      return new String[] {"1", "2", "3"};
    }

    @Override
    public void run() {
      ArrayList<Integer> list = new CustomArrayList();
      Collections.addAll(list, 1, 2, 3);
      for (Object x : list) {
        System.out.println(x);
      }
    }
  }

  public static class CustomArrayListWithIterator {
    public static String[] EXPECTED_OUTPUT = new String[] {"1", "2", "3"};

    static class CustomArrayListBase extends ArrayList<Integer> {}

    static class CustomArrayList extends CustomArrayListBase {
      @Override
      public Iterator<Integer> iterator() {
        return Arrays.asList(1, 2, 3).iterator();
      }
    }

    public static void main(String[] args) {
      ArrayList<Integer> list = new CustomArrayList();
      for (Object x : list) {
        System.out.println(x);
      }
    }
  }

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    // Optimization does affected by API / runtime.
    return getTestParameters().withDefaultRuntimes().withMinimumApiLevel().build();
  }

  @Parameter public TestParameters parameters;

  public static class RewritesMain {
    // Comment out cases from this list to debug individual tests.
    static final TestCase[] CASES =
        new TestCase[] {
          new ArrayListLocal(),
          new ArrayListLocalNoOutValue(),
          new NestedLocalArrayList(),
          new ArrayListMethod(),
          new ArrayListMethodWithTryCatch(),
          new ArrayListMethodWithSubsequentLoop(),
          new ArrayListMethodWithSubsequentLoopAndTryCatch(),
          new LocalArrayListWithExtraConstInstr(),
          new LocalArrayListWithInvertedIfCondition(),
          new LoopWithContinue(),
          new CustomArrayListNoIterator(),
        };

    @NeverInline
    public static void main(String[] args) {
      for (TestCase test : CASES) {
        System.out.println(test.getName());
        test.run();
      }
    }
  }

  private static String[] getExpectedTestOutput(TestCase[] testCases) {
    return Arrays.stream(testCases)
        .flatMap(x -> Stream.concat(Stream.of(x.getName()), Arrays.stream(x.expectedOutput())))
        .toArray(String[]::new);
  }

  @Test
  public void testRewritesD8() throws Exception {
    parameters.assumeDexRuntime();
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForD8(parameters)
        .addProgramClassesAndInnerClasses(ListUtils.map(RewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(RewritesMain.class, TestCase.class)
        .addOptionsModification(
            o -> {
              assertFalse(o.testing.listIterationRewritingRewriteCustomIterators);
              o.testing.listIterationRewritingRewriteCustomIterators = true;
              assertFalse(o.testing.listIterationRewritingRewriteInterfaces);
              o.testing.listIterationRewritingRewriteInterfaces = true;
            })
        .release()
        .compile()
        .run(parameters.getRuntime(), RewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(RewritesMain.CASES))
        .inspect(
            inspector -> inspector.forAllClasses(ListIterationRewriterTest::checkNoIteratorInvoke));
  }

  @Test
  public void testRewritesR8() throws Exception {
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForR8(parameters)
        .addProgramClassesAndInnerClasses(ListUtils.map(RewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(RewritesMain.class, TestCase.class)
        .addKeepMainRule(RewritesMain.class)
        .addDontObfuscate()
        .noHorizontalClassMerging()
        .enableInliningAnnotations()
        .compile()
        .run(parameters.getRuntime(), RewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(RewritesMain.CASES))
        .inspect(
            inspector -> inspector.forAllClasses(ListIterationRewriterTest::checkNoIteratorInvoke));
  }

  public static class NoRewritesMain {
    // Comment out cases from this list to debug individual tests.
    static final TestCase[] CASES =
        new TestCase[] {
          new DoNotOptimizeCopyOnWrite(),
          new DoNotOptimizeLinkedList(),
          new DoNotOptimizeNonLoop(),
          new DoNotOptimizeCustomExtraUsers(),
          new DoNotOptimizeCustomLoopWithExtraInstruction(),
          new DoNotOptimizePhiIterator(),
          new DoNotOptimizeExtraConditionLoop(),
          new DoNotOptimizeOnlyNoOutValue(),
          new DoNotOptimizeWrongReceiver(),
          new DoNotOptimizeExtraNextPredecessors(),
        };

    @NeverInline
    public static void main(String[] args) {
      for (TestCase test : CASES) {
        System.out.println(test.getName());
        test.run();
      }
    }
  }

  private static boolean isIteratorInvoke(InstructionSubject ins) {
    return ins.isInvoke() && ins.getMethod().name.toString().equals("iterator");
  }

  private static void checkIteratorInvokeExists(FoundClassSubject classSubject) {
    if (classSubject.isImplementing(TestCase.class)) {
      assertTrue(
          "No iterator() found in " + classSubject,
          classSubject.allMethods().stream()
              .flatMap(m -> m.streamInstructions())
              .anyMatch(ListIterationRewriterTest::isIteratorInvoke));
    }
  }

  private static void checkNoIteratorInvoke(FoundClassSubject classSubject) {
    if (classSubject.isImplementing(TestCase.class)) {
      assertTrue(
          "Found iterator() in " + classSubject,
          classSubject.allMethods().stream()
              .flatMap(m -> m.streamInstructions())
              .noneMatch(ListIterationRewriterTest::isIteratorInvoke));
    }
  }

  @Test
  public void testNoRewritesD8() throws Exception {
    parameters.assumeDexRuntime();
    // Run all test classes in one compilation for faster tests compared to compiling each one
    // separately.
    testForD8(parameters)
        .addOptionsModification(
            o -> {
              // Ensure tests do not pass due to the optimization being disabled.
              assertFalse(o.testing.listIterationRewritingRewriteCustomIterators);
              o.testing.listIterationRewritingRewriteCustomIterators = true;
              assertFalse(o.testing.listIterationRewritingRewriteInterfaces);
              o.testing.listIterationRewritingRewriteInterfaces = true;
            })
        .addProgramClassesAndInnerClasses(ListUtils.map(NoRewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(NoRewritesMain.class, TestCase.class)
        .release()
        .compile()
        .run(parameters.getRuntime(), NoRewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(NoRewritesMain.CASES))
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }

  @Test
  public void testNoRewritesR8() throws Exception {
    testForR8(parameters)
        .addOptionsModification(
            o -> {
              // Ensure tests do not pass due to the optimization being disabled.
              o.testing.listIterationRewritingRewriteCustomIterators = true;
            })
        .addProgramClassesAndInnerClasses(ListUtils.map(NoRewritesMain.CASES, TestCase::getClass))
        .addProgramClasses(NoRewritesMain.class, TestCase.class)
        .addKeepMainRule(NoRewritesMain.class)
        .addDontObfuscate()
        .enableInliningAnnotations()
        .noHorizontalClassMerging()
        .compile()
        .run(parameters.getRuntime(), NoRewritesMain.class)
        .assertSuccessWithOutputLines(getExpectedTestOutput(NoRewritesMain.CASES))
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }

  @Test
  public void testCustomIteratorThatDisablesOptimization() throws Exception {
    testForR8(parameters.getBackend())
        .setMinApi(parameters)
        .addProgramClassesAndInnerClasses(CustomArrayListWithIterator.class)
        .addKeepMainRule(CustomArrayListWithIterator.class)
        .compile()
        .run(parameters.getRuntime(), CustomArrayListWithIterator.class)
        .assertSuccessWithOutputLines(CustomArrayListWithIterator.EXPECTED_OUTPUT)
        .inspect(
            inspector ->
                inspector.forAllClasses(ListIterationRewriterTest::checkIteratorInvokeExists));
  }

  public static class DynamicUpperBoundRewrites {
    @NoFieldTypeStrengthening public static List<Integer> arrayListField = new ArrayList<>();

    static {
      Collections.addAll(arrayListField, 1, 2);
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static List<Integer> getArrayList(boolean empty) {
      ArrayList<Integer> list = new ArrayList<>();
      if (!empty) {
        Collections.addAll(list, 3, 4);
      }
      return list;
    }

    @NeverInline
    private static void iterateInstanceOf(List<Integer> list) {
      if (list instanceof ArrayList) {
        for (Integer x : list) {
          System.out.println(x);
        }
      } else {
        for (Integer x : list) {
          System.out.println(x);
        }
      }
    }

    @NeverInline
    private static void iteratePhi(boolean useField) {
      List<Integer> list = useField ? arrayListField : getArrayList(false);
      for (Integer x : list) {
        System.out.println(x);
      }
    }

    @NeverInline
    private static void iterateNonWellBehavedOrMixed(boolean linked) {
      List<Integer> mixed = linked ? new LinkedList<>(Arrays.asList(5)) : getArrayList(false);
      for (Integer x : mixed) {
        System.out.println(x);
      }
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static Collection<Integer> getArrayListAsCollection() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 6, 7);
      return list;
    }

    @NeverInline
    private static void iterateCollectionNotStaticList() {
      Collection<Integer> collection = getArrayListAsCollection();
      for (Integer x : collection) {
        System.out.println(x);
      }
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void iterateCollectionInstanceOf(Collection<Integer> collection) {
      if (collection instanceof ArrayList) {
        for (Integer x : collection) {
          System.out.println(x);
        }
      }
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static List<Integer> getNullableArrayList() {
      if (System.currentTimeMillis() <= 0) {
        return null;
      }
      return getArrayList(false);
    }

    @NeverInline
    private static void iterateNullableSequential() {
      List<Integer> list = getNullableArrayList();
      for (Integer x : list) {
        System.out.println(x);
      }
      for (Integer x : list) {
        System.out.println(x);
      }
    }

    @NeverInline
    @NoParameterTypeStrengthening
    private static void iterateListParam(List<Integer> list) {
      for (Integer x : list) {
        System.out.println(x);
      }
    }

    @NeverInline
    @NoReturnTypeStrengthening
    private static AbstractList<Integer> getAbstractArrayList() {
      ArrayList<Integer> list = new ArrayList<>();
      Collections.addAll(list, 9, 10);
      return list;
    }

    public static void main(String[] args) {
      for (Integer x : arrayListField) {
        System.out.println(x);
      }
      for (Integer x : getArrayList(System.currentTimeMillis() <= 0)) {
        System.out.println(x);
      }
      for (Integer x : getAbstractArrayList()) {
        System.out.println(x);
      }
      iterateInstanceOf(getArrayList(false));
      iterateInstanceOf(new LinkedList<>(Arrays.asList(8)));
      iteratePhi(System.currentTimeMillis() > 0);
      iteratePhi(System.currentTimeMillis() <= 0);
      iterateNonWellBehavedOrMixed(System.currentTimeMillis() <= 0);
      iterateNonWellBehavedOrMixed(System.currentTimeMillis() > 0);
      iterateCollectionNotStaticList();
      iterateCollectionInstanceOf(getArrayListAsCollection());
      iterateCollectionInstanceOf(Collections.singleton(11));
      iterateNullableSequential();
      iterateListParam(getArrayList(false));
    }
  }

  // Also test with devirtualization disabled to exercise receivers Devirtualizer cannot rewrite
  // (e.g. non-exact upper bounds or stripped library stubs omitting ArrayList.iterator()).
  @Test
  public void testDynamicUpperBoundType() throws Exception {
    runDynamicUpperBoundTest(false);
  }

  @Test
  public void testDynamicUpperBoundTypeWithDefaultDevirtualization() throws Exception {
    runDynamicUpperBoundTest(true);
  }

  private void runDynamicUpperBoundTest(boolean enableDevirtualization) throws Exception {
    testForR8(parameters)
        .addProgramClasses(DynamicUpperBoundRewrites.class)
        .addKeepMainRule(DynamicUpperBoundRewrites.class)
        .addOptionsModification(o -> o.enableDevirtualization = enableDevirtualization)
        .enableInliningAnnotations()
        .enableNoFieldTypeStrengtheningAnnotations()
        .enableNoParameterTypeStrengtheningAnnotations()
        .enableNoReturnTypeStrengtheningAnnotations()
        .compile()
        .run(parameters.getRuntime(), DynamicUpperBoundRewrites.class)
        .assertSuccessWithOutputLines(
            "1", "2", "3", "4", "9", "10", "3", "4", "8", "1", "2", "3", "4", "3", "4", "5", "6",
            "7", "6", "7", "3", "4", "3", "4", "3", "4")
        .inspect(
            inspector -> {
              ClassSubject clazz = inspector.clazz(DynamicUpperBoundRewrites.class);
              assertThat(clazz, Matchers.isPresent());
              assertEquals(
                  "java.util.List",
                  clazz.uniqueFieldWithOriginalName("arrayListField").getType().getTypeName());
              assertEquals(
                  "java.util.List",
                  clazz.uniqueMethodWithOriginalName("getArrayList").getReturnType().getTypeName());
              assertEquals(
                  "java.util.AbstractList",
                  clazz
                      .uniqueMethodWithOriginalName("getAbstractArrayList")
                      .getReturnType()
                      .getTypeName());
              assertEquals(
                  "java.util.Collection",
                  clazz
                      .uniqueMethodWithOriginalName("getArrayListAsCollection")
                      .getReturnType()
                      .getTypeName());
              assertEquals(
                  "java.util.List",
                  clazz
                      .uniqueMethodWithOriginalName("iterateListParam")
                      .getParameter(0)
                      .getTypeName());
              assertEquals(
                  "java.util.Collection",
                  clazz
                      .uniqueMethodWithOriginalName("iterateCollectionInstanceOf")
                      .getParameter(0)
                      .getTypeName());
              assertEquals(0, countIterators(clazz.mainMethod()));
              if (!enableDevirtualization) {
                // Only arrayListField and getArrayList() emit List.size(); getAbstractArrayList()
                // resolves invoke-virtual AbstractCollection.size().
                assertEquals(2, countListSizes(clazz.mainMethod()));
                assertEquals(
                    2,
                    countListSizes(
                        clazz.uniqueMethodWithOriginalName("iterateNullableSequential")));
              }
              assertEquals(0, countIterators(clazz.uniqueMethodWithOriginalName("iteratePhi")));
              assertEquals(
                  0,
                  countIterators(clazz.uniqueMethodWithOriginalName("iterateNullableSequential")));
              assertEquals(
                  0, countIterators(clazz.uniqueMethodWithOriginalName("iterateListParam")));
              assertEquals(
                  1, countIterators(clazz.uniqueMethodWithOriginalName("iterateInstanceOf")));
              assertEquals(
                  1, countListSizes(clazz.uniqueMethodWithOriginalName("iterateInstanceOf")));
              assertEquals(
                  1,
                  countIterators(
                      clazz.uniqueMethodWithOriginalName("iterateNonWellBehavedOrMixed")));
              assertEquals(
                  enableDevirtualization ? 0 : 1,
                  countIterators(
                      clazz.uniqueMethodWithOriginalName("iterateCollectionNotStaticList")));
              assertEquals(
                  1,
                  countIterators(
                      clazz.uniqueMethodWithOriginalName("iterateCollectionInstanceOf")));
            });
  }

  private static long countIterators(MethodSubject method) {
    assertThat(method, Matchers.isPresent());
    return method.streamInstructions().filter(ListIterationRewriterTest::isIteratorInvoke).count();
  }

  private static long countListSizes(MethodSubject method) {
    assertThat(method, Matchers.isPresent());
    return method
        .streamInstructions()
        .filter(CodeMatchers.isInvokeWithTarget("java.util.List", "size"))
        .count();
  }
}
