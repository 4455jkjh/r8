// Copyright (c) 2018, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.android.tools.r8.debug.DebugTestConfig;
import com.android.tools.r8.utils.ListUtils;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Collection;

public abstract class TestBuilder<RR extends TestRunResult, T extends TestBuilder<RR, T>> {

  private final TestState state;

  public TestBuilder(TestState state) {
    this.state = state;
  }

  public TestState getState() {
    return state;
  }

  abstract T self();

  public <S> S map(ThrowableFunction<T, S> fn) {
    return fn.applyWithRuntimeException(self());
  }

  public T apply(ThrowableConsumer<T> fn) {
    fn.acceptWithRuntimeException(self());
    return self();
  }

  @Deprecated
  public abstract RR run(String mainClass) throws IOException, CompilationFailedException;

  public abstract RR run(TestRuntime runtime, String mainClass)
      throws IOException, CompilationFailedException;

  @Deprecated
  public RR run(Class mainClass) throws IOException, CompilationFailedException {
    return run(mainClass.getTypeName());
  }

  public RR run(TestRuntime runtime, Class mainClass)
      throws IOException, CompilationFailedException {
    return run(runtime, mainClass.getTypeName());
  }

  public abstract DebugTestConfig debugConfig();

  public abstract T addProgramFiles(Collection<Path> files);

  public abstract T addProgramClassFileData(Collection<byte[]> classes);

  public T addProgramClassFileData(byte[]... classes) {
    return addProgramClassFileData(Arrays.asList(classes));
  }

  public abstract T addProgramDexFileData(Collection<byte[]> data);

  public T addProgramDexFileData(byte[]... data) {
    return addProgramDexFileData(Arrays.asList(data));
  }

  public T addProgramClasses(Class<?>... classes) {
    return addProgramClasses(Arrays.asList(classes));
  }

  public T addProgramClasses(Collection<Class<?>> classes) {
    return addProgramFiles(getFilesForClasses(classes));
  }

  public T addProgramFiles(Path... files) {
    return addProgramFiles(Arrays.asList(files));
  }

  public T addProgramClassesAndInnerClasses(Class<?>... classes) throws IOException {
    return addProgramClassesAndInnerClasses(Arrays.asList(classes));
  }

  public T addProgramClassesAndInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramClasses(classes).addInnerClasses(classes);
  }

  public T addInnerClasses(Class<?>... classes) throws IOException {
    return addInnerClasses(Arrays.asList(classes));
  }

  public T addInnerClasses(Collection<Class<?>> classes) throws IOException {
    return addProgramFiles(getFilesForInnerClasses(classes));
  }

  public abstract T addLibraryFiles(Collection<Path> files);

  public T addLibraryClasses(Class<?>... classes) {
    return addLibraryClasses(Arrays.asList(classes));
  }

  public abstract T addLibraryClasses(Collection<Class<?>> classes);

  public T addLibraryFiles(Path... files) {
    return addLibraryFiles(Arrays.asList(files));
  }

  static Collection<Path> getFilesForClasses(Collection<Class<?>> classes) {
    return ListUtils.map(classes, ToolHelper::getClassFileForTestClass);
  }

  static Collection<Path> getFilesForInnerClasses(Collection<Class<?>> classes) throws IOException {
    return ToolHelper.getClassFilesForInnerClasses(classes);
  }
}
