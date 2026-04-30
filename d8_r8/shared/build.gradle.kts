// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
}

val downloadDeps by
  tasks.registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allPublicDependencies())
    this.markerFile.set(layout.buildDirectory.file("downloadDeps.marker"))
  }

val sharedDepsFiles by
  configurations.consumable("sharedDepsFiles") {
    outgoing.artifact(downloadDeps.flatMap { it.markerFile })
  }

val downloadTestDeps by
  tasks.registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allPublicTestDependencies())
    this.markerFile.set(layout.buildDirectory.file("downloadTestDeps.marker"))
  }

val sharedTestDepsFiles by
  configurations.consumable("sharedTestDepsFiles") {
    outgoing.artifact(downloadTestDeps.flatMap { it.markerFile })
  }

val downloadDepsInternal by
  tasks.registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allInternalDependencies())
    this.markerFile.set(layout.buildDirectory.file("downloadDepsInternal.marker"))
  }

val sharedDepsInternalFiles by
  configurations.consumable("sharedDepsInternalFiles") {
    outgoing.artifact(downloadDepsInternal.flatMap { it.markerFile })
  }

val downloadTestDepsInternal by
  tasks.registering(DownloadAllDependenciesTask::class) {
    this.setDependencies(getRoot(), allInternalTestDependencies())
    this.markerFile.set(layout.buildDirectory.file("downloadTestDepsInternal.marker"))
  }

val sharedTestDepsInternalFiles by
  configurations.consumable("sharedTestDepsInternalFiles") {
    outgoing.artifact(downloadTestDepsInternal.flatMap { it.markerFile })
  }
