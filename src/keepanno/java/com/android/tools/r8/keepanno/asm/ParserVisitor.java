// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.keepanno.asm;

import com.android.tools.r8.keepanno.ast.ParsingContext.AnnotationParsingContext;
import java.util.Collections;
import java.util.List;
import org.objectweb.asm.AnnotationVisitor;

/** Convert parser(s) into an annotation visitor. */
public class ParserVisitor extends AnnotationVisitorBase {

  private final List<Parser<?>> parsers;
  private final Runnable onVisitEnd;

  public ParserVisitor(
      AnnotationParsingContext parsingContext,
      List<Parser<?>> parsers,
      Runnable onVisitEnd,
      AnnotationVisitor annotationVisitor) {
    super(parsingContext, annotationVisitor);
    this.parsers = parsers;
    this.onVisitEnd = onVisitEnd;
  }

  public ParserVisitor(
      AnnotationParsingContext parsingContext,
      Parser<?> parser,
      Runnable onVisitEnd,
      AnnotationVisitor annotationVisitor) {
    this(parsingContext, Collections.singletonList(parser), onVisitEnd, annotationVisitor);
  }

  private <T> void ignore(T unused) {}

  @Override
  public void visit(String name, Object value) {
    super.visit(name, value);
    for (Parser<?> parser : parsers) {
      if (parser.tryParse(name, value, this::ignore)) {
        return;
      }
    }
    unhandledValue(name, value);
  }

  @Override
  public AnnotationVisitor visitArray(String name) {
    AnnotationVisitor annotationVisitor = super.visitArray(name);
    for (Parser<?> parser : parsers) {
      AnnotationVisitor visitor = parser.tryParseArray(name, this::ignore, annotationVisitor);
      if (visitor != null) {
        return visitor;
      }
    }
    return unhandledArray(name);
  }

  @Override
  public void visitEnum(String name, String descriptor, String value) {
    super.visitEnum(name, descriptor, value);
    for (Parser<?> parser : parsers) {
      if (parser.tryParseEnum(name, descriptor, value, this::ignore)) {
        return;
      }
    }
    unhandledEnum(name, descriptor, value);
  }

  @Override
  public AnnotationVisitor visitAnnotation(String name, String descriptor) {
    AnnotationVisitor annotationVisitor = super.visitAnnotation(name, descriptor);
    for (Parser<?> parser : parsers) {
      AnnotationVisitor visitor =
          parser.tryParseAnnotation(name, descriptor, this::ignore, annotationVisitor);
      if (visitor != null) {
        return visitor;
      }
    }
    return unhandledAnnotation(name, descriptor);
  }

  @Override
  public void visitEnd() {
    super.visitEnd();
    onVisitEnd.run();
  }
}
