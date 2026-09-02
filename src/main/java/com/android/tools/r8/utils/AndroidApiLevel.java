// Copyright (c) 2017, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8.utils;

import com.android.tools.r8.utils.internal.exceptions.Unreachable;
import com.android.tools.r8.utils.structural.Ordered;
import com.google.common.collect.ImmutableList;
import java.util.List;

/** Android API level description */
public class AndroidApiLevel implements Ordered<AndroidApiLevel> {
  private static final List<AndroidApiLevel> valuesSorted;

  public static final AndroidApiLevel B;
  public static final AndroidApiLevel B_1_1;
  public static final AndroidApiLevel C;
  public static final AndroidApiLevel D;
  public static final AndroidApiLevel E;
  public static final AndroidApiLevel E_0_1;
  public static final AndroidApiLevel E_MR1;
  public static final AndroidApiLevel F;
  public static final AndroidApiLevel G;
  public static final AndroidApiLevel G_MR1;
  public static final AndroidApiLevel H;
  public static final AndroidApiLevel H_MR1;
  public static final AndroidApiLevel H_MR2;
  public static final AndroidApiLevel I;
  public static final AndroidApiLevel I_MR1;
  public static final AndroidApiLevel J;
  public static final AndroidApiLevel J_MR1;
  public static final AndroidApiLevel J_MR2;
  public static final AndroidApiLevel K;
  public static final AndroidApiLevel K_WATCH;
  public static final AndroidApiLevel L;
  public static final AndroidApiLevel L_MR1;
  public static final AndroidApiLevel M;
  public static final AndroidApiLevel N;
  public static final AndroidApiLevel N_MR1;
  public static final AndroidApiLevel O;
  public static final AndroidApiLevel O_MR1;
  public static final AndroidApiLevel P;
  public static final AndroidApiLevel Q;
  public static final AndroidApiLevel R;
  public static final AndroidApiLevel S;
  public static final AndroidApiLevel Sv2;
  public static final AndroidApiLevel T;
  public static final AndroidApiLevel U;
  public static final AndroidApiLevel V;
  public static final AndroidApiLevel BAKLAVA;
  public static final AndroidApiLevel BAKLAVA_1;
  public static final AndroidApiLevel CINNAMON_BUN;
  public static final AndroidApiLevel MAIN;
  // Used for API modeling of Android extension APIs.
  public static final AndroidApiLevel EXTENSION;

  // When updating LATEST and a new version goes public, add a new api-versions.xml to third_party
  // and update the version and generated jar in AndroidApiDatabaseBuilderGeneratorTest. Together
  // with that update third_party/android_jar/libcore_latest/core-oj.jar and run
  // GenerateCovariantReturnTypeMethodsTest.
  public static final AndroidApiLevel LATEST;

  public static final AndroidApiLevel API_DATABASE_LEVEL;

  public static final AndroidApiLevel UNKNOWN;

  /** Constant used to signify some unknown min api when compiling platform. */
  public static final int ANDROID_PLATFORM_CONSTANT = 10_000;

