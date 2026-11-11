// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package uncalledentries;

import dagger.Component;
import dagger.Reusable;
import javax.inject.Inject;
import javax.inject.Singleton;

@Singleton
class UsedSingleton1 {
  @Inject
  UsedSingleton1() {}

  String name() {
    return "used1";
  }
}

@Singleton
class UsedSingleton2 {
  @Inject
  UsedSingleton2() {}

  String name() {
    return "used2";
  }
}

@Singleton
class UsedSingleton3 {
  @Inject
  UsedSingleton3() {}

  String name() {
    return "used3";
  }
}

@Reusable
class UsedReusable1 {
  @Inject
  UsedReusable1() {}

  String name() {
    return "usedReusable1";
  }
}

@Reusable
class UsedReusable2 {
  @Inject
  UsedReusable2() {}

  String name() {
    return "usedReusable2";
  }
}

@Singleton
class UnusedEntrySingleton {
  @Inject
  UnusedEntrySingleton() {
    System.out.println("MARKER_UnusedEntrySingleton");
  }
}

@Reusable
class UnusedEntryReusable {
  @Inject
  UnusedEntryReusable() {
    System.out.println("MARKER_UnusedEntryReusable");
  }
}

@Singleton
@Component
interface AppComponent {
  UsedSingleton1 used1();

  UsedSingleton2 used2();

  UsedSingleton3 used3();

  UsedReusable1 usedReusable1();

  UsedReusable2 usedReusable2();

  UnusedEntrySingleton unusedEntrySingleton();

  UnusedEntryReusable unusedEntryReusable();
}

public class Main {
  public static void main(String[] args) {
    AppComponent c = DaggerAppComponent.create();
    System.out.println(c.used1().name());
    System.out.println(c.used2().name());
    System.out.println(c.used3().name());
    System.out.println(c.usedReusable1().name());
    System.out.println(c.usedReusable2().name());
  }
}
