// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.graph;

import com.google.common.collect.Sets;
import java.util.Iterator;
import java.util.Set;
import java.util.function.Consumer;

public class ProgramPackage implements Iterable<DexProgramClass> {

  private final String packageDescriptor;
  private final Set<DexProgramClass> classes = Sets.newIdentityHashSet();

  public ProgramPackage(String packageDescriptor) {
    this.packageDescriptor = packageDescriptor;
  }

  public void add(DexProgramClass clazz) {
    assert clazz.getType().getPackageDescriptor().equals(packageDescriptor);
    classes.add(clazz);
  }

  public String getLastPackageName() {
    int index = packageDescriptor.lastIndexOf('/');
    if (index >= 0) {
      return packageDescriptor.substring(index + 1);
    }
    return packageDescriptor;
  }

  public void forEachClass(Consumer<DexProgramClass> consumer) {
    forEach(consumer);
  }

  public void forEachField(Consumer<ProgramField> consumer) {
    forEach(clazz -> clazz.forEachProgramField(consumer));
  }

  public void forEachMethod(Consumer<ProgramMethod> consumer) {
    forEach(clazz -> clazz.forEachProgramMethod(consumer));
  }

  @Override
  public Iterator<DexProgramClass> iterator() {
    return classes.iterator();
  }
}
