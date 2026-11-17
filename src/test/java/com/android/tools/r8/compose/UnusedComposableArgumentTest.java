// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.compose;

import static com.android.tools.r8.utils.codeinspector.Matchers.isPresent;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;

import com.android.tools.r8.NeverClassInline;
import com.android.tools.r8.NeverInline;
import com.android.tools.r8.NoHorizontalClassMerging;
import com.android.tools.r8.NoVerticalClassMerging;
import com.android.tools.r8.TestBase;
import com.android.tools.r8.TestParameters;
import com.android.tools.r8.TestParametersCollection;
import com.android.tools.r8.utils.codeinspector.ClassSubject;
import com.android.tools.r8.utils.codeinspector.MethodSubject;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;

@RunWith(Parameterized.class)
public class UnusedComposableArgumentTest extends TestBase {

  private static final String COMPOSABLE_DESCRIPTOR = "Landroidx/compose/runtime/Composable;";

  @Parameter(0)
  public TestParameters parameters;

  @Parameters(name = "{0}")
  public static TestParametersCollection data() {
    return getTestParameters().withAllRuntimesAndApiLevels().build();
  }

  @Test
  public void test() throws Exception {
    testForR8(parameters)
        .addProgramClasses(
            Main.class,
            Composer.class,
            ComposerImpl.class,
            OtherComposerImpl.class,
            ScopeUpdateScope.class,
            RecomposeLambda.class,
            TextRecomposeLambda.class,
            Modifier.class,
            TextUnit.class)
        .addProgramClassFileData(
            transformer(Composable.class).setClassDescriptor(COMPOSABLE_DESCRIPTOR).transform(),
            transformer(TextKt.class)
                .replaceClassDescriptorInAnnotations(
                    descriptor(Composable.class), COMPOSABLE_DESCRIPTOR)
                .transform())
        .addKeepMainRule(Main.class)
        .addOptionsModification(
            options -> options.getComposeOptions().enableUnusedComposableArgumentSimplifier = true)
        .enableInliningAnnotations()
        .enableNeverClassInliningAnnotations()
        .enableNoHorizontalClassMergingAnnotations()
        .enableNoVerticalClassMergingAnnotations()
        .compile()
        .inspect(
            inspector -> {
              ClassSubject textKtClassSubject = inspector.clazz(TextKt.class);
              assertThat(textKtClassSubject, isPresent());

              MethodSubject textMethodSubject =
                  textKtClassSubject.uniqueMethodWithOriginalName("Text");
              assertThat(textMethodSubject, isPresent());
              assertEquals(5, textMethodSubject.getParameters().size());
              assertEquals(
                  inspector.getTypeSubject(String.class), textMethodSubject.getParameter(0));
              assertEquals(inspector.getTypeSubject(int.class), textMethodSubject.getParameter(1));
              assertEquals(inspector.getTypeSubject(int.class), textMethodSubject.getParameter(2));
              assertEquals(
                  inspector.clazz(Composer.class).asTypeSubject(),
                  textMethodSubject.getParameter(3));
              assertEquals(inspector.getTypeSubject(int.class), textMethodSubject.getParameter(4));
            })
        .run(parameters.getRuntime(), Main.class)
        .assertSuccessWithOutputLines(
            "Hello:Modifier:42:true:1:default:class java.lang.String:10:7",
            "skipToGroupEnd",
            "skipToGroupEnd",
            "Hello:Modifier:42:true:1:default:class java.lang.String:10:7",
            "World:Modifier:42:true:1:default:class java.lang.String:20:7");
  }

  @Retention(RetentionPolicy.CLASS)
  @Target(ElementType.METHOD)
  public @interface Composable {}

  public interface RecomposeLambda {
    void invoke(Composer composer);
  }

  public interface ScopeUpdateScope {
    void updateScope(RecomposeLambda block);
  }

  @NoVerticalClassMerging
  public interface Composer {
    boolean changed(Object value);

    boolean changed(long value);

    boolean changed(int value);

    boolean shouldExecute(int dirty, boolean changed);

    void startDefaults();

    void endDefaults();

    boolean getDefaultsInvalid();

    void skipToGroupEnd();

