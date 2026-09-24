// Copyright (c) 2022, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.

package com.android.tools.r8.desugar.desugaredlibrary.jdktests;

import static com.android.tools.r8.TestBase.descriptor;
import static com.android.tools.r8.TestBase.transformer;

import com.android.tools.r8.ToolHelper;
import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.io.PrintStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;

// Provides convenience to use Paths/SafeVarargs which are missing on old Android but
// required by some Jdk tests, and for java.base extensions.

public class Jdk11SupportFiles {

  // TODO(b/289346278): See if we can remove the xxx subfolder.
  private static final Path ANDROID_PATHS_FILES_DIR =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "android_jar/lib-v26/xxx/");
  private static final Path ANDROID_SAFE_VAR_ARGS_LOCATION =
      Paths.get(ToolHelper.THIRD_PARTY_DIR + "android_jar/lib-v26/java/lang/SafeVarargs.class");
  private static final Path[] ANDROID_PATHS_FILES =
      new Path[] {
        Paths.get("java/nio/file/Files.class"),
        Paths.get("java/nio/file/OpenOption.class"),
        Paths.get("java/nio/file/Watchable.class"),
        Paths.get("java/nio/file/Path.class"),
        Paths.get("java/nio/file/Paths.class")
      };

  public static Path[] getPathsFiles() {
    return Arrays.stream(ANDROID_PATHS_FILES)
        .map(ANDROID_PATHS_FILES_DIR::resolve)
        .toArray(Path[]::new);
  }

  public static Path getSafeVarArgsFile() {
    return ANDROID_SAFE_VAR_ARGS_LOCATION;
  }

  public static Path[] testNGSupportProgramFiles() {
    return new Path[] {testNGPath(), jcommanderPath()};
  }

  public static Path testNGPath() {
    return Paths.get(ToolHelper.DEPENDENCIES + "org/testng/testng/6.10/testng-6.10.jar");
  }

  public static Path jcommanderPath() {
    return Paths.get(ToolHelper.DEPENDENCIES + "com/beust/jcommander/1.48/jcommander-1.48.jar");
  }

  public static byte[] getTestNGMainRunner() throws Exception {
    return transformer(TestNGMainRunner.class)
        .setClassDescriptor("LTestNGMainRunner;")
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TestNGMainRunner.class), "LTestNGMainRunner;")
        .replaceClassDescriptorInMethodInstructions(descriptor(TestNG.class), "Lorg/testng/TestNG;")
        .replaceClassDescriptorInMethodInstructions(
            descriptor(TextReporter.class), "Lorg/testng/reporters/TextReporter;")
        .transform();
  }

  /** TestNGMainRunner used as the test runner in JDK11 tests. */
  public static class TestNGMainRunner extends PrintStream implements Runnable {

    private static final ThreadLocal<ByteArrayOutputStream> THREAD_OUT = new ThreadLocal<>();

    private final String className;
    private final int verbose;
    private final ByteArrayOutputStream buffer;

    public TestNGMainRunner(
        OutputStream out, String className, int verbose, ByteArrayOutputStream buffer) {
      super(out, true);
      this.className = className;
      this.verbose = verbose;
      this.buffer = buffer;
    }

    @Override
    public void write(byte[] buf, int off, int len) {
      ByteArrayOutputStream threadBuffer = THREAD_OUT.get();
      if (threadBuffer != null) {
        threadBuffer.write(buf, off, len);
      } else {
        super.write(buf, off, len);
      }
    }

    @Override
    public void write(int b) {
      ByteArrayOutputStream threadBuffer = THREAD_OUT.get();
      if (threadBuffer != null) {
        threadBuffer.write(b);
      } else {
        super.write(b);
      }
    }

    @Override
    public void run() {
      THREAD_OUT.set(buffer);
      try {
        runTestNg(className, verbose);
      } finally {
        THREAD_OUT.remove();
      }
    }

    private static void runTestNg(String className, int verbose) {
      System.out.println("Running tests in " + className);
      try {
        Class<?> testClass = Class.forName(className);
        TestNG testng = new TestNG(false);
        testng.setTestClasses(new Class<?>[] {testClass});
        testng.setVerbose(verbose);
        // Deprecated API used because it works on Android unlike the recommended one.
        testng.addListener(new TextReporter(testClass.getName(), verbose));
        testng.run();
        System.out.println(
            "Tests result in " + className + ": " + (testng.hasFailure() ? "FAILURE" : "SUCCESS"));
      } catch (Throwable e) {
        e.printStackTrace(System.out);
        System.out.println("Tests result in " + className + ": ERROR");
      }
    }

    public static void main(String[] args) throws Exception {
      // First arg is the verbosity level.
      // Subsequent args are the classes to run.
      int verbose = Integer.parseInt(args[0]);
      if (args.length <= 2) {
        for (int i = 1; i < args.length; i++) {
          runTestNg(args[i], verbose);
        }
        return;
      }
      PrintStream origOut = System.out;
      System.setOut(new TestNGMainRunner(origOut, null, 0, null));
      try {
        int count = args.length - 1;
        ByteArrayOutputStream[] buffers = new ByteArrayOutputStream[count];
        Thread[] threads = new Thread[count];
        for (int i = 0; i < count; i++) {
          buffers[i] = new ByteArrayOutputStream();
          threads[i] = new Thread(new TestNGMainRunner(origOut, args[i + 1], verbose, buffers[i]));
          threads[i].start();
        }
        for (int i = 0; i < count; i++) {
          threads[i].join();
        }
        for (int i = 0; i < count; i++) {
          origOut.write(buffers[i].toByteArray());
        }
        origOut.flush();
      } finally {
        System.setOut(origOut);
      }
    }
  }

  /** Stubs for the TestNGRunner */
  public static class TextReporter {

    public TextReporter(String name, int verbose) {}
  }

  public static class TestNG {

    public TestNG(boolean val) {}

    public void setTestClasses(Class<?>[] classes) {}

    public void setVerbose(int verbose) {}

    public void addListener(Object textReporter) {}

    public void run() {}

    public boolean hasFailure() {
      return false;
    }
  }
}
