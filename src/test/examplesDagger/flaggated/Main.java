// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package flaggated;

import dagger.Component;
import dagger.Lazy;
import dagger.Module;
import dagger.Provides;
import dagger.Reusable;
import javax.inject.Inject;
import javax.inject.Provider;
import javax.inject.Singleton;

final class FeatureFlag {
  static boolean isEnabled() {
    return false;
  }
}

@Singleton
class UsedSingleton {
  @Inject
  UsedSingleton() {}

  String name() {
    return "used";
  }
}

@Singleton
class FlaggedSingleton {
  @Inject
  FlaggedSingleton() {
    System.out.println("MARKER_FlaggedSingleton");
  }

  String name() {
    return "flaggedSingleton";
  }
}

@Reusable
class FlaggedReusable {
  @Inject
  FlaggedReusable() {
    System.out.println("MARKER_FlaggedReusable");
  }

  String name() {
    return "flaggedReusable";
  }
}

@Singleton
class FlaggedLazySingleton {
  @Inject
  FlaggedLazySingleton() {
    System.out.println("MARKER_FlaggedLazySingleton");
  }

  String name() {
    return "flaggedLazy";
  }
}

@Module
final class BannerModule {
  @Provides
  static String provideBanner(
      UsedSingleton used,
      Provider<FlaggedSingleton> flaggedSingleton,
      Provider<FlaggedReusable> flaggedReusable,
      Lazy<FlaggedLazySingleton> flaggedLazy) {
    if (FeatureFlag.isEnabled()) {
      return flaggedSingleton.get().name()
          + ":"
          + flaggedReusable.get().name()
          + ":"
          + flaggedLazy.get().name();
    }
    return used.name();
  }
}

@Singleton
@Component(modules = BannerModule.class)
interface AppComponent {
  UsedSingleton used();

  String banner();
}

public class Main {
  public static void main(String[] args) {
    AppComponent c = DaggerAppComponent.create();
    System.out.println(c.used().name());
    System.out.println(c.banner());
  }
}
