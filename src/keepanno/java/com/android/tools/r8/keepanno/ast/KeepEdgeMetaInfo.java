// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.keepanno.ast;

import com.android.tools.r8.keepanno.keeprules.RulePrintingUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class KeepEdgeMetaInfo {

  public enum KeepEdgeVersion {
    UNKNOWN;

    public String toVersionString() {
      return name();
    }
  }

  private static final KeepEdgeMetaInfo NONE =
      new KeepEdgeMetaInfo(
          KeepEdgeVersion.UNKNOWN, KeepEdgeContext.none(), KeepEdgeDescription.empty());

  public static KeepEdgeMetaInfo none() {
    return NONE;
  }

  public static Builder builder() {
    return new Builder();
  }

  private final KeepEdgeVersion version;
  private final KeepEdgeContext context;
  private final KeepEdgeDescription description;

  private KeepEdgeMetaInfo(
      KeepEdgeVersion version, KeepEdgeContext context, KeepEdgeDescription description) {
    this.version = version;
    this.context = context;
    this.description = description;
  }

  public boolean hasDescription() {
    return !KeepEdgeDescription.empty().equals(description);
  }

  public String getDescriptionString() {
    return description.description;
  }

  public String getContextDescriptorString() {
    return context.getDescriptorString();
  }

  public boolean hasContext() {
    return !KeepEdgeContext.none().equals(context);
  }

  public boolean hasVersion() {
    return version != KeepEdgeVersion.UNKNOWN;
  }

  public KeepEdgeVersion getVersion() {
    return version;
  }

  public String toString() {
    List<String> props = new ArrayList<>(3);
    if (hasVersion()) {
      props.add("version=" + version);
    }
    if (hasContext()) {
      props.add("context=" + context.getDescriptorString());
    }
    if (hasDescription()) {
      props.add(
          "description=\"" + RulePrintingUtils.escapeLineBreaks(description.description) + "\"");
    }
    return "MetaInfo{" + String.join(", ", props) + "}";
  }

  public static class Builder {
    private KeepEdgeContext context = KeepEdgeContext.none();
    private KeepEdgeDescription description = KeepEdgeDescription.empty();

    public Builder setDescription(String description) {
      this.description = new KeepEdgeDescription(description);
      return this;
    }

    public Builder setContextFromClassDescriptor(String classDescriptor) {
      context = new KeepEdgeClassContext(classDescriptor);
      return this;
    }

    public Builder setContextFromMethodDescriptor(
        String classDescriptor, String methodName, String methodDescriptor) {
      context = new KeepEdgeMethodContext(classDescriptor, methodName, methodDescriptor);
      return this;
    }

    public Builder setContextFromFieldDescriptor(
        String classDescriptor, String fieldName, String fieldType) {
      context = new KeepEdgeFieldContext(classDescriptor, fieldName, fieldType);
      return this;
    }

    public KeepEdgeMetaInfo build() {
      if (context.equals(KeepEdgeContext.none())
          && description.equals(KeepEdgeDescription.empty())) {
        return none();
      }
      return new KeepEdgeMetaInfo(KeepEdgeVersion.UNKNOWN, context, description);
    }
  }

  private static class KeepEdgeContext {
    private static final KeepEdgeContext NONE = new KeepEdgeContext();

    public static KeepEdgeContext none() {
      return NONE;
    }

    private KeepEdgeContext() {}

    public String getDescriptorString() {
      throw new KeepEdgeException("Invalid attempt to get descriptor string from none context");
    }

    @Override
    public boolean equals(Object obj) {
      return this == obj;
    }

    @Override
    public int hashCode() {
      return System.identityHashCode(this);
    }
  }

  private static class KeepEdgeClassContext extends KeepEdgeContext {
    private final String classDescriptor;

    public KeepEdgeClassContext(String classDescriptor) {
      assert classDescriptor != null;
      this.classDescriptor = classDescriptor;
    }

    @Override
    public String getDescriptorString() {
      return classDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeClassContext)) {
        return false;
      }
      KeepEdgeClassContext that = (KeepEdgeClassContext) o;
      return classDescriptor.equals(that.classDescriptor);
    }

    @Override
    public int hashCode() {
      return classDescriptor.hashCode();
    }
  }

  private static class KeepEdgeMethodContext extends KeepEdgeContext {
    private final String classDescriptor;
    private final String methodName;
    private final String methodDescriptor;

    public KeepEdgeMethodContext(
        String classDescriptor, String methodName, String methodDescriptor) {
      assert classDescriptor != null;
      assert methodName != null;
      assert methodDescriptor != null;
      this.classDescriptor = classDescriptor;
      this.methodName = methodName;
      this.methodDescriptor = methodDescriptor;
    }

    @Override
    public String getDescriptorString() {
      return classDescriptor + methodName + methodDescriptor;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeMethodContext)) {
        return false;
      }
      KeepEdgeMethodContext that = (KeepEdgeMethodContext) o;
      return classDescriptor.equals(that.classDescriptor)
          && methodName.equals(that.methodName)
          && methodDescriptor.equals(that.methodDescriptor);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classDescriptor, methodName, methodDescriptor);
    }
  }

  private static class KeepEdgeFieldContext extends KeepEdgeContext {
    private final String classDescriptor;
    private final String fieldName;
    private final String fieldType;

    public KeepEdgeFieldContext(String classDescriptor, String fieldName, String fieldType) {
      this.classDescriptor = classDescriptor;
      this.fieldName = fieldName;
      this.fieldType = fieldType;
    }

    @Override
    public String getDescriptorString() {
      return classDescriptor + fieldName + ":" + fieldType;
    }

    @Override
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (!(o instanceof KeepEdgeFieldContext)) {
        return false;
      }
      KeepEdgeFieldContext that = (KeepEdgeFieldContext) o;
      return classDescriptor.equals(that.classDescriptor)
          && fieldName.equals(that.fieldName)
          && fieldType.equals(that.fieldType);
    }

    @Override
    public int hashCode() {
      return Objects.hash(classDescriptor, fieldName, fieldType);
    }
  }

  private static class KeepEdgeDescription {
    private static final KeepEdgeDescription EMPTY = new KeepEdgeDescription("");

    public static KeepEdgeDescription empty() {
      return EMPTY;
    }

    private final String description;

    public KeepEdgeDescription(String description) {
      assert description != null;
      this.description = description;
    }

    @Override
    @SuppressWarnings("EqualsGetClass")
    public boolean equals(Object o) {
      if (this == o) {
        return true;
      }
      if (o == null || getClass() != o.getClass()) {
        return false;
      }
      KeepEdgeDescription that = (KeepEdgeDescription) o;
      return description.equals(that.description);
    }

    @Override
    public int hashCode() {
      return description.hashCode();
    }

    @Override
    public String toString() {
      return description;
    }
  }
}
