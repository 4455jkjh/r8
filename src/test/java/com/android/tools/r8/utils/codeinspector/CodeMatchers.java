// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.utils.codeinspector;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.graph.DexMethod;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.FieldReference;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.utils.MethodReferenceUtils;
import java.util.Arrays;
import java.util.List;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.hamcrest.Description;
import org.hamcrest.Matcher;
import org.hamcrest.TypeSafeMatcher;

public class CodeMatchers {

  public static Matcher<MethodSubject> accessesField(FieldSubject targetSubject) {
    if (!targetSubject.isPresent()) {
      throw new IllegalArgumentException();
    }
    return accessesField(targetSubject.getField().getReference().asFieldReference());
  }

  public static Matcher<MethodSubject> accessesField(FieldReference target) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject.streamInstructions().anyMatch(isFieldAccessWithTarget(target));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("accesses field `" + target.toString() + "`");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> containsThrow() {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent()
            && subject.getMethod().hasCode()
            && subject.streamInstructions().anyMatch(InstructionSubject::isThrow);
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("contains throw");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> containsCheckCast(ClassReference classReference) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent()
            && subject.getMethod().hasCode()
            && subject
                .streamInstructions()
                .anyMatch(
                    instructionSubject ->
                        instructionSubject.isCheckCast(classReference.getTypeName()));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("contains checkcast");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> containsInstanceOf(ClassReference classReference) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent()
            && subject.getMethod().hasCode()
            && subject
                .streamInstructions()
                .anyMatch(
                    instructionSubject ->
                        instructionSubject.isInstanceOf(classReference.getTypeName()));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("contains instanceof");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> containsConstClass(ClassReference classReference) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent()
            && subject.getMethod().hasCode()
            && subject
                .streamInstructions()
                .anyMatch(
                    instructionSubject ->
                        instructionSubject.isConstClass(classReference.getTypeName()));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("contains constclass");
      }

      @Override
      public void describeMismatchSafely(MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> containsConstString(String string) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        return subject.isPresent()
            && subject.getMethod().hasCode()
            && subject
                .streamInstructions()
                .anyMatch(instructionSubject -> instructionSubject.isConstString(string));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("contains const-string");
      }

      @Override
      public void describeMismatchSafely(MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> instantiatesClass(Class<?> clazz) {
    return instantiatesClass(clazz.getTypeName());
  }

  public static Matcher<MethodSubject> instantiatesClass(String clazz) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject
            .streamInstructions()
            .anyMatch(instruction -> instruction.isNewInstance(clazz));
      }

      @Override
      public void describeTo(Description description) {
        description.appendText("instantiates class `" + clazz + "`");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethod(MethodSubject targetSubject) {
    return invokesMethod(
        () -> {
          assertThat(targetSubject, isPresent());
          return targetSubject.getFinalReference();
        });
  }

  public static Matcher<MethodSubject> invokesMethod(MethodReference targetReference) {
    return invokesMethod(() -> targetReference);
  }

  public static Matcher<MethodSubject> invokesMethod(
      Supplier<MethodReference> targetReferenceSupplier) {
    return new TypeSafeMatcher<MethodSubject>() {

      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        MethodReference targetReference = targetReferenceSupplier.get();
        return subject.streamInstructions().anyMatch(isInvokeWithTarget(targetReference));
      }

      @Override
      public void describeTo(Description description) {
        MethodReference targetReference = targetReferenceSupplier.get();
        description.appendText(
            "invokes method `" + MethodReferenceUtils.toSourceString(targetReference) + "`");
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethod(
      String returnType, String holderType, String methodName, List<String> parameterTypes) {
    return new TypeSafeMatcher<MethodSubject>() {
      @Override
      protected boolean matchesSafely(MethodSubject subject) {
        if (!subject.isPresent()) {
          return false;
        }
        if (!subject.getMethod().hasCode()) {
          return false;
        }
        return subject
            .streamInstructions()
            .anyMatch(isInvokeWithTarget(returnType, holderType, methodName, parameterTypes));
      }

      @Override
      public void describeTo(Description description) {
        StringBuilder text =
            new StringBuilder("invokes method `")
                .append(returnType != null ? returnType : "*")
                .append(" ")
                .append(holderType != null ? holderType : "*")
                .append(".")
                .append(methodName != null ? methodName : "*")
                .append("(");
        if (parameterTypes != null) {
          text.append(
              parameterTypes.stream()
                  .map(parameterType -> parameterType != null ? parameterType : "*")
                  .collect(Collectors.joining(", ")));
        } else {
          text.append("...");
        }
        text.append(")`");
        description.appendText(text.toString());
      }

      @Override
      public void describeMismatchSafely(final MethodSubject subject, Description description) {
        description.appendText("method did not");
      }
    };
  }

  public static Matcher<MethodSubject> invokesMethodWithHolderAndName(
      String holderType, String name) {
    return invokesMethod(null, holderType, name, null);
  }

  public static Matcher<MethodSubject> invokesMethodWithName(String name) {
    return invokesMethod(null, null, name, null);
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(MethodReference target) {
    return instruction ->
        instruction.isInvokeMethod() && instruction.getMethod().asMethodReference().equals(target);
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(MethodSubject target) {
    return isInvokeWithTarget(target.getMethod().getReference());
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(DexMethod target) {
    return isInvokeWithTarget(target.asMethodReference());
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(
      String returnType, String holderType, String methodName, String... parameterTypes) {
    return isInvokeWithTarget(returnType, holderType, methodName, Arrays.asList(parameterTypes));
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(
      String holderType, String methodName) {
    return isInvokeWithTarget(null, holderType, methodName, (List<String>) null);
  }

  public static Predicate<InstructionSubject> isInvokeWithTarget(
      String returnType, String holderType, String methodName, List<String> parameterTypes) {
    return instruction -> {
      if (!instruction.isInvoke()) {
        return false;
      }
      DexMethod invokedMethod = instruction.getMethod();
      if (returnType != null
          && !invokedMethod.getReturnType().toSourceString().equals(returnType)) {
        return false;
      }
      if (holderType != null
          && !invokedMethod.getHolderType().toSourceString().equals(holderType)) {
        return false;
      }
      if (methodName != null && !invokedMethod.getName().toSourceString().equals(methodName)) {
        return false;
      }
      if (parameterTypes != null) {
        if (parameterTypes.size() != invokedMethod.getArity()) {
          return false;
        }
        for (int i = 0; i < parameterTypes.size(); i++) {
          String parameterType = parameterTypes.get(i);
          if (parameterType != null
              && !invokedMethod.getParameter(i).toSourceString().equals(parameterType)) {
            return false;
          }
        }
      }
      return true;
    };
  }

  public static Predicate<InstructionSubject> isFieldAccessWithTarget(FieldReference target) {
    return instruction ->
        instruction.isFieldAccess() && instruction.getField().asFieldReference().equals(target);
  }
}
