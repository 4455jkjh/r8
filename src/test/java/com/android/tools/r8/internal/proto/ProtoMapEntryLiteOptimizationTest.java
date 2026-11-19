// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.MapEntryLite;
import com.google.protobuf.MapFieldLite;
import com.google.protobuf.WireFormat.FieldType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoMapEntryLiteOptimizationTest extends TestBase {

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
        .enableProtoShrinking()
        .compile()
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines("0");
  }

  static final class Container extends GeneratedMessageLite<Container, Container.Builder> {

    static final Container DEFAULT_INSTANCE = new Container();

    MapFieldLite<String, Container> entries_ = MapFieldLite.emptyMapField();

    @Override
    protected Object dynamicMethod(MethodToInvoke method, Object arg0, Object arg1) {
      switch (method) {
        case NEW_MUTABLE_INSTANCE:
          return new Container();
        case NEW_BUILDER:
          return new Builder();
        case BUILD_MESSAGE_INFO:
          return newMessageInfo(
              DEFAULT_INSTANCE,
              "\u0000\u0001\u0000\u0000\u0001\u0001\u0001\u0001\u0000\u0000\u00012",
              new Object[] {
                "entries_",
                MapEntryLite.newDefaultInstance(
                    FieldType.STRING, "", FieldType.MESSAGE, DEFAULT_INSTANCE)
              });
        case GET_DEFAULT_INSTANCE:
          return DEFAULT_INSTANCE;
        case GET_MEMOIZED_IS_INITIALIZED:
          return (byte) 1;
        default:
          return null;
      }
    }

    static final class Builder extends GeneratedMessageLite.Builder<Container, Builder> {

      Builder() {
        super(DEFAULT_INSTANCE);
      }
    }
  }

  static class Main {

    public static void main(String[] args) {
      System.out.println(
          Container.DEFAULT_INSTANCE.newBuilderForType().build().getSerializedSize());
    }
  }
}
