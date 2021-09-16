// Copyright (c) 2019, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import com.android.tools.r8.errors.Unimplemented;
import com.android.tools.r8.naming.MemberNaming;
import com.android.tools.r8.naming.MemberNaming.FieldSignature;
import com.android.tools.r8.references.Reference;
import com.android.tools.r8.retrace.RetraceFieldElement;
import com.android.tools.r8.retrace.RetraceFieldResult;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.RetraceClassResultImpl.RetraceClassElementImpl;
import com.android.tools.r8.utils.DescriptorUtils;
import com.android.tools.r8.utils.Pair;
import java.util.List;
import java.util.stream.Stream;

public class RetraceFieldResultImpl implements RetraceFieldResult {

  private final RetraceClassResultImpl classResult;
  private final List<Pair<RetraceClassElementImpl, List<MemberNaming>>> memberNamings;
  private final FieldDefinition fieldDefinition;
  private final Retracer retracer;

  RetraceFieldResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassElementImpl, List<MemberNaming>>> memberNamings,
      FieldDefinition fieldDefinition,
      Retracer retracer) {
    this.classResult = classResult;
    this.memberNamings = memberNamings;
    this.fieldDefinition = fieldDefinition;
    this.retracer = retracer;
    assert classResult != null;
    assert !memberNamings.isEmpty();
  }

  @Override
  public Stream<RetraceFieldElement> stream() {
    return memberNamings.stream()
        .flatMap(
            mappedNamePair -> {
              RetraceClassElementImpl classElement = mappedNamePair.getFirst();
              List<MemberNaming> memberNamings = mappedNamePair.getSecond();
              if (memberNamings == null) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedFieldReferenceImpl.create(
                            fieldDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference()))));
              }
              return memberNamings.stream()
                  .map(
                      memberNaming -> {
                        FieldSignature fieldSignature =
                            memberNaming.getOriginalSignature().asFieldSignature();
                        RetracedClassReferenceImpl holder =
                            fieldSignature.isQualified()
                                ? RetracedClassReferenceImpl.create(
                                    Reference.classFromDescriptor(
                                        DescriptorUtils.javaTypeToDescriptor(
                                            fieldSignature.toHolderFromQualified())))
                                : classElement.getRetracedClass();
                        return new ElementImpl(
                            this,
                            classElement,
                            RetracedFieldReferenceImpl.create(
                                Reference.field(
                                    holder.getClassReference(),
                                    fieldSignature.isQualified()
                                        ? fieldSignature.toUnqualifiedName()
                                        : fieldSignature.name,
                                    Reference.typeFromTypeName(fieldSignature.type))));
                      });
            });
  }

  @Override
  public boolean isAmbiguous() {
    if (memberNamings.size() > 1) {
      return true;
    }
    List<MemberNaming> mappings = memberNamings.get(0).getSecond();
    if (mappings == null) {
      return false;
    }
    return mappings.size() > 1;
  }

  public static class ElementImpl implements RetraceFieldElement {

    private final RetracedFieldReferenceImpl fieldReference;
    private final RetraceFieldResultImpl retraceFieldResult;
    private final RetraceClassElementImpl classElement;

    private ElementImpl(
        RetraceFieldResultImpl retraceFieldResult,
        RetraceClassElementImpl classElement,
        RetracedFieldReferenceImpl fieldReference) {
      this.classElement = classElement;
      this.fieldReference = fieldReference;
      this.retraceFieldResult = retraceFieldResult;
    }

    @Override
    public boolean isCompilerSynthesized() {
      throw new Unimplemented("b/172014416");
    }

    @Override
    public boolean isUnknown() {
      return fieldReference.isUnknown();
    }

    @Override
    public RetracedFieldReferenceImpl getField() {
      return fieldReference;
    }

    @Override
    public RetraceFieldResult getRetraceResultContext() {
      return retraceFieldResult;
    }

    @Override
    public RetraceClassElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public RetracedSourceFile getSourceFile() {
      return classElement.getSourceFile();
    }
  }
}
