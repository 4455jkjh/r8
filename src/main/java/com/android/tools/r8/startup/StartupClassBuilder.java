// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.startup;

import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.references.ClassReference;

/** Interface for providing information about a startup class to the compiler. */
@KeepForApi
public interface StartupClassBuilder {

  StartupClassBuilder setClassReference(ClassReference classReference);
}