    ScopeUpdateScope endRestartGroup();
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class ComposerImpl implements Composer, ScopeUpdateScope {

    private final boolean defaultsInvalid;
    private boolean forceShouldExecute = true;
    private RecomposeLambda block;

    public ComposerImpl() {
      this.defaultsInvalid = System.currentTimeMillis() < 0;
    }

    @NeverInline
    @Override
    public boolean changed(Object value) {
      return defaultsInvalid && value == null;
    }

    @NeverInline
    @Override
    public boolean changed(long value) {
      return defaultsInvalid && value == 0;
    }

    @NeverInline
    @Override
    public boolean changed(int value) {
      return defaultsInvalid && value == 0;
    }

    @NeverInline
    @Override
    public boolean shouldExecute(int dirty, boolean changed) {
      return forceShouldExecute || changed;
    }

    @NeverInline
    @Override
    public void startDefaults() {}

    @NeverInline
    @Override
    public void endDefaults() {}

    @NeverInline
    @Override
    public boolean getDefaultsInvalid() {
      return defaultsInvalid;
    }

    @NeverInline
    @Override
    public void skipToGroupEnd() {
      if (!defaultsInvalid) {
        System.out.println("skipToGroupEnd");
      }
    }

    @NeverInline
    @Override
    public ScopeUpdateScope endRestartGroup() {
      return System.currentTimeMillis() >= 0 ? this : null;
    }

    @NeverInline
    @Override
    public void updateScope(RecomposeLambda block) {
      this.block = block;
    }

    @NeverInline
    public void recompose(boolean forceShouldExecute) {
      if (block != null) {
        this.forceShouldExecute = forceShouldExecute;
        RecomposeLambda current = block;
        block = null;
        current.invoke(this);
      }
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class OtherComposerImpl implements Composer {

    @Override
    public boolean changed(Object value) {
      return false;
    }

    @Override
    public boolean changed(long value) {
      return false;
    }

    @Override
    public boolean changed(int value) {
      return false;
    }

    @Override
    public boolean shouldExecute(int dirty, boolean changed) {
      return false;
    }

    @Override
    public void startDefaults() {}

    @Override
    public void endDefaults() {}

    @Override
    public boolean getDefaultsInvalid() {
      return false;
    }

    @Override
    public void skipToGroupEnd() {}

    @Override
    public ScopeUpdateScope endRestartGroup() {
      return null;
    }
  }

  @NeverClassInline
  @NoHorizontalClassMerging
  public static class Modifier {

    public static final Modifier DUMMY = new Modifier("Dummy");
    public static final Modifier INSTANCE = new Modifier("Modifier");

    private final String name;

    public Modifier(String name) {
      this.name = name;
    }

    @NeverInline
    @Override
    public String toString() {
      return name;
    }
  }

  public static class TextUnit {

    public static final long Unspecified = pack(42L);

    @NeverInline
    private static long pack(long value) {
      return System.currentTimeMillis() >= 0 ? value : 0L;
    }
  }

  public static class TextKt {

    @NeverInline
    private static int defaultOverflow() {
      return System.currentTimeMillis() >= 0 ? 7 : 0;
    }

    @Composable
    @NeverInline
    public static void Text(
        String text,
        Modifier modifier,
        long fontSize,
        boolean softWrap,
        int minLines,
        String label,
        Class<?> tag,
        int maxLines,
        int overflow,
        Composer composer,
        int changed,
        int defaultFlags) {
      int dirty = changed;
      if ((changed & 6) == 0) {
        dirty |= composer.changed(text) ? 4 : 2;
      }
      if ((defaultFlags & 2) != 0) {
        dirty |= 48;
      } else if ((changed & 48) == 0) {
        dirty |= composer.changed(modifier) ? 32 : 16;
      }
      if ((defaultFlags & 4) != 0) {
        dirty |= 384;
      } else if ((changed & 384) == 0) {
        dirty |= composer.changed(fontSize) ? 256 : 128;
      }
      if ((defaultFlags & 8) != 0) {
        dirty |= 3072;
      }
      if ((defaultFlags & 16) != 0) {
        dirty |= 24576;
      }
      if ((defaultFlags & 32) != 0) {
        dirty |= 196608;
      } else if ((changed & 196608) == 0) {
        dirty |= composer.changed(label) ? 131072 : 65536;
      }
      if ((defaultFlags & 64) != 0) {
        dirty |= 1572864;
      } else if ((changed & 1572864) == 0) {
        dirty |= composer.changed(tag) ? 1048576 : 524288;
      }
      if ((defaultFlags & 128) != 0) {
        dirty |= 12582912;
      } else if ((changed & 12582912) == 0) {
        dirty |= composer.changed(maxLines) ? 8388608 : 4194304;
      }
      if ((defaultFlags & 256) != 0) {
        dirty |= 100663296;
      } else if ((changed & 100663296) == 0) {
        dirty |= composer.changed(overflow) ? 67108864 : 33554432;
      }
      if (composer.shouldExecute(dirty & 1, (dirty & 1) == 0 && dirty == 0)) {
        composer.startDefaults();
        if ((changed & 1) == 0 || composer.getDefaultsInvalid()) {
          if ((defaultFlags & 2) != 0) {
            modifier = Modifier.INSTANCE;
          }
          if ((defaultFlags & 4) != 0) {
            fontSize = TextUnit.Unspecified;
          }
          if ((defaultFlags & 8) != 0) {
            softWrap = true;
          }
          if ((defaultFlags & 16) != 0) {
            minLines = 1;
          }
          if ((defaultFlags & 32) != 0) {
            label = "default";
          }
          if ((defaultFlags & 64) != 0) {
            tag = String.class;
          }
          if ((defaultFlags & 128) != 0) {
            maxLines = 99;
          }
          if ((defaultFlags & 256) != 0) {
            overflow = defaultOverflow();
          }
        } else {
          composer.skipToGroupEnd();
        }
        composer.endDefaults();
        System.out.println(
            text + ":" + modifier + ":" + fontSize + ":" + softWrap + ":" + minLines + ":" + label
                + ":" + tag + ":" + maxLines + ":" + overflow);
      } else {
        composer.skipToGroupEnd();
      }
      ScopeUpdateScope scope = composer.endRestartGroup();
      if (scope != null) {
        scope.updateScope(
            new TextRecomposeLambda(
                text,
                modifier,
                fontSize,
                softWrap,
                minLines,
                label,
                tag,
                maxLines,
                overflow,
                changed,
                defaultFlags));
      }
    }
  }

  public static class TextRecomposeLambda implements RecomposeLambda {

    private final String text;
    private final Modifier modifier;
    private final long fontSize;
    private final boolean softWrap;
    private final int minLines;
    private final String label;
    private final Class<?> tag;
    private final int maxLines;
    private final int overflow;
    private final int changed;
    private final int defaultFlags;

    public TextRecomposeLambda(
        String text,
        Modifier modifier,
        long fontSize,
        boolean softWrap,
        int minLines,
        String label,
        Class<?> tag,
        int maxLines,
        int overflow,
        int changed,
        int defaultFlags) {
      this.text = text;
      this.modifier = modifier;
      this.fontSize = fontSize;
      this.softWrap = softWrap;
      this.minLines = minLines;
      this.label = label;
      this.tag = tag;
      this.maxLines = maxLines;
      this.overflow = overflow;
      this.changed = changed;
      this.defaultFlags = defaultFlags;
    }

    @Override
    public void invoke(Composer composer) {
      TextKt.Text(
          text,
          modifier,
          fontSize,
          softWrap,
          minLines,
          label,
          tag,
          maxLines,
          overflow,
          composer,
          changed | 1,
          defaultFlags);
    }
  }

  public static class Main {

    public static void main(String[] args) {
      ComposerImpl composerImpl = new ComposerImpl();
      Composer composer = System.currentTimeMillis() >= 0 ? composerImpl : new OtherComposerImpl();
      TextKt.Text("Hello", Modifier.DUMMY, 0L, false, 0, null, null, 10, 0, composer, 0, 382);
      composerImpl.recompose(false);
      composerImpl.recompose(true);
      TextKt.Text("World", Modifier.DUMMY, 0L, false, 0, null, null, 20, 0, composer, 0, 382);
    }
  }
}
