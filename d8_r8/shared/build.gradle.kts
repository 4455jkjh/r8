// Copyright (c) 2023, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

plugins {
  `java-library`
  id("dependencies-plugin")
}

tasks {
  val downloadDeps by
    registering(DownloadAllDependenciesTask::class) {
      this.setDependencies(getRoot(), allPublicDependencies())
    }

  val downloadTestDeps by
    registering(DownloadAllDependenciesTask::class) {
      this.setDependencies(getRoot(), allPublicTestDependencies())
    }

  val downloadDepsInternal by
    registering(DownloadAllDependenciesTask::class) {
      this.setDependencies(getRoot(), allInternalDependencies())
    }

  val downloadTestDepsInternal by
    registering(DownloadAllDependenciesTask::class) {
      this.setDependencies(getRoot(), allInternalTestDependencies())
    }
}

val sharedDepsFiles by
  configurations.consumable("sharedDepsFiles") {
    outgoing.artifacts(
      tasks.named<DownloadAllDependenciesTask>("downloadDeps").map { it.getOutputFiles() }
    )
  }

val sharedTestDepsFiles by
  configurations.consumable("sharedTestDepsFiles") {
    outgoing.artifacts(
      tasks.named<DownloadAllDependenciesTask>("downloadTestDeps").map { it.getOutputFiles() }
    )
  }

val sharedDepsInternalFiles by
  configurations.consumable("sharedDepsInternalFiles") {
    outgoing.artifacts(
      tasks.named<DownloadAllDependenciesTask>("downloadDepsInternal").map { it.getOutputFiles() }
    )
  }

val sharedTestDepsInternalFiles by
  configurations.consumable("sharedTestDepsInternalFiles") {
    outgoing.artifacts(
      tasks.named<DownloadAllDependenciesTask>("downloadTestDepsInternal").map {
        it.getOutputFiles()
      }
    )
  }
