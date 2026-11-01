// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.internal.proto;

import static com.android.tools.r8.DiagnosticsMatcher.diagnosticException;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.google.protobuf.GeneratedMessageLite;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.MapEntryLite;
import com.google.protobuf.MapFieldLite;
import com.google.protobuf.WireFormat.FieldType;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class ProtoMapEntryLiteBoxedDefaultsTest extends TestBase {

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withDefaultDexRuntime().withAllApiLevels().build();
  }

  @Test(expected = CompilationFailedException.class)
  public void test() throws Exception {
    testForR8(parameters)
        .addInnerClasses(getClass())
        .addProgramFiles(ProtoRuntime.LEGACY.getProgramFiles())
        .addKeepMainRule(Main.class)
        .enableProtoShrinking()
        .compileWithExpectedDiagnostics(
            diagnostics ->
                diagnostics.assertErrorsMatch(diagnosticException(AssertionError.class)));
  }

  static final class Container extends GeneratedMessageLite<Container, Container.Builder> {

    static final Container DEFAULT_INSTANCE = new Container();

    // map<int64, double> longToDouble = 1;
    MapFieldLite<Long, Double> longToDouble_ = MapFieldLite.emptyMapField();

    // map<bool, float> boolToFloat = 2;
    MapFieldLite<Boolean, Float> boolToFloat_ = MapFieldLite.emptyMapField();

    // map<string, int64> stringToLong = 3;
    MapFieldLite<String, Long> stringToLong_ = MapFieldLite.emptyMapField();

    static Container parseFrom(byte[] data) throws InvalidProtocolBufferException {
      return parseFrom(DEFAULT_INSTANCE, data);
    }

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
              "\u0000\u0003\u0000\u0000\u0001\u0003\u0003\u0003\u0000\u0000\u00012\u00022\u00032",
              new Object[] {
                "longToDouble_",
                MapEntryLite.newDefaultInstance(FieldType.INT64, 0L, FieldType.DOUBLE, 0D),
                "boolToFloat_",
                MapEntryLite.newDefaultInstance(FieldType.BOOL, false, FieldType.FLOAT, 0F),
                "stringToLong_",
                MapEntryLite.newDefaultInstance(FieldType.STRING, "", FieldType.INT64, 0L)
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

    public static void main(String[] args) throws Exception {
      Container container = new Container();
      container.longToDouble_ = container.longToDouble_.mutableCopy();
      container.longToDouble_.put(1L, 2.5);
      container.boolToFloat_ = container.boolToFloat_.mutableCopy();
      container.boolToFloat_.put(true, 1.5f);
      container.stringToLong_ = container.stringToLong_.mutableCopy();
      container.stringToLong_.put("a", 3L);
      Container parsed = Container.parseFrom(container.toByteArray());
      System.out.println(parsed.longToDouble_);
      System.out.println(parsed.boolToFloat_);
      System.out.println(parsed.stringToLong_);
    }
  }
}
