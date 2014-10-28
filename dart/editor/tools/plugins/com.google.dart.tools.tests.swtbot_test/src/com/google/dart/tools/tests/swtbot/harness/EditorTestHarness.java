/*
 * Copyright 2014 Dart project authors.
 * 
 * Licensed under the Eclipse Public License v1.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 * 
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.dart.tools.tests.swtbot.harness;

import com.google.dart.engine.sdk.DirectoryBasedDartSdk;

import org.eclipse.swtbot.eclipse.finder.SWTWorkbenchBot;
import org.eclipse.swtbot.swt.finder.junit.SWTBotJunit4ClassRunner;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import java.io.File;

@RunWith(SWTBotJunit4ClassRunner.class)
public class EditorTestHarness {
  protected static SWTWorkbenchBot bot;

  public static void closeAllEditors() {
    bot.closeAllEditors(); // Note that this will close the Welcome page!
  }

  @AfterClass
  public static void saveAllEditors() {
    bot.saveAllEditors();
  }

  @BeforeClass
  public static void setUp() throws Exception {
    bot = new SWTWorkbenchBot();
  }

  public boolean isDartiumInstalled() {
    File sdkDirectory = DirectoryBasedDartSdk.getDefaultSdkDirectory();
    if (sdkDirectory == null) {
      return false;
    }
    return new DirectoryBasedDartSdk(sdkDirectory).isDartiumInstalled();
  }
}
