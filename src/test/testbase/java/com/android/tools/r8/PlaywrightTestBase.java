// Copyright (c) 2026, the R8 project authors. Please see the AUTHORS file
// for details. All rights reserved. Use of this source code is governed by a
// BSD-style license that can be found in the LICENSE file.
package com.android.tools.r8;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserType;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Playwright;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import org.junit.After;
import org.junit.AfterClass;
import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

public class PlaywrightTestBase extends TestBase {

  @Rule public TemporaryFolder tempFolder = new TemporaryFolder();

  private static Playwright playwright;
  private static Browser browser;
  private Page page;

  public Page getPage(boolean headful) {
    if (page == null) {
      initPage(headful);
    }
    return page;
  }

  private static synchronized void initBrowser(boolean headful) {
    if (browser != null) {
      return;
    }
    boolean headless = !headful;
    Path chromeExecutable = headless ? resolveChromeExecutable() : null;

    Map<String, String> env = new HashMap<>();
    if (headless) {
      env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "1");
    } else {
      // Allow downloading when headful. We set it to 0 to override any inherited env var.
      env.put("PLAYWRIGHT_SKIP_BROWSER_DOWNLOAD", "0");
    }

    Playwright.CreateOptions createOptions = new Playwright.CreateOptions().setEnv(env);
    playwright = Playwright.create(createOptions);

    BrowserType.LaunchOptions launchOptions = new BrowserType.LaunchOptions();
    if (headless) {
      if (chromeExecutable == null) {
        throw new RuntimeException("chrome-headless-shell binary is missing");
      }
      launchOptions.setExecutablePath(chromeExecutable);
      launchOptions.setHeadless(true);
    } else {
      launchOptions.setHeadless(false);
      launchOptions.setSlowMo(50);
    }

    browser = playwright.chromium().launch(launchOptions);
  }

  private void initPage(boolean headful) {
    initBrowser(headful);
    page = browser.newPage();
  }

  @After
  public void tearDownPage() {
    if (page != null) {
      page.close();
      page = null;
    }
  }

  @AfterClass
  public static void tearDownBrowser() {
    if (browser != null) {
      browser.close();
      browser = null;
    }
    if (playwright != null) {
      playwright.close();
      playwright = null;
    }
  }


  private static Path resolveChromeExecutable() {
    Path chromeDir = Paths.get(ToolHelper.THIRD_PARTY_DIR, "chrome_headless");
    if (!Files.exists(chromeDir)) {
      return null;
    }

    boolean isMac = ToolHelper.isMac();
    boolean isWindows = ToolHelper.isWindows();
    boolean isLinux = ToolHelper.isLinux();

    if (isMac) {
      chromeDir = chromeDir.resolve("mac");
    } else if (isLinux) {
      chromeDir = chromeDir.resolve("linux");
    } else if (isWindows) {
      chromeDir = chromeDir.resolve("windows");
    }

    if (!Files.exists(chromeDir)) {
      return null;
    }

    String[] executables = {
      "chrome-headless-shell", "chrome", "chromium", "chrome-headless-shell.exe", "chrome.exe"
    };

    String macArch = "";
    if (isMac) {
      String arch = System.getProperty("os.arch").toLowerCase();
      if (arch.contains("aarch64") || arch.contains("arm64")) {
        macArch = "mac-arm64";
      } else {
        macArch = "mac-x64";
      }
    }
    final String targetMacArch = macArch;

    try {
      return Files.walk(chromeDir, 3)
          .filter(
              p -> {
                String name = p.getFileName().toString();
                if (isMac && !p.toString().contains(targetMacArch)) {
                  return false;
                }
                for (String exec : executables) {
                  if (name.equalsIgnoreCase(exec) && Files.isExecutable(p)) {
                    return true;
                  }
                }
                return false;
              })
          .findFirst()
          .orElse(null);
    } catch (IOException e) {
      return null;
    }
  }
}
