// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import java.util.List;
import java.util.function.Consumer;
import org.objectweb.asm.AnnotationVisitor;

/** Base for a parser that does not have "properties" as such. */
public abstract class DeclarationParser<T> implements Parser<T> {

  abstract List<Parser<?>> parsers();

  private void ignore(Object arg) {}

  @Override
  public boolean isDeclared() {
    for (Parser<?> parser : parsers()) {
      if (parser.isDeclared()) {
        return true;
      }
    }
    return false;
  }

  @Override
  public boolean tryParse(String name, Object value, Consumer<T> setValue) {
    return tryParse(name, value);
  }

  @Override
  public boolean tryParseEnum(String name, String descriptor, String value, Consumer<T> setValue) {
    return tryParseEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor tryParseArray(
      String name, Consumer<T> setValue, AnnotationVisitor annotationVisitor) {
    return tryParseArray(name, annotationVisitor);
  }

  @Override
  public AnnotationVisitor tryParseAnnotation(
      String name, String descriptor, Consumer<T> setValue, AnnotationVisitor annotationVisitor) {
    return tryParseAnnotation(name, descriptor, annotationVisitor);
  }

  public boolean tryParse(String name, Object value) {
    for (Parser<?> parser : parsers()) {
      if (parser.tryParse(name, value, this::ignore)) {
        return true;
      }
    }
    return false;
  }

  public boolean tryParseEnum(String name, String descriptor, String value) {
    for (Parser<?> parser : parsers()) {
      if (parser.tryParseEnum(name, descriptor, value, this::ignore)) {
        return true;
      }
    }
    return false;
  }

  public AnnotationVisitor tryParseArray(String name, AnnotationVisitor annotationVisitor) {
    for (Parser<?> parser : parsers()) {
      AnnotationVisitor visitor = parser.tryParseArray(name, this::ignore, annotationVisitor);
      if (visitor != null) {
        return visitor;
      }
    }
    return annotationVisitor;
  }

  public AnnotationVisitor tryParseAnnotation(
      String name, String descriptor, AnnotationVisitor annotationVisitor) {
    for (Parser<?> parser : parsers()) {
      AnnotationVisitor visitor =
          parser.tryParseAnnotation(name, descriptor, this::ignore, annotationVisitor);
      if (visitor != null) {
        return visitor;
      }
    }
    return annotationVisitor;
  }
}
