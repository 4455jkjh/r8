# Gemini Code Exploration Guide: R8 Project

## Project Overview

This project contains the source code for D8 and R8, two critical tools for Android development.

*   **D8:** A dexer that converts Java bytecode to DEX format.
*   **R8:** A shrinker and minifier that optimizes Java bytecode and converts it to DEX format.

The Git project is written in Java and Kotlin and uses Gradle for building. It is a multi-module project with several components, including `main`, `library_desugar`, and `test`.

## Building and Running

### Building

The primary build script is `tools/gradle.py`. To build the project, run:

```bash
tools/gradle.py -q r8
```

This will produce a JAR file at `build/libs/r8.jar`.

For a lighter target that just compiles the production code without building the full R8 JAR or test modules, you can use:

```bash
tools/gradle.py -q classes
```

To compile both production code and test modules (without running the tests), use:

```bash
tools/gradle.py -q testClasses
```

Only run without `-q` if more information is needed about the build details.

### Disabling Error Prone

Compilation is usually pretty strict due to Error Prone. You can turn it off for debugging-style edits (e.g., leaving unused imports) by passing the property `-Pdisable_errorprone`:

```bash
tools/gradle.py -q classes -Pdisable_errorprone
```

### Running D8

D8 is used to convert Java class files to the DEX format.

**Debug build:**

```bash
java -cp build/libs/r8.jar com.android.tools.r8.D8 \
       --min-api <min-api> \
       --output out.zip \
       --lib <android.jar> \
       input.jar
```

**Release build:**

```bash
java -cp build/libs/r8.jar com.android.tools.r8.D8 \
       --release \
       --min-api <min-api> \
       --output out.zip \
       --lib <android.jar> \
       input.jar
```

### Running R8

R8 is used to shrink, optimize and minify Java class files.

```bash
java -cp build/libs/r8.jar com.android.tools.r8.R8 \
       --release \
       --min-api <min-api> \
       --output out.zip \
       --pg-conf proguard-rules.pro \
       --lib <android.jar> \
       input.jar
```

### Updating third party dependencies

The R8 project is configured as a hermetic build with all third party dependencies in a local Maven repositorry under `third_party/dependencies`
and `third_party/dependencies_plugin`.

The versions of these dependencies are in both `tools/create_local_maven_with_dependencies.py` and `d8_r8/commonBuildSrc/src/main/kotlin/DependenciesPlugin.kt`.

To update the local Maven repository run `tools/create_local_maven_with_dependencies.py --studio <path>`, where `<path>` is the path to an Android Studio
checkout. This is typically in `$HOME/studio` or `$HOME/studio-main`.

### Testing

Tests are run using the `tools/test.py` script:

```bash
tools/test.py -q --no-internal
```

> [!NOTE]
> `test.py` invokes Gradle, so avoid running concurrent invocations of it to prevent conflicts.

By default, this runs tests using r8lib.jar, which is a bootstrapped R8. It is possible to speed up local testing by running tests with a non-bootstrapped R8:

```bash
tools/test.py -q --no-internal --no-r8lib
```

It is possible to run a single test by passing the name of the test, e.g.,

```bash
tools/test.py -q --no-internal --no-r8lib *ProguardConfigurationParserTest*
```

`tools/test.py` can take multiple additive filters.

```bash
tools/test.py -q --no-internal --no-r8lib *ProguardConfigurationParserTest* *ClassInlinerTest*
```

Only run without `-q` if more information is needed about the build details.

### Updating code for tests extending `com.android.tools.r8.cfmethodgeneration.CodeGenerationBase`

Subclasses of `com.android.tools.r8.cfmethodgeneration.CodeGenerationBase` have tests to check that code templates have been generated. E.g.
`com.android.tools.r8.ir.desugar.backports.GenerateBackportMethods` has the test `testBackportsGenerated()`. When these test fails due to
changes requiring regenerating code the option `--generate-methods` can be passed to `tools/test.py` when running the test to generate the
code instead of just checking what was previously generated.


### Testing with Kotlin developer compiler versions

To run tests with the latest Kotlin developer version first download it by running

```bash
tools/download_kotlin.py --version dev
```

To run tests with this downloaded compiler pass `--kotlin-compiler-dev` to
`tools/test.py` (or alternatively set `withDevCompiler` to `true` in the class
`com.android.tools.r8.KotlinTestParameters.Builder` either using the system property
`com.android.tools.r8.kotlincompilerdev` or by temporarily editing the file).

## Development Conventions

### Code Formatting

The project enforces a strict code style.

*   **Java:** `google-java-format` is used for formatting Java files.
*   **Kotlin:** `ktfmt` is used for formatting Kotlin files.

The `PRESUBMIT.py` script checks that the code has been correctly formatted and contains the exact commands to format the code.

It is also possible to format the code using the `tools/fmt_diff.py` script:

```bash
tools/fmt_diff.py
```

### Copyright Headers

All new files must contain a copyright header. The format is checked by the presubmit script.

### Testing

There are several conventions for writing tests, including:

*   Do not add `.disassemble()` to tests.
*   Do not add `.allowStdoutMessages()` or `.allowStderrMessages()` to tests.

### Contributions

Contributions to the project require signing a Contributor License Agreement (CLA). See `CONTRIBUTING.md` for more details.
