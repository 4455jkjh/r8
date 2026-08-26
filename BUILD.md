# R8 Build System Architecture & Overview

This document provides an overview of the build architecture.

---

## Build Design

The build setup follows these core principles:

- **Hermetic:** Builds only download against Google resources (located in
  [`third_party/`](./third_party)).
- **Layered Tooling:**
  - **Python Drivers** ([`tools/gradle.py`](./tools/gradle.py),
    [`tools/test.py`](./tools/test.py), etc.) ensure pre-builts (JDKs,
    Gradle binaries, local Maven caches) from Google Cloud Storage (GCS)
    before invoking Gradle.
  - **Gradle Project** (located under [`d8_r8/`](./d8_r8)) coordinates the
    actual build.
- **Bootstrapping:** The official compiler jar is compiled with R8.
- **Testing what you release:** Tests run against the official bootstrapped and
  optimized [`r8lib.jar`](./build/libs/r8lib.jar) release artifact rather than a
  debug build.

---

## The Build Process

```
+---------------------------------------------------------------------------------------+
|                                   R8 COMPILER SUITE                                   |
+---------------------------------------------------------------------------------------+

  [main] ───────────────────────────> threading-module-blocking.jar ───────┐
  [main] ───────────────────────────> threading-module-single-threaded.jar ┼──> deps.jar
  [third_party], [resourceshrinker] ───────────────────────────────────────┘       │
                                                                                   │
  [keepradius], [libanalyzer] ────────────────────────────────────────> proto.jar  │
                                                                           │       │
  [main], [keepanno], [resourceshrinker],                                  │       │
  [keepradius], [libanalyzer], [assistant] ──┬──> r8-src.jar               │       │
                                             │                             │       │
                                             └──> r8-full-exclude-deps.jar │       │
                     r8lib-exclude-deps.jar <──────┘    │                  │       │
                                      ▲                 ▼                  │       │
                                      │               r8.jar <─────────────┴───────┘
                                      │              /      \
                                      │             ▼        ▼
  [tests] ───────────────> test-references ─--> r8lib.jar  processkeepruleslib.jar
                                                    │
                                                    ▼
                                                r8lib.zip

+---------------------------------------------------------------------------------------+
|                                KEEP ANNOTATIONS TOOLS                                 |
+---------------------------------------------------------------------------------------+

  [keepanno] ──┬──> keepanno-annotations.jar
               ├──> keepanno-annotations-legacy.jar
               ├──> keepanno-annotations-androidx.jar
               └──> keepanno-tools.jar ──> keepanno-toolslib.jar

+---------------------------------------------------------------------------------------+
|                                 DESUGAR CONFIGURATION                                 |
+---------------------------------------------------------------------------------------+

  [library_desugar] ──> desugar_jdk_libs_configuration.zip
```

### R8 Compiler Suite

- [`r8lib.zip`](./build/libs/r8lib.zip):
  - Released: To GMaven for distribution to AGP and other build systems.
  - What: The Maven repository archive for the compiler.
  - How: Package [`r8lib.jar`](./build/libs/r8lib.jar) with POM and checksum
    files in a Maven repository layout.
- [`r8lib.jar`](./build/libs/r8lib.jar):
  - Released: The primary production compiler JAR.
  - What: The relocated, standalone, optimized compiler JAR.
  - How: [`r8.jar`](./build/libs/r8.jar) is compiled and optimized with R8 with
    respect to declared keep rules and dynamic keep rules from `test-references`.
- [`r8.jar`](./build/libs/r8.jar):
  - Released: The non-optimized production compiler JAR.
  - What: The relocated, standalone compiler JAR.
  - How: [`r8-full-exclude-deps.jar`](./build/libs/r8-full-exclude-deps.jar)
    and [`proto.jar`](./build/libs/proto.jar) are bundled together, along with
    dependencies in [`deps.jar`](./build/libs/deps.jar) relocated under
    `com.android.tools.r8.*`.
- [`r8lib-exclude-deps.jar`](./build/libs/r8lib-exclude-deps.jar):
  - Released: Published for build systems (such as internal Google repo)
    that manage and provide their own dependencies on the classpath.
  - What: The optimized compiler JAR without dependencies (note the special
    proto and threading modules (part of [`deps.jar`](./build/libs/deps.jar))
    that are not generic dependencies).
  - How: [`r8-full-exclude-deps.jar`](./build/libs/r8-full-exclude-deps.jar)
    is compiled and optimized with R8 with respect to declared keep rules and
    dynamic keep rules from `test-references`, using
    [`deps.jar`](./build/libs/deps.jar) and
    [`proto.jar`](./build/libs/proto.jar) as classpath.
- `test-references`:
  - What: Dynamically generated keep rules extracted from test references.
  - How: Trace compiler references from the test suite to preserve compiler
    internals accessed during testing.
- [`r8-full-exclude-deps.jar`](./build/libs/r8-full-exclude-deps.jar):
  - Released: Published as an unoptimized compiler JAR without third-party
    dependencies for custom downstream packaging.
  - What: The compiler JAR without dependencies.
  - How: Compile classes from
    [`main`](./d8_r8/main),
    [`keepanno`](./d8_r8/keepanno),
    [`resourceshrinker`](./d8_r8/resourceshrinker),
    [`keepradius`](./d8_r8/keepradius),
    [`libanalyzer`](./d8_r8/libanalyzer),
    and [`assistant`](./d8_r8/assistant).
