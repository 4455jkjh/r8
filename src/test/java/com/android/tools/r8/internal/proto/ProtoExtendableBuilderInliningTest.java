// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.utils.codeinspector.Matchers.isAbsent;
import static com.android.tools.r8.utils.codeinspector.Matchers.isAbstract;
import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.CoreMatchers.not;
import static org.hamcrest.MatcherAssert.assertThat;

import com.android.tools.r8.NeverInline;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.GeneratedMessageLite.ExtendableBuilder;
import com.google.protobuf.GeneratedMessageLite.ExtendableMessage;
import com.google.protobuf.MessageLite;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoExtendableBuilderInliningTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addProgramFiles(ProtoRuntime.LEGACY.getProgramFiles())
        .addKeepMainRule(Main.class)
        .enableInliningAnnotations()
        .enableProtoShrinking()
        .compile()
        .inspect(
            inspector -> {
              assertThat(inspector.clazz(ExtendableMessage.class), isPresent());
              ClassSubject extendableBuilder = inspector.clazz(ExtendableBuilder.class);
              assertThat(extendableBuilder, not(isAbstract()));
              assertThat(extendableBuilder.uniqueInstanceInitializer(), isPresent());
              assertThat(inspector.clazz(SampleExtendableMessage.Builder.class), isAbsent());
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("true");
  }

  static final class SampleExtendableMessage
      extends ExtendableMessage<SampleExtendableMessage, SampleExtendableMessage.Builder> {

    static final SampleExtendableMessage DEFAULT_INSTANCE = new SampleExtendableMessage();

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE:
          return new SampleExtendableMessage();
        case NEW_BUILDER:
          return new Builder();
        case BUILD_MESSAGE_INFO:
          return newMessageInfo(DEFAULT_INSTANCE, "\u0001\u0000", null);
        case GET_DEFAULT_INSTANCE:
          return DEFAULT_INSTANCE;
        case GET_MEMOIZED_IS_INITIALIZED:
          return (byte) 1;
        default:
          return null;
      }
    }

    static final class Builder extends ExtendableBuilder<SampleExtendableMessage, Builder> {

      @NeverInline
      Builder() {
        super(DEFAULT_INSTANCE);
      }
    }
  }

  static final class OtherMessage extends GeneratedMessageLite<OtherMessage, OtherMessage.Builder> {

    static final OtherMessage DEFAULT_INSTANCE = new OtherMessage();

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE:
          return new OtherMessage();
        case NEW_BUILDER:
          return new Builder();
        case BUILD_MESSAGE_INFO:
          return newMessageInfo(DEFAULT_INSTANCE, "\u0001\u0000", null);
        case GET_DEFAULT_INSTANCE:
          return DEFAULT_INSTANCE;
        case GET_MEMOIZED_IS_INITIALIZED:
          return (byte) 1;
        default:
          return null;
      }
    }

    static final class Builder extends GeneratedMessageLite.Builder<OtherMessage, Builder> {

      Builder() {
        super(DEFAULT_INSTANCE);
      }
    }
  }

  static class Main {

    static boolean alwaysFalse() {
      return false;
    }

    @NeverInline
    static MessageLite rebuild(MessageLite message) {
      return message.newBuilderForType().build();
    }

    public static void main(String[] args) {
      // Only live in the first round of tree shaking.
      if (alwaysFalse()) {
        System.out.println(new SampleExtendableMessage.Builder());
      }
      MessageLite message =
          System.currentTimeMillis() > 0
              ? SampleExtendableMessage.DEFAULT_INSTANCE
              : OtherMessage.DEFAULT_INSTANCE;
      System.out.println(rebuild(message) instanceof SampleExtendableMessage);
    }
  }
}
