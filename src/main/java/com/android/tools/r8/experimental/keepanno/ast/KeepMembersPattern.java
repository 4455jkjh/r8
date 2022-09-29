// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.experimental.keepanno.ast;

import java.util.ArrayList;
import java.util.List;

public abstract class KeepMembersPattern {

  public static Builder builder() {
    return new Builder();
  }

  public static KeepMembersPattern none() {
    return KeepMembersNonePattern.getInstance();
  }

  public static KeepMembersPattern all() {
    return KeepMembersAllPattern.getInstance();
  }

  public static class Builder {

    private boolean anyMethod = false;
    private boolean anyField = false;
    private List<KeepMethodPattern> methods = new ArrayList<>();
    private List<KeepFieldPattern> fields = new ArrayList<>();

    public Builder addMethodPattern(KeepMethodPattern methodPattern) {
      if (anyMethod) {
        return this;
      }
      if (methodPattern.isAnyMethod()) {
        methods.clear();
        anyMethod = true;
      }
      methods.add(methodPattern);
      return this;
    }

    public Builder addFieldPattern(KeepFieldPattern fieldPattern) {
      if (anyField) {
        return this;
      }
      if (fieldPattern.isAnyField()) {
        fields.clear();
        anyField = true;
      }
      fields.add(fieldPattern);
      return this;
    }

    public KeepMembersPattern build() {
      if (methods.isEmpty() && fields.isEmpty()) {
        return KeepMembersPattern.none();
      }
      if (anyMethod && anyField) {
        return KeepMembersPattern.all();
      }
      return new KeepMembersSomePattern(methods, fields);
    }
  }

  private static class KeepMembersAllPattern extends KeepMembersPattern {

    private static KeepMembersAllPattern INSTANCE = null;

    public static KeepMembersAllPattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepMembersAllPattern();
      }
      return INSTANCE;
    }

    @Override
    public boolean isAll() {
      return true;
    }

    @Override
    public boolean isNone() {
      return true;
    }
  }

  private static class KeepMembersNonePattern extends KeepMembersPattern {

    private static KeepMembersNonePattern INSTANCE = null;

    public static KeepMembersNonePattern getInstance() {
      if (INSTANCE == null) {
        INSTANCE = new KeepMembersNonePattern();
      }
      return INSTANCE;
    }

    @Override
    public boolean isAll() {
      return false;
    }

    @Override
    public boolean isNone() {
      return true;
    }
  }

  private static class KeepMembersSomePattern extends KeepMembersPattern {

    private final List<KeepMethodPattern> methods;
    private final List<KeepFieldPattern> fields;

    private KeepMembersSomePattern(List<KeepMethodPattern> methods, List<KeepFieldPattern> fields) {
      assert !methods.isEmpty() || !fields.isEmpty();
      this.methods = methods;
      this.fields = fields;
    }

    @Override
    public boolean isAll() {
      // Since there is at least one none-all field or method this is not a match all.
      return false;
    }

    @Override
    public boolean isNone() {
      // Since there is at least one field or method this is not a match none.
      return false;
    }
  }

  private KeepMembersPattern() {}

  public abstract boolean isAll();

  public abstract boolean isNone();
}
