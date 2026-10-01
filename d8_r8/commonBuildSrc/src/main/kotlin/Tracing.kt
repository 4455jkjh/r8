// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

import androidx.tracing.wire.TraceDriver
import androidx.tracing.wire.TraceSink
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.flow.FlowAction
import org.gradle.api.flow.FlowParameters
import org.gradle.api.provider.Property
import org.gradle.api.services.BuildService
import org.gradle.api.services.BuildServiceParameters
import org.gradle.api.services.ServiceReference

public abstract class TracingBuildService : BuildService<TracingBuildService.Parameters> {
  public interface Parameters : BuildServiceParameters {
    public val traceDir: DirectoryProperty
  }

  public var driver: TraceDriver? = newDriver()

  private fun newDriver(): TraceDriver {
    val dir = parameters.traceDir.get().asFile
    dir.mkdirs()
    return TraceDriver(TraceSink(dir, 1))
  }

  public fun beginSection(sectionName: String) {
    driver!!.tracer.beginSection("gradle", sectionName, null, false) {}
  }

  public fun endSection() {
    driver!!.context.process.currentThreadTrack().endSection()
  }
}

public class TracingServiceCloseAction :
  FlowAction<TracingServiceCloseAction.TracingServiceCloseActionParameters> {
  @Throws(Exception::class)
  override fun execute(parameters: TracingServiceCloseActionParameters) {
    if (parameters.tracingBuildService.isPresent()) {
      parameters.tracingBuildService.get().driver?.flush()
      parameters.tracingBuildService.get().driver = null
    }
  }

  public interface TracingServiceCloseActionParameters : FlowParameters {
    @get:ServiceReference("tracingBuildService")
    public val tracingBuildService: Property<TracingBuildService>
  }
}
