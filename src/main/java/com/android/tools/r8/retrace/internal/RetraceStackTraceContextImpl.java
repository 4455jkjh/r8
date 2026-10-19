// Copyright (c) 2021, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.dex.CompatByteBuffer;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.Condition;
import com.android.tools.r8.naming.mappinginformation.RewriteFrameMappingInformation.RewriteAction;
import com.android.tools.r8.references.ClassReference;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.utils.internal.SerializationUtils;
import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.OptionalInt;

public class RetraceStackTraceContextImpl implements RetraceStackTraceContext {

  private final ClassReference thrownException;
  private final OptionalInt rewritePosition;

  private RetraceStackTraceContextImpl(
      ClassReference thrownException, OptionalInt rewritePosition) {
    this.thrownException = thrownException;
    this.rewritePosition = rewritePosition;
  }

  public ClassReference getThrownException() {
    return thrownException;
  }

  RetraceStackTraceCurrentEvaluationInformation computeRewriteFrameInformation(
      List<MappedRange> mappedRanges) {
    if (mappedRanges == null || mappedRanges.isEmpty()) {
      return RetraceStackTraceCurrentEvaluationInformation.empty();
    }
    RetraceStackTraceCurrentEvaluationInformation.Builder builder =
        RetraceStackTraceCurrentEvaluationInformation.builder();
    for (MappedRange mappedRange : mappedRanges) {
      for (RewriteFrameMappingInformation rewriteInformation :
          mappedRange.getRewriteFrameMappingInformation()) {
        if (evaluateConditions(rewriteInformation.getConditions())) {
          for (RewriteAction action : rewriteInformation.getActions()) {
            action.evaluate(builder);
          }
        }
      }
    }
    return builder.build();
  }

  public boolean isEmpty() {
    return thrownException == null && !rewritePosition.isPresent();
  }

  public boolean hasRewritePosition() {
    return rewritePosition.isPresent();
  }

  public int getRewritePosition() {
    return rewritePosition.getAsInt();
  }

  @Override
  public byte[] serialize() {
    if (isEmpty()) {
      return new byte[0];
    }
    ByteArrayOutputStream temp = new ByteArrayOutputStream();
    DataOutputStream dataOutputStream = new DataOutputStream(temp);
    try {
      dataOutputStream.writeBoolean(thrownException != null);
      if (thrownException != null) {
        SerializationUtils.writeUTFOfIntSize(dataOutputStream, thrownException.getDescriptor());
      }
      dataOutputStream.writeBoolean(rewritePosition.isPresent());
      if (rewritePosition.isPresent()) {
        dataOutputStream.writeInt(rewritePosition.getAsInt());
      }
    } catch (IOException e) {
      throw new Unreachable(e);
    }
    return temp.toByteArray();
  }

  public static RetraceStackTraceContextImpl deserialize(byte[] bytes) {
    Builder builder = builder();
    CompatByteBuffer buffer = CompatByteBuffer.wrap(bytes);
    if (buffer.hasRemaining()) {
      if (buffer.get() != 0) {
        builder.setThrownException(Reference.classFromDescriptor(buffer.getUTFOfIntSize()));
      }
      if (buffer.get() != 0) {
        builder.setRewritePosition(OptionalInt.of(buffer.getInt()));
      }
      assert !buffer.hasRemaining();
    }
    return builder.build();
  }

  private boolean evaluateConditions(List<Condition> conditions) {
    for (Condition condition : conditions) {
      if (!condition.evaluate(this)) {
        return false;
      }
    }
    return true;
  }

  public static Builder builder() {
    return Builder.create();
  }

  public Builder buildFromThis() {
    return builder().setThrownException(thrownException).setRewritePosition(rewritePosition);
  }

  @Override
  public boolean equals(Object other) {
    if (this == other) {
      return true;
    }
    if (!(other instanceof RetraceStackTraceContextImpl)) {
      return false;
    }
    RetraceStackTraceContextImpl that = (RetraceStackTraceContextImpl) other;
    return Objects.equals(thrownException, that.thrownException)
        && Objects.equals(rewritePosition, that.rewritePosition);
  }

  @Override
  public int hashCode() {
    return Objects.hash(thrownException, rewritePosition);
  }

  public static class Builder {

    private ClassReference thrownException;
    private OptionalInt rewritePosition = OptionalInt.empty();

    private Builder() {}

    public Builder setThrownException(ClassReference thrownException) {
      this.thrownException = thrownException;
      return this;
    }

    public Builder setRewritePosition(OptionalInt rewritePosition) {
      this.rewritePosition = rewritePosition;
      return this;
    }

    public Builder clearRewritePosition() {
      this.rewritePosition = OptionalInt.empty();
      return this;
    }

    public RetraceStackTraceContextImpl build() {
      return new RetraceStackTraceContextImpl(thrownException, rewritePosition);
    }

    public static Builder create() {
      return new Builder();
    }
  }
}
