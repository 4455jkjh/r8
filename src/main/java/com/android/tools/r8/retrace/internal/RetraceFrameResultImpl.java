// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.RetraceUtils.methodReferenceFromMappedRange;

import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedClassMemberReference;
import com.android.tools.r8.retrace.RetracedMethodReference;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.Retracer;
import com.android.tools.r8.retrace.internal.RetraceClassResultImpl.RetraceClassElementImpl;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

public class RetraceFrameResultImpl implements RetraceFrameResult {

  private final RetraceClassResultImpl classResult;
  private final MethodDefinition methodDefinition;
  private final int obfuscatedPosition;
  private final List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges;
  private final Retracer retracer;

  private OptionalBool isAmbiguousCache = OptionalBool.UNKNOWN;

  public RetraceFrameResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      int obfuscatedPosition,
      Retracer retracer) {
    this.classResult = classResult;
    this.methodDefinition = methodDefinition;
    this.obfuscatedPosition = obfuscatedPosition;
    this.mappedRanges = mappedRanges;
    this.retracer = retracer;
  }

  @Override
  public boolean isAmbiguous() {
    if (isAmbiguousCache.isUnknown()) {
      if (mappedRanges.size() > 1) {
        isAmbiguousCache = OptionalBool.TRUE;
        return true;
      }
      List<MappedRange> methodRanges = mappedRanges.get(0).getSecond();
      if (methodRanges != null && !methodRanges.isEmpty()) {
        MappedRange lastRange = methodRanges.get(0);
        for (MappedRange mappedRange : methodRanges) {
          if (mappedRange != lastRange
              && (mappedRange.minifiedRange == null
                  || !mappedRange.minifiedRange.equals(lastRange.minifiedRange))) {
            isAmbiguousCache = OptionalBool.TRUE;
            return true;
          }
        }
      }
      isAmbiguousCache = OptionalBool.FALSE;
    }
    assert !isAmbiguousCache.isUnknown();
    return isAmbiguousCache.isTrue();
  }

  @Override
  public Stream<RetraceFrameElement> stream() {
    return mappedRanges.stream()
        .flatMap(
            mappedRangePair -> {
              RetraceClassElementImpl classElement = mappedRangePair.getFirst();
              List<MappedRange> mappedRanges = mappedRangePair.getSecond();
              if (mappedRanges == null || mappedRanges.isEmpty()) {
                return Stream.of(
                    new ElementImpl(
                        this,
                        classElement,
                        RetracedMethodReferenceImpl.create(
                            methodDefinition.substituteHolder(
                                classElement.getRetracedClass().getClassReference())),
                        ImmutableList.of(),
                        obfuscatedPosition));
              }
              // Iterate over mapped ranges that may have different positions than specified.
              List<ElementImpl> ambiguousFrames = new ArrayList<>();
              Range minifiedRange = mappedRanges.get(0).minifiedRange;
              List<MappedRange> mappedRangesForElement = Lists.newArrayList(mappedRanges.get(0));
              for (int i = 1; i < mappedRanges.size(); i++) {
                MappedRange mappedRange = mappedRanges.get(i);
                if (minifiedRange == null || !minifiedRange.equals(mappedRange.minifiedRange)) {
                  // This is a new frame
                  ambiguousFrames.add(
                      elementFromMappedRanges(mappedRangesForElement, classElement));
                  mappedRangesForElement = new ArrayList<>();
                }
                mappedRangesForElement.add(mappedRange);
              }
              ambiguousFrames.add(elementFromMappedRanges(mappedRangesForElement, classElement));
              return ambiguousFrames.stream();
            });
  }

  private ElementImpl elementFromMappedRanges(
      List<MappedRange> mappedRangesForElement, RetraceClassElementImpl classElement) {
    MappedRange topFrame = mappedRangesForElement.get(0);
    MethodReference methodReference =
        methodReferenceFromMappedRange(
            topFrame, classElement.getRetracedClass().getClassReference());
    return new ElementImpl(
        this,
        classElement,
        getRetracedMethod(methodReference, topFrame, obfuscatedPosition),
        mappedRangesForElement,
        obfuscatedPosition);
  }

  private RetracedMethodReferenceImpl getRetracedMethod(
      MethodReference methodReference, MappedRange mappedRange, int obfuscatedPosition) {
    if (mappedRange.minifiedRange == null || (obfuscatedPosition == -1 && !isAmbiguous())) {
      int originalLineNumber = mappedRange.getFirstLineNumberOfOriginalRange();
      return RetracedMethodReferenceImpl.create(
          methodReference, originalLineNumber > 0 ? originalLineNumber : obfuscatedPosition);
    }
    if (!mappedRange.minifiedRange.contains(obfuscatedPosition)) {
      return RetracedMethodReferenceImpl.create(methodReference);
    }
    return RetracedMethodReferenceImpl.create(
        methodReference, mappedRange.getOriginalLineNumber(obfuscatedPosition));
  }

  public static class ElementImpl implements RetraceFrameElement {

    private final RetracedMethodReferenceImpl methodReference;
    private final RetraceFrameResultImpl retraceFrameResult;
    private final RetraceClassElementImpl classElement;
    private final List<MappedRange> mappedRanges;
    private final int obfuscatedPosition;

    public ElementImpl(
        RetraceFrameResultImpl retraceFrameResult,
        RetraceClassElementImpl classElement,
        RetracedMethodReferenceImpl methodReference,
        List<MappedRange> mappedRanges,
        int obfuscatedPosition) {
      this.methodReference = methodReference;
      this.retraceFrameResult = retraceFrameResult;
      this.classElement = classElement;
      this.mappedRanges = mappedRanges;
      this.obfuscatedPosition = obfuscatedPosition;
    }

    private boolean isOuterMostFrameCompilerSynthesized() {
      if (mappedRanges == null || mappedRanges.isEmpty()) {
        return false;
      }
      return ListUtils.last(mappedRanges).isCompilerSynthesized();
    }

    /**
     * Predicate determines if the *entire* frame is to be considered synthetic.
     *
     * <p>That is only true for a frame that has just one entry and that entry is synthetic.
     */
    @Override
    public boolean isCompilerSynthesized() {
      return getOuterFrames().isEmpty() && isOuterMostFrameCompilerSynthesized();
    }

    @Override
    public RetraceStackTraceContext getContext() {
      return RetraceStackTraceContext.getInitialContext();
    }

    @Override
    public RetraceFrameResult getRetraceResultContext() {
      return retraceFrameResult;
    }

    @Override
    public boolean isUnknown() {
      return methodReference.isUnknown();
    }

    @Override
    public RetracedMethodReferenceImpl getTopFrame() {
      return methodReference;
    }

    @Override
    public RetraceClassElementImpl getClassElement() {
      return classElement;
    }

    @Override
    public void visitAllFrames(BiConsumer<RetracedMethodReference, Integer> consumer) {
      int counter = 0;
      consumer.accept(getTopFrame(), counter++);
      for (RetracedMethodReferenceImpl outerFrame : getOuterFrames()) {
        consumer.accept(outerFrame, counter++);
      }
    }

    @Override
    public void visitNonCompilerSynthesizedFrames(
        BiConsumer<RetracedMethodReference, Integer> consumer) {
      int index = 0;
      RetracedMethodReferenceImpl prev = getTopFrame();
      for (RetracedMethodReferenceImpl next : getOuterFrames()) {
        consumer.accept(prev, index++);
        prev = next;
      }
      // We expect only the last frame, i.e., the outer-most caller to potentially be synthesized.
      // If not include it too.
      if (!isOuterMostFrameCompilerSynthesized()) {
        consumer.accept(prev, index);
      }
    }

    @Override
    public RetracedSourceFile getSourceFile(RetracedClassMemberReference frame) {
      return RetraceUtils.getSourceFileOrLookup(
          frame.getHolderClass(), classElement, retraceFrameResult.retracer);
    }

    @Override
    public List<RetracedMethodReferenceImpl> getOuterFrames() {
      if (mappedRanges == null) {
        return Collections.emptyList();
      }
      List<RetracedMethodReferenceImpl> outerFrames = new ArrayList<>();
      for (int i = 1; i < mappedRanges.size(); i++) {
        MappedRange mappedRange = mappedRanges.get(i);
        MethodReference methodReference =
            methodReferenceFromMappedRange(
                mappedRange, classElement.getRetracedClass().getClassReference());
        outerFrames.add(
            retraceFrameResult.getRetracedMethod(methodReference, mappedRange, obfuscatedPosition));
      }
      return outerFrames;
    }
  }
}