  static {
    ImmutableList.Builder<AndroidApiLevel> builder = ImmutableList.builder();
    builder.add(B = new AndroidApiLevel(1, 0, "B"));
    builder.add(B_1_1 = new AndroidApiLevel(2, 0, "B_1_1"));
    builder.add(C = new AndroidApiLevel(3, 0, "C"));
    builder.add(D = new AndroidApiLevel(4, 0, "D"));
    builder.add(E = new AndroidApiLevel(5, 0, "E"));
    builder.add(E_0_1 = new AndroidApiLevel(6, 0, "E_0_1"));
    builder.add(E_MR1 = new AndroidApiLevel(7, 0, "E_MR1"));
    builder.add(F = new AndroidApiLevel(8, 0, "F"));
    builder.add(G = new AndroidApiLevel(9, 0, "G"));
    builder.add(G_MR1 = new AndroidApiLevel(10, 0, "G_MR1"));
    builder.add(H = new AndroidApiLevel(11, 0, "H"));
    builder.add(H_MR1 = new AndroidApiLevel(12, 0, "H_MR1"));
    builder.add(H_MR2 = new AndroidApiLevel(13, 0, "H_MR2"));
    builder.add(I = new AndroidApiLevel(14, 0, "I"));
    builder.add(I_MR1 = new AndroidApiLevel(15, 0, "I_MR1"));
    builder.add(J = new AndroidApiLevel(16, 0, "J"));
    builder.add(J_MR1 = new AndroidApiLevel(17, 0, "J_MR1"));
    builder.add(J_MR2 = new AndroidApiLevel(18, 0, "J_MR2"));
    builder.add(K = new AndroidApiLevel(19, 0, "K"));
    builder.add(K_WATCH = new AndroidApiLevel(20, 0, "K_WATCH"));
    builder.add(L = new AndroidApiLevel(21, 0, "L"));
    builder.add(L_MR1 = new AndroidApiLevel(22, 0, "L_MR1"));
    builder.add(M = new AndroidApiLevel(23, 0, "M"));
    builder.add(N = new AndroidApiLevel(24, 0, "N"));
    builder.add(N_MR1 = new AndroidApiLevel(25, 0, "N_MR1"));
    builder.add(O = new AndroidApiLevel(26, 0, "O"));
    builder.add(O_MR1 = new AndroidApiLevel(27, 0, "O_MR1"));
    builder.add(P = new AndroidApiLevel(28, 0, "P"));
    builder.add(Q = new AndroidApiLevel(29, 0, "Q"));
    builder.add(R = new AndroidApiLevel(30, 0, "R"));
    builder.add(S = new AndroidApiLevel(31, 0, "S"));
    builder.add(Sv2 = new AndroidApiLevel(32, 0, "Sv2"));
    builder.add(T = new AndroidApiLevel(33, 0, "T"));
    builder.add(U = new AndroidApiLevel(34, 0, "U"));
    builder.add(V = new AndroidApiLevel(35, 0, "V"));
    builder.add(BAKLAVA = new AndroidApiLevel(36, 0, "BAKLAVA"));
    builder.add(BAKLAVA_1 = new AndroidApiLevel(36, 1, "BAKLAVA_1"));
    builder.add(CINNAMON_BUN = new AndroidApiLevel(37, 0, "CINNAMON_BUN"));
    builder.add(MAIN = new AndroidApiLevel(38, 0, "MAIN"));
    builder.add(EXTENSION = new AndroidApiLevel(Integer.MAX_VALUE, 0, "EXTENSION"));
    valuesSorted = builder.build();
    assert valuesSorted.size() == 40;
    assert checkValuesSorted();

    LATEST = CINNAMON_BUN;
    API_DATABASE_LEVEL = LATEST;
    UNKNOWN = MAIN;
    assert UNKNOWN.isGreaterThan(LATEST);
    assert EXTENSION.isGreaterThan(LATEST);
    assert EXTENSION.isGreaterThan(MAIN);
    assert MAIN.isGreaterThan(LATEST);
  }

  private final int major;
  private final int minor;
  private final String name;

  private AndroidApiLevel(int major, int minor, String name) {
    this.major = major;
    this.minor = minor;
    this.name = name;
  }

  private static boolean checkValuesSorted() {
    for (int i = 1; i < valuesSorted.size(); i++) {
      assert valuesSorted.get(i - 1).isLessThan(valuesSorted.get(i));
    }
    return true;
  }

  public int getMajor() {
    return major;
  }

  public int getMinor() {
    return minor;
  }

  public String getName() {
    return "Android " + name;
  }

  public String getNumericString() {
    return getNumericString(major, minor);
  }

  private static String getNumericString(int major, int minor) {
    return minor == 0 ? Integer.toString(major) : major + "." + minor;
  }

  public static AndroidApiLevel getDefault() {
    return AndroidApiLevel.B;
  }

  public AndroidApiLevel max(AndroidApiLevel other) {
    return Ordered.max(this, other);
  }

  public AndroidApiLevel min(AndroidApiLevel other) {
    return Ordered.min(this, other);
  }

  public DexVersion getDexVersion() {
    return DexVersion.getDexVersion(this);
  }

  public AndroidApiLevel getNextMajorLevel() {
    if (this.isEqualTo(LATEST)) {
      return MAIN;
    }
    return getAndroidApiLevel(getMajor() + 1, 0);
  }

  public static List<AndroidApiLevel> getAndroidApiLevelsSorted() {
    return valuesSorted;
  }

  public static AndroidApiLevel getMinAndroidApiLevel(DexVersion dexVersion) {
    switch (dexVersion) {
      case V35:
        return AndroidApiLevel.B;
      case V37:
        return AndroidApiLevel.N;
      case V38:
        return AndroidApiLevel.O;
      case V39:
        return AndroidApiLevel.P;
      case V40:
        return AndroidApiLevel.R;
      case V41:
        assert InternalOptions.containerDexApiLevel().isEqualTo(AndroidApiLevel.BAKLAVA);
        return AndroidApiLevel.BAKLAVA;
      default:
        throw new Unreachable();
    }
  }

  @Deprecated
  @SuppressWarnings("InlineMeSuggester")
  public static AndroidApiLevel getAndroidApiLevel(int major) {
    return getAndroidApiLevel(major, 0);
  }

