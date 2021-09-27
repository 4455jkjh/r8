// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace.internal;

import static com.android.tools.r8.retrace.internal.RetraceUtils.methodReferenceFromMappedRange;

import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.naming.ClassNamingForNameMapper.MappedRange;
import com.android.tools.r8.naming.Range;
import com.android.tools.r8.references.MethodReference;
import com.android.tools.r8.retrace.RetraceFrameElement;
import com.android.tools.r8.retrace.RetraceFrameResult;
import com.android.tools.r8.retrace.RetraceInvalidRewriteFrameDiagnostics;
import com.android.tools.r8.retrace.RetraceStackTraceContext;
import com.android.tools.r8.retrace.RetracedClassMemberReference;
import com.android.tools.r8.retrace.RetracedSingleFrame;
import com.android.tools.r8.retrace.RetracedSourceFile;
import com.android.tools.r8.retrace.internal.RetraceClassResultImpl.RetraceClassElementImpl;
import com.android.tools.r8.utils.ListUtils;
import com.android.tools.r8.utils.OptionalBool;
import com.android.tools.r8.utils.Pair;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.Stream;

class RetraceFrameResultImpl implements RetraceFrameResult {

  private final RetraceClassResultImpl classResult;
  private final MethodDefinition methodDefinition;
  private final OptionalInt obfuscatedPosition;
  private final List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges;
  private final RetracerImpl retracer;
  private final RetraceStackTraceContextImpl context;

  private OptionalBool isAmbiguousCache = OptionalBool.UNKNOWN;

