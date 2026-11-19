// Copyright (c) 2020, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.shaking;

import com.android.tools.r8.graph.DexField;
import com.android.tools.r8.graph.DexProgramClass;
import com.android.tools.r8.graph.MutableFieldAccessInfo;
import com.android.tools.r8.graph.MutableFieldAccessInfoCollection;
import com.android.tools.r8.utils.internal.SetUtils;
import java.util.Set;

/** Used to mutate AppInfoWithLiveness between waves. */
public class AppInfoWithLivenessModifier {

  private Set<DexProgramClass> noLongerInstantiatedClasses = SetUtils.newConcurrentHashSet();
  private Set<DexField> noLongerWrittenFields = SetUtils.newConcurrentHashSet();

  AppInfoWithLivenessModifier() {}

  public boolean isEmpty() {
    return noLongerInstantiatedClasses.isEmpty() && noLongerWrittenFields.isEmpty();
  }

  public void removeInstantiatedType(DexProgramClass clazz) {
    noLongerInstantiatedClasses.add(clazz);
  }

  public void removeWrittenField(DexField field) {
    noLongerWrittenFields.add(field);
  }

  public void modify(AppInfoWithLiveness appInfo) {
    // Instantiated classes.
    if (!noLongerInstantiatedClasses.isEmpty()) {
      noLongerInstantiatedClasses.forEach(appInfo::removeFromSingleTargetLookupCache);
      appInfo.removeNoLongerInstantiatedClasses(noLongerInstantiatedClasses);
      noLongerInstantiatedClasses = SetUtils.clearOrCreateConcurrent(noLongerInstantiatedClasses);
    }
    // Written fields.
    if (!noLongerWrittenFields.isEmpty()) {
      MutableFieldAccessInfoCollection<?, ? extends MutableFieldAccessInfo>
          fieldAccessInfoCollection = appInfo.getMutableFieldAccessInfoCollection();
      noLongerWrittenFields.forEach(
          field -> {
            MutableFieldAccessInfo fieldAccessInfo = fieldAccessInfoCollection.get(field);
            if (fieldAccessInfo != null) {
              fieldAccessInfo.clearWrites();
            }
          });
      noLongerWrittenFields = SetUtils.clearOrCreateConcurrent(noLongerWrittenFields);
    }
  }
}
