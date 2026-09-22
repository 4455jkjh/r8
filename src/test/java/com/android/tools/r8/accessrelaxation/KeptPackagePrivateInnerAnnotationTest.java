// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.accessrelaxation;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPackagePrivate;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPublic;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.graph.ClassAccessFlags;
import com.android.tools.r8.graph.InnerClassAttribute;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class KeptPackagePrivateInnerAnnotationTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void testKeptByPackageWildcard() throws Exception {
    // Reproduces b/563871730: keeping both the package-private outer class ChannelHandlerMask
    // and the package-private inner annotation ChannelHandlerMask$Skip retains the InnerClasses
    // attribute, while AccessModifier does not promote Skip's ClassFile access_flags to public
    // (because Skip is kept), yet unconditionally promotes its InnerClasses attribute access flags
    // to public.
    // On JVM runtimes (JDK 16+), Class.getModifiers() reads the InnerClasses attribute (public),
    // causing java.lang.reflect.Proxy to generate the annotation proxy in jdk.proxy2.$ProxyN
    // instead of in ChannelHandlerMask's package, which then fails ClassLoader.defineClass1 with
    // IllegalAccessError because ChannelHandlerMask$Skip's ClassFile access_flags are still
    // package-private.
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ChannelHandlerMask.class, ChannelHandlerMask.Skip.class)
        .addKeepRuntimeVisibleAnnotations()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject outerSubject = inspector.clazz(ChannelHandlerMask.class);
              assertThat(outerSubject, isPresent());
              assertThat(outerSubject, isPackagePrivate());

              ClassSubject skipSubject = inspector.clazz(ChannelHandlerMask.Skip.class);
              assertThat(skipSubject, isPresent());
              // Top-level ClassFile access_flags remains package-private because Skip is kept.
              assertThat(skipSubject, isPackagePrivate());

              // InnerClassAttribute also remains package-private because Skip is kept.
              InnerClassAttribute innerClassAttribute =
                  skipSubject.getDexProgramClass().getInnerClassAttributeForThisClass();
              assertNotNull(innerClassAttribute);
              ClassAccessFlags innerAccessFlags =
                  ClassAccessFlags.fromSharedAccessFlags(innerClassAttribute.getAccess());
              assertFalse(innerAccessFlags.isPublic());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("isPublic=false", "isSkippable=true");
  }

  @Test
  public void testKeptWithAllowAccessModificationOnInnerAnnotation() throws Exception {
    // When the outer class ChannelHandlerMask (A) is kept (package-private) and the inner
    // annotation Skip (A$B) is kept with allowaccessmodification, R8 should update the
    // InnerClassAttribute for A$B on both A and A$B to public, while keeping the
    // InnerClassAttribute for A on both A and A$B package-private.
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addKeepMainRule(Main.class)
        .addKeepClassAndMembersRules(ChannelHandlerMask.class)
        .addKeepRules(
            "-keep,allowaccessmodification class "
                + ChannelHandlerMask.Skip.class.getTypeName()
                + " { *; }")
        .addKeepRuntimeVisibleAnnotations()
        .addKeepAttributeInnerClassesAndEnclosingMethod()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject outerSubject = inspector.clazz(ChannelHandlerMask.class);
              assertThat(outerSubject, isPresent());
              assertThat(outerSubject, isPackagePrivate());

              ClassSubject skipSubject = inspector.clazz(ChannelHandlerMask.Skip.class);
              assertThat(skipSubject, isPresent());
              assertThat(skipSubject, isPublic());

              InnerClassAttribute skipAttrOnSkip =
                  skipSubject.getDexProgramClass().getInnerClassAttributeForThisClass();
              assertNotNull(skipAttrOnSkip);
              ClassAccessFlags skipOnSkipFlags =
                  ClassAccessFlags.fromSharedAccessFlags(skipAttrOnSkip.getAccess());
              assertTrue(skipOnSkipFlags.isPublic());
              assertFalse(skipOnSkipFlags.isPrivate());
              assertFalse(skipOnSkipFlags.isProtected());

              if (parameters.isCfRuntime()) {
                InnerClassAttribute skipAttrOnOuter =
                    getInnerClassAttribute(outerSubject, skipSubject);
                assertNotNull(skipAttrOnOuter);
                assertTrue(
                    ClassAccessFlags.fromSharedAccessFlags(skipAttrOnOuter.getAccess()).isPublic());
              }
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("isPublic=true", "isSkippable=true");
  }

  private static InnerClassAttribute getInnerClassAttribute(
      ClassSubject holder, ClassSubject inner) {
    for (InnerClassAttribute attr : holder.getDexProgramClass().getInnerClasses()) {
      if (attr.getInner().isIdenticalTo(inner.getDexProgramClass().getType())) {
        return attr;
      }
    }
    return null;
  }

  static class ChannelHandlerMask {

    @Target(ElementType.METHOD)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Skip {}

    @Skip
    static void skippableMethod() {}

    static boolean isSkippable() throws Exception {
      Method m = ChannelHandlerMask.class.getDeclaredMethod("skippableMethod");
      return m.isAnnotationPresent(Skip.class);
    }
  }

  public static class Main {

    public static void main(String[] args) throws Exception {
      System.out.println(
          "isPublic=" + Modifier.isPublic(ChannelHandlerMask.Skip.class.getModifiers()));
      System.out.println("isSkippable=" + ChannelHandlerMask.isSkippable());
    }
  }
}