  public RetraceFrameResultImpl(
      RetraceClassResultImpl classResult,
      List<Pair<RetraceClassElementImpl, List<MappedRange>>> mappedRanges,
      MethodDefinition methodDefinition,
      OptionalInt obfuscatedPosition,
      RetracerImpl retracer,
      RetraceStackTraceContextImpl context) {
    this.classResult = classResult;
    this.methodDefinition = methodDefinition;
    this.obfuscatedPosition = obfuscatedPosition;
    this.mappedRanges = mappedRanges;
    this.retracer = retracer;
    this.context = context;
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
                        obfuscatedPosition,
                        retracer,
                        context));
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
        obfuscatedPosition,
        retracer,
        context);
  }

  private RetracedMethodReferenceImpl getRetracedMethod(
      MethodReference methodReference, MappedRange mappedRange, OptionalInt obfuscatedPosition) {
    if (mappedRange.minifiedRange == null
        || (obfuscatedPosition.orElse(-1) == -1 && !isAmbiguous())) {
      int originalLineNumber = mappedRange.getFirstLineNumberOfOriginalRange();
      if (originalLineNumber > 0) {
        return RetracedMethodReferenceImpl.create(methodReference, originalLineNumber);
      } else {
        return RetracedMethodReferenceImpl.create(methodReference);
      }
    }
    if (!obfuscatedPosition.isPresent()
        || !mappedRange.minifiedRange.contains(obfuscatedPosition.getAsInt())) {
      return RetracedMethodReferenceImpl.create(methodReference);
    }
    return RetracedMethodReferenceImpl.create(
        methodReference, mappedRange.getOriginalLineNumber(obfuscatedPosition.getAsInt()));
  }

  public static class ElementImpl implements RetraceFrameElement {

    private final RetracedMethodReferenceImpl methodReference;
    private final RetraceFrameResultImpl retraceFrameResult;
    private final RetraceClassElementImpl classElement;
    private final List<MappedRange> mappedRanges;
    private final OptionalInt obfuscatedPosition;
    private final RetracerImpl retracer;
    private final RetraceStackTraceContextImpl context;

    ElementImpl(
        RetraceFrameResultImpl retraceFrameResult,
        RetraceClassElementImpl classElement,
        RetracedMethodReferenceImpl methodReference,
        List<MappedRange> mappedRanges,
        OptionalInt obfuscatedPosition,
        RetracerImpl retracer,
        RetraceStackTraceContextImpl context) {
      this.methodReference = methodReference;
      this.retraceFrameResult = retraceFrameResult;
      this.classElement = classElement;
      this.mappedRanges = mappedRanges;
      this.obfuscatedPosition = obfuscatedPosition;
      this.retracer = retracer;
      this.context = context;
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
    public void visitFrames(Consumer<RetracedSingleFrame> consumer) {
      if (mappedRanges == null || mappedRanges.isEmpty()) {
        consumer.accept(RetracedSingleFrameImpl.create(getTopFrame(), 0));
        return;
      }
      int counter = 0;
      consumer.accept(RetracedSingleFrameImpl.create(getTopFrame(), counter++));
      for (RetracedMethodReferenceImpl outerFrame : getOuterFrames()) {
        consumer.accept(RetracedSingleFrameImpl.create(outerFrame, counter++));
      }
    }

    @Override
    public Stream<RetracedSingleFrame> forEachFrame() {
      Stream.Builder<RetracedSingleFrame> builder = Stream.builder();
      visitFrames(builder::add);
      return builder.build();
    }

    @Override
    public void visitRewrittenFrames(
        RetraceStackTraceContext context, Consumer<RetracedSingleFrame> consumer) {
      RetraceStackTraceContextImpl contextImpl = (RetraceStackTraceContextImpl) context;
      RetraceStackTraceCurrentEvaluationInformation currentFrameInformation =
          context == null
              ? RetraceStackTraceCurrentEvaluationInformation.empty()
              : contextImpl.computeRewritingInformation(mappedRanges);
      int index = 0;
      int numberOfFramesToRemove = currentFrameInformation.getRemoveInnerFrames();
      int totalNumberOfFrames =
          (mappedRanges == null || mappedRanges.isEmpty()) ? 1 : mappedRanges.size();
      if (numberOfFramesToRemove > totalNumberOfFrames) {
        DiagnosticsHandler diagnosticsHandler = retracer.getDiagnosticsHandler();
        diagnosticsHandler.warning(
            RetraceInvalidRewriteFrameDiagnostics.create(
                numberOfFramesToRemove, getTopFrame().asKnown().toString()));
        numberOfFramesToRemove = 0;
      }
      RetracedMethodReferenceImpl prev = getTopFrame();
      List<RetracedMethodReferenceImpl> outerFrames = getOuterFrames();
      for (RetracedMethodReferenceImpl next : outerFrames) {
        if (numberOfFramesToRemove-- <= 0) {
          consumer.accept(RetracedSingleFrameImpl.create(prev, index++));
        }
        prev = next;
      }
      // We expect only the last frame, i.e., the outer-most caller to potentially be synthesized.
      // If not include it too.
      if (numberOfFramesToRemove <= 0 && !isOuterMostFrameCompilerSynthesized()) {
        consumer.accept(RetracedSingleFrameImpl.create(prev, index));
      }
    }

    @Override
    public Stream<RetracedSingleFrame> forEachRewrittenFrame(RetraceStackTraceContext context) {
      Stream.Builder<RetracedSingleFrame> builder = Stream.builder();
      visitRewrittenFrames(context, builder::add);
      return builder.build();
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
        outerFrames.add(getMethodReferenceFromMappedRange(mappedRanges.get(i)));
      }
      return outerFrames;
    }

    private RetracedMethodReferenceImpl getMethodReferenceFromMappedRange(MappedRange mappedRange) {
      MethodReference methodReference =
          methodReferenceFromMappedRange(
              mappedRange, classElement.getRetracedClass().getClassReference());
      return retraceFrameResult.getRetracedMethod(methodReference, mappedRange, obfuscatedPosition);
    }

    @Override
    public RetraceStackTraceContext getContext() {
      // This will change when supporting outline frames.
      return RetraceStackTraceContext.empty();
    }
  }
}