- [`r8-src.jar`](./build/libs/r8-src.jar):
  - Released: Published to provide source code for IDE navigation, debugging,
    and open-source compliance.
  - What: The compiler sources JAR.
  - How: Package sources from
    [`main`](./d8_r8/main),
    [`keepanno`](./d8_r8/keepanno),
    [`resourceshrinker`](./d8_r8/resourceshrinker),
    [`keepradius`](./d8_r8/keepradius),
    [`libanalyzer`](./d8_r8/libanalyzer),
    and [`assistant`](./d8_r8/assistant).
- [`deps.jar`](./build/libs/deps.jar):
  - What: The JAR containing third-party dependencies, resource shrinker
    dependencies, threading modules, and consolidated licensing.
  - How: Runtime dependencies from [`third_party/`](./third_party),
    resource shrinker dependencies from
    [`resourceshrinker`](./d8_r8/resourceshrinker),
    [`threading-module-blocking.jar`](./build/libs/threading-module-blocking.jar),
    [`threading-module-single-threaded.jar`](./build/libs/threading-module-single-threaded.jar),
    and the consolidated `LICENSE` file are merged together.
- [`proto.jar`](./build/libs/proto.jar):
  - What: The JAR containing compiled protobuf classes used for keep rule
    radius and library analysis data models. These are only needed for optional
    analysis features. Users without this dependency must compile the published
    `.proto` schemas directly using their own toolchains.
  - How: Protobuf classes from [`keepradius`](./d8_r8/keepradius) and
    [`libanalyzer`](./d8_r8/libanalyzer) are bundled together.
- [`threading-module-blocking.jar`](./build/libs/threading-module-blocking.jar):
  - Released: Published as an optional runtime module providing multi-threaded
    execution for modular classpath environments.
  - What: The multi-threaded execution provider. It is packaged separately so
    the concurrency model can be controlled via classpath. This is useful to
    have a compiler without code even mentioning blocking code (for aggressive
    code checkers).
  - How: Package blocking provider classes from [`main`](./d8_r8/main).
- [`threading-module-single-threaded.jar`](./build/libs/threading-module-single-threaded.jar):
  - Released: Published as an optional runtime module providing single-threaded
    execution for environments that prohibit blocking code.
  - What: The single-threaded execution provider. (see
    [`threading-module-blocking.jar`](./build/libs/threading-module-blocking.jar))
  - How: Package single-threaded provider classes from [`main`](./d8_r8/main).
- [`processkeepruleslib.jar`](./build/libs/processkeepruleslib.jar):
  - Released: Published as a standalone optimized tool for parsing, evaluating,
    and filtering ProGuard keep rules.
  - What: The standalone, optimized tool JAR used to process and filter ProGuard
    keep rules.
  - How: [`r8.jar`](./build/libs/r8.jar) is compiled and optimized with R8 using
    `src/main/keep_processkeeprules.txt`.

### Keep Annotations Tools

- [`keepanno-toolslib.jar`](./build/libs/keepanno-toolslib.jar):
  - Released: Published as a standalone, optimized library for processing keep
    annotations in build pipelines.
  - What: The relocated, standalone, optimized keep-annotation tools JAR.
  - How: [`keepanno-tools.jar`](./build/libs/keepanno-tools.jar) is compiled and
    optimized with R8 with respect to keep rules, using unrelocated ASM on the
    classpath.
- [`keepanno-tools.jar`](./build/libs/keepanno-tools.jar):
  - Released: Published as an unminified standalone JAR with relocated
    dependencies for keep-annotation processing and debugging.
  - What: The standalone JAR containing tools for processing keep annotations
    with non-ASM dependencies relocated under
    `com.android.tools.r8.keepanno.*`. ASM is kept external as it is used as the
    public API interface.
  - How: Compiled classes from [`keepanno`](./d8_r8/keepanno) and its non-ASM
    dependencies are bundled together, with dependencies relocated under
    `com.android.tools.r8.keepanno.*` (leaving ASM external for public API
    integration).
- [`keepanno-annotations.jar`](./build/libs/keepanno-annotations.jar):
  - Released: Published as the compile-time annotations library for source
    code declaring keep annotations in
    `com.android.tools.r8.keepanno.annotations.*`.
  - What: The annotation library containing keep-annotation definitions
    (`com.android.tools.r8.keepanno.annotations.*`).
  - How: Package compiled annotation classes from [`keepanno`](./d8_r8/keepanno).
- [`keepanno-annotations-legacy.jar`](./build/libs/keepanno-annotations-legacy.jar):
  - Released: Published for backward compatibility with projects using legacy
    keep-annotation package namespaces.
  - What: The legacy package variant of the keep-annotations library.
  - How: Package compiled legacy annotation classes from
    [`keepanno`](./d8_r8/keepanno).
- [`keepanno-annotations-androidx.jar`](./build/libs/keepanno-annotations-androidx.jar):
  - Released: Published for AndroidX projects using the
    `androidx.annotation.keep.*` package namespace.
  - What: The AndroidX package variant (`androidx.annotation.keep.*`) of the
    keep-annotations library.
  - How: Package compiled AndroidX keep-annotation classes from
    [`keepanno`](./d8_r8/keepanno).

### Desugar Configuration

- [`desugar_jdk_libs_configuration.zip`](./build/libs/desugar_jdk_libs_configuration.zip):
  - Released: Published to GMaven to configure Java standard library desugaring
    in D8 and R8 (along with JDK 11, minimal, and NIO variants).
  - What: The Maven repository archive containing the configuration JAR and POM.
  - How: Package the desugar specification JSON, generated lint metadata, and
    compiled conversion classes from
    [`library_desugar`](./d8_r8/library_desugar) with POM and checksum files in
    a Maven repository layout.
