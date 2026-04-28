// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.retrace;

import com.android.tools.r8.CompilationFailedException;
import com.android.tools.r8.DiagnosticsHandler;
import com.android.tools.r8.PartitionMapConsumer;
import com.android.tools.r8.keepanno.annotations.KeepForApi;
import com.android.tools.r8.origin.Origin;
import com.android.tools.r8.utils.ExceptionUtils;
import com.android.tools.r8.utils.Reporter;
import com.android.tools.r8.utils.internal.Box;
import java.util.ArrayList;
import java.util.List;

@KeepForApi
public class PartitionCommand {

  private final PartitionMapConsumer partitionMapConsumer;
  private final List<PartitionMappingSupplier> partitionMapSuppliers;
  private final ProguardMapProducer proguardMapProducer;
  private final boolean printHelp;
  private final boolean printVersion;
  private final Reporter reporter;
  private final String partitionMapId;

  private PartitionCommand(
      PartitionMapConsumer partitionMapConsumer,
      String partitionMapId,
      List<PartitionMappingSupplier> partitionMapSuppliers,
      ProguardMapProducer proguardMapProducer,
      Reporter reporter) {
    this.partitionMapConsumer = partitionMapConsumer;
    this.partitionMapId = partitionMapId;
    this.partitionMapSuppliers = partitionMapSuppliers;
    this.proguardMapProducer = proguardMapProducer;
    this.printHelp = false;
    this.printVersion = false;
    this.reporter = reporter;
  }

  private PartitionCommand(boolean printHelp, boolean printVersion) {
    this.partitionMapConsumer = null;
    this.partitionMapSuppliers = null;
    this.proguardMapProducer = null;
    this.printHelp = printHelp;
    this.printVersion = printVersion;
    this.reporter = null;
    this.partitionMapId = null;
  }

  public static PartitionCommand.Builder parse(String[] args, Origin origin) {
    return PartitionCommandParser.parse(args, origin);
  }

  PartitionMapConsumer getPartitionMapConsumer() {
    return partitionMapConsumer;
  }

  List<PartitionMappingSupplier> getPartitionMapSuppliers() {
    return partitionMapSuppliers;
  }

  ProguardMapProducer getProguardMapProducer() {
    return proguardMapProducer;
  }

  Reporter getReporter() {
    return reporter;
  }

  public String getPartitionMapId() {
    return partitionMapId;
  }

  boolean isPrintHelp() {
    return printHelp;
  }

  boolean isPrintVersion() {
    return printVersion;
  }

  /** Utility method for obtaining a RetraceCommand builder with a default diagnostics handler. */
  public static Builder builder() {
    return new Builder(new Reporter());
  }

  @KeepForApi
  public static class Builder {

    private final Reporter reporter;

    private PartitionMapConsumer partitionMapConsumer;
    private List<PartitionMappingSupplier> partitionMapSuppliers = new ArrayList<>();
    private ProguardMapProducer proguardMapProducer;
    private String partitionMapId;

    private boolean printHelp;
    private boolean printVersion;

    private Builder(DiagnosticsHandler diagnosticsHandler) {
      this.reporter = Reporter.create(diagnosticsHandler);
    }

    Reporter getReporter() {
      return reporter;
    }

    public Builder setPartitionMapConsumer(PartitionMapConsumer partitionMapConsumer) {
      this.partitionMapConsumer = partitionMapConsumer;
      return this;
    }

    public Builder setPartitionMapId(String partitionMapId) {
      this.partitionMapId = partitionMapId;
      return this;
    }

    public Builder addPartitionMapSupplier(PartitionMappingSupplier partitionMappingSupplier) {
      partitionMapSuppliers.add(partitionMappingSupplier);
      return this;
    }

    public Builder setProguardMapProducer(ProguardMapProducer proguardMapProducer) {
      this.proguardMapProducer = proguardMapProducer;
      return this;
    }

    Builder setPrintHelp(boolean printHelp) {
      this.printHelp = printHelp;
      return this;
    }

    Builder setPrintVersion(boolean printVersion) {
      this.printVersion = printVersion;
      return this;
    }

    public PartitionCommand build() throws CompilationFailedException {
      Box<PartitionCommand> box = new Box<>();
      ExceptionUtils.withCompilationHandler(
          reporter,
          () -> {
            validate();
            box.set(makeCommand());
            reporter.failIfPendingErrors();
          });
      return box.get();
    }

    private PartitionCommand makeCommand() {
      if (printHelp || printVersion) {
        return new PartitionCommand(printHelp, printVersion);
      } else {
        return new PartitionCommand(
            partitionMapConsumer,
            partitionMapId,
            partitionMapSuppliers,
            proguardMapProducer,
            reporter);
      }
    }

    private void validate() {
      if (partitionMapConsumer == null) {
        throw new RetracePartitionException("PartitionMapConsumer not specified");
      }
      // Check that only one of partitionMapSuppliers and proguardMapProducer is set.
      if (proguardMapProducer == null) {
        if (partitionMapSuppliers.isEmpty()) {
          throw new RetracePartitionException(
              "Expected PartitionMappingSupplier or ProguardMapSupplier");
        }
      } else if (!partitionMapSuppliers.isEmpty()) {
        throw new RetracePartitionException(
            "Expected PartitionMappingSupplier or ProguardMapSupplier, not both");
      }
      // Check that partitionMapId is only set when partitionMapSuppliers is set,
      // and that partitionMapId is 64 characters.
      if (partitionMapId == null) {
        if (!partitionMapSuppliers.isEmpty()) {
          throw new RetracePartitionException(
              "PartitionMapId must be set when a PartitionMappingSupplier is given");
        }
      } else {
        if (partitionMapSuppliers.isEmpty()) {
          throw new RetracePartitionException(
              "PartitionMapId can only be set when a PartitionMappingSupplier is given");
        }
        if (partitionMapId.length() != 64) {
          throw new RetracePartitionException(
              "Expected PartitionMapId to be 64 characters, was: " + partitionMapId);
        }
      }
    }
  }
}