  /**
   * @throws IllegalArgumentException if the parsed API version is invalid (e.g. 99.99).
   */
  public static AndroidApiLevel getAndroidApiLevel(int major, int minor) {
    assert CINNAMON_BUN == LATEST; // This has to be updated when new API levels are added.
    switch (major) {
      case 1:
        if (minor == 0) {
          return B;
        }
        break;
      case 2:
        if (minor == 0) {
          return B_1_1;
        }
        break;
      case 3:
        if (minor == 0) {
          return C;
        }
        break;
      case 4:
        if (minor == 0) {
          return D;
        }
        break;
      case 5:
        if (minor == 0) {
          return E;
        }
        break;
      case 6:
        if (minor == 0) {
          return E_0_1;
        }
        break;
      case 7:
        if (minor == 0) {
          return E_MR1;
        }
        break;
      case 8:
        if (minor == 0) {
          return F;
        }
        break;
      case 9:
        if (minor == 0) {
          return G;
        }
        break;
      case 10:
        if (minor == 0) {
          return G_MR1;
        }
        break;
      case 11:
        if (minor == 0) {
          return H;
        }
        break;
      case 12:
        if (minor == 0) {
          return H_MR1;
        }
        break;
      case 13:
        if (minor == 0) {
          return H_MR2;
        }
        break;
      case 14:
        if (minor == 0) {
          return I;
        }
        break;
      case 15:
        if (minor == 0) {
          return I_MR1;
        }
        break;
      case 16:
        if (minor == 0) {
          return J;
        }
        break;
      case 17:
        if (minor == 0) {
          return J_MR1;
        }
        break;
      case 18:
        if (minor == 0) {
          return J_MR2;
        }
        break;
      case 19:
        if (minor == 0) {
          return K;
        }
        break;
      case 20:
        if (minor == 0) {
          return K_WATCH;
        }
        break;
      case 21:
        if (minor == 0) {
          return L;
        }
        break;
      case 22:
        if (minor == 0) {
          return L_MR1;
        }
        break;
      case 23:
        if (minor == 0) {
          return M;
        }
        break;
      case 24:
        if (minor == 0) {
          return N;
        }
        break;
      case 25:
        if (minor == 0) {
          return N_MR1;
        }
        break;
      case 26:
        if (minor == 0) {
          return O;
        }
        break;
      case 27:
        if (minor == 0) {
          return O_MR1;
        }
        break;
      case 28:
        if (minor == 0) {
          return P;
        }
        break;
      case 29:
        if (minor == 0) {
          return Q;
        }
        break;
      case 30:
        if (minor == 0) {
          return R;
        }
        break;
      case 31:
        if (minor == 0) {
          return S;
        }
        break;
      case 32:
        if (minor == 0) {
          return Sv2;
        }
        break;
      case 33:
        if (minor == 0) {
          return T;
        }
        break;
      case 34:
        if (minor == 0) {
          return U;
        }
        break;
      case 35:
        if (minor == 0) {
          return V;
        }
        break;
      case 36:
        if (minor == 0) {
          return BAKLAVA;
        }
        if (minor == 1) {
          return BAKLAVA_1;
        }
        break;
      case 37:
        if (minor == 0) {
          return CINNAMON_BUN;
        }
        break;
      case 38:
        if (minor == 0) {
          return MAIN;
        }
        break;
      default:
        // All future versions (e.g. platforms API 10_000 returns MAIN).
        if (major > 0) {
          return MAIN;
        }
        break;
    }
    throw new IllegalArgumentException(
        "Unsupported or invalid Android API level: " + getNumericString(major, minor));
  }

  /**
   * Parses strings like {@code <int>} or {@code <int>.<int>}.
   *
   * @throws NumberFormatException if the number parts are malformatted
   * @throws IllegalArgumentException if the parsed API version is invalid (e.g. 99.99).
   */
  public static AndroidApiLevel parseAndroidApiLevel(String apiLevel) {
    int dotPosition = apiLevel.indexOf('.');
    if (dotPosition == -1) {
      return AndroidApiLevel.getAndroidApiLevel(Integer.parseInt(apiLevel), 0);
    } else {
      String majorApiLevel = apiLevel.substring(0, dotPosition);
      String minorApiLevel = apiLevel.substring(dotPosition + 1);
      return AndroidApiLevel.getAndroidApiLevel(
          Integer.parseInt(majorApiLevel), Integer.parseInt(minorApiLevel));
    }
  }

  public byte serializeAsByte() {
    if (this == EXTENSION) {
      return 0x7f;
    }
    assert major < 64;
    assert minor < 2; // Re-evaluate this when minor can be higher than 1.
    return (byte) (major << 1 | minor);
  }

  public static AndroidApiLevel deserializeFromByte(byte b) {
    if (b == 0x7f) {
      return EXTENSION;
    }
    int i = Byte.toUnsignedInt(b);
    int major = i >> 1;
    int minor = i & 0x1;
    return getAndroidApiLevel(major, minor);
  }

  @Override
  public int compareTo(AndroidApiLevel other) {
    return major != other.major ? major - other.major : minor - other.minor;
  }

  @Override
  public String toString() {
    return getName();
  }
}
