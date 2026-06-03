// Copyright (c) 2024, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.metadata.impl;

import com.android.tools.r8.dex.VirtualFile;
import com.android.tools.r8.keepanno.annotations.AnnotationPattern;
import com.android.tools.r8.keepanno.annotations.FieldAccessFlags;
import com.android.tools.r8.keepanno.annotations.KeepConstraint;
import com.android.tools.r8.keepanno.annotations.KeepItemKind;
import com.android.tools.r8.keepanno.annotations.UsedByReflection;
import com.android.tools.r8.metadata.D8BuildMetadata;
import com.android.tools.r8.metadata.D8DexFileMetadata;
import com.android.tools.r8.metadata.D8OptionsMetadata;
import com.android.tools.r8.utils.internal.ListUtils;
import com.google.gson.Gson;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;
import java.util.List;

@UsedByReflection(
    description = "Keep and preserve @SerializedName for correct (de)serialization",
    constraints = {KeepConstraint.LOOKUP},
    constrainAnnotations = @AnnotationPattern(constant = SerializedName.class),
    kind = KeepItemKind.CLASS_AND_FIELDS,
    fieldAccess = {FieldAccessFlags.PRIVATE},
    fieldAnnotatedByClassConstant = SerializedName.class)
public class D8BuildMetadataImpl implements D8BuildMetadata {

  @Expose
  @SerializedName("dexFiles")
  private final List<D8DexFileMetadata> dexFilesMetadata;

  @Expose
  @SerializedName("options")
  private final D8OptionsMetadata options;

  @Expose
  @SerializedName("version")
  private final String version;

  public D8BuildMetadataImpl(
      List<D8DexFileMetadata> dexFilesMetadata, D8OptionsMetadata options, String version) {
    this.dexFilesMetadata = dexFilesMetadata;
    this.options = options;
    this.version = version;
  }

  public static Builder builder() {
    return new Builder();
  }

  @Override
  public List<D8DexFileMetadata> getDexFilesMetadata() {
    return dexFilesMetadata;
  }

  @Override
  public D8OptionsMetadata getOptionsMetadata() {
    return options;
  }

  @Override
  public String getVersion() {
    return version;
  }

  @Override
  public String toJson() {
    return new Gson().toJson(this);
  }

  public static class Builder {

    private List<D8DexFileMetadata> dexFilesMetadata;
    private D8OptionsMetadata options;
    private String version;

    public Builder setDexFilesMetadata(List<VirtualFile> virtualFiles) {
      this.dexFilesMetadata = ListUtils.map(virtualFiles, D8DexFileMetadataImpl::create);
      return this;
    }

    public Builder setOptions(D8OptionsMetadata options) {
      this.options = options;
      return this;
    }

    public Builder setVersion(String version) {
      this.version = version;
      return this;
    }

    public D8BuildMetadataImpl build() {
      return new D8BuildMetadataImpl(dexFilesMetadata, options, version);
    }
  }
}
